package app.replay.player.service

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * Custom session commands for queue operations (ADR-006). Every queue mutation travels to the
 * service and is executed by the QueueManager — the single writer.
 */
object ReplaySessionCommands {
    const val SET_CONTEXT = "app.replay.queue.SET_CONTEXT"
    const val PLAY_NEXT = "app.replay.queue.PLAY_NEXT"
    const val ADD_TO_QUEUE = "app.replay.queue.ADD_TO_QUEUE"
    const val MOVE = "app.replay.queue.MOVE"
    const val REMOVE = "app.replay.queue.REMOVE"
    const val SKIP_TO = "app.replay.queue.SKIP_TO"
    const val CLEAR_UPCOMING = "app.replay.queue.CLEAR_UPCOMING"

    const val ARG_TRACK_IDS = "trackIds"
    const val ARG_START_INDEX = "startIndex"
    const val ARG_CONTEXT_LABEL = "contextLabel"
    const val ARG_SHUFFLE = "shuffle"
    const val ARG_RADIO = "radio"
    const val ARG_UID = "uid"
    const val ARG_UIDS = "uids"
    const val ARG_TO_INDEX = "toIndex"

    val all: List<SessionCommand> =
        listOf(SET_CONTEXT, PLAY_NEXT, ADD_TO_QUEUE, MOVE, REMOVE, SKIP_TO, CLEAR_UPCOMING)
            .map { SessionCommand(it, Bundle.EMPTY) }

    fun command(action: String) = SessionCommand(action, Bundle.EMPTY)
}

/** Keys used in MediaItem extras and session extras. Provider-neutral display data only. */
object ReplayExtras {
    const val TRACK_ID = "replay.trackId"
    const val ORIGIN = "replay.origin"

    /** The item's [app.replay.core.model.MediaKind] name (a song, or audio from a video). */
    const val MEDIA_KIND = "replay.kind"

    // Session extras describing the current item's resolution (who is serving it, and how).
    const val CURRENT_UID = "replay.current.uid"
    const val SERVED_BY = "replay.current.servedBy"
    const val SERVED_BY_ID = "replay.current.servedById"
    const val RESOLUTION_PATH = "replay.current.path"
    const val CLAIMED_CODEC = "replay.current.claimed.codec"
    const val CLAIMED_BITRATE = "replay.current.claimed.kbps"
    const val CLAIMED_SAMPLE_RATE = "replay.current.claimed.hz"
    const val CLAIMED_BIT_DEPTH = "replay.current.claimed.bits"
    const val CONTEXT_LABEL = "replay.queue.context"
    const val LAST_ERROR = "replay.error.kind"

    /** A [app.replay.player.api.Recovery] name while the engine is dealing with the current song's problem. */
    const val RECOVERY = "replay.error.recovery"
}
