package com.justgba.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.justgba.emulator.EmulatorThread
import com.justgba.emulator.NativeBridge
import com.justgba.emulator.StateSlots
import com.justgba.input.BindingActions
import com.justgba.input.BindingEngine
import com.justgba.input.GameInput
import com.justgba.input.InputState
import com.justgba.settings.Picture
import com.justgba.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GameScreen(
    statePath: String?,
    onExit: () -> Unit,
    emulatorThread: EmulatorThread?,
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var isFastForwarding by remember { mutableStateOf(false) }
    var isMenuOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = !isMenuOpen) {
        isMenuOpen = true
        emulatorThread?.pause()
    }

    val scope = rememberCoroutineScope()
    var slotRevision by remember { mutableStateOf(0) }
    var slotStatus by remember { mutableStateOf("") }
    val slotLabels = remember(statePath, slotRevision) {
        (1..3).map { slot ->
            val file = File("${statePath?.removeSuffix(".state.auto")}.state.$slot")
            if (file.exists()) SimpleDateFormat("MMM d HH:mm", Locale.getDefault()).format(Date(file.lastModified())) else "Empty"
        }
    }

    val bindings by settingsManager.bindings.collectAsState(BindingActions.defaults)
    val activeSlot by settingsManager.activeSlot.collectAsState(1)
    val shaderFiles by settingsManager.shaderFiles.collectAsState(listOf("", ""))
    val shaderScales by settingsManager.shaderScales.collectAsState(listOf(1, 1))
    val context = LocalContext.current
    fun notice(message: String) {
        slotStatus = message
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    val slots = remember(statePath) { statePath?.let { StateSlots(it, NativeBridge::nativeStateSave, NativeBridge::nativeStateLoad) } }
    val saveSlot: (Int) -> Unit = { slot ->
        val ok = slots?.save(slot) == true
        notice(if (ok) "Saved slot $slot" else "Save failed"); slotRevision++
    }
    val loadSlot: (Int) -> Unit = { slot ->
        val ok = slots?.load(slot) == true
        InputState.clear(); notice(if (ok) "Loaded slot $slot" else "Slot $slot is empty or could not load")
    }
    val engine = remember(statePath) { BindingEngine() }
    SideEffect { engine.configure(bindings) }
    val dispatchFrame by rememberUpdatedState<(BindingEngine.Frame) -> Unit> { frame ->
        InputState.setPhysicalMask(frame.mask)
        isFastForwarding = frame.fastForward
        emulatorThread?.fastForward = frame.fastForward
        for (action in frame.actions) when (action) {
            "save" -> saveSlot(activeSlot)
            "load" -> loadSlot(activeSlot)
            "menu" -> { GameInput.menuOpen = true; isMenuOpen = true; emulatorThread?.pause() }
        }
    }
    DisposableEffect(isMenuOpen, statePath) {
        GameInput.menuOpen = isMenuOpen
        if (!isMenuOpen) {
            GameInput.key = { code, down -> val frame = engine.key(code, down); dispatchFrame(frame); frame.consumed }
            GameInput.reset = { dispatchFrame(engine.reset()) }
        }
        onDispose { engine.reset(); GameInput.key = null; GameInput.reset = null; InputState.clear() }
    }
    DisposableEffect(view) {
        val owner = view.findViewTreeLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) dispatchFrame(engine.reset())
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(statePath) {
        if (statePath != null) {
            val error = CheatStore.apply(CheatStore.load(statePath))
            if (error.isNotBlank()) notice(error)
        }
    }

    val gbaColors by settingsManager.gbaColors.collectAsState(false)
    val shader by settingsManager.shader.collectAsState(0)
    val screenSize by settingsManager.screenSize.collectAsState(SettingsManager.SCREEN_SIZE_FIT)
    val picture by settingsManager.picture.collectAsState(Picture())
    val ffSpeed by settingsManager.ffSpeed.collectAsState(2f)
    val ffAudioMode by settingsManager.ffAudioMode.collectAsState(1)
    val frameRate by settingsManager.frameRate.collectAsState(SettingsManager.FRAME_RATE_NATIVE)
    val showFps by settingsManager.showFps.collectAsState(false)
    val lockLandscape by settingsManager.lockLandscape.collectAsState(false)

    LaunchedEffect(ffSpeed) { emulatorThread?.ffSpeedMultiplier = ffSpeed }
    LaunchedEffect(ffAudioMode) { emulatorThread?.ffAudioMode = ffAudioMode }
    LaunchedEffect(frameRate) { emulatorThread?.targetFps = frameRate }

    val currentFps by (emulatorThread?.fps ?: MutableStateFlow(0)).collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        EmulatorSurface(
            gbaColors = gbaColors,
            shader = shader,
            screenSize = screenSize,
            shaderFiles = shaderFiles,
            shaderScales = shaderScales,
            picture = picture,
            modifier = Modifier.fillMaxSize()
        )

        if (showFps) {
            Text(
                text = "FPS: $currentFps",
                color = Color.Yellow,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                isMenuOpen = true
                emulatorThread?.pause()
            }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Menu",
                    tint = Color.White
                )
            }

            TextButton(onClick = { dispatchFrame(engine.toggleFastForward()) }) {
                Icon(
                    imageVector = Icons.Filled.FastForward,
                    contentDescription = "Fast Forward",
                    tint = if (isFastForwarding) Color(0xFF4CAF50) else Color(0xFFB0B0B0)
                )
                Text(if (isFastForwarding) "${ffSpeed}x" else "1x", color = Color.White)
            }
        }
    }

    if (isMenuOpen) {
        SettingsDialog(
            settingsManager = settingsManager,
            statePath = statePath,
            gbaColors = gbaColors,
            onGbaColorsChange = { scope.launch { settingsManager.setGbaColors(it) } },
            slotLabels = slotLabels,
            status = slotStatus,
            onSaveSlot = saveSlot,
            onLoadSlot = loadSlot,
            shader = shader,
            onShaderChange = { mode -> scope.launch { settingsManager.setShader(mode) } },
            ffSpeed = ffSpeed,
            ffAudioMode = ffAudioMode,
            frameRate = frameRate,
            onFrameRateChange = { rate ->
                scope.launch {
                    settingsManager.setFrameRate(rate)
                    emulatorThread?.targetFps = rate
                }
            },
            showFps = showFps,
            lockLandscape = lockLandscape,
            onFfSpeedChange = { speed ->
                scope.launch {
                    settingsManager.setFfSpeed(speed)
                    emulatorThread?.ffSpeedMultiplier = speed
                }
            },
            onFfAudioModeChange = { mode ->
                scope.launch {
                    settingsManager.setFfAudioMode(mode)
                    emulatorThread?.ffAudioMode = mode
                }
            },
            onShowFpsChange = { show ->
                scope.launch { settingsManager.setShowFps(show) }
            },
            onLockLandscapeChange = { locked ->
                scope.launch { settingsManager.setLockLandscape(locked) }
            },
            onResume = {
                isMenuOpen = false
                emulatorThread?.resume()
            },
            onRestart = {
                NativeBridge.nativeReset()
                isMenuOpen = false
                emulatorThread?.resume()
            },
            onExit = {
                isMenuOpen = false
                emulatorThread?.resume()
                onExit()
            },
        )
    }
}

@Composable
private fun EmulatorSurface(
    shader: Int, gbaColors: Boolean, screenSize: Int,
    shaderFiles: List<String>, shaderScales: List<Int>, picture: Picture, modifier: Modifier = Modifier,
) {
    AndroidView(factory = { ctx -> ShaderSurface(ctx) }, onRelease = { it.onPause() }, update = { view ->
        view.shaderMode = shader; view.gbaColors = gbaColors; view.screenSize = screenSize
        view.shaderFiles = shaderFiles; view.shaderScales = shaderScales; view.picture = picture
        view.coreWidth = NativeBridge.nativeGetVideoWidth().coerceAtLeast(1)
        view.coreHeight = NativeBridge.nativeGetVideoHeight().coerceAtLeast(1)
    }, modifier = modifier)
}
