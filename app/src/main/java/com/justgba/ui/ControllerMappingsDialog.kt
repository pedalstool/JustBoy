package com.justgba.ui

import android.content.ContextWrapper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.justgba.input.BindingAction
import com.justgba.input.BindingActions
import com.justgba.settings.SettingsManager
import kotlinx.coroutines.launch

@Composable
fun BindingEditor(settings: SettingsManager, actions: List<BindingAction>) {
    val context=LocalContext.current
    val fallbackScope=rememberCoroutineScope()
    val activity=generateSequence(context){(it as? ContextWrapper)?.baseContext}.filterIsInstance<ComponentActivity>().firstOrNull()
    val scope=activity?.lifecycleScope ?: fallbackScope
    val bindings by settings.bindings.collectAsState(BindingActions.defaults)
    var status by remember { mutableStateOf("") }
    var recorder by remember { mutableStateOf<HotkeyCaptureDialog?>(null) }
    DisposableEffect(Unit) { onDispose { recorder?.dismiss() } }
    if (status.isNotEmpty()) {
        Text(status, color = MaterialTheme.colorScheme.primary)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (action in actions) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(action.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        bindingNames(bindings[action.id] ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            recorder = HotkeyCaptureDialog(context, action.label) { keys ->
                                scope.launch {
                                    try {
                                        settings.setBinding(action.id, keys)
                                        status = "Saved ${action.label}: ${bindingNames(BindingActions.encode(keys))}"
                                    } catch (e: Exception) {
                                        status = "Could not save: ${e.message}"
                                    }
                                }
                            }.also { it.show() }
                        }) { Text("Record") }
                        OutlinedButton(onClick = { scope.launch { settings.setBinding(action.id, emptySet()) } }) {
                            Text("Clear")
                        }
                        TextButton(onClick = { scope.launch { settings.defaultBinding(action.id) } }) {
                            Text("Default")
                        }
                    }
                }
            }
        }
    }
}

internal fun bindingNames(value: String): String {
    val chords = BindingActions.parse(value)
    if (chords.isEmpty()) return "Unbound"
    return chords.joinToString(" / ") { keys ->
        keys.sorted().joinToString(" + ") {
            "${KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_").removePrefix("BUTTON_")} [$it]"
        }
    }
}

