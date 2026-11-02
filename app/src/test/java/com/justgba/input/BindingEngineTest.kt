package com.justgba.input

import org.junit.Assert.*
import org.junit.Test

class BindingEngineTest {
    private fun engine(values:Map<String,String> = BindingActions.defaults)=BindingEngine().apply{configure(values)}
    @Test fun backTapOpensMenuOnRelease() {
        val e=engine();assertTrue(e.key(4,true).actions.isEmpty())
        assertEquals(listOf("menu"),e.key(4,false).actions)
        assertTrue(e.key(4,false).actions.isEmpty())
    }
    @Test fun backYSaveDoesNotAlsoOpenMenuOrPressGbaR() {
        val e=engine();e.key(4,true)
        val save=e.key(100,true);assertEquals(listOf("save"),save.actions);assertEquals(0,save.mask)
        assertTrue(e.key(100,true).actions.isEmpty())
        assertTrue(e.key(100,false).actions.isEmpty());assertTrue(e.key(4,false).actions.isEmpty())
        assertEquals(listOf("menu"),run{e.key(4,true);e.key(4,false)}.actions)
    }
    @Test fun reverseOrderLoadDoesNotOpenMenuAndNormalXStillWorks() {
        val e=engine();assertEquals(1 shl 10,e.key(99,true).mask)
        assertEquals(listOf("load"),e.key(4,true).actions)
        assertTrue(e.key(4,false).actions.isEmpty());assertEquals(0,e.key(99,false).mask)
        assertEquals(1 shl 10,e.key(99,true).mask)
    }
    @Test fun canRemapEveryGameControlToAnySingleKeyOrCombination() {
        for(action in BindingActions.all.filter{it.bit!=null}) {
            val e=engine(mapOf(action.id to "250,251"))
            assertEquals(0,e.key(250,true).mask)
            assertEquals(1 shl action.bit!!,e.key(251,true).mask)
            assertEquals(0,e.key(250,false).mask)
        }
    }
    @Test fun canRemapMenuSaveLoadHoldAndToggleWithoutReservedKeys() {
        val e=engine(mapOf("menu" to "106,107","save" to "96,102","load" to "97,103","ff_hold" to "21,22","ff_toggle" to "19,20"))
        e.key(96,true);assertEquals(listOf("save"),e.key(102,true).actions);e.key(96,false);e.key(102,false)
        e.key(97,true);assertEquals(listOf("load"),e.key(103,true).actions);e.key(97,false);e.key(103,false)
        e.key(106,true);e.key(107,true);assertEquals(listOf("menu"),e.key(107,false).actions);e.key(106,false)
        e.key(21,true);assertTrue(e.key(22,true).fastForward);assertFalse(e.key(21,false).fastForward);e.key(22,false)
        e.key(19,true);assertTrue(e.key(20,true).fastForward);e.key(19,false);assertTrue(e.key(20,false).fastForward)
        e.key(19,true);assertFalse(e.key(20,true).fastForward)
    }
    @Test fun shortcutDoesNotClearOtherHeldGameButtons() {
        val e=engine();assertEquals(1 shl 4,e.key(19,true).mask)
        e.key(4,true);assertEquals(1 shl 4,e.key(100,true).mask)
        e.key(100,false);e.key(4,false);assertEquals(1 shl 4,e.key(19,true).mask)
    }
    @Test fun resettingInputDoesNotLaterFireAPendingMenuTap() {
        val e=engine();e.key(4,true);e.reset();assertTrue(e.key(4,false).actions.isEmpty())
    }
}
