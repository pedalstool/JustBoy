package com.justgba.input

/** Focus-independent input state machine. No Android dependencies, so it can be tested on the host. */
class HotkeyRouter {
    var bindings: Map<String, Set<Int>> = emptyMap()
    var onAction: (String) -> Unit = {}
    var onFastForward: (Boolean) -> Unit = {}
    private val pressed = mutableSetOf<Int>()
    private val fired = mutableSetOf<String>()
    var latched = false
        private set
    private var holding = false
    fun toggle() { latched = !latched; publish() }
    fun reset() { pressed.clear(); fired.clear(); latched=false; holding=false; publish() }
    private fun publish() = onFastForward(latched || holding)
    fun key(code: Int, down: Boolean): Boolean {
        // Multi-button action keys remain usable for the game when the modifier is not held.
        val modifiers = setOf(82,109,110,104,105,106,107)
        val reserved = bindings.values.any { code in it && (it.size==1 || code in modifiers || pressed.containsAll(it) || (down && pressed.containsAll(it-code))) }
        val changed = if (down) pressed.add(code) else pressed.remove(code)
        if (!changed) return reserved
        val active = bindings.filterValues { it.isNotEmpty() && pressed.containsAll(it) }
        // A toggle wins a conflicting hold binding. Releasing hold never clears a toggle.
        val toggle = active["ff_toggle"]
        holding = active.containsKey("ff_hold") && toggle != active["ff_hold"]
        for ((action, _) in active.entries.sortedByDescending { it.value.size }) {
            if (action !in fired) {
                if (action == "ff_toggle") latched = !latched
                else if (action != "ff_hold") onAction(action)
            }
        }
        fired.retainAll(active.keys); fired.addAll(active.keys)
        publish()
        return reserved
    }
    companion object {
        fun decode(value: String): Set<Int> = value.split(',').mapNotNull { it.toIntOrNull() }.filter { it >= 0 }.toSet()
        fun encode(keys: Set<Int>): String = keys.sorted().joinToString(",")
    }
}

