package com.justgba.input

import android.view.MotionEvent

/** MainActivity feeds these hooks before Android focus/navigation can swallow gamepad keys. */
object GameInput {
    var key: ((Int, Boolean) -> Boolean)? = null
    var reset: (() -> Unit)? = null
    var menuOpen = false
    private val motionKeys = MotionKeys()
    fun dispatch(code: Int, down: Boolean): Boolean = key?.invoke(code, down) ?: false
    fun motion(event: MotionEvent): Boolean {
        val source = event.source
        if ((source and android.view.InputDevice.SOURCE_CLASS_JOYSTICK) == 0 &&
            (source and android.view.InputDevice.SOURCE_GAMEPAD) != android.view.InputDevice.SOURCE_GAMEPAD
        ) return false
        if (key == null) return false
        motionKeys.update(
            maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)),
            maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)),
            event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y),
            event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        ) { code, down -> dispatch(code, down) }
        return true
    }
    fun clear() { motionKeys.reset(); reset?.invoke(); InputState.clear() }
}
