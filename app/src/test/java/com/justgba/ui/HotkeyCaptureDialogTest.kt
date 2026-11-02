package com.justgba.ui

import android.app.Activity
import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class HotkeyCaptureDialogTest {
    @Test fun showsRecorderCapturesMenuCombinationAndConfirmsIt() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        val activity=controller.get()
        var saved:Set<Int>?=null
        val dialog=HotkeyCaptureDialog(activity,"Save state"){saved=it}
        dialog.show()
        assertTrue(dialog.isShowing)
        assertFalse(dialog.acceptButton.isEnabled)
        // MODE is what many gamepads call Menu; record its actual code, not an assumed MENU alias.
        assertTrue(dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_MODE)))
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_Y))
        assertTrue(dialog.status.text.toString(), dialog.status.text.contains("[110]"))
        assertFalse(dialog.acceptButton.isEnabled)
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_Y))
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_MODE))
        assertTrue(dialog.acceptButton.isEnabled)
        dialog.acceptButton.performClick()
        assertEquals(setOf(110,100),saved)
        assertFalse(dialog.isShowing)
        controller.pause().stop().destroy()
    }
    @Test fun openingButtonReleaseIsNotRecorded() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        val dialog=HotkeyCaptureDialog(controller.get(),"Load state"){}
        dialog.show()
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_A))
        assertFalse(dialog.acceptButton.isEnabled)
        assertTrue(dialog.status.text.contains("Listening"))
        dialog.dismiss();controller.pause().stop().destroy()
    }
    @Test fun canRecordAAndThenConfirmWithAWithoutChangingTheBinding() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        var saved:Set<Int>?=null
        val dialog=HotkeyCaptureDialog(controller.get(),"GBA A"){saved=it};dialog.show()
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,96));dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,96))
        assertTrue(dialog.acceptButton.isEnabled)
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,96));dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,96))
        assertEquals(setOf(96),saved);assertFalse(dialog.isShowing)
        controller.pause().stop().destroy()
    }}


