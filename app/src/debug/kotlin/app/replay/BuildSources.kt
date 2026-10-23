package app.replay

import android.content.Context
import app.replay.sources.api.MusicSource
import app.replay.sources.test.TestMusicSource

/** Debug builds add the deterministic test-tone sources (never shipped in release). */
internal fun buildVariantSources(context: Context): List<MusicSource> = listOf(
    TestMusicSource(context, TestMusicSource.Variant.PRIMARY),
    TestMusicSource(context, TestMusicSource.Variant.MIRROR),
)
