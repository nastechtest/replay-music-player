package app.replay.core.database

import androidx.test.core.app.ApplicationProvider
import app.replay.core.model.AlbumId
import app.replay.core.model.AlbumRef
import app.replay.core.model.ArtistCredit
import app.replay.core.model.ArtistId
import app.replay.core.model.ArtworkRef
import app.replay.core.model.PartialDate
import app.replay.core.model.SourceId
import app.replay.core.model.SourceRef
import app.replay.core.model.Track
import app.replay.core.model.TrackId

/** An in-memory database on the same bundled SQLite (FTS5 included) the app ships. */
internal fun testDatabase(): ReplayDatabase = ReplayDatabase.createInMemory(ApplicationProvider.getApplicationContext())

internal val Local = SourceId("local")
internal val Server = SourceId("server")

/** A library song with the metadata a real source gives: album and artist ids, numbering, art. */
internal fun song(
    title: String,
    artist: String = "Northern Sines",
    album: String = "Woodland",
    source: SourceId = Local,
    key: String = title.lowercase().replace(' ', '-'),
    disc: Int? = 1,
    number: Int? = null,
    year: Int? = 2024,
): Track = Track(
    id = TrackId.of(source, key),
    source = SourceRef(source, key),
    title = title,
    artists = listOf(ArtistCredit(artist, id = ArtistId.of(source, "ar-" + artist.lowercase()))),
    album = AlbumRef(album, AlbumId.of(source, "al-" + album.lowercase()), artist),
    durationMs = 180_000,
    artwork = ArtworkRef(source, "al-" + album.lowercase()),
    releaseDate = year?.let { PartialDate(it) },
    discNumber = disc,
    trackNumber = number,
)
