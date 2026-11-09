package com.justgba.ui

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.justgba.emulator.NativeBridge
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CheatEntry(val name: String, val code: String, val enabled: Boolean)
object CheatStore {
    fun file(statePath: String)=File(statePath.removeSuffix(".state.auto")+".cheats.json")
    fun load(statePath: String): List<CheatEntry> = runCatching {
        val a=JSONArray(file(statePath).readText()); (0 until minOf(a.length(),20)).map { i -> a.getJSONObject(i).let { CheatEntry(it.getString("name"),it.getString("code"),it.optBoolean("enabled")) } }
    }.getOrDefault(emptyList())
    fun save(path: String, entries: List<CheatEntry>) {
        val a=JSONArray(); entries.forEach { a.put(JSONObject().put("name",it.name).put("code",it.code).put("enabled",it.enabled)) }
        val target=file(path); val temp=File(target.path+".tmp"); temp.writeText(a.toString(2)); check(temp.renameTo(target)) {"Cannot save cheats"}
    }
    fun normalize(code: String): String {
        val lines=code.trim().lines().map { it.trim() }.filter { it.isNotBlank() }
        require(lines.isNotEmpty() && lines.size<=64) {"Enter 1–64 cheat lines"}
        val normalized=lines.joinToString("\n") { it.uppercase().replace(Regex("\\s+"), " ") }
        require(normalized.length<2048){"Cheat is too long"}
        return normalized
    }
    fun apply(entries: List<CheatEntry>): String {
        NativeBridge.nativeClearCheats()
        val errors=mutableListOf<String>()
        entries.forEachIndexed { i,e -> if(e.enabled) {
            val result=runCatching{NativeBridge.nativeSetCheat(i,normalize(e.code))}.getOrDefault(4)
            if(result!=0) errors.add("${e.name}: unsupported or invalid code")
        } }
        return errors.joinToString("\n")
    }
}
@Composable
fun CheatMenu(statePath: String?, keys: MenuKeys) {
    if (statePath == null) {
        MenuList(
            rows = listOf(MenuRow("Cheats", detail = "Open a game to add and toggle cheats.")),
            keys = keys,
            resetKey = "cheats-empty",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val context = LocalContext.current
    var entries by remember(statePath) { mutableStateOf(CheatStore.load(statePath)) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf("list") }
    var selected by remember { mutableIntStateOf(-1) }
    var editing by remember { mutableStateOf(false) }
    fun commit(next: List<CheatEntry>) {
        try {
            CheatStore.save(statePath, next)
            entries = next
            message = CheatStore.apply(next).ifBlank { "Cheats updated" }
        } catch (e: Exception) {
            message = e.message ?: "Cheat update failed"
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            require(text.length <= 65536) { "Cheat file too large" }
            val imported = mutableListOf<CheatEntry>()
            val codes = Regex("(?m)^cheat(\\d+)_code\\s*=\\s*\"([^\"]*)\"").findAll(text).toList()
            if (codes.isNotEmpty()) for (m in codes) {
                val id = m.groupValues[1]
                val desc = Regex("(?m)^cheat${id}_desc\\s*=\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1) ?: "Cheat $id"
                imported.add(CheatEntry(desc, CheatStore.normalize(m.groupValues[2].replace('+', '\n').replace("\\n", "\n")), false))
            } else imported.add(CheatEntry("Imported cheat", CheatStore.normalize(text), false))
            require(entries.size + imported.size <= 20) { "Maximum 20 cheats per game" }
            commit(entries + imported)
        } catch (e: Exception) {
            message = e.message ?: "Import failed"
        }
    }
    DisposableEffect(Unit) { onDispose { keys.nestedBack = null } }
    Column(Modifier.fillMaxSize()) {
        if (message.isNotBlank()) {
            Text(
                message,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (screen == "actions" && selected in entries.indices) {
            val entry = entries[selected]
            MenuList(
                rows = listOf(
                    MenuRow("On", if (entry.enabled) "Selected" else "", onClick = {
                        commit(entries.mapIndexed { i, c -> if (i == selected) c.copy(enabled = true) else c })
                        screen = "list"
                    }),
                    MenuRow("Off", if (!entry.enabled) "Selected" else "", onClick = {
                        commit(entries.mapIndexed { i, c -> if (i == selected) c.copy(enabled = false) else c })
                        screen = "list"
                    }),
                    MenuRow("Edit", onClick = { name = entry.name; code = entry.code; editing = true }),
                    MenuRow("Delete", destructive = true, onClick = {
                        commit(entries.filterIndexed { i, _ -> i != selected })
                        screen = "list"
                    }),
                ),
                keys = keys,
                resetKey = "cheat-$selected",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            MenuList(
                rows = buildList {
                    entries.forEachIndexed { i, entry ->
                        add(MenuRow(
                            entry.name,
                            if (entry.enabled) "On" else "Off",
                            entry.code.lineSequence().firstOrNull().orEmpty(),
                            onClick = { selected = i; screen = "actions" },
                        ))
                    }
                    add(MenuRow("Add cheat", onClick = {
                        selected = -1
                        name = ""
                        code = ""
                        editing = true
                    }))
                    add(MenuRow("Import cheat file", onClick = { picker.launch(arrayOf("*/*")) }))
                    add(MenuRow("Reset defaults", detail = "Removes the cheats for this game.", destructive = true, onClick = {
                        commit(emptyList())
                        if (entries.isEmpty()) message = "Cheats reset"
                    }))
                },
                keys = keys,
                resetKey = "cheats",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(if (selected in entries.indices) "Edit cheat" else "Add cheat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "One code pair per line. GBA uses CodeBreaker, GameShark, or Action Replay. GB and GBC use Game Genie or GameShark.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        require(name.isNotBlank()) { "Enter a name" }
                        require(selected in entries.indices || entries.size < 20) { "Maximum 20 cheats" }
                        val entry = CheatEntry(name.trim(), CheatStore.normalize(code), entries.getOrNull(selected)?.enabled == true)
                        val next = if (selected in entries.indices) entries.mapIndexed { i, c -> if (i == selected) entry else c } else entries + entry
                        commit(next)
                        editing = false
                        screen = "list"
                    } catch (e: Exception) {
                        message = e.message ?: "Invalid cheat"
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
    SideEffect {
        if (editing) keys.capture = false
        keys.nestedBack = {
            when {
                editing -> { editing = false; true }
                screen != "list" -> { screen = "list"; true }
                else -> false
            }
        }
    }
}
