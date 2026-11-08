package com.justgba.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.justgba.settings.SettingsManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ShaderImportPanel(settings: SettingsManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files by settings.shaderFiles.collectAsState(listOf("", ""))
    val scales by settings.shaderScales.collectAsState(listOf(1, 1))
    var message by remember { mutableStateOf("") }
    var importSlot by remember { mutableStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importShaderDocument(context, uri, importSlot, settings, scope) { message = it }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Two ordered passes. Self-contained GLSL (.glsl VERTEX/FRAGMENT or .frag). Presets, Slang and external includes are not supported.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (i in 0..1) {
            Text(
                "Pass ${i + 1}: ${files.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { File(it).name } ?: "Off"}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { importSlot = i; picker.launch(arrayOf("*/*")) }) { Text("Import") }
                TextButton(onClick = { scope.launch { settings.setShaderFile(i, "") } }) { Text("Remove") }
                for (scale in 1..2) {
                    FilterChip(
                        selected = scales.getOrNull(i) == scale,
                        onClick = { scope.launch { settings.setShaderScale(i, scale) } },
                        label = { Text("${scale}x") },
                    )
                }
            }
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    }
}

internal fun importShaderDocument(
    context: android.content.Context,
    uri: android.net.Uri,
    slot: Int,
    settings: SettingsManager,
    scope: kotlinx.coroutines.CoroutineScope,
    onMessage: (String) -> Unit,
) {
    try {
        val source = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readTextLimited(524288) }
        require(!source.contains("#include")) { "Includes need a bundled shader; import a self-contained .glsl or .frag file." }
        require(source.contains("void main")) { "Choose a GLSL shader file, not a .glslp or .slang preset." }
        val dir = File(context.filesDir, "imported-shaders").apply { mkdirs() }
        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "shader.glsl"
        val file = File(dir, "${System.currentTimeMillis()}-${File(name).name}")
        file.writeText(source)
        scope.launch { settings.setShaderFile(slot, file.path) }
        onMessage("Imported $name. Compile errors will appear when the game resumes.")
    } catch (e: Exception) {
        onMessage(e.message ?: "Import failed")
    }
}

private fun java.io.Reader.readTextLimited(limit: Int): String {
    val text = StringBuilder()
    val buffer = CharArray(4096)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        require(text.length + n <= limit) { "Shader exceeds 512 KB" }
        text.append(buffer, 0, n)
    }
    return text.toString()
}
