package app.replay

import android.content.Context
import app.replay.sources.api.MusicSource

/** Release builds ship only real sources. */
internal fun buildVariantSources(context: Context): List<MusicSource> = emptyList()
