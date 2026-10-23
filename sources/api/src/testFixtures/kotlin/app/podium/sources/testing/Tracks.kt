package app.replay.sources.testing

import app.replay.core.model.AlbumRef
import app.replay.core.model.ArtistCredit
import app.replay.core.model.ArtistRole
import app.replay.core.model.Explicitness
import app.replay.core.model.RecordingIdentifiers
import app.replay.core.model.SourceId
import app.replay.core.model.SourceRef
import app.replay.core.model.Track
import app.replay.core.model.TrackId

/** Concise track builder for tests. */
fun track(
    title: String,
    artist: String = "Test Artist",
    source: String = "test",
    key: String = "${title.hashCode()}-${artist.hashCode()}",
    album: String? = null,
    durationSec: Double? = 200.0,
    isrc: String? = null,
    mbid: String? = null,
    explicitness: Explicitness = Explicitness.UNKNOWN,
    featured: List<String> = emptyList(),
): Track {
    val sourceId = SourceId(source)
    return Track(
        id = TrackId.of(sourceId, key),
        source = SourceRef(sourceId, key),
        title = title,
        artists = listOf(ArtistCredit(artist)) + featured.map { ArtistCredit(it, ArtistRole.FEATURED) },
        artistDisplay = artist,
        album = album?.let { AlbumRef(it) },
        durationMs = durationSec?.let { (it * 1000).toLong() },
        identifiers = RecordingIdentifiers(isrc = isrc, musicBrainzRecordingId = mbid),
        explicitness = explicitness,
    )
}
