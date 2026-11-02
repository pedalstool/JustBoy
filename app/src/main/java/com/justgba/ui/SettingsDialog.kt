package com.justgba.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.justgba.input.BindingActions
import com.justgba.settings.SettingsManager
import com.justgba.ui.theme.THEME_FOLLOW_SYSTEM
import com.justgba.ui.theme.THEME_OFF
import com.justgba.ui.theme.THEME_ON
import kotlinx.coroutines.launch

private enum class SettingsTab(val label: String) {
    General("General"),
    Video("Video"),
    Controller("Controller"),
    Hotkeys("Hotkeys"),
    Saves("Save states"),
    Cheats("Cheats"),
}

private val VideoShaders = listOf(
    0 to "Sharp",
    1 to "Smooth",
    4 to "2xBR",
)

private val ScreenSizes = listOf(
    SettingsManager.SCREEN_SIZE_FIT to "Fit",
    SettingsManager.SCREEN_SIZE_INTEGER to "Integer",
    2 to "2x",
    3 to "3x",
    4 to "4x",
    5 to "5x",
    6 to "6x",
    7 to "7x",
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    settingsManager: SettingsManager,
    statePath: String? = null,
    gbaColors: Boolean = false,
    onGbaColorsChange: (Boolean) -> Unit = {},
    shader: Int = 0,
    onShaderChange: (Int) -> Unit = {},
    onSaveSlot: ((Int) -> Unit)? = null,
    onLoadSlot: ((Int) -> Unit)? = null,
    slotLabels: List<String> = emptyList(),
    status: String = "",
    ffSpeed: Float,
    ffAudioMode: Int,
    showFps: Boolean,
    lockLandscape: Boolean,
    onFfSpeedChange: (Float) -> Unit,
    onFfAudioModeChange: (Int) -> Unit,
    onShowFpsChange: (Boolean) -> Unit,
    onLockLandscapeChange: (Boolean) -> Unit,
    onResume: () -> Unit,
    onExit: (() -> Unit)? = null,
    onRestart: (() -> Unit)? = null,
    title: String = "Game Paused",
) {
    val selectedTabState = remember { mutableIntStateOf(SettingsTab.General.ordinal) }
    var selectedTab by selectedTabState
    val tabs = SettingsTab.entries
    val scope = rememberCoroutineScope()
    val activeSlot by settingsManager.activeSlot.collectAsState(1)
    val themeMode by settingsManager.themeMode.collectAsState(THEME_FOLLOW_SYSTEM)
    val screenSize by settingsManager.screenSize.collectAsState(SettingsManager.SCREEN_SIZE_FIT)
    fun moveTab(previous: Boolean) {
        selectedTabState.intValue = if (previous) {
            (selectedTabState.intValue - 1).coerceAtLeast(0)
        } else {
            (selectedTabState.intValue + 1).coerceAtMost(tabs.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = onResume,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = ((view as? DialogWindowProvider)
                ?: generateSequence(view.parent) { it.parent }.filterIsInstance<DialogWindowProvider>().firstOrNull())?.window
            val previous = window?.callback
            val bridge = if (previous != null) object : android.view.Window.Callback by previous {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (isTabPreviousKey(event.keyCode) || isTabNextKey(event.keyCode)) {
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            moveTab(isTabPreviousKey(event.keyCode))
                        }
                        return true
                    }
                    if (event.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                        if (event.action == KeyEvent.ACTION_DOWN) view.requestFocusFromTouch()
                        return previous.dispatchKeyEvent(
                            KeyEvent(
                                event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER,
                                event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source
                            )
                        )
                    }
                    if (event.keyCode in setOf(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_MODE)) {
                        if (event.action == KeyEvent.ACTION_UP) onResume()
                        return true
                    }
                    return previous.dispatchKeyEvent(event)
                }
            } else null
            if (bridge != null) window?.callback = bridge
            onDispose { if (window?.callback === bridge && previous != null) window.callback = previous }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        val key = event.nativeKeyEvent
                        when {
                            isTabPreviousKey(key.keyCode) || isTabNextKey(key.keyCode) -> {
                                if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) {
                                    moveTab(isTabPreviousKey(key.keyCode))
                                }
                                true
                            }
                            key.keyCode in setOf(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_MODE) -> {
                                if (key.action == KeyEvent.ACTION_UP) onResume()
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 12.dp),
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    SettingsTabBar(
                        selected = selectedTab,
                        tabs = tabs,
                        onSelect = { selectedTab = it },
                    )
                    VerticalDivider(modifier = Modifier.fillMaxHeight())
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        when (tabs[selectedTab]) {
                            SettingsTab.Controller -> {
                                SettingsSection("Controller") {
                                    Text(
                                        "Remap GBA buttons. Record one button or hold a combination together.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    BindingEditor(settingsManager, BindingActions.controller)
                                }
                            }
                            SettingsTab.Hotkeys -> {
                                SettingsSection("Hotkeys") {
                                    Text(
                                        "Remap pause, fast-forward, save and load. A menu tap runs on release; using its combination does not also open pause.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    BindingEditor(settingsManager, BindingActions.hotkeys)
                                }
                            }
                            SettingsTab.Video -> {
                                SettingsSection("Screen size") {
                                    ChoiceRow(
                                        options = ScreenSizes,
                                        selected = screenSize,
                                        onSelect = { size -> scope.launch { settingsManager.setScreenSize(size) } },
                                    )
                                }
                                SettingsSection("Video shader") {
                                    ChoiceRow(
                                        options = VideoShaders,
                                        selected = shader,
                                        onSelect = onShaderChange,
                                    )
                                    SettingsSwitch("Original GBA colors", gbaColors, onGbaColorsChange)
                                }
                                SettingsSection("Import shaders") {
                                    ShaderImportPanel(settingsManager)
                                }
                                SettingsSection("Show FPS") {
                                    SettingsSwitch("Show FPS Counter", showFps, onShowFpsChange)
                                }
                            }
                            SettingsTab.General -> {
                                SettingsSection("Dark theme") {
                                    Text(
                                        "On uses a dark interface. Off uses light. Follow system matches Android.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val themeOptions = listOf(
                                        THEME_ON to "On",
                                        THEME_OFF to "Off",
                                        THEME_FOLLOW_SYSTEM to "Follow system",
                                    )
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        themeOptions.forEachIndexed { index, (value, label) ->
                                            SegmentedButton(
                                                selected = themeMode == value,
                                                onClick = { scope.launch { settingsManager.setThemeMode(value) } },
                                                shape = SegmentedButtonDefaults.itemShape(index, themeOptions.size),
                                            ) {
                                                Text(label)
                                            }
                                        }
                                    }
                                }
                                SettingsSection("Fast Forward Speed") {
                                    ChoiceRow(
                                        options = listOf(1.5f to "1.5x", 2f to "2x", 2.5f to "2.5x", 3f to "3x", 3.5f to "3.5x", 4f to "4x"),
                                        selected = ffSpeed,
                                        onSelect = onFfSpeedChange,
                                    )
                                }
                                SettingsSection("Fast Forward Audio") {
                                    ChoiceRow(
                                        options = listOf(1 to "Increase Pitch", 2 to "No Correction", 0 to "Muted"),
                                        selected = ffAudioMode,
                                        onSelect = onFfAudioModeChange,
                                    )
                                }
                                SettingsSection("Display") {
                                    SettingsSwitch("Lock to Landscape", lockLandscape, onLockLandscapeChange)
                                }
                                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (onExit == null) "Close" else "Resume Game")
                                }
                                if (onRestart != null) {
                                    OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                                        Text("Restart Game")
                                    }
                                }
                                if (onExit != null) {
                                    TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                                        Text("Quit Game", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            SettingsTab.Cheats -> {
                                SettingsSection("Cheats") {
                                    if (statePath != null) {
                                        CheatPanel(statePath)
                                    } else {
                                        Text(
                                            "Open a game to add and toggle cheats.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            SettingsTab.Saves -> {
                                SettingsSection("Slot selection") {
                                    Text(
                                        "Hotkey save/load uses this slot.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ChoiceRow(
                                        options = (1..3).map { it to "Slot $it" },
                                        selected = activeSlot,
                                        onSelect = { slot -> scope.launch { settingsManager.setActiveSlot(slot) } },
                                    )
                                }
                                SettingsSection("Save / Load") {
                                    if (onSaveSlot != null && onLoadSlot != null) {
                                        Text(
                                            "Autosave is separate from these slots.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        for (slot in 1..3) {
                                            Text("Slot $slot: ${slotLabels.getOrNull(slot - 1) ?: "Empty"}")
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilledTonalButton(onClick = { onSaveSlot(slot) }) { Text("Save $slot") }
                                                OutlinedButton(
                                                    enabled = slotLabels.getOrNull(slot - 1) != "Empty",
                                                    onClick = { onLoadSlot(slot) },
                                                ) { Text("Load $slot") }
                                            }
                                        }
                                        if (status.isNotEmpty()) Text(status)
                                    } else {
                                        Text(
                                            "Open a game to save and load states.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        FilledTonalButton(onClick = onResume) {
                            Text(if (onExit == null) "Close" else "Resume")
                        }
                    }
                }
            }
        }
    }
}

private fun isTabPreviousKey(code: Int) = code == KeyEvent.KEYCODE_BUTTON_L1 || code == KeyEvent.KEYCODE_PAGE_UP
private fun isTabNextKey(code: Int) = code == KeyEvent.KEYCODE_BUTTON_R1 || code == KeyEvent.KEYCODE_PAGE_DOWN

@Composable
private fun SettingsTabBar(
    selected: Int,
    tabs: List<SettingsTab>,
    onSelect: (Int) -> Unit,
) {
    val focusRequesters = remember { tabs.map { FocusRequester() } }
    LaunchedEffect(selected) {
        runCatching { focusRequesters.getOrNull(selected)?.requestFocus() }
    }
    Column(
        modifier = Modifier
            .width(196.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .focusGroup()
            .onPreviewKeyEvent { event ->
                val key = event.nativeKeyEvent
                if (key.action != KeyEvent.ACTION_DOWN || key.repeatCount > 0) return@onPreviewKeyEvent false
                when (key.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (selected > 0) onSelect(selected - 1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (selected < tabs.lastIndex) onSelect(selected + 1)
                        true
                    }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            NavigationDrawerItem(
                label = { Text(tab.label) },
                selected = selected == index,
                onClick = { onSelect(index) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequesters[index])
                    .onFocusChanged { if (it.isFocused) onSelect(index) },
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
