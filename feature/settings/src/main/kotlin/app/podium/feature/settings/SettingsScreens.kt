package app.replay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.replay.core.designsystem.component.FocusList
import app.replay.player.api.AudioEffects
import app.replay.player.api.SleepTimer
import app.replay.player.api.SleepTimerState
import app.replay.player.api.SleepTimerText
import app.replay.core.designsystem.component.KeyLayout
import app.replay.core.designsystem.component.KeyboardAction
import app.replay.core.designsystem.component.ListInputEffect
import app.replay.core.designsystem.component.LocalMiniature
import app.replay.core.designsystem.component.LocalScreenInsets
import app.replay.core.designsystem.component.MenuPreview
import app.replay.core.designsystem.component.MenuRow
import app.replay.core.designsystem.component.ReplayTextField
import app.replay.core.designsystem.shell.DeviceAppearance
import app.replay.core.designsystem.shell.FinishPreset
import app.replay.core.designsystem.shell.formatHex
import app.replay.core.designsystem.shell.palette
import app.replay.core.designsystem.shell.parseHex
import app.replay.core.designsystem.theme.BoneColors
import app.replay.core.designsystem.theme.CarbonColors
import app.replay.core.designsystem.theme.DisplayBackground
import app.replay.core.designsystem.theme.DisplayTheme
import app.replay.core.designsystem.theme.ReplayTheme
import app.replay.core.designsystem.theme.Spacing
import app.replay.core.designsystem.type.ReplayText
import app.replay.core.interaction.FocusListState
import app.replay.core.interaction.InputTargetEffect
import app.replay.core.interaction.ReplayInput
import app.replay.core.interaction.WheelButton
import app.replay.core.interaction.WheelContext
import app.replay.core.interaction.rememberFocusListState
import app.replay.core.interaction.rememberReplayHaptics

private enum class SettingsRow { Appearance, ReplaySpace, FullScreen, TitleBarBattery, Keyboard, MusicFolders, OnlineSources, Autoplay, Recommendations, AvoidRepeats, Crossfade, Equalizer, SleepTimer, OnlineLyrics, LyricsCredit, Haptics, Clicks, StartupSound }

/**
 * Settings (D-26, D-32, D-35): the device's look, which folders hold its music, which online sources it
 * uses, and how it answers touch.
 */
@Composable
fun SettingsScreen(
    repository: DeviceSettingsRepository,
    folders: MusicFolderSettings,
    onlineService: OnlineServiceSettings,
    onAppearance: () -> Unit,
    onMusicFolders: () -> Unit,
    onOnlineService: () -> Unit,
    /** Replay's space (D-54) without the two-finger pinch: personalize, stickers, help. */
    onReplaySpace: () -> Unit = {},
    /** Settings ▸ Equaliser (D-76). */
    onEqualizer: () -> Unit = {},
    /** The sleep timer (D-75): its row shows when the music stops; choosing opens its choices. */
    sleepTimer: SleepTimer? = null,
    onSleepTimer: () -> Unit = {},
) {
    val appearance by repository.appearance.collectAsStateWithLifecycle()
    val startupSound by repository.startupSound.collectAsStateWithLifecycle()
    val hapticsOn by repository.haptics.collectAsStateWithLifecycle()
    val clicksOn by repository.clicks.collectAsStateWithLifecycle()
    val autoplayOn by repository.autoplay.collectAsStateWithLifecycle()
    val recommendationsOn by repository.onlineRecommendations.collectAsStateWithLifecycle()
    val avoidRepeatsOn by repository.avoidRepeats.collectAsStateWithLifecycle()
    val onlineLyricsOn by repository.onlineLyrics.collectAsStateWithLifecycle()
    val replayKeyboard by repository.replayKeyboard.collectAsStateWithLifecycle()
    val immersive by repository.immersive.collectAsStateWithLifecycle()
    val titleBarBattery by repository.titleBarBattery.collectAsStateWithLifecycle()
    val folderList by folders.folders.collectAsStateWithLifecycle()
    val selection by folders.selection.collectAsStateWithLifecycle()
    val service by onlineService.state.collectAsStateWithLifecycle()
    val effects by repository.effects.collectAsStateWithLifecycle()
    val overlay = app.replay.core.designsystem.component.LocalOverlayHost.current
    val sleepState by (sleepTimer?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow<SleepTimerState>(SleepTimerState.Off) }).collectAsStateWithLifecycle()
    val sleepSummary by androidx.compose.runtime.produceState(SleepTimerText.summary(sleepState, sleepTimer?.remainingMs()), sleepState) {
        while (true) {
            value = SleepTimerText.summary(sleepState, sleepTimer?.remainingMs())
            if (sleepState !is SleepTimerState.Counting) break
            kotlinx.coroutines.delay(1_000)
        }
    }
    val haptics = rememberReplayHaptics()
    // Only a build with an online service has anything to choose there.
    val rows = SettingsRow.entries.filter { (it != SettingsRow.OnlineSources || service != null) && (it != SettingsRow.SleepTimer || sleepTimer != null) }
    val focus = rememberFocusListState("settings")
    val activate: (Int) -> Unit = { index ->
        when (rows[index]) {
            SettingsRow.Appearance -> onAppearance()
            SettingsRow.ReplaySpace -> onReplaySpace()
            SettingsRow.FullScreen -> {
                haptics.confirm()
                repository.setImmersive(!immersive)
            }
            SettingsRow.TitleBarBattery -> if (!immersive) haptics.reject() else {
                haptics.confirm()
                repository.setTitleBarBattery(!titleBarBattery)
            }
            SettingsRow.Keyboard -> {
                haptics.confirm()
                repository.setReplayKeyboard(!replayKeyboard)
            }
            SettingsRow.MusicFolders -> onMusicFolders()
            SettingsRow.OnlineSources -> onOnlineService()
            SettingsRow.Autoplay -> {
                haptics.confirm()
                repository.setAutoplay(!autoplayOn)
            }
            SettingsRow.Recommendations -> if (!autoplayOn) haptics.reject() else {
                haptics.confirm()
                repository.setOnlineRecommendations(!recommendationsOn)
            }
            SettingsRow.AvoidRepeats -> if (!autoplayOn) haptics.reject() else {
                haptics.confirm()
                repository.setAvoidRepeats(!avoidRepeatsOn)
            }
            SettingsRow.Crossfade -> overlay.show(
                app.replay.core.designsystem.component.MenuSpec(
                    if (effects.crossfadeSeconds == 0) "Crossfade is off" else "Crossfade: ${crossfadeLabel(effects.crossfadeSeconds)}",
                    AudioEffects.CROSSFADE_CHOICES.map { seconds ->
                        app.replay.core.designsystem.component.MenuAction(if (seconds == 0) "Off" else crossfadeLabel(seconds)) { repository.setCrossfade(seconds) }
                    },
                ),
            )
            SettingsRow.Equalizer -> onEqualizer()
            SettingsRow.SleepTimer -> onSleepTimer()
            SettingsRow.OnlineLyrics -> {
                haptics.confirm()
                repository.setOnlineLyrics(!onlineLyricsOn)
            }
            SettingsRow.LyricsCredit -> haptics.reject()
            SettingsRow.Haptics -> {
                repository.setHaptics(!hapticsOn)
                haptics.confirm()
            }
            SettingsRow.Clicks -> {
                haptics.confirm()
                repository.setClicks(!clicksOn)
            }
            SettingsRow.StartupSound -> {
                haptics.confirm()
                repository.setStartupSound(!startupSound)
            }
        }
    }
    ListInputEffect(focus, onActivate = activate)
    FocusList(
        items = rows,
        state = focus,
        key = { it },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { row ->
            when (row) {
                SettingsRow.Appearance -> appearance.baseArgb?.let { MenuPreview.Swatch(Color(it)) } ?: MenuPreview.Instrument
                else -> MenuPreview.Instrument
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { row, _, focused ->
        when (row) {
            SettingsRow.Appearance -> MenuRow("Appearance", focused, value = appearance.display.label)
            SettingsRow.ReplaySpace -> MenuRow("Replay body", focused, value = "Stickers, help, turn off")
            // Typing: the Wheel becomes Replay's keyboard, or the phone's keyboard appears (D-45).
            // Full screen (D-85): the phone's bars hidden; the title bar shows the time and the battery.
            SettingsRow.FullScreen -> MenuRow("Full screen", focused, value = if (immersive) "On" else "Off", showChevron = false)
            SettingsRow.TitleBarBattery -> MenuRow(
                "Battery in title bar",
                focused,
                value = if (!immersive) "Full screen only" else if (titleBarBattery) "Shown" else "Hidden",
                enabled = immersive,
                showChevron = false,
            )
            SettingsRow.Keyboard -> MenuRow("Keyboard", focused, value = if (replayKeyboard) "Replay" else "Phone", showChevron = false)
            SettingsRow.MusicFolders -> MenuRow(
                "Music folders",
                focused,
                value = when {
                    folderList == null -> null
                    selection == app.replay.sources.api.FolderSelection.Everything -> "All"
                    else -> "${songsIncluded(folderList.orEmpty(), selection)} songs"
                },
            )
            SettingsRow.OnlineSources -> MenuRow(service?.name ?: "Online music", focused, value = onlineServiceSummary(service))
            SettingsRow.Autoplay -> MenuRow("Autoplay", focused, value = if (autoplayOn) "On" else "Off", showChevron = false)
            SettingsRow.Recommendations -> MenuRow("Online recommendations", focused, value = if (recommendationsOn) "On" else "Off", enabled = autoplayOn, showChevron = false)
            SettingsRow.AvoidRepeats -> MenuRow("Avoid repeats", focused, value = if (avoidRepeatsOn) "On" else "Off", enabled = autoplayOn, showChevron = false)
            // Replay's own player (D-75 – D-77).
            SettingsRow.Crossfade -> MenuRow("Crossfade", focused, value = if (effects.crossfadeSeconds == 0) "Off" else "${effects.crossfadeSeconds} s", showChevron = false)
            SettingsRow.Equalizer -> MenuRow("Equaliser", focused, value = equalizerSummary(effects))
            SettingsRow.SleepTimer -> MenuRow("Sleep timer", focused, value = sleepSummary, showChevron = false)
            SettingsRow.OnlineLyrics -> MenuRow("Online lyrics", focused, value = if (onlineLyricsOn) "On" else "Off", showChevron = false)
            // The lyrics service's credit (LYRICS_ARCHITECTURE.md §9): its community wrote the words.
            SettingsRow.LyricsCredit -> MenuRow("Lyrics from LRCLIB, written by its community", focused, enabled = false, showChevron = false)
            SettingsRow.Haptics -> MenuRow("Haptics", focused, value = if (hapticsOn) "On" else "Off", showChevron = false)
            SettingsRow.Clicks -> MenuRow("Click sound", focused, value = if (clicksOn) "On" else "Off", showChevron = false)
            SettingsRow.StartupSound -> MenuRow("Startup sound", focused, value = if (startupSound) "On" else "Off", showChevron = false)
        }
    }
}

/** "2 seconds", "12 seconds". */
internal fun crossfadeLabel(seconds: Int): String = if (seconds == 1) "1 second" else "$seconds seconds"

/**
 * Finish picker. Turning the wheel tries each finish on the device itself; the center keeps it,
 * Menu puts the old one back (and goes back). "Custom color" opens the colour editor.
 */
@Composable
fun FinishScreen(repository: DeviceSettingsRepository, onCustomColor: () -> Unit) {
    val appearance by repository.appearance.collectAsStateWithLifecycle()
    val presets = FinishPreset.entries
    val focus = remember { FocusListState(initialIndex = presets.indexOf(repository.appearance.value.preset)) }
    val miniature = LocalMiniature.current
    if (!miniature) LaunchedEffect(focus) {
        snapshotFlow { focus.focusedIndex }.collect { index ->
            val preset = presets.getOrNull(index) ?: return@collect
            val current = repository.appearance.value
            repository.setPreview(if (preset == current.preset) null else current.copy(preset = preset))
        }
    }
    if (!LocalMiniature.current) DisposableEffect(Unit) { onDispose { repository.setPreview(null) } }
    val activate: (Int) -> Unit = { index ->
        val preset = presets[index]
        if (preset == FinishPreset.CUSTOM) {
            onCustomColor()
        } else {
            // Choosing keeps you here with the check on your choice; Menu goes back.
            repository.setAppearance(repository.appearance.value.copy(preset = preset))
            repository.setPreview(null)
        }
    }
    ListInputEffect(focus, onActivate = activate)
    val dark = ReplayTheme.colors.isDark
    FocusList(
        items = presets,
        state = focus,
        key = { it },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { preset -> DeviceAppearance(preset, customArgb = appearance.customArgb).baseArgb?.let { MenuPreview.Swatch(Color(it)) } ?: MenuPreview.Instrument },
        modifier = Modifier.fillMaxSize(),
    ) { preset, _, focused ->
        val swatch = when (preset) {
            FinishPreset.GLASS -> null
            FinishPreset.CUSTOM -> Color(appearance.customArgb)
            else -> DeviceAppearance(preset).palette(dark).bodyTop
        }
        MenuRow(
            preset.label,
            focused,
            leadingContent = { Swatch(swatch) },
            selected = preset == appearance.preset,
            showChevron = preset == FinishPreset.CUSTOM,
        )
    }
}

/**
 * Theme picker (D-29, D-41): Glass, the matte instrument looks Carbon and Bone, or Custom (the same
 * instrument on your own background). Turning the wheel tries each one on the whole device; Center
 * keeps it, Menu puts the old one back.
 */
@Composable
fun ThemeScreen(repository: DeviceSettingsRepository) {
    val appearance by repository.appearance.collectAsStateWithLifecycle()
    val themes = DisplayTheme.entries
    val focus = remember { FocusListState(initialIndex = themes.indexOf(repository.appearance.value.display)) }
    val miniature = LocalMiniature.current
    if (!miniature) LaunchedEffect(focus) {
        snapshotFlow { focus.focusedIndex }.collect { index ->
            val theme = themes.getOrNull(index) ?: return@collect
            val current = repository.appearance.value
            repository.setPreview(if (theme == current.display) null else current.copy(display = theme))
        }
    }
    if (!LocalMiniature.current) DisposableEffect(Unit) { onDispose { repository.setPreview(null) } }
    val activate: (Int) -> Unit = { index ->
        repository.setAppearance(repository.appearance.value.copy(display = themes[index]))
        repository.setPreview(null)
    }
    ListInputEffect(focus, onActivate = activate)
    FocusList(
        items = themes,
        state = focus,
        key = { it },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { theme ->
            when (theme) {
                DisplayTheme.GLASS -> MenuPreview.Instrument
                DisplayTheme.CARBON -> MenuPreview.Swatch(CarbonColors.canvas)
                DisplayTheme.BONE -> MenuPreview.Swatch(BoneColors.canvas)
                DisplayTheme.CUSTOM -> MenuPreview.Swatch(Color(appearance.screen.solidArgb))
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { theme, _, focused ->
        MenuRow(
            theme.label,
            focused,
            leadingContent = {
                Swatch(
                    when (theme) {
                        DisplayTheme.GLASS -> null
                        DisplayTheme.CARBON -> CarbonColors.canvas
                        DisplayTheme.BONE -> BoneColors.canvas
                        DisplayTheme.CUSTOM -> Color(appearance.screen.solidArgb)
                    },
                )
            },
            selected = theme == appearance.display,
            showChevron = false,
        )
    }
}

/** What the colour editor colours: the body's Custom finish, or the display's background. */
enum class ColorTarget { Finish, DisplayBackground }

/**
 * Custom colour editor: type a hex code, or turn the wheel to walk the hue. The device previews
 * the colour as it changes; the center (or the button) applies it — as the Custom finish, or as
 * the display's background colour (which turns a Glass display's background to Solid if it had
 * none, so the choice shows).
 */
@Composable
fun CustomColorScreen(repository: DeviceSettingsRepository, target: ColorTarget = ColorTarget.Finish) {
    val colors = ReplayTheme.colors
    val type = ReplayTheme.type
    val insets = LocalScreenInsets.current
    val haptics = rememberReplayHaptics()
    val focusManager = LocalFocusManager.current
    val appearance = remember { repository.appearance.value }
    val initial = if (target == ColorTarget.Finish) appearance.customArgb else appearance.screen.solidArgb
    var text by remember { mutableStateOf(formatHex(initial).drop(1)) }
    var invalid by remember { mutableStateOf(false) }
    var argb by remember { mutableIntStateOf(initial) }
    var hsv by remember { mutableStateOf(Hsv.of(initial)) }
    var channel by remember { mutableStateOf(ColorChannel.HUE) }
    fun choose(next: Hsv) {
        hsv = next
        argb = next.argb
        text = formatHex(argb).drop(1)
        invalid = false
    }
    fun applied(color: Int): DeviceAppearance = when (target) {
        ColorTarget.Finish -> appearance.copy(preset = FinishPreset.CUSTOM, customArgb = color)
        ColorTarget.DisplayBackground -> appearance.copy(
            screen = appearance.screen.copy(
                solidArgb = color,
                background = if (appearance.screen.background == DisplayBackground.NONE) DisplayBackground.SOLID else appearance.screen.background,
            ),
        )
    }

    val miniature = LocalMiniature.current
    if (!miniature) {
        LaunchedEffect(argb) { repository.setPreview(applied(argb)) }
        if (!LocalMiniature.current) DisposableEffect(Unit) { onDispose { repository.setPreview(null) } }
    }

    val apply = {
        repository.setAppearance(applied(argb))
        repository.setPreview(null)
        haptics.confirm()
        focusManager.clearFocus()
    }
    InputTargetEffect(WheelContext.VOLUME) { input ->
        when (input) {
            is ReplayInput.Rotate -> {
                choose(hsv.turned(channel, input.detents))
                true
            }
            is ReplayInput.Press -> when (input.button) {
                WheelButton.CENTER -> {
                    apply()
                    true
                }
                // Skip forward and back choose the bar the Wheel turns.
                WheelButton.NEXT, WheelButton.PREVIOUS -> {
                    val all = ColorChannel.entries
                    val step = if (input.button == WheelButton.NEXT) 1 else all.size - 1
                    channel = all[(channel.ordinal + step) % all.size]
                    haptics.step()
                    true
                }
                else -> false
            }
            else -> false
        }
    }

    Column(
        Modifier.fillMaxSize().padding(top = insets.top + Spacing.s, bottom = insets.bottom).padding(horizontal = Spacing.xl),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(argb), RoundedCornerShape(16.dp))
                .border(1.dp, colors.separator, RoundedCornerShape(16.dp))
                .semantics { contentDescription = "Color preview ${formatHex(argb)}" },
        )
        Spacer(Modifier.height(Spacing.l))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(colors.canvasRaised, RoundedCornerShape(14.dp))
                .border(1.dp, if (invalid) colors.critical else colors.separator, RoundedCornerShape(14.dp))
                .padding(horizontal = Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReplayText("#", type.title, colors.labelSecondary)
            Spacer(Modifier.width(Spacing.xs))
            ReplayTextField(
                value = text,
                onValueChange = { raw ->
                    val cleaned = raw.uppercase().filter { it.isDigit() || it in 'A'..'F' }.take(6)
                    text = cleaned
                    val parsed = parseHex(cleaned)
                    invalid = cleaned.length == 6 && parsed == null
                    if (parsed != null) {
                        argb = parsed
                        hsv = Hsv.of(parsed)
                    }
                },
                textStyle = type.title.copy(color = colors.labelPrimary),
                description = "Hex color code",
                action = KeyboardAction.DONE,
                onAction = { focusManager.clearFocus() },
                layout = KeyLayout.HEX,
                maxLength = 6,
                accept = { it.isDigit() || it.uppercaseChar() in 'A'..'F' },
                phoneOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                ),
                cursorColor = colors.highlightText,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Spacing.m))
        // The whole palette, and how much colour and light: the Wheel turns the marked bar.
        for (c in ColorChannel.entries) {
            ColorBar(c, hsv, chosen = c == channel, onChoose = { channel = c }, onSet = { fraction -> choose(hsv.with(c, fraction)) })
            Spacer(Modifier.height(Spacing.s))
        }
        ReplayText(
            if (text.length in 1..5) "Use six hex digits, like 2F6F5E." else "Turn the Wheel to change ${channel.label.lowercase()}. Skip forward or back for the next bar.",
            type.footnote,
            colors.labelSecondary,
            maxLines = 2,
        )
        Spacer(Modifier.height(Spacing.m))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(colors.highlight, RoundedCornerShape(14.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (parseHex(text) != null) apply() else haptics.reject()
                },
            contentAlignment = Alignment.Center,
        ) {
            ReplayText("Use this color", type.rowFocused, colors.onHighlight)
        }
    }
}

@Composable
internal fun Swatch(color: Color?) {
    val colors = ReplayTheme.colors
    val shape = CircleShape
    Box(
        Modifier
            .size(22.dp)
            .then(
                if (color != null) {
                    Modifier.background(color, shape)
                } else {
                    // Glass: a clear disc with a rim.
                    Modifier.background(colors.labelPrimary.copy(alpha = 0.08f), shape)
                },
            )
            .border(1.dp, colors.labelPrimary.copy(alpha = 0.25f), shape),
    )
}

/** The Equaliser row's value: the preset, the effect, both, or off. */
internal fun equalizerSummary(effects: AudioEffects): String {
    val eq = effects.equalizer.takeIf { it.enabled }?.preset?.label
    val fx = effects.sound.takeIf { it != app.replay.player.api.SoundEffect.NONE }?.label
    return when {
        eq != null && fx != null -> "$eq, $fx"
        else -> eq ?: fx ?: "Off"
    }
}
