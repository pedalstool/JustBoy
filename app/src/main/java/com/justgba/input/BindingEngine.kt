package com.justgba.input

data class BindingAction(val id: String, val label: String, val bit: Int? = null)
object BindingActions {
    val all=listOf(
        BindingAction("gba_a","A",8),BindingAction("gba_b","B",0),
        BindingAction("gba_up","Up",4),BindingAction("gba_down","Down",5),
        BindingAction("gba_left","Left",6),BindingAction("gba_right","Right",7),
        BindingAction("gba_l","L",10),BindingAction("gba_r","R",11),
        BindingAction("gba_start","Start",3),BindingAction("gba_select","Select",2),
        BindingAction("menu","Pause menu"),BindingAction("ff_hold","Fast-forward hold"),
        BindingAction("ff_toggle","Fast-forward toggle"),BindingAction("save","Save state"),BindingAction("load","Load state")
    )
    val controller=all.filter { it.bit != null }
    val hotkeys=all.filter { it.bit == null }
    // Semicolon separates alternative bindings; comma joins the keys within one combination.
    val defaults=mapOf("gba_a" to "96;52","gba_b" to "97;54","gba_up" to "19","gba_down" to "20",
        "gba_left" to "21","gba_right" to "22","gba_l" to "102;99;29;45","gba_r" to "103;100;47;51",
        "gba_start" to "108;66","gba_select" to "109;59","menu" to "4;82;110;111",
        "ff_hold" to "105","ff_toggle" to "","save" to "4,100","load" to "4,99")
    fun parse(value: String): List<Set<Int>> = value.split(';').map{HotkeyRouter.decode(it)}.filter{it.isNotEmpty()}.distinct()
    fun encode(keys: Set<Int>)=HotkeyRouter.encode(keys)
}

/** One input engine for gameplay controls and emulator actions. Entirely independent of UI focus. */
class BindingEngine {
    data class Frame(val mask: Int, val fastForward: Boolean, val actions: List<String> = emptyList(), val consumed: Boolean = false)
    private data class Match(val action: BindingAction,val keys:Set<Int>)
    private var configured: Map<String,String> = emptyMap()
    private var matches=listOf<Match>()
    private val pressed=mutableSetOf<Int>()
    private val pending=mutableMapOf<Match,Boolean>()
    private val blocked=mutableSetOf<Int>()
    private var latched=false
    fun configure(values: Map<String,String>) {
        if(configured==values)return
        configured=values.toMap()
        matches=BindingActions.all.flatMap { a->BindingActions.parse(values[a.id] ?: "").map{Match(a,it)} }
        reset()
    }
    fun reset(): Frame {pressed.clear();pending.clear();blocked.clear();latched=false;return Frame(0,false)}
    fun toggleFastForward():Frame {latched=!latched;return snapshot(emptyList())}
    private fun active()=matches.filter {pressed.containsAll(it.keys)}
    private fun winners(active:List<Match>)=active.filter{a->active.none{b->b.keys.size>a.keys.size && b.keys.containsAll(a.keys)}}
    private fun snapshot(actions:List<String>,consumed:Boolean=false):Frame {
        val active=active();val winning=winners(active)
        val shortcut=winning.filter{it.action.bit==null}
        val suppress=shortcut.flatMap{it.keys}.toSet()+blocked
        val mask=winning.filter{it.action.bit!=null && it.keys.none{key->key in suppress}}
            .fold(0){value,m->value or (1 shl m.action.bit!!)}
        val holding=winning.any{it.action.id=="ff_hold"}
        return Frame(mask,latched||holding,actions,consumed)
    }
    fun key(code:Int,down:Boolean):Frame {
        val consumed=matches.any{code in it.keys}
        val before=active().toSet()
        val changed=if(down)pressed.add(code) else pressed.remove(code)
        if(!changed)return snapshot(emptyList(),consumed)
        if(!down)blocked.remove(code)
        val active=active();val winning=winners(active)
        val actions=mutableListOf<String>()
        // A completed larger chord cancels any pending tap on its component key(s).
        pending.keys.toList().forEach { p->if(active.any{it.keys.size>p.keys.size && it.keys.containsAll(p.keys)})pending[p]=true }
        if(down) for(m in winning.filter{it !in before && it.action.bit==null}) {
            if(m.action.id=="ff_hold")continue
            val defer=m.action.id=="menu" || matches.any{it.keys.size>m.keys.size && it.keys.containsAll(m.keys)}
            if(defer)pending[m]=false
            else {
                if(m.action.id=="ff_toggle")latched=!latched else actions.add(m.action.id)
                blocked.addAll(m.keys)
            }
        }
        // Stop a held modifier's tap action after any multi-key shortcut completed.
        for(m in active.filter{it.action.bit==null && it.keys.size>1}) {
            pending.keys.toList().forEach{p->if(m.keys.size>p.keys.size && m.keys.containsAll(p.keys))pending[p]=true}
        }
        if(!down) for((m,cancelled)in pending.toMap()) {
            if(!pressed.containsAll(m.keys)) {
                if(!cancelled) {if(m.action.id=="ff_toggle")latched=!latched else actions.add(m.action.id)}
                pending.remove(m)
            }
        }
        return snapshot(actions,consumed)
    }
}
