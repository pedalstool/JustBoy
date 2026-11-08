package com.justgba.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "justgba_settings")

class SettingsManager(private val context: Context) {
    companion object {
        val FF_SPEED = floatPreferencesKey("ff_speed")
        val FF_AUDIO_MODE = intPreferencesKey("ff_audio_mode")
        val SHOW_FPS = booleanPreferencesKey("show_fps")
        val FF_HOLD_KEY = intPreferencesKey("ff_hold_key")
        val FF_TOGGLE_KEY = intPreferencesKey("ff_toggle_key")
        val LOCK_LANDSCAPE = booleanPreferencesKey("lock_landscape")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val SCREEN_SIZE = intPreferencesKey("screen_size")
        val SAVE_DIRECTORY = stringPreferencesKey("save_directory")
        val RESUME_AUTOSAVE = booleanPreferencesKey("resume_autosave")
        const val SCREEN_SIZE_FIT = 0
        const val SCREEN_SIZE_INTEGER = 1
        val PICTURE_BRIGHTNESS = floatPreferencesKey("picture_brightness")
        val PICTURE_CONTRAST = floatPreferencesKey("picture_contrast")
        val PICTURE_SATURATION = floatPreferencesKey("picture_saturation")
        val PICTURE_TEMPERATURE = floatPreferencesKey("picture_temperature")
        val PICTURE_SHARPNESS = floatPreferencesKey("picture_sharpness")
        val PICTURE_GRID = floatPreferencesKey("picture_grid")
    }

    val bindings: Flow<Map<String,String>> = context.dataStore.data.map { p ->
        com.justgba.input.BindingActions.all.associate { a ->
            val legacy = when(a.id) {
                "ff_hold" -> p[stringPreferencesKey("hotkey_ff_hold")] ?: p[FF_HOLD_KEY]?.toString()
                "ff_toggle" -> p[stringPreferencesKey("hotkey_ff_toggle")] ?: p[FF_TOGGLE_KEY]?.toString()
                "save","load" -> p[stringPreferencesKey("hotkey_${a.id}")]
                else -> null
            }
            a.id to (p[stringPreferencesKey("binding_${a.id}")] ?: legacy ?: com.justgba.input.BindingActions.defaults.getValue(a.id))
        }
    }
    suspend fun setBinding(action:String, keys:Set<Int>) {
        val value=com.justgba.input.BindingActions.encode(keys)
        val current=bindings.first()
        context.dataStore.edit { p ->
            // Explicit reassignment removes exact collisions, including a default menu binding.
            for((other,encoded)in current)if(other!=action) {
                val remaining=com.justgba.input.BindingActions.parse(encoded).filter{it!=keys}
                if(remaining.size!=com.justgba.input.BindingActions.parse(encoded).size)
                    p[stringPreferencesKey("binding_$other")]=remaining.joinToString(";"){com.justgba.input.BindingActions.encode(it)}
            }
            p[stringPreferencesKey("binding_$action")]=value
        }
    }
    suspend fun defaultBinding(action:String) { context.dataStore.edit { it[stringPreferencesKey("binding_$action")]=com.justgba.input.BindingActions.defaults.getValue(action) } }
    val activeSlot: Flow<Int> = context.dataStore.data.map { (it[intPreferencesKey("active_slot")] ?: 1).coerceIn(1,3) }
    suspend fun setActiveSlot(slot: Int) { context.dataStore.edit { it[intPreferencesKey("active_slot")] = slot.coerceIn(1,3) } }
    val shaderFiles: Flow<List<String>> = context.dataStore.data.map { p -> (0..1).map { p[stringPreferencesKey("shader_file_$it")] ?: "" } }
    val shaderScales: Flow<List<Int>> = context.dataStore.data.map { p -> (0..1).map { (p[intPreferencesKey("shader_scale_$it")] ?: 1).coerceIn(1,2) } }
    suspend fun setShaderFile(slot: Int, path: String) { context.dataStore.edit { it[stringPreferencesKey("shader_file_$slot")] = path } }
    suspend fun setShaderScale(slot: Int, scale: Int) { context.dataStore.edit { it[intPreferencesKey("shader_scale_$slot")] = scale.coerceIn(1,2) } }
    val gbaColors: Flow<Boolean> = context.dataStore.data.map { it[booleanPreferencesKey("gba_colors")] ?: false }
    suspend fun setGbaColors(value: Boolean) { context.dataStore.edit { it[booleanPreferencesKey("gba_colors")] = value } }
    val shader: Flow<Int> = context.dataStore.data.map {
        when (val value = it[intPreferencesKey("shader")] ?: 0) {
            2, 3 -> 0
            else -> value
        }
    }
    suspend fun setShader(value: Int) { context.dataStore.edit { it[intPreferencesKey("shader")] = value } }

    val ffSpeed: Flow<Float> = context.dataStore.data.map { prefs ->
        (prefs[FF_SPEED] ?: 2f).coerceIn(1.5f, 4f)
    }

    val ffAudioMode: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[FF_AUDIO_MODE] ?: 1
    }

    val showFps: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHOW_FPS] ?: false
    }

    val lockLandscape: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LOCK_LANDSCAPE] ?: true
    }

    val themeMode: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[THEME_MODE] ?: 0).coerceIn(0, 2)
    }

    val screenSize: Flow<Int> = context.dataStore.data.map { prefs ->
        when (val value = prefs[SCREEN_SIZE] ?: SCREEN_SIZE_FIT) {
            SCREEN_SIZE_FIT, SCREEN_SIZE_INTEGER -> value
            in 2..7 -> value
            else -> SCREEN_SIZE_FIT
        }
    }

    suspend fun setFfSpeed(speed: Float) {
        context.dataStore.edit { prefs ->
            prefs[FF_SPEED] = speed
        }
    }

    suspend fun setFfAudioMode(mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[FF_AUDIO_MODE] = mode
        }
    }

    suspend fun setShowFps(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHOW_FPS] = show
        }
    }

    suspend fun setLockLandscape(locked: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[LOCK_LANDSCAPE] = locked
        }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode.coerceIn(0, 2)
        }
    }

    val picture: Flow<Picture> = context.dataStore.data.map { prefs ->
        Picture(
            brightness = prefs[PICTURE_BRIGHTNESS] ?: 1f,
            contrast = prefs[PICTURE_CONTRAST] ?: 1f,
            saturation = prefs[PICTURE_SATURATION] ?: 1f,
            temperature = prefs[PICTURE_TEMPERATURE] ?: 0f,
            sharpness = prefs[PICTURE_SHARPNESS] ?: 0f,
            pixelGrid = prefs[PICTURE_GRID] ?: 0f,
        ).sanitized()
    }

    suspend fun setPicture(picture: Picture) {
        val next = picture.sanitized()
        context.dataStore.edit { prefs ->
            prefs[PICTURE_BRIGHTNESS] = next.brightness
            prefs[PICTURE_CONTRAST] = next.contrast
            prefs[PICTURE_SATURATION] = next.saturation
            prefs[PICTURE_TEMPERATURE] = next.temperature
            prefs[PICTURE_SHARPNESS] = next.sharpness
            prefs[PICTURE_GRID] = next.pixelGrid
        }
    }

    suspend fun setScreenSize(size: Int) {
        val value = when (size) {
            SCREEN_SIZE_FIT, SCREEN_SIZE_INTEGER -> size
            in 2..7 -> size
            else -> SCREEN_SIZE_FIT
        }
        context.dataStore.edit { prefs ->
            prefs[SCREEN_SIZE] = value
        }
    }

    val saveDirectory: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SAVE_DIRECTORY].orEmpty()
    }

    val resumeAutosave: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[RESUME_AUTOSAVE] ?: false
    }

    suspend fun setResumeAutosave(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[RESUME_AUTOSAVE] = enabled
        }
    }

    suspend fun setSaveDirectory(path: String) {
        val next = path.trim()
        val previous = saveDirectory.first()
        withContext(Dispatchers.IO) {
            val from = SaveLocations.resolve(context, previous)
            val to = SaveLocations.resolve(context, next)
            runCatching { SaveLocations.copyMissingSaves(from, to) }
            context.getExternalFilesDir(null)?.let { legacy ->
                runCatching { SaveLocations.copyMissingSaves(legacy, to) }
            }
        }
        context.dataStore.edit { prefs ->
            if (next.isEmpty()) prefs.remove(SAVE_DIRECTORY) else prefs[SAVE_DIRECTORY] = next
        }
    }

    suspend fun preparedSaveDirectory(): File = withContext(Dispatchers.IO) {
        val dir = SaveLocations.resolve(context, saveDirectory.first())
        SaveLocations.ensureDirectory(dir)
        context.getExternalFilesDir(null)?.let { legacy ->
            runCatching { SaveLocations.copyMissingSaves(legacy, dir) }
        }
        dir
    }
}






