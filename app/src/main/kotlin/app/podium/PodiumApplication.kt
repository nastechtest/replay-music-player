package app.replay

import android.app.Application
import app.replay.player.service.PlaybackDependencies

class ReplayApplication : Application(), PlaybackDependencies.Provider {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }

    override val playbackDependencies: PlaybackDependencies
        get() = graph.playbackDependencies
}
