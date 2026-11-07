package com.justgba

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.justgba.ui.theme.JustGbaTheme
import com.justgba.audio.AudioEngine
import com.justgba.data.RecentRomEntry
import com.justgba.data.RecentRomsManager
import com.justgba.emulator.EmulatorThread
import com.justgba.emulator.NativeBridge
import com.justgba.settings.SaveLocations
import com.justgba.settings.SettingsManager
import com.justgba.ui.GameScreen
import com.justgba.ui.MainMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private var audioEngine: AudioEngine? = null
    private var emulatorThread: EmulatorThread? = null
    private var currentSavePath by mutableStateOf<String?>(null)
    private var currentStatePath by mutableStateOf<String?>(null)
    private var askedForStorage = false
    private val writePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private lateinit var settingsManager: SettingsManager
    private lateinit var recentRomsManager: RecentRomsManager
    private var currentRomPath by mutableStateOf<String?>(null)
    private var launchedExternally = false

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN || event.action == android.view.KeyEvent.ACTION_UP) {
            if (com.justgba.input.GameInput.dispatch(event.keyCode, event.action == android.view.KeyEvent.ACTION_DOWN)) return true
        }
        return super.dispatchKeyEvent(event)
    }
    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (com.justgba.input.GameInput.motion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        settingsManager = SettingsManager(applicationContext)
        recentRomsManager = RecentRomsManager(applicationContext)
        lifecycleScope.launch {
            settingsManager.saveDirectory.collect { custom ->
                val dir = withContext(Dispatchers.IO) {
                    val directory = SaveLocations.resolve(custom, SaveLocations.primaryRoot())
                    SaveLocations.ensureDirectory(directory)
                    directory
                }
                val saveName = currentSavePath?.let { File(it).name } ?: return@collect
                val updatedSave = File(dir, saveName).absolutePath
                if (updatedSave != currentSavePath) {
                    currentSavePath = updatedSave
                    currentStatePath = currentStatePath?.let { File(dir, File(it).name).absolutePath }
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsManager.lockLandscape.collect { locked ->
                    requestedOrientation = if (locked) {
                        ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }

        handleIntent(intent)

        setContent {
            val themeMode by settingsManager.themeMode.collectAsState(0)
            JustGbaTheme(themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (currentRomPath == null) {
                        MainMenu(
                            recentRomsManager = recentRomsManager,
                            settingsManager = settingsManager,
                            onRomSelected = { uri ->
                                persistRomUri(uri)
                                val path = resolveRomPath(uri)
                                if (path != null) launchRom(path, getOriginalFilename(uri), uri)
                            },
                            onRecentSelected = { entry ->
                                val path = if (File(entry.cachePath).exists()) {
                                    entry.cachePath
                                } else {
                                    resolveRomPath(Uri.parse(entry.uri))
                                }
                                if (path != null) launchRom(path, entry.displayName, null)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        GameScreen(
                            statePath = currentStatePath,
                            onExit = {
                                stopEmulation()
                                currentRomPath = null
                                if (launchedExternally) finish()
                            },
                            emulatorThread = emulatorThread,
                            settingsManager = settingsManager,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            setIntent(intent)
            stopEmulation()
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        launchedExternally = true
        persistRomUri(uri)
        val path = resolveRomPath(uri) ?: return
        launchRom(path, getOriginalFilename(uri), uri)
    }

    private fun persistRomUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
    }

    private fun launchRom(path: String, displayName: String, uri: Uri?) {
        stopEmulation()
        val baseName = File(displayName).nameWithoutExtension
        val saveDir = runBlocking(Dispatchers.IO) { settingsManager.preparedSaveDirectory() }
        if (!SaveLocations.isWritable(saveDir)) {
            Toast.makeText(this, "Cannot write saves in ${saveDir.absolutePath}", Toast.LENGTH_LONG).show()
            requestSaveStorageAccess()
        }
        val savePath = File(saveDir, "$baseName.sav").absolutePath
        val statePath = File(saveDir, "$baseName.state.auto").absolutePath
        currentSavePath = savePath
        currentStatePath = statePath
        startEmulation(path, saveDir.absolutePath, savePath, statePath)
        currentRomPath = path
        if (uri != null) {
            lifecycleScope.launch {
                recentRomsManager.addOrUpdateEntry(
                    RecentRomEntry(
                        displayName = File(displayName).name,
                        uri = uri.toString(),
                        lastPlayed = System.currentTimeMillis(),
                        cachePath = path,
                    )
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        com.justgba.input.GameInput.clear()
        emulatorThread?.fastForward = false
        com.justgba.input.InputState.clear()
        emulatorThread?.pause()
        audioEngine?.pause()
        currentStatePath?.let { saveAtomically(it) { path -> NativeBridge.nativeStateSave(path) } }
        currentSavePath?.let { saveAtomically(it) { path -> NativeBridge.nativeBatterySave(path) } }
    }

    override fun onResume() {
        super.onResume()
        ensureSaveFolder()
        audioEngine?.resume()
        if(!com.justgba.input.GameInput.menuOpen) emulatorThread?.resume()
    }

    override fun onDestroy() {
        stopEmulation()
        super.onDestroy()
    }

    private fun resolveRomPath(uri: Uri): String? {
        try {
            val docId = if (uri.scheme == "content") {
                val parts = uri.lastPathSegment?.split(":")
                if (parts != null && parts.size > 1) parts[1] else null
            } else null

            if (docId != null && docId.startsWith("/")) {
                val file = File(docId)
                if (file.exists()) return file.absolutePath
            }

            val originalName = File(getOriginalFilename(uri)).name
            val cacheFile = File(cacheDir, originalName)
            contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            if (originalName.lowercase().endsWith(".zip")) {
                return extractRomFromZip(cacheFile)
            }

            return cacheFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve ROM path", e)
            return null
        }
    }

    private fun getOriginalFilename(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex) ?: "rom.gba"
                }
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment ?: "rom.gba"
    }

    private val romExtensions = setOf("gba", "gbc", "gb", "sgb", "bin", "agb")

    private fun extractRomFromZip(zipFile: File): String? {
        try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.entries().asSequence()
                    .firstOrNull { e ->
                        !e.isDirectory &&
                            romExtensions.contains(e.name.substringAfterLast('.').lowercase())
                    } ?: run {
                    Log.e(TAG, "No ROM file found in zip archive")
                    return null
                }

                val outputName = File(entry.name).name
                val outputFile = File(cacheDir, outputName)
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Extracted ${entry.name} from zip to $outputFile")
                return outputFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract ROM from zip", e)
            return null
        }
    }

    private fun ensureSaveFolder() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dir = settingsManager.preparedSaveDirectory()
            if (SaveLocations.isWritable(dir)) return@launch
            withContext(Dispatchers.Main) { requestSaveStorageAccess() }
        }
    }

    private fun requestSaveStorageAccess() {
        if (askedForStorage) return
        askedForStorage = true
        SaveLocations.requestStorageAccess(this) {
            writePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun startEmulation(romPath: String, saveDir: String, savePath: String, statePath: String) {
        val sysDir = filesDir.absolutePath

        if (!NativeBridge.nativeInit(sysDir, saveDir)) {
            Log.e(TAG, "nativeInit failed")
            return
        }

        if (!NativeBridge.nativeLoadGame(romPath)) {
            Log.e(TAG, "nativeLoadGame failed")
            NativeBridge.nativeDeinit()
            return
        }

        val saveFile = File(savePath)
        val stateFile = File(statePath)
        if (saveFile.exists() && !NativeBridge.nativeBatteryLoad(savePath)) {
            Log.w(TAG, "Could not load battery save $savePath")
        }
        val resumeAutosave = runBlocking { settingsManager.resumeAutosave.first() }
        if (resumeAutosave && stateFile.exists() && !NativeBridge.nativeStateLoad(statePath)) {
            Log.w(TAG, "Could not load auto state $statePath")
        }

        val audio = AudioEngine()
        if (!audio.init()) {
            Log.w(TAG, "AudioEngine init failed, continuing without audio")
        }
        audioEngine = audio

        val thread = EmulatorThread(audio)
        thread.start()
        emulatorThread = thread
    }

    private fun saveAtomically(path: String, writer: (String) -> Boolean) {
        val temporary = File("$path.tmp")
        if (!writer(temporary.path) || !temporary.renameTo(File(path))) {
            Log.e(TAG, "Could not save $path")
        }
    }

    private fun stopEmulation() {
        emulatorThread?.stop()
        emulatorThread = null
        currentStatePath?.let { saveAtomically(it) { path -> NativeBridge.nativeStateSave(path) } }
        currentSavePath?.let { saveAtomically(it) { path -> NativeBridge.nativeBatterySave(path) } }
        audioEngine?.release()
        audioEngine = null
        currentSavePath = null
        currentStatePath = null
        try {
            NativeBridge.nativeDeinit()
            Log.i(TAG, "Emulation stopped")
        } catch (e: Exception) {
            Log.e(TAG, "nativeDeinit failed", e)
        }
    }
}




