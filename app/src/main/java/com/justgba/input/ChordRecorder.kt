package com.justgba.input

/** Records the largest simultaneous chord in a press/release cycle, not a union of separate taps. */
class ChordRecorder {
    var held: Set<Int> = emptySet()
        private set
    var candidate: Set<Int> = emptySet()
        private set
    val ready get() = candidate.isNotEmpty() && held.isEmpty()
    fun key(code: Int, down: Boolean) {
        if (code <= 0) return
        if (down) {
            if (code in held) return
            if (held.isEmpty()) candidate = emptySet()
            held = held + code
            if (held.size > candidate.size) candidate = held.toSet()
        } else held = held - code
    }
    fun releaseAll() { held = emptySet() }
    fun restart() { held=emptySet();candidate=emptySet() }
}

/** Converts analog trigger and D-pad/stick edges into the same keys used by digital controllers. */
class MotionKeys {
    private var down: Set<Int> = emptySet()
    fun reset() { down = emptySet() }
    fun update(leftTrigger: Float, rightTrigger: Float, x: Float, y: Float, hatX: Float, hatY: Float, emit: (Int, Boolean) -> Unit) {
        fun trigger(value: Float, code: Int) = value > if(code in down) 0.2f else 0.5f
        val dx = if(kotlin.math.abs(hatX) > 0.5f) hatX else x
        val dy = if(kotlin.math.abs(hatY) > 0.5f) hatY else y
        val next = mutableSetOf<Int>()
        if(trigger(leftTrigger,104)) next.add(104)
        if(trigger(rightTrigger,105)) next.add(105)
        if(dx < -0.5f) next.add(21)
        if(dx > 0.5f) next.add(22)
        if(dy < -0.5f) next.add(19)
        if(dy > 0.5f) next.add(20)
        (down-next).forEach { emit(it,false) }
        (next-down).forEach { emit(it,true) }
        down=next
    }
}

