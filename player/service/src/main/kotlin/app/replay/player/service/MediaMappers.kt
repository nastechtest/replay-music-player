@file:OptIn(UnstableApi::class)

package app.replay.player.service

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import app.replay.core.model.MediaKind
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import app.replay.core.common.ReplayError
import app.replay.core.model.AudioQuality
import app.replay.core.model.Codec
import app.replay.core.model.QueueUid
import app.replay.player.api.QueueItem
import app.replay.sources.api.HealthOutcome

/** The URI ExoPlayer sees for a queue item; resolved to real media just before it is opened. */
object QueueMediaItems {
    private const val SCHEME = "replay"
    private const val HOST = "queue"

    fun uriFor(uid: QueueUid): Uri = Uri.Builder().scheme(SCHEME).authority(HOST).appendPath(uid.value).build()

    fun uidOf(uri: Uri): QueueUid? =
        if (uri.scheme == SCHEME && uri.authority == HOST) uri.lastPathSegment?.let(::QueueUid) else null

    fun toMediaItem(item: QueueItem): MediaItem {
        val track = item.track
        val extras = Bundle().apply {
            putString(ReplayExtras.TRACK_ID, track.id.value)
            putString(ReplayExtras.ORIGIN, item.origin.name)
            putString(ReplayExtras.MEDIA_KIND, track.kind.name)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistDisplay)
            .setAlbumTitle(track.album?.title)
            .setAlbumArtist(track.album?.albumArtist)
            .setArtworkUri(track.artwork?.uri?.let(Uri::parse))
            .setDurationMs(track.durationMs)
            .setTrackNumber(track.trackNumber)
            .setDiscNumber(track.discNumber)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(if (track.kind == MediaKind.LIVE) MediaMetadata.MEDIA_TYPE_RADIO_STATION else MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(item.uid.value)
            .setUri(uriFor(item.uid))
            .setMediaMetadata(metadata)
            .apply {
                // A live station (D-90) is a live HLS playlist: the player must know before it loads.
                if (track.kind == MediaKind.LIVE) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                    setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
                }
            }
            .build()
    }
}

/** Actual media quality, measured from the decoder's selected audio format. */
object AudioQualityMapper {

    fun fromTracks(tracks: Tracks): AudioQuality? {
        val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected } ?: return null
        val index = (0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: return null
        return fromFormat(group.getTrackFormat(index))
    }

    fun fromFormat(format: Format): AudioQuality {
        val codec = codecOf(format)
        val bitDepth = bitDepthOf(format.pcmEncoding)
        val sampleRate = format.sampleRate.takeIf { it != Format.NO_VALUE }
        val channels = format.channelCount.takeIf { it != Format.NO_VALUE }
        val reportedBitrate = listOf(format.averageBitrate, format.bitrate, format.peakBitrate)
            .firstOrNull { it != Format.NO_VALUE }?.let { it / 1000 }
        val bitrate = reportedBitrate ?: if (codec == Codec.PCM && sampleRate != null && channels != null && bitDepth != null) {
            sampleRate * channels * bitDepth / 1000
        } else null
        return AudioQuality(
            codec = codec,
            container = format.containerMimeType,
            bitrateKbps = bitrate,
            sampleRateHz = sampleRate,
            bitDepth = bitDepth?.takeIf { codec?.isLossless == true },
            channels = channels,
        )
    }

    fun codecOf(format: Format): Codec? = when (format.sampleMimeType) {
        MimeTypes.AUDIO_FLAC -> Codec.FLAC
        MimeTypes.AUDIO_ALAC -> Codec.ALAC
        MimeTypes.AUDIO_RAW, MimeTypes.AUDIO_WAV -> Codec.PCM
        MimeTypes.AUDIO_AAC -> Codec.AAC
        MimeTypes.AUDIO_OPUS -> Codec.OPUS
        MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L1, MimeTypes.AUDIO_MPEG_L2 -> Codec.MP3
        MimeTypes.AUDIO_VORBIS -> Codec.VORBIS
        MimeTypes.AUDIO_AC3 -> Codec.AC3
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> Codec.EAC3
        null -> null
        else -> Codec.OTHER
    }

    private fun bitDepthOf(pcmEncoding: Int): Int? = when (pcmEncoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 32
        else -> null
    }
}

/** Raised by the data-source resolver when a queue item couldn't be resolved before opening. */
class ReplayResolveException(val healthOutcome: HealthOutcome?, message: String) : java.io.IOException(message)

/** Maps player errors to Replay's classified errors — raw exception text never reaches the UI. */
object PlaybackErrors {
    /**
     * The server refused the stream URL (401/403/404/410) — for a signed, expiring URL that means
     * it went stale or was revoked, and the same source can be asked for a fresh one.
     */
    fun isRefusedStream(error: PlaybackException): Boolean {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode in REFUSED_CODES
            cause = cause.cause
        }
        return false
    }

    private val REFUSED_CODES = setOf(401, 403, 404, 410)

    /**
     * The engine's own classification (published by kind, [ReplayExtras.LAST_ERROR]) back as an
     * error, so the UI says what actually happened rather than what an error code suggests.
     */
    fun fromKind(kind: String?): ReplayError? = when (kind) {
        "Offline" -> ReplayError.Offline
        "Network" -> ReplayError.Network()
        "Server" -> ReplayError.Server()
        "AuthRequired" -> ReplayError.AuthRequired()
        "AuthExpired" -> ReplayError.AuthExpired()
        "RateLimited" -> ReplayError.RateLimited()
        "NotFound" -> ReplayError.NotFound("stream")
        "InvalidMedia" -> ReplayError.InvalidMedia()
        "UnsupportedFormat" -> ReplayError.UnsupportedFormat()
        "PolicyDisabled" -> ReplayError.PolicyDisabled()
        "NotPlayable" -> ReplayError.NotPlayable("stream")
        "Unexpected" -> ReplayError.Unexpected()
        else -> null
    }

    fun classify(error: PlaybackException): ReplayError {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is ReplayResolveException) return fromHealth(cause.healthOutcome)
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return when (cause.responseCode) {
                    401, 403 -> ReplayError.AuthRequired()
                    404, 410 -> ReplayError.NotFound("stream")
                    429 -> ReplayError.RateLimited()
                    else -> ReplayError.Server(cause.responseCode)
                }
            }
            cause = cause.cause
        }
        return fromCode(error.errorCode)
    }

    fun fromCode(code: Int): ReplayError = when (code) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> ReplayError.Network()
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> ReplayError.NotFound("file")
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> ReplayError.PermissionRequired("storage")
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_DECODING_FAILED -> ReplayError.InvalidMedia()
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> ReplayError.UnsupportedFormat()
        else -> ReplayError.Unexpected("code $code")
    }

    /** Which health outcome a player error means for the source that served the media. */
    fun healthOutcomeOf(error: ReplayError): HealthOutcome = when (error) {
        is ReplayError.Network, ReplayError.Offline -> HealthOutcome.NETWORK_FAILURE
        is ReplayError.AuthRequired -> HealthOutcome.AUTH_FAILURE
        is ReplayError.RateLimited -> HealthOutcome.RATE_LIMIT
        is ReplayError.Server -> HealthOutcome.SERVER_ERROR
        is ReplayError.InvalidMedia, is ReplayError.UnsupportedFormat -> HealthOutcome.INVALID_MEDIA
        is ReplayError.NotFound -> HealthOutcome.MISS
        is ReplayError.PolicyDisabled -> HealthOutcome.POLICY_DISABLED
        else -> HealthOutcome.UNKNOWN
    }

    private fun fromHealth(outcome: HealthOutcome?): ReplayError = when (outcome) {
        HealthOutcome.NETWORK_FAILURE -> ReplayError.Network()
        HealthOutcome.AUTH_FAILURE -> ReplayError.AuthRequired()
        HealthOutcome.RATE_LIMIT -> ReplayError.RateLimited()
        HealthOutcome.SERVER_ERROR -> ReplayError.Server()
        HealthOutcome.INVALID_MEDIA -> ReplayError.InvalidMedia()
        HealthOutcome.POLICY_DISABLED -> ReplayError.PolicyDisabled()
        null, HealthOutcome.MISS -> ReplayError.NotFound("stream")
        else -> ReplayError.Unexpected()
    }
}
