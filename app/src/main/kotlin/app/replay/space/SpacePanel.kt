package app.replay.space

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.replay.core.designsystem.component.pressFeedback
import app.replay.core.designsystem.symbol.ReplaySymbol
import app.replay.core.designsystem.symbol.Symbol
import app.replay.core.designsystem.theme.CarbonColors
import app.replay.core.designsystem.theme.LocalReplayType
import app.replay.core.designsystem.theme.ReplayTheme
import app.replay.core.designsystem.theme.Spacing
import app.replay.core.designsystem.type.DisplayFont
import app.replay.core.designsystem.type.LocalTypographyPreset
import app.replay.core.designsystem.type.ReplayText
import app.replay.core.designsystem.type.TypographyPreset

/** The settings page's pages: the main one (the sticker gallery), one sticker, and Help. */
enum class SpacePage { MAIN, STICKER, HELP }

/**
 * The space's settings page (D-54, D-56): outside the Replay, only what belongs outside it — the
 * listener's stickers as a gallery (and adding more), Help, Return to Replay and Turn off Replay.
 * Everything about the Replay's own look stays in its Settings. Large by default, using the space
 * the small Replay leaves; [ReplaySpaceState.focusReplay] shrinks it into the corner while the
 * Replay comes close, and a tap on it (or the switch) brings it back. Carbon's ink always: the
 * space is dark whatever the display's theme; one crisp face whatever the display's font.
 */
@Composable
fun BoxScope.SpacePanel(
    state: ReplaySpaceState,
    page: SpacePage,
    onPage: (SpacePage) -> Unit,
    stickers: @Composable ColumnScope.() -> Unit,
    sticker: @Composable ColumnScope.() -> Unit,
    help: @Composable ColumnScope.() -> Unit,
    onTurnOff: () -> Unit,
) {
    SpaceType {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(top = Spacing.m, end = Spacing.m, bottom = ToggleHeight + Spacing.xl)
                .fillMaxWidth(PageWidthFraction)
                .fillMaxHeight()
                .graphicsLayer {
                    // In from the right as the Replay settles (the last half of the way); small in
                    // its corner while the Replay is close; away while stickers are arranged. It
                    // swings in from a slight turn under the same camera as the object and squares up
                    // as it lands — a panel in the same space, not a sheet over it (D-65). Tied to
                    // depth, so it follows the fingers like the object does.
                    val inSpace = ((state.depth.value - 0.5f) / 0.5f).coerceIn(0f, 1f)
                    val shown = inSpace * (1f - state.edit.value)
                    translationX = (1f - shown) * (size.width + 48.dp.toPx())
                    alpha = shown
                    rotationY = -PageSwingDegrees * (1f - shown)
                    cameraDistance = PageCameraDistance * density
                    val s = 1f - (1f - SmallPageScale) * state.zoom.value
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(1f, 1f)
                },
        ) {
            PageCard(page, onPage, onReturn = state::leave, stickers, sticker, help, onTurnOff)
            // Small in the corner, the whole page is one key: it comes back large.
            if (state.focus == SpaceFocus.REPLAY) {
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(state) { detectTapGestures { state.focusSettings() } }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Settings. Activate to bring them close."
                        },
                )
            }
        }
        SpaceToggle(state, Modifier.align(Alignment.BottomCenter))
    }
}

/** Everything outside the Replay speaks in one crisp face, whatever the display's font. */
@Composable
fun SpaceType(content: @Composable () -> Unit) {
    val classic = TypographyPreset.of(DisplayFont.CLASSIC)
    CompositionLocalProvider(LocalTypographyPreset provides classic, LocalReplayType provides classic.type, content = content)
}

@Composable
private fun PageCard(
    page: SpacePage,
    onPage: (SpacePage) -> Unit,
    onReturn: () -> Unit,
    stickers: @Composable ColumnScope.() -> Unit,
    sticker: @Composable ColumnScope.() -> Unit,
    help: @Composable ColumnScope.() -> Unit,
    onTurnOff: () -> Unit,
) {
    val ink = CarbonColors
    Column(
        Modifier
            .fillMaxSize()
            .background(ink.canvasRaised, PageShape)
            .border(1.dp, ink.separator, PageShape)
            .padding(Spacing.l),
    ) {
        // The page's legend strip, and the way back to using the Replay.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(ink.highlightText, CircleShape))
            Spacer(Modifier.width(Spacing.s))
            ReplayText("Replay", ReplayTheme.type.caption, ink.labelTertiary, Modifier.weight(1f))
            SmallKey("Return to Replay", onReturn)
        }
        Spacer(Modifier.height(Spacing.s))
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != SpacePage.MAIN
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { if (forward) it / 6 else -it / 6 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { if (forward) -it / 6 else it / 6 })
            },
            modifier = Modifier.weight(1f),
            label = "spacePage",
        ) { shown ->
            Column(Modifier.fillMaxSize()) {
                when (shown) {
                    SpacePage.MAIN -> {
                        PanelTitle("Stickers")
                        Column(Modifier.weight(1f).fillMaxWidth()) { stickers() }
                        Divider()
                        PanelRow("Help", "The Wheel, this space, stickers") { onPage(SpacePage.HELP) }
                        PanelRow("Turn off Replay", "Stops the music and closes Replay", tint = ink.critical, chevron = false, onClick = onTurnOff)
                    }
                    SpacePage.STICKER -> {
                        BackLink { onPage(SpacePage.MAIN) }
                        Column(Modifier.weight(1f).fillMaxWidth()) { sticker() }
                    }
                    SpacePage.HELP -> {
                        BackLink { onPage(SpacePage.MAIN) }
                        PanelTitle("Help")
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { help() }
                    }
                }
            }
        }
    }
}

/**
 * The switch under the space: Replay (the Replay close, the page small) or Settings (the page
 * large, the default). A two-part key, the lit half is what's large.
 */
@Composable
private fun SpaceToggle(state: ReplaySpaceState, modifier: Modifier) {
    val ink = CarbonColors
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(bottom = Spacing.l)
            .graphicsLayer {
                val inSpace = ((state.depth.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                alpha = inSpace * (1f - state.edit.value)
                translationY = (1f - inSpace) * 24.dp.toPx()
            }
            .height(ToggleHeight)
            .background(ink.canvasRaised, ToggleShape)
            .border(1.dp, ink.separator, ToggleShape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleHalf("Replay", state.focus == SpaceFocus.REPLAY, state::focusReplay)
        ToggleHalf("Settings", state.focus == SpaceFocus.SETTINGS, state::focusSettings)
    }
}

/** A row of keys where one is chosen (lit): the outside's segmented choice. */
@Composable
internal fun SegmentedKeys(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ink = CarbonColors
    Row(
        modifier
            .height(40.dp)
            .background(ink.canvas, ToggleShape)
            .border(1.dp, ink.separator, ToggleShape)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (on) ink.labelPrimary.copy(alpha = 0.92f) else Color.Transparent, KeyShape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                    .semantics {
                        role = Role.Tab
                        this.selected = on
                    },
                contentAlignment = Alignment.Center,
            ) {
                ReplayText(label, ReplayTheme.type.caption, if (on) ink.canvas else ink.labelSecondary)
            }
        }
    }
}

@Composable
private fun ToggleHalf(label: String, on: Boolean, onClick: () -> Unit) {
    val ink = CarbonColors
    Box(
        Modifier
            .fillMaxHeight()
            .width(ToggleHalfWidth)
            .pressFeedback(0.95f)
            .background(if (on) ink.labelPrimary.copy(alpha = 0.92f) else Color.Transparent, KeyShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics {
                role = Role.Tab
                selected = on
            },
        contentAlignment = Alignment.Center,
    ) {
        ReplayText(label, ReplayTheme.type.rowSecondary, if (on) ink.canvas else ink.labelSecondary)
    }
}

@Composable
private fun BackLink(onBack: () -> Unit) {
    val ink = CarbonColors
    Row(
        Modifier
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onBack)
            .semantics {
                role = Role.Button
                contentDescription = "Back to stickers"
            }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol(ReplaySymbol.ChevronLeft, ink.labelSecondary, size = 18.dp, weight = 600)
        ReplayText("Stickers", ReplayTheme.type.caption, ink.labelSecondary)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.s).height(1.dp).background(CarbonColors.separator))
}

@Composable
internal fun PanelTitle(text: String) {
    ReplayText(
        text,
        ReplayTheme.type.title,
        CarbonColors.labelPrimary,
        Modifier.padding(bottom = Spacing.s).semantics { heading() },
    )
}

/** A row of the page: a label, a quiet line under it, a chevron. Touch only (the Wheel stays on the object). */
@Composable
internal fun PanelRow(label: String, detail: String? = null, enabled: Boolean = true, tint: Color? = null, chevron: Boolean = true, onClick: () -> Unit) {
    val ink = CarbonColors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            ReplayText(label, ReplayTheme.type.row, tint ?: if (enabled) ink.labelPrimary else ink.labelTertiary)
            if (detail != null) ReplayText(detail, ReplayTheme.type.caption, ink.labelTertiary, maxLines = 2)
        }
        if (chevron) Symbol(ReplaySymbol.ChevronRight, ink.labelTertiary, size = 18.dp, weight = 600)
    }
}

/** The page's strong action: a flat key, lit ink on the page's dark. */
@Composable
internal fun PanelButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, emphasized: Boolean = true, color: Color? = null) {
    val ink = CarbonColors
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .pressFeedback(0.97f)
            .background(if (emphasized) ink.labelPrimary.copy(alpha = 0.92f) else Color.Transparent, KeyShape)
            .border(1.dp, if (emphasized) Color.Transparent else ink.separator, KeyShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        ReplayText(label, ReplayTheme.type.rowFocused, color ?: if (emphasized) ink.canvas else ink.labelPrimary)
    }
}

/** A small outlined key (Return to Replay at the page's top). */
@Composable
internal fun SmallKey(label: String, onClick: () -> Unit) {
    val ink = CarbonColors
    Box(
        Modifier
            .pressFeedback(0.94f)
            .border(1.dp, ink.separator, KeyShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = Spacing.m, vertical = Spacing.xs + 2.dp),
    ) {
        ReplayText(label, ReplayTheme.type.caption, ink.labelPrimary)
    }
}

/** Help (D-54): the controls, the space, stickers — and the guide again. */
@Composable
fun ColumnScope.HelpContent(onReplayGuide: () -> Unit) {
    HelpSection("Basic navigation", listOf(
        "Turn the Wheel" to "move through menus",
        "Center" to "choose",
        "Menu" to "go back (hold it for Home)",
        "Play/Pause" to "play or pause; the arrows skip, hold them to scan",
    ))
    HelpSection("Outside your Replay", listOf(
        "Pinch with two fingers" to "on your Replay to step outside it, into this space",
        "Replay and Settings, at the bottom" to "bring your Replay or this page close",
        "Return to Replay, or spread two fingers" to "to go back to using it",
        "Settings ▸ Replay body" to "opens this space without the gesture",
    ))
    HelpSection("Stickers", listOf(
        "Add a sticker" to "choose a picture, keep its subject, give it a border, stick it on",
        "One finger" to "moves a sticker while you arrange them",
        "Two fingers" to "resize and turn it",
        "Arrange stickers" to "move them later, or take one off",
    ))
    HelpSection("Turning off", listOf(
        "Turn off Replay" to "stops the music and closes Replay completely",
    ))
    Spacer(Modifier.height(Spacing.s))
    PanelButton("Replay the hands-on guide", onReplayGuide, emphasized = false)
}

@Composable
private fun HelpSection(title: String, lines: List<Pair<String, String>>) {
    val ink = CarbonColors
    val type = ReplayTheme.type
    ReplayText(title, type.sectionHeader, ink.labelSecondary, Modifier.padding(top = Spacing.s, bottom = Spacing.xs))
    for ((what, does) in lines) {
        Column(Modifier.padding(bottom = Spacing.xs).semantics(mergeDescendants = true) {}) {
            ReplayText(what, type.rowSecondary, ink.labelPrimary, maxLines = 2)
            ReplayText(does, type.caption, ink.labelTertiary, maxLines = 3)
        }
    }
}

private val PageShape = RoundedCornerShape(22.dp)
private val KeyShape = RoundedCornerShape(10.dp)
private val ToggleShape = RoundedCornerShape(14.dp)
private val ToggleHeight = 44.dp
private val ToggleHalfWidth = 104.dp
private const val PageWidthFraction = 0.6f
private const val SmallPageScale = 0.4f

/** How far the page is turned as it starts to come in (it squares up as it lands). */
private const val PageSwingDegrees = 9f

/** The object's own camera (ReplaySpace), so the page turns in the same perspective. */
private const val PageCameraDistance = 18f
