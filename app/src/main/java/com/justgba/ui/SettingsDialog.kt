package com.justgba.ui

import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.justgba.input.BindingAction
import com.justgba.input.BindingActions
import com.justgba.settings.Picture
import com.justgba.settings.SaveLocations
import com.justgba.settings.SettingsManager
import com.justgba.ui.theme.THEME_FOLLOW_SYSTEM
import com.justgba.ui.theme.THEME_OFF
import com.justgba.ui.theme.THEME_ON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private sealed interface MenuPage {
    data object General : MenuPage
    data object Video : MenuPage
    data object Controller : MenuPage
    data object Hotkeys : MenuPage
    data object Saves : MenuPage
    data object Cheats : MenuPage
}

private val Sections = listOf(
    MenuPage.General to "General",
    MenuPage.Video to "Video",
    MenuPage.Controller to "Controller",
    MenuPage.Hotkeys to "Hotkeys",
    MenuPage.Saves to "Saves",
    MenuPage.Cheats to "Cheats",
)

private val VideoShaders = listOf(0 to "Sharp", 1 to "Smooth", 4 to "2xBR")

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

private val ThemeOptions = listOf(
    THEME_ON to "On",
    THEME_OFF to "Off",
    THEME_FOLLOW_SYSTEM to "Follow system",
)

private val SpeedOptions = listOf(1.5f to "1.5x", 2f to "2x", 2.5f to "2.5x", 3f to "3x", 3.5f to "3.5x", 4f to "4x")

private val AudioOptions = listOf(1 to "Increase pitch", 2 to "No correction", 0 to "Muted")

private val FrameRates = listOf(SettingsManager.FRAME_RATE_NATIVE to "Native", 90 to "90", 120 to "120")

@OptIn(ExperimentalMaterial3Api::class)
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
    frameRate: Int = SettingsManager.FRAME_RATE_NATIVE,
    onFrameRateChange: (Int) -> Unit = {},
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keys = remember { MenuKeys() }
    var tab by remember { mutableStateOf<MenuPage>(MenuPage.General) }
    var picking by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf("size") }
    var pendingAction by remember { mutableStateOf<BindingAction?>(null) }
    var recorder by remember { mutableStateOf<HotkeyCaptureDialog?>(null) }
    var shaderSlot by remember { mutableStateOf(0) }
    var shaderNote by remember { mutableStateOf("") }
    val bindings by settingsManager.bindings.collectAsState(BindingActions.defaults)
    val shaderFiles by settingsManager.shaderFiles.collectAsState(listOf("", ""))
    val shaderScales by settingsManager.shaderScales.collectAsState(listOf(1, 1))
    val shaderImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importShaderDocument(context, uri, shaderSlot, settingsManager, scope) { shaderNote = it }
    }
    DisposableEffect(Unit) { onDispose { recorder?.dismiss() } }
    val activeSlot by settingsManager.activeSlot.collectAsState(1)
    val themeMode by settingsManager.themeMode.collectAsState(THEME_FOLLOW_SYSTEM)
    val screenSize by settingsManager.screenSize.collectAsState(SettingsManager.SCREEN_SIZE_FIT)
    val picture by settingsManager.picture.collectAsState(Picture())
    var draft by remember { mutableStateOf<Picture?>(null) }
    val shown = draft ?: picture
    val held = remember { PictureHolder() }
    SideEffect { if (draft == null) held.picture = picture }
    val sections = Sections.map { it.first }

    fun closePick() { picking = false }

    fun back() {
        if (keys.nestedBack?.invoke() == true) return
        if (picking) {
            picking = false
            return
        }
        onResume()
    }

    fun switchTab(previous: Boolean) {
        val index = sections.indexOf(tab)
        if (index < 0) return
        val next = if (previous) index - 1 else index + 1
        if (next in sections.indices) {
            tab = sections[next]
            picking = false
        }
    }

    fun bindingRows(actions: List<BindingAction>): List<MenuRow> = actions.map { action ->
        MenuRow(
            title = action.label,
            value = bindingNames(bindings[action.id] ?: ""),
            onClick = {
                pendingAction = action
                picker = "binding"
                picking = true
            },
        )
    }

    fun updatePicture(next: Picture) {
        val sanitized = next.sanitized()
        held.picture = sanitized
        draft = sanitized
        scope.launch { settingsManager.setPicture(sanitized) }
    }

    SideEffect {
        keys.back = { back() }
        keys.tab = { previous -> switchTab(previous) }
    }

    Dialog(
        onDismissRequest = onResume,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = ((view as? DialogWindowProvider)
                ?: generateSequence(view.parent) { it.parent }.filterIsInstance<DialogWindowProvider>().firstOrNull())?.window
            val previous = window?.callback
            val bridge = if (previous != null) object : android.view.Window.Callback by previous {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    val down = event.action == KeyEvent.ACTION_DOWN
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> if (keys.capture && down) { keys.up(); return true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> if (keys.capture && down) { keys.down(); return true }
                        KeyEvent.KEYCODE_DPAD_LEFT -> if (keys.capture && down) { keys.left(); return true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> if (keys.capture && down) { keys.right(); return true }
                        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (keys.capture) {
                            if (down && event.repeatCount == 0) keys.confirm()
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_PAGE_UP -> {
                            if (down && event.repeatCount == 0) keys.tab(true)
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_PAGE_DOWN -> {
                            if (down && event.repeatCount == 0) keys.tab(false)
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_MODE -> {
                            if (event.action == KeyEvent.ACTION_UP) keys.back()
                            return true
                        }
                    }
                    if (!keys.capture && event.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                        return previous.dispatchKeyEvent(
                            KeyEvent(
                                event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER,
                                event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source,
                            )
                        )
                    }
                    return previous.dispatchKeyEvent(event)
                }
            } else null
            if (bridge != null) window?.callback = bridge
            onDispose { if (window?.callback === bridge && previous != null) window.callback = previous }
        }
        val heading = if (!picking) title else when (picker) {
            "size" -> "Screen size"
            "shader" -> "Shader"
            "theme" -> "Dark theme"
            "ffspeed" -> "Fast forward"
            "ffaudio" -> "Fast forward audio"
            "fps" -> "Frame rate"
            "slot" -> "Slot"
            "pass" -> "Shader pass ${shaderSlot + 1}"
            "binding" -> pendingAction?.label ?: "Binding"
            else -> "Choose"
        }
        val tabIndex = sections.indexOf(tab).coerceAtLeast(0)
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { back() }) {
                        Text(if (picking) "Back" else if (onExit == null) "Close" else "Resume")
                    }
                    Text(
                        heading,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PrimaryScrollableTabRow(selectedTabIndex = tabIndex) {
                    Sections.forEachIndexed { index, (destination, label) ->
                        Tab(
                            selected = index == tabIndex,
                            onClick = { tab = destination; picking = false },
                            text = { Text(label, maxLines = 1) },
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (picking) {
                        when (picker) {
                            "size" -> OptionList(ScreenSizes, screenSize, keys, "size") { scope.launch { settingsManager.setScreenSize(it) }; closePick() }
                            "shader" -> OptionList(VideoShaders, shader, keys, "shader") { onShaderChange(it); closePick() }
                            "theme" -> OptionList(ThemeOptions, themeMode, keys, "theme") { scope.launch { settingsManager.setThemeMode(it) }; closePick() }
                            "ffspeed" -> OptionList(SpeedOptions, ffSpeed, keys, "ffspeed") { onFfSpeedChange(it); closePick() }
                            "ffaudio" -> OptionList(AudioOptions, ffAudioMode, keys, "ffaudio") { onFfAudioModeChange(it); closePick() }
                            "fps" -> OptionList(FrameRates, frameRate, keys, "fps") { onFrameRateChange(it); closePick() }
                            "slot" -> OptionList((1..3).map { it to "Slot $it" }, activeSlot, keys, "slot") {
                                scope.launch { settingsManager.setActiveSlot(it) }
                                closePick()
                            }
                            "pass" -> MenuList(
                                rows = listOf(
                                    MenuRow("Import", onClick = { shaderImport.launch(arrayOf("*/*")); closePick() }),
                                    MenuRow("Remove", onClick = { scope.launch { settingsManager.setShaderFile(shaderSlot, "") }; closePick() }),
                                    MenuRow("1x", if (shaderScales.getOrNull(shaderSlot) == 1) "Selected" else "", onClick = {
                                        scope.launch { settingsManager.setShaderScale(shaderSlot, 1) }
                                        closePick()
                                    }),
                                    MenuRow("2x", if (shaderScales.getOrNull(shaderSlot) == 2) "Selected" else "", onClick = {
                                        scope.launch { settingsManager.setShaderScale(shaderSlot, 2) }
                                        closePick()
                                    }),
                                ),
                                keys = keys,
                                resetKey = "pass-$shaderSlot",
                                modifier = Modifier.fillMaxSize(),
                            )
                            else -> {
                                val action = pendingAction
                                MenuList(
                                    rows = if (action == null) emptyList() else listOf(
                                        MenuRow("Record", detail = "Hold a combination, then confirm.", onClick = {
                                            recorder?.dismiss()
                                            recorder = HotkeyCaptureDialog(context, action.label) { pressed ->
                                                scope.launch { settingsManager.setBinding(action.id, pressed) }
                                            }.also { it.show() }
                                            closePick()
                                        }),
                                        MenuRow("Clear", onClick = { scope.launch { settingsManager.setBinding(action.id, emptySet()) }; closePick() }),
                                        MenuRow("Default", onClick = { scope.launch { settingsManager.defaultBinding(action.id) }; closePick() }),
                                    ),
                                    keys = keys,
                                    resetKey = action?.id ?: "binding",
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    } else when (tab) {
                        MenuPage.General -> MenuList(
                            rows = buildList {
                                add(MenuRow("Dark theme", labelOf(ThemeOptions, themeMode), onClick = { picker = "theme"; picking = true }))
                                add(MenuRow("Fast forward", labelOf(SpeedOptions, ffSpeed), onClick = { picker = "ffspeed"; picking = true }))
                                add(MenuRow("Fast forward audio", labelOf(AudioOptions, ffAudioMode), onClick = { picker = "ffaudio"; picking = true }))
                                add(MenuRow(
                                    "Lock to landscape",
                                    if (lockLandscape) "On" else "Off",
                                    onClick = { onLockLandscapeChange(!lockLandscape) },
                                    onStep = { onLockLandscapeChange(!lockLandscape) },
                                ))
                                onRestart?.let { add(MenuRow("Restart game", onClick = it)) }
                                onExit?.let { add(MenuRow("Quit game", destructive = true, onClick = it)) }
                            },
                            keys = keys,
                            resetKey = MenuPage.General,
                            modifier = Modifier.fillMaxSize(),
                        )
                        MenuPage.Video -> MenuList(
                            rows = listOf(
                                MenuRow(
                                    "Frame rate",
                                    labelOf(FrameRates, frameRate),
                                    "Native keeps normal GBA speed. 90 and 120 run the game faster.",
                                    onClick = { picker = "fps"; picking = true },
                                ),
                                MenuRow("Screen size", labelOf(ScreenSizes, screenSize), "Fit keeps the game's shape. Integer keeps every pixel the same size.", onClick = { picker = "size"; picking = true }),
                                MenuRow("Shader", labelOf(VideoShaders, shader), onClick = { picker = "shader"; picking = true }),
                                MenuRow(
                                    "Original GBA colors",
                                    if (gbaColors) "On" else "Off",
                                    "Matches the color of a GBA screen.",
                                    onClick = { onGbaColorsChange(!gbaColors) },
                                    onStep = { onGbaColorsChange(!gbaColors) },
                                ),
                                sliderRow("Brightness", shown.brightness, { held.picture.brightness }, Picture.BRIGHTNESS_RANGE, percentLabel(shown.brightness), 1f) { updatePicture(held.picture.copy(brightness = it)) },
                                sliderRow("Contrast", shown.contrast, { held.picture.contrast }, Picture.CONTRAST_RANGE, percentLabel(shown.contrast), 1f) { updatePicture(held.picture.copy(contrast = it)) },
                                sliderRow("Saturation", shown.saturation, { held.picture.saturation }, Picture.SATURATION_RANGE, percentLabel(shown.saturation), 1f) { updatePicture(held.picture.copy(saturation = it)) },
                                sliderRow("Temperature", shown.temperature, { held.picture.temperature }, Picture.TEMPERATURE_RANGE, temperatureLabel(shown.temperature), 0f) { updatePicture(held.picture.copy(temperature = it)) },
                                sliderRow("Sharpness", shown.sharpness, { held.picture.sharpness }, Picture.SHARPNESS_RANGE, percentLabel(shown.sharpness), 0f) { updatePicture(held.picture.copy(sharpness = it)) },
                                sliderRow("Pixel grid", shown.pixelGrid, { held.picture.pixelGrid }, Picture.GRID_RANGE, percentLabel(shown.pixelGrid), 0f) { updatePicture(held.picture.copy(pixelGrid = it)) }.copy(detail = "Darkens the gap between pixels."),
                                MenuRow(
                                    "Show FPS",
                                    if (showFps) "On" else "Off",
                                    onClick = { onShowFpsChange(!showFps) },
                                    onStep = { onShowFpsChange(!showFps) },
                                ),
                                MenuRow(
                                    "Shader pass 1",
                                    shaderPassLabel(shaderFiles.getOrNull(0), shaderScales.getOrNull(0)),
                                    shaderNote,
                                    onClick = { shaderSlot = 0; picker = "pass"; picking = true },
                                ),
                                MenuRow(
                                    "Shader pass 2",
                                    shaderPassLabel(shaderFiles.getOrNull(1), shaderScales.getOrNull(1)),
                                    onClick = { shaderSlot = 1; picker = "pass"; picking = true },
                                ),
                            ),
                            keys = keys,
                            resetKey = MenuPage.Video,
                            modifier = Modifier.fillMaxSize(),
                        )
                        MenuPage.Controller -> MenuList(
                            rows = bindingRows(BindingActions.controller),
                            keys = keys,
                            resetKey = MenuPage.Controller,
                            modifier = Modifier.fillMaxSize(),
                        )
                        MenuPage.Hotkeys -> MenuList(
                            rows = bindingRows(BindingActions.hotkeys),
                            keys = keys,
                            resetKey = MenuPage.Hotkeys,
                            modifier = Modifier.fillMaxSize(),
                        )
                        MenuPage.Saves -> SavesPage(
                            settingsManager = settingsManager,
                            keys = keys,
                            activeSlot = activeSlot,
                            onPickSlot = { picker = "slot"; picking = true },
                            onSaveSlot = onSaveSlot,
                            onLoadSlot = onLoadSlot,
                            slotLabels = slotLabels,
                            status = status,
                        )
                        MenuPage.Cheats -> CheatMenu(statePath, keys)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> OptionList(
    options: List<Pair<T, String>>,
    selected: T,
    keys: MenuKeys,
    resetKey: Any,
    onSelect: (T) -> Unit,
) {
    MenuList(
        rows = options.map { (value, label) ->
            MenuRow(label, if (value == selected) "Selected" else "", onClick = { onSelect(value) })
        },
        keys = keys,
        resetKey = resetKey,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun SavesPage(
    settingsManager: SettingsManager,
    keys: MenuKeys,
    activeSlot: Int,
    onPickSlot: () -> Unit,
    onSaveSlot: ((Int) -> Unit)?,
    onLoadSlot: ((Int) -> Unit)?,
    slotLabels: List<String>,
    status: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val customPath by settingsManager.saveDirectory.collectAsState("")
    val resumeAutosave by settingsManager.resumeAutosave.collectAsState(false)
    val primaryRoot = remember { SaveLocations.primaryRoot() }
    val directory = remember(customPath) { SaveLocations.resolve(context, customPath) }
    var writable by remember(directory.absolutePath) { mutableStateOf(true) }
    LaunchedEffect(directory.absolutePath) {
        writable = withContext(Dispatchers.IO) { SaveLocations.isWritable(directory) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val chosen = SaveLocations.pathFromTreeUri(uri, primaryRoot)
        if (chosen == null) {
            Toast.makeText(context, "Choose a folder on internal storage or the SD card", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        val stored = if (File(chosen).absolutePath == SaveLocations.defaultDirectory(context).absolutePath) "" else chosen
        scope.launch {
            settingsManager.setSaveDirectory(stored)
            writable = withContext(Dispatchers.IO) { SaveLocations.isWritable(SaveLocations.resolve(context, stored)) }
            Toast.makeText(context, "Save folder updated", Toast.LENGTH_SHORT).show()
        }
    }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { writable = withContext(Dispatchers.IO) { SaveLocations.isWritable(directory) } }
    }
    Column(Modifier.fillMaxSize()) {
        if (!writable || status.isNotEmpty()) {
            Text(
                if (!writable) "JustBoy cannot write to this folder yet." else status,
                color = if (!writable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        MenuList(
            rows = buildList {
                add(MenuRow("Save folder", directory.name, directory.absolutePath, onClick = {
                    picker.launch(SaveLocations.initialDocumentUri(directory.absolutePath, primaryRoot))
                }))
                if (customPath.isNotBlank()) {
                    add(MenuRow("Use SD card/Saves", onClick = {
                        scope.launch {
                            settingsManager.setSaveDirectory("")
                            writable = withContext(Dispatchers.IO) { SaveLocations.isWritable(SaveLocations.defaultDirectory(context)) }
                        }
                    }))
                }
                if (!writable) {
                    add(MenuRow("Allow storage access", onClick = {
                        val opened = SaveLocations.requestStorageAccess(context) {
                            legacyPermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                        if (!opened) {
                            Toast.makeText(context, "Allow all files access for JustBoy in Android settings", Toast.LENGTH_LONG).show()
                        }
                    }))
                }
                add(MenuRow(
                    "Resume last session",
                    if (resumeAutosave) "On" else "Off",
                    "On continues from the last time you left.",
                    onClick = { scope.launch { settingsManager.setResumeAutosave(!resumeAutosave) } },
                    onStep = { scope.launch { settingsManager.setResumeAutosave(!resumeAutosave) } },
                ))
                add(MenuRow("Active slot", "Slot $activeSlot", "Hotkey save and load use this slot.", onClick = onPickSlot, onStep = { dir ->
                    scope.launch { settingsManager.setActiveSlot((activeSlot + dir).coerceIn(1, 3)) }
                }))
                if (onSaveSlot != null && onLoadSlot != null) {
                    for (slot in 1..3) {
                        val label = slotLabels.getOrNull(slot - 1) ?: "Empty"
                        add(MenuRow("Save slot $slot", label, onClick = { onSaveSlot(slot) }))
                        add(MenuRow("Load slot $slot", label, onClick = { if (label != "Empty") onLoadSlot(slot) }))
                    }
                } else {
                    add(MenuRow("Save states", detail = "Open a game to save and load states."))
                }
            },
            keys = keys,
            resetKey = MenuPage.Saves,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

private fun sliderRow(
    title: String,
    value: Float,
    current: () -> Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    reset: Float,
    apply: (Float) -> Unit,
): MenuRow = MenuRow(
    title = title,
    value = label,
    onClick = { apply(reset) },
    onStep = { dir -> apply(Picture.step(current(), dir, range)) },
    slider = value,
    sliderRange = range,
    onSlider = apply,
)

private fun <T> labelOf(options: List<Pair<T, String>>, selected: T): String =
    options.firstOrNull { it.first == selected }?.second ?: ""

private class PictureHolder(var picture: Picture = Picture())

private fun shaderPassLabel(path: String?, scale: Int?): String {
    val name = path?.takeIf { it.isNotEmpty() }?.let { File(it).name } ?: return "Off"
    return "$name ${scale ?: 1}x"
}

private fun percentLabel(value: Float) = "${(value * 100f).roundToInt()}%"

private fun temperatureLabel(value: Float): String {
    val n = (value * 100f).roundToInt()
    return when {
        n > 0 -> "Warm $n"
        n < 0 -> "Cool ${-n}"
        else -> "Neutral"
    }
}
