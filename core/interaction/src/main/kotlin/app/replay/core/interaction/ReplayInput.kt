package app.replay.core.interaction

/** The Wheel's five buttons. */
enum class WheelButton { MENU, NEXT, PLAY_PAUSE, PREVIOUS, CENTER }

/**
 * Semantic input (interaction-model.md §2). The Wheel, keyboard, D-pad, rotary encoders and mouse
 * wheels all produce these; screens never see raw gestures.
 */
sealed interface ReplayInput {
    /** Positive = clockwise = "down"/"more". [velocity] in detents per second. */
    data class Rotate(val detents: Int, val velocity: Float = 0f) : ReplayInput
    data class Press(val button: WheelButton) : ReplayInput
    data class LongPress(val button: WheelButton) : ReplayInput
    data class Release(val button: WheelButton) : ReplayInput
}

/** What the wheel's rotation means on the current screen (interaction-model.md §4). */
enum class WheelContext { LIST_FOCUS, VOLUME, SCRUB, QUEUE, OVERLAY }
