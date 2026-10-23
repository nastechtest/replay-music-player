package app.replay.core.designsystem.component

import androidx.compose.runtime.Composable
import app.replay.core.interaction.FocusListState
import app.replay.core.interaction.InputTargetEffect
import app.replay.core.interaction.ReplayInput
import app.replay.core.interaction.WheelAcceleration
import app.replay.core.interaction.WheelButton
import app.replay.core.interaction.WheelContext
import app.replay.core.interaction.rememberReplayHaptics

/**
 * The standard list behaviour (interaction-model.md §4, ListFocus): rotate moves focus with a
 * boundary haptic at the ends, Center activates, long-press Center opens the context menu.
 * Menu and transport buttons fall through to the global handlers.
 */
@Composable
fun ListInputEffect(
    focus: FocusListState,
    onActivate: (Int) -> Unit,
    onLongPress: ((Int) -> Unit)? = null,
) {
    val haptics = rememberReplayHaptics()
    InputTargetEffect(WheelContext.LIST_FOCUS) { input ->
        when (input) {
            is ReplayInput.Rotate -> {
                val steps = input.detents * WheelAcceleration.multiplier(input.velocity, focus.itemCount)
                if (!focus.moveBy(steps)) haptics.boundary()
                true
            }
            is ReplayInput.Press -> if (input.button == WheelButton.CENTER) {
                if (focus.itemCount > 0) onActivate(focus.focusedIndex)
                true
            } else false
            is ReplayInput.LongPress -> if (input.button == WheelButton.CENTER && onLongPress != null) {
                if (focus.itemCount > 0) onLongPress(focus.focusedIndex)
                true
            } else false
            is ReplayInput.Release -> false
        }
    }
}
