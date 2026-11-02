package com.justgba.input
import org.junit.Test
import org.junit.Assert.*
class HotkeyRouterTest {
    @Test fun toggleWinsIdenticalHoldAndSurvivesRelease() {
        val r=HotkeyRouter();var ff=false;r.onFastForward={ff=it};r.bindings=mapOf("ff_hold" to setOf(105),"ff_toggle" to setOf(105))
        r.key(105,true);assertTrue(ff);r.key(105,false);assertTrue(ff)
        r.key(105,true);assertFalse(ff);r.key(105,false);assertFalse(ff)
    }
    @Test fun toggleDoesNotRepeatAndHoldReleaseKeepsLatchedState() {
        val r=HotkeyRouter();var ff=false;r.onFastForward={ff=it};r.bindings=mapOf("ff_hold" to setOf(105),"ff_toggle" to setOf(104))
        r.key(104,true);r.key(104,true);r.key(104,false);assertTrue(ff)
        r.key(105,true);r.key(105,false);assertTrue(ff)
        r.reset();assertFalse(ff)
    }
    @Test fun chordFiresOnceInEitherOrderAndConsumesModifier() {
        val r=HotkeyRouter();val actions=mutableListOf<String>();r.onAction={actions.add(it)}
        r.bindings=mapOf("save" to setOf(82,100),"load" to setOf(82,99))
        assertTrue(r.key(82,true));assertTrue(actions.isEmpty());r.key(100,true);r.key(100,true);assertEquals(listOf("save"),actions)
        r.key(100,false);r.key(82,false);r.key(99,true);r.key(82,true);assertEquals(listOf("save","load"),actions)
        assertFalse(r.key(7,true))
    }
    @Test fun holdStopsWhenEitherChordKeyIsReleased() {
        val r=HotkeyRouter();var ff=false;r.onFastForward={ff=it};r.bindings=mapOf("ff_hold" to setOf(82,105))
        r.key(105,true);assertFalse(ff);r.key(82,true);assertTrue(ff);r.key(82,false);assertFalse(ff)
    }
}
