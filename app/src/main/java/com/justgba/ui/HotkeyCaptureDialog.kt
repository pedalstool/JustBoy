package com.justgba.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.justgba.input.ChordRecorder
import com.justgba.input.MotionKeys

/** Owns its Android window's input directly, without relying on Compose focus/window ancestry. */
class HotkeyCaptureDialog(context: Context, private val label: String, private val onConfirm: (Set<Int>) -> Unit) :
    Dialog(context, android.R.style.Theme_Material_Dialog_Alert) {
    private val recorder = ChordRecorder()
    private val motion = MotionKeys()
    private var reviewing=false
    internal lateinit var status: TextView
    internal lateinit var acceptButton: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle("Record $label")
        setCancelable(false)
        val pad=(20*context.resources.displayMetrics.density).toInt()
        val content=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; setPadding(pad,pad,pad,pad) }
        content.addView(TextView(context).apply {
            text="Hold the buttons together, release all of them, then tap Use combination.\nIf your menu button opens Android's system menu instead, choose another modifier (for example Select)."
            textSize=16f
        })
        status=TextView(context).apply { textSize=18f; setPadding(0,pad,0,pad) }
        content.addView(status)
        acceptButton=Button(context).apply {
            text="Use combination"
            setOnClickListener {
                if(recorder.ready) { val keys=recorder.candidate.toSet(); dismiss(); onConfirm(keys) }
            }
        }
        content.addView(acceptButton)
        content.addView(Button(context).apply { text="Record again";setOnClickListener { reviewing=false;recorder.restart();motion.reset();refresh() } })
        content.addView(Button(context).apply { text="Cancel";setOnClickListener { dismiss() } })
        setContentView(android.widget.ScrollView(context).apply { addView(content) })
        refresh()
    }
    private fun refresh() {
        if(!::status.isInitialized)return
        status.text=when {
            recorder.candidate.isEmpty()->"Listening for controller input…"
            recorder.held.isNotEmpty()->"Holding: ${names(recorder.held)}\nRelease all buttons."
            else->"Recorded: ${names(recorder.candidate)}\nA / Enter: use • X: retry • B: cancel"
        }
        acceptButton.isEnabled=recorder.ready
        if(recorder.ready && !reviewing) { reviewing=true;acceptButton.isFocusableInTouchMode=true;acceptButton.requestFocus() }
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if(reviewing) {
            if(event.action==KeyEvent.ACTION_UP) when(event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_DPAD_CENTER -> {acceptButton.performClick();return true}
                KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_BACK -> {dismiss();return true}
                KeyEvent.KEYCODE_BUTTON_X -> {reviewing=false;recorder.restart();motion.reset();refresh();return true}
            }
            return true
        }
        when(event.action) {
            KeyEvent.ACTION_DOWN -> recorder.key(event.keyCode,true)
            KeyEvent.ACTION_UP -> recorder.key(event.keyCode,false)
            else -> return super.dispatchKeyEvent(event)
        }
        refresh()
        return true
    }
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if(reviewing)return true
        if((event.source and InputDevice.SOURCE_CLASS_JOYSTICK)==0 && (event.source and InputDevice.SOURCE_GAMEPAD)!=InputDevice.SOURCE_GAMEPAD) return super.dispatchGenericMotionEvent(event)
        motion.update(
            maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),event.getAxisValue(MotionEvent.AXIS_BRAKE)),
            maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),event.getAxisValue(MotionEvent.AXIS_GAS)),
            event.getAxisValue(MotionEvent.AXIS_X),event.getAxisValue(MotionEvent.AXIS_Y),
            event.getAxisValue(MotionEvent.AXIS_HAT_X),event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        ) { code,down->recorder.key(code,down) }
        refresh();return true
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(!hasFocus) {recorder.releaseAll();motion.reset();refresh()}
    }
    private fun names(keys: Set<Int>)=keys.sorted().joinToString(" + ") {
        "${KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_").removePrefix("BUTTON_")} [$it]"
    }
}


