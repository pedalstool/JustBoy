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
fun CheatPanel(statePath: String) {
    val context=LocalContext.current
    var entries by remember(statePath){mutableStateOf(CheatStore.load(statePath))}
    var name by remember{mutableStateOf("")}; var code by remember{mutableStateOf("")}
    var message by remember{mutableStateOf("")}
    var editing by remember{mutableStateOf<Int?>(null)}
    fun commit(next: List<CheatEntry>) {
        try { CheatStore.save(statePath,next); entries=next; message=CheatStore.apply(next).ifBlank{"Cheats updated"} }
        catch(e:Exception){message=e.message ?: "Cheat update failed"}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) try {
            val text=context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            require(text.length<=65536){"Cheat file too large"}
            val imported=mutableListOf<CheatEntry>()
            val codes=Regex("(?m)^cheat(\\d+)_code\\s*=\\s*\"([^\"]*)\"").findAll(text).toList()
            if(codes.isNotEmpty()) for(m in codes) {
                val id=m.groupValues[1]
                val desc=Regex("(?m)^cheat${id}_desc\\s*=\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1) ?: "Cheat $id"
                imported.add(CheatEntry(desc,CheatStore.normalize(m.groupValues[2].replace('+','\n').replace("\\n","\n")),false))
            } else imported.add(CheatEntry("Imported cheat",CheatStore.normalize(text),false))
            require(entries.size+imported.size<=20){"Maximum 20 cheats per game"}; commit(entries+imported)
        } catch(e:Exception){message=e.message ?: "Import failed"}
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "GBA: CodeBreaker, GameShark, or Action Replay. GB/GBC: Game Genie or GameShark. One code per line. Codes depend on the exact game/region.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        entries.forEachIndexed { i,e ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked=e.enabled,onCheckedChange={value -> commit(entries.mapIndexed{j,c -> if(i==j)c.copy(enabled=value) else c})})
                        Text(e.name,modifier=Modifier.weight(1f).padding(start = 8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick={editing=i;name=e.name;code=e.code}){Text("Edit")}
                        TextButton(onClick={commit(entries.filterIndexed{j,_->i!=j})}){Text("Delete", color = MaterialTheme.colorScheme.error)}
                    }
                }
            }
        }
        OutlinedTextField(value=name,onValueChange={name=it},label={Text("Cheat name")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=code,onValueChange={code=it},label={Text("Code (one pair per line)")},minLines=2,modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick={try {
                require(name.isNotBlank()){"Enter a name"}; require(editing!=null || entries.size<20){"Maximum 20 cheats"}
                val e=CheatEntry(name.trim(),CheatStore.normalize(code),false)
                val next=if(editing!=null)entries.mapIndexed{i,c->if(i==editing)e else c} else entries+e
                commit(next);name="";code="";editing=null
            }catch(e:Exception){message=e.message ?: "Invalid cheat"}}){Text(if(editing==null)"Add" else "Save edit")}
            OutlinedButton(onClick={picker.launch(arrayOf("*/*"))}){Text("Import .cht / .txt")}
        }
        if(message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    }
}
