package com.justgba.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun QuickMenu(
    activeSlot: Int,
    slotLabels: List<String>,
    onSlotChange: (Int) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onQuitGame: () -> Unit,
    onQuitEmulator: () -> Unit,
    onSettings: () -> Unit,
) {
    val keys = remember { MenuKeys() }
    var picking by remember { mutableStateOf(false) }
    val slotLabel = slotLabels.getOrNull(activeSlot - 1) ?: "Empty"

    SideEffect { keys.back = { if (picking) picking = false else onResume() } }

    Dialog(
        onDismissRequest = onResume,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false),
    ) {
        MenuKeyCapture(keys)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(0.86f).heightIn(max = 640.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                    Text(
                        if (picking) "Save slot" else "Menu",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (picking) {
                        MenuList(
                            rows = (1..3).map { slot ->
                                val label = slotLabels.getOrNull(slot - 1) ?: "Empty"
                                MenuRow(
                                    "Slot $slot",
                                    if (slot == activeSlot) "Selected" else label,
                                    onClick = {
                                        onSlotChange(slot)
                                        picking = false
                                    },
                                )
                            },
                            keys = keys,
                            resetKey = "slot",
                        )
                    } else {
                        MenuList(
                            rows = listOf(
                                MenuRow(
                                    "Save slot",
                                    "Slot $activeSlot",
                                    slotLabel,
                                    onClick = { picking = true },
                                    onStep = { dir -> onSlotChange((activeSlot + dir).coerceIn(1, 3)) },
                                ),
                                MenuRow("Save state", onClick = onSave),
                                MenuRow("Load state", slotLabel, onClick = onLoad),
                                MenuRow("Settings", onClick = onSettings),
                                MenuRow("Resume game", onClick = onResume),
                                MenuRow("Restart game", onClick = onRestart),
                                MenuRow("Quit game", destructive = true, onClick = onQuitGame),
                                MenuRow("Quit emulator", destructive = true, onClick = onQuitEmulator),
                            ),
                            keys = keys,
                            resetKey = "quick",
                        )
                    }
                }
            }
        }
    }
}
