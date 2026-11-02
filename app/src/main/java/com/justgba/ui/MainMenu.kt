package com.justgba.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.justgba.data.RecentRomEntry
import com.justgba.data.RecentRomsManager
import com.justgba.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun MainMenu(
    recentRomsManager: RecentRomsManager,
    settingsManager: SettingsManager,
    onRomSelected: (Uri) -> Unit,
    onRecentSelected: (RecentRomEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recents by recentRomsManager.recentRoms.collectAsState(initial = emptyList())
    var isSettingsOpen by remember { mutableStateOf(false) }

    val shader by settingsManager.shader.collectAsState(0)
    val gbaColors by settingsManager.gbaColors.collectAsState(false)
    val ffSpeed by settingsManager.ffSpeed.collectAsState(2f)
    val ffAudioMode by settingsManager.ffAudioMode.collectAsState(1)
    val showFps by settingsManager.showFps.collectAsState(false)
    val lockLandscape by settingsManager.lockLandscape.collectAsState(false)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onRomSelected(it) }
    }

    var selectedEntry by remember { mutableStateOf<RecentRomEntry?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val entry = selectedEntry ?: return@rememberLauncherForActivityResult
        selectedEntry = null
        val saveFile = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "${File(entry.displayName).nameWithoutExtension}.sav"
        )
        if (!saveFile.exists()) {
            Toast.makeText(context, "No save file found", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val target = uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(target)?.use { out ->
                    saveFile.inputStream().use { inp -> inp.copyTo(out) }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save exported", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val entry = selectedEntry ?: return@rememberLauncherForActivityResult
        selectedEntry = null
        val source = uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val saveFile = File(
                    context.getExternalFilesDir(null) ?: context.filesDir,
                    "${File(entry.displayName).nameWithoutExtension}.sav"
                )
                context.contentResolver.openInputStream(source)?.use { inp ->
                    saveFile.outputStream().use { out -> inp.copyTo(out) }
                }
                val stateFile = File(
                    context.getExternalFilesDir(null) ?: context.filesDir,
                    "${File(entry.displayName).nameWithoutExtension}.state.auto"
                )
                if (stateFile.exists()) stateFile.delete()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save imported", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalIconButton(onClick = { isSettingsOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                        )
                    }
                    Column {
                        Text(
                            text = "JustBoy",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "GBA · GB · GBC",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Button(
                        onClick = {
                            launcher.launch(
                                arrayOf(
                                    "*/*",
                                    "application/octet-stream",
                                    "application/gba",
                                    "application/zip",
                                    "application/x-zip-compressed",
                                )
                            )
                        },
                        modifier = Modifier.widthIn(min = 200.dp),
                    ) {
                        Text("Select ROM File")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Supports .gba, .gbc, .gb, .bin, .agb, .zip files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (recents.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "Recently Played",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(recents, key = { it.uri }) { entry ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RecentRow(
                                entry = entry,
                                onClick = { onRecentSelected(entry) },
                                onExportSave = {
                                    selectedEntry = entry
                                    exportLauncher.launch(
                                        "${File(entry.displayName).nameWithoutExtension}.sav"
                                    )
                                },
                                onImportSave = {
                                    selectedEntry = entry
                                    importLauncher.launch(arrayOf("*/*"))
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (isSettingsOpen) {
        SettingsDialog(
            settingsManager = settingsManager,
            shader = shader,
            onShaderChange = { scope.launch { settingsManager.setShader(it) } },
            gbaColors = gbaColors,
            onGbaColorsChange = { scope.launch { settingsManager.setGbaColors(it) } },
            ffSpeed = ffSpeed,
            ffAudioMode = ffAudioMode,
            showFps = showFps,
            lockLandscape = lockLandscape,
            onFfSpeedChange = { speed ->
                scope.launch { settingsManager.setFfSpeed(speed) }
            },
            onFfAudioModeChange = { mode ->
                scope.launch { settingsManager.setFfAudioMode(mode) }
            },
            onShowFpsChange = { show ->
                scope.launch { settingsManager.setShowFps(show) }
            },
            onLockLandscapeChange = { locked ->
                scope.launch { settingsManager.setLockLandscape(locked) }
            },
            onResume = { isSettingsOpen = false },
            title = "Settings",
        )
    }
}

@Composable
private fun RecentRow(
    entry: RecentRomEntry,
    onClick: () -> Unit,
    onExportSave: () -> Unit,
    onImportSave: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val timeString = DateFormat.getDateTimeInstance(
        DateFormat.SHORT, DateFormat.SHORT
    ).format(Date(entry.lastPlayed))

    ListItem(
        headlineContent = {
            Text(
                text = entry.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(timeString)
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More",
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Export Save") },
                        onClick = {
                            menuExpanded = false
                            onExportSave()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import Save") },
                        onClick = {
                            menuExpanded = false
                            onImportSave()
                        },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
