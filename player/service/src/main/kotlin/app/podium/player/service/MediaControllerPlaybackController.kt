package app.replay.player.service

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.replay.core.model.MediaKind
import app.replay.core.model.AudioQuality
import app.replay.core.model.Codec
import app.replay.core.model.QualityReport
import app.replay.core.model.QueueUid
import app.replay.core.model.SourceId
import app.replay.core.model.TrackId
import app.replay.player.api.EnginePhase
import app.replay.player.api.NowPlayingItem
import app.replay.player.api.PauseReason
import app.replay.player.api.PlayIntent
import app.replay.player.api.PlaybackController
import app.replay.player.api.PlaybackSnapshot
import app.replay.player.api.PlaybackStatusMapper
import app.replay.player.api.Recovery
import app.replay.player.api.PlayerFacts
import app.replay.player.api.QueueEntry
import app.replay.player.api.QueueOrigin
import app.replay.player.api.QueueView
import app.replay.player.api.RepeatMode
import app.replay.sources.api.ResolutionPath
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * The UI's PlaybackController, backed by a Media3 MediaController connected to [PlaybackService].
 * Must be used from the main thread. Commands issued before the connection completes are queued.
 */
class MediaControllerPlaybackController(
    private val context: Context,
    private val scope: CoroutineScope,
) : PlaybackController {

    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    override val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private val _queue = MutableStateFlow(QueueView())
    override val queue: StateFlow<QueueView> = _queue.asStateFlow()

    private val connected = CompletableDeferred<MediaController>()
    private var controller: MediaController? = null
    private var sessionExtras: Bundle = Bundle.EMPTY
    private var readyMediaId: String? = null
    private var lastPauseReason = PauseReason.USER

    private var connecting = false

    fun connect() {
        if (connecting || controller != null) return
        connecting = true
        scope.launch {
            try {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val built = MediaController.Builder(context, token)
                    .setListener(object : MediaController.Listener {
                        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                            sessionExtras = extras
                            publish()
                        }
                    })
                    .buildAsync()
                    .await()
                controller = built
                sessionExtras = built.sessionExtras
                built.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) = publish()

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        lastPauseReason = when (reason) {
                            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> PauseReason.FOCUS_LOSS
                            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> PauseReason.BECAME_NOISY
                            Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> PauseReason.REMOTE
                            else -> PauseReason.USER
                        }
                    }
                })
                connected.complete(built)
                publish()
            } catch (t: Throwable) {
                connecting = false
                Log.e(TAG, "Could not connect to the playback service", t)
            }
        }
    }

    fun release() {
        controller?.release()
        controller = null
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let(block) ?: scope.launch { block(connected.await()) }
    }

    private fun send(action: String, args: Bundle) = withController { c ->
        c.sendCustomCommand(ReplaySessionCommands.command(action), args)
    }

    // --- Commands ---------------------------------------------------------------------------------

    override fun positionMs(): Long = controller?.currentPosition ?: 0L

    override fun play() = withController { it.play() }
    override fun pause() = withController { it.pause() }
    override fun togglePlayPause() = withController { if (it.playWhenReady) it.pause() else it.play() }
    override fun next() = withController { it.seekToNext() }
    override fun previous() = withController { it.seekToPrevious() }
    override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    override fun setRepeat(mode: RepeatMode) = withController {
        it.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    override fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }

    override fun playContext(tracks: List<TrackId>, startIndex: Int, contextLabel: String?, shuffle: Boolean, radio: Boolean) =
        send(
            ReplaySessionCommands.SET_CONTEXT,
            Bundle().apply {
                putStringArray(ReplaySessionCommands.ARG_TRACK_IDS, tracks.map { it.value }.toTypedArray())
                putInt(ReplaySessionCommands.ARG_START_INDEX, startIndex)
                putString(ReplaySessionCommands.ARG_CONTEXT_LABEL, contextLabel)
                putBoolean(ReplaySessionCommands.ARG_SHUFFLE, shuffle)
                putBoolean(ReplaySessionCommands.ARG_RADIO, radio)
            },
        )

    override fun playNext(tracks: List<TrackId>) = send(
        ReplaySessionCommands.PLAY_NEXT,
        Bundle().apply { putStringArray(ReplaySessionCommands.ARG_TRACK_IDS, tracks.map { it.value }.toTypedArray()) },
    )

    override fun addToQueue(tracks: List<TrackId>) = send(
        ReplaySessionCommands.ADD_TO_QUEUE,
        Bundle().apply { putStringArray(ReplaySessionCommands.ARG_TRACK_IDS, tracks.map { it.value }.toTypedArray()) },
    )

    override fun move(uid: QueueUid, toIndex: Int) = send(
        ReplaySessionCommands.MOVE,
        Bundle().apply {
            putString(ReplaySessionCommands.ARG_UID, uid.value)
            putInt(ReplaySessionCommands.ARG_TO_INDEX, toIndex)
        },
    )

    override fun remove(uids: Set<QueueUid>) = send(
        ReplaySessionCommands.REMOVE,
        Bundle().apply { putStringArray(ReplaySessionCommands.ARG_UIDS, uids.map { it.value }.toTypedArray()) },
    )

    override fun skipTo(uid: QueueUid) = send(
        ReplaySessionCommands.SKIP_TO,
        Bundle().apply { putString(ReplaySessionCommands.ARG_UID, uid.value) },
    )

    override fun clearUpcoming() = send(ReplaySessionCommands.CLEAR_UPCOMING, Bundle())

    // --- State -------------------------------------------------------------------------------------

    private fun publish() {
        val c = controller ?: return
        val current = c.currentMediaItem
        if (c.playbackState == Player.STATE_READY) readyMediaId = current?.mediaId
        val extrasForItem = sessionExtras.takeIf { it.getString(ReplayExtras.CURRENT_UID) == current?.mediaId }
        val recovery = extrasForItem?.getString(ReplayExtras.RECOVERY)?.let { runCatching { Recovery.valueOf(it) }.getOrNull() }
        // While a refused stream is being replaced, the song is loading again, not failing.
        val refreshing = recovery == Recovery.REFRESHING_STREAM
        val error = if (refreshing) null else c.playerError?.let {
            PlaybackErrors.fromKind(extrasForItem?.getString(ReplayExtras.LAST_ERROR)) ?: PlaybackErrors.fromCode(it.errorCode)
        }
        val facts = PlayerFacts(
            phase = if (refreshing) EnginePhase.BUFFERING else when (c.playbackState) {
                Player.STATE_BUFFERING -> EnginePhase.BUFFERING
                Player.STATE_READY -> EnginePhase.READY
                Player.STATE_ENDED -> EnginePhase.ENDED
                else -> EnginePhase.IDLE
            },
            playWhenReady = c.playWhenReady,
            isPlaying = c.isPlaying,
            hasItems = c.mediaItemCount > 0,
            currentItemWasReady = current != null && current.mediaId == readyMediaId,
            suppressed = c.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            error = error,
            pauseReason = lastPauseReason,
        )
        val extras = sessionExtras
        val uid = current?.mediaId
        val sameItem = extras.getString(ReplayExtras.CURRENT_UID) == uid
        val item = current?.let { toNowPlaying(it, c.currentMediaItemIndex, c.mediaItemCount, extras.takeIf { sameItem }) }
        _snapshot.value = PlaybackSnapshot(
            status = PlaybackStatusMapper.map(facts),
            intent = if (c.playWhenReady) PlayIntent.PLAY else PlayIntent.PAUSE,
            item = item,
            repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            shuffleEnabled = c.shuffleModeEnabled,
            quality = QualityReport(
                sourceClaimed = if (sameItem) claimedQuality(extras) else null,
                actualMedia = AudioQualityMapper.fromTracks(c.currentTracks),
            ),
            recovery = recovery,
        )
        _queue.value = QueueView(
            entries = (0 until c.mediaItemCount).map { index -> toEntry(c.getMediaItemAt(index), index == c.currentMediaItemIndex) },
            currentIndex = c.currentMediaItemIndex,
            contextLabel = extras.getString(ReplayExtras.CONTEXT_LABEL),
        )
    }

    private fun toNowPlaying(item: MediaItem, index: Int, size: Int, extras: Bundle?): NowPlayingItem {
        val md = item.mediaMetadata
        return NowPlayingItem(
            uid = QueueUid(item.mediaId),
            trackId = TrackId(md.extras?.getString(ReplayExtras.TRACK_ID) ?: "unknown|${item.mediaId}"),
            title = md.title?.toString().orEmpty(),
            artistDisplay = md.artist?.toString().orEmpty(),
            albumTitle = md.albumTitle?.toString(),
            artworkUri = md.artworkUri?.toString(),
            durationMs = md.durationMs,
            indexInQueue = index,
            queueSize = size,
            servedByDisplayName = extras?.getString(ReplayExtras.SERVED_BY),
            resolutionPath = extras?.getString(ReplayExtras.RESOLUTION_PATH)?.let { runCatching { ResolutionPath.valueOf(it) }.getOrNull() },
            servedBy = extras?.getString(ReplayExtras.SERVED_BY_ID)?.takeIf { it.isNotBlank() }?.let(::SourceId),
            kind = md.extras?.getString(ReplayExtras.MEDIA_KIND)?.let { runCatching { MediaKind.valueOf(it) }.getOrNull() } ?: MediaKind.SONG,
        )
    }

    private fun toEntry(item: MediaItem, isCurrent: Boolean): QueueEntry {
        val md = item.mediaMetadata
        return QueueEntry(
            uid = QueueUid(item.mediaId),
            trackId = TrackId(md.extras?.getString(ReplayExtras.TRACK_ID) ?: "unknown|${item.mediaId}"),
            title = md.title?.toString().orEmpty(),
            artistDisplay = md.artist?.toString().orEmpty(),
            artworkUri = md.artworkUri?.toString(),
            durationMs = md.durationMs,
            origin = md.extras?.getString(ReplayExtras.ORIGIN)?.let { runCatching { QueueOrigin.valueOf(it) }.getOrNull() }
                ?: QueueOrigin.CONTEXT,
            isCurrent = isCurrent,
        )
    }

    private fun claimedQuality(extras: Bundle): AudioQuality? {
        val codec = extras.getString(ReplayExtras.CLAIMED_CODEC)?.let { runCatching { Codec.valueOf(it) }.getOrNull() }
            ?: return null
        fun int(key: String) = if (extras.containsKey(key)) extras.getInt(key) else null
        return AudioQuality(
            codec = codec,
            bitrateKbps = int(ReplayExtras.CLAIMED_BITRATE),
            sampleRateHz = int(ReplayExtras.CLAIMED_SAMPLE_RATE),
            bitDepth = int(ReplayExtras.CLAIMED_BIT_DEPTH),
        )
    }

    private companion object {
        const val TAG = "ReplayController"
    }
}
