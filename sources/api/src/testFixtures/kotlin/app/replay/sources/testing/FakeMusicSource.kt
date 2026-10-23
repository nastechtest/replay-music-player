package app.replay.sources.testing

import app.replay.core.common.Outcome
import app.replay.core.common.ReplayError
import app.replay.core.model.AlbumId
import app.replay.core.model.ArtistId
import app.replay.core.model.PlaybackRoute
import app.replay.core.model.SourceId
import app.replay.core.model.SourceRef
import app.replay.core.model.Track
import app.replay.core.model.TrackId
import app.replay.sources.api.AlbumDetail
import app.replay.sources.api.ArtistDetail
import app.replay.sources.api.Basis
import app.replay.sources.api.Capability
import app.replay.sources.api.CapabilityState
import app.replay.sources.api.CatalogFacet
import app.replay.sources.api.FacetResolution
import app.replay.sources.api.MusicSource
import app.replay.sources.api.PlayableMedia
import app.replay.sources.api.PlaybackFacet
import app.replay.sources.api.PlaybackTarget
import app.replay.sources.api.Purpose
import app.replay.sources.api.QualityRequest
import app.replay.sources.api.SearchQuery
import app.replay.sources.api.SearchResults
import app.replay.sources.api.SourceCapabilities
import app.replay.sources.api.SourceDescriptor
import app.replay.sources.api.matching.TrackNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A scriptable in-memory source for tests. Resolution outcomes can be overridden per track; every
 * call is counted so tests can assert "resolved once", "never asked", etc.
 */
class FakeMusicSource(
    id: String,
    tracks: List<Track> = emptyList(),
    basis: Basis = Basis.LOCAL_DEVICE,
    capabilities: SourceCapabilities = SourceCapabilities.available(
        Capability.SEARCH, Capability.BROWSE, Capability.LIBRARY, Capability.DIRECT_STREAM, Capability.ARTWORK,
    ),
    private val expiresAfterMillis: Long? = null,
    private val clock: () -> Long = { 0L },
) : MusicSource {

    val sourceId = SourceId(id)
    private val catalogTracks = tracks.associateBy { it.id }.toMutableMap()
    val outcomes = mutableMapOf<TrackId, FacetResolution>()
    var defaultOutcome: FacetResolution? = null
    var searchFailure: ReplayError? = null

    /** Makes resolve suspend, so concurrency (e.g. request de-duplication) can be exercised. */
    var resolveDelayMillis: Long = 0

    var resolveCalls = 0
        private set
    var searchCalls = 0
        private set
    val resolvedIds = mutableListOf<TrackId>()

    override val descriptor = SourceDescriptor(sourceId, displayName = "Fake $id", providerName = "Fake", basis = basis)

    private val _capabilities = MutableStateFlow(capabilities)
    override val capabilities: StateFlow<SourceCapabilities> = _capabilities

    fun setCapability(capability: Capability, state: CapabilityState) {
        _capabilities.value = SourceCapabilities(_capabilities.value.states + (capability to state))
    }

    fun add(track: Track) {
        catalogTracks[track.id] = track
    }

    override val catalog: CatalogFacet = object : CatalogFacet {
        override suspend fun search(query: SearchQuery): Outcome<SearchResults> {
            searchCalls++
            searchFailure?.let { return Outcome.Failure(it) }
            val words = TrackNormalizer.identityKey(query.text).split(' ').filter { it.isNotEmpty() }
            val hits = catalogTracks.values.filter { t ->
                val hay = TrackNormalizer.identityKey("${t.title} ${t.artistDisplay}")
                words.all { it in hay.split(' ') }
            }
            return Outcome.Success(SearchResults(tracks = hits.take(query.limit)))
        }

        override suspend fun track(ref: SourceRef): Outcome<Track> =
            catalogTracks[TrackId.of(ref.sourceId, ref.providerKey)]?.let { Outcome.Success(it) }
                ?: Outcome.Failure(ReplayError.NotFound(ref.providerKey))

        override suspend fun album(id: AlbumId): Outcome<AlbumDetail> = Outcome.Failure(ReplayError.NotFound("album"))
        override suspend fun artist(id: ArtistId): Outcome<ArtistDetail> = Outcome.Failure(ReplayError.NotFound("artist"))
    }

    override val playback: PlaybackFacet = object : PlaybackFacet {
        override val routes = setOf(PlaybackRoute.DIRECT)

        override suspend fun resolve(track: Track, quality: QualityRequest, purpose: Purpose): FacetResolution {
            resolveCalls++
            resolvedIds += track.id
            if (resolveDelayMillis > 0) kotlinx.coroutines.delay(resolveDelayMillis)
            outcomes[track.id]?.let { return it }
            defaultOutcome?.let { return it }
            if (track.id !in catalogTracks) return FacetResolution.Miss(app.replay.sources.api.MissReason.NOT_FOUND)
            return FacetResolution.Resolved(
                PlaybackTarget.DirectStream(
                    track.id,
                    PlayableMedia(
                        uri = "fake://${sourceId.value}/${track.id.providerKey}",
                        sourceId = sourceId,
                        cacheKey = "${track.id.value}|max",
                        expiresAtMillis = expiresAfterMillis?.let { clock() + it },
                        durationMs = track.durationMs,
                    ),
                ),
            )
        }
    }
}
