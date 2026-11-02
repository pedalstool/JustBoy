package com.justgba.input

import com.justgba.emulator.StateSlots
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class HotkeyRecordingTest {
    @get:Rule val temp=TemporaryFolder()
    @Test fun capturesSimultaneousChordAndIgnoresRepeatAndUnmatchedRelease() {
        val c=ChordRecorder();c.key(96,false);assertFalse(c.ready)
        c.key(110,true);c.key(110,true);c.key(100,true);c.key(110,false);c.key(100,false)
        assertTrue(c.ready);assertEquals(setOf(110,100),c.candidate)
        c.key(99,true);c.key(99,false);assertEquals(setOf(99),c.candidate)
    }
    @Test fun analogTriggerAndHatProduceRecordableEdges() {
        val c=ChordRecorder();val m=MotionKeys()
        m.update(0f,1f,0f,0f,-1f,0f,c::key)
        assertEquals(setOf(105,21),c.held)
        m.update(0f,0.4f,0f,0f,-1f,0f,c::key);assertEquals(setOf(105,21),c.held)
        m.update(0f,0f,0f,0f,0f,0f,c::key)
        assertTrue(c.ready);assertEquals(setOf(105,21),c.candidate)
    }
    @Test fun recordedCombosSaveAndLoadSelectedSlotWithoutConsumingFaceKeysAlone() {
        val root=temp.newFolder();var restored=""
        val slots=StateSlots(File(root,"game.state.auto").path,{File(it).writeText("snapshot");true},{restored=File(it).readText();true})
        val save=ChordRecorder();save.key(110,true);save.key(100,true);save.releaseAll()
        val load=ChordRecorder();load.key(110,true);load.key(99,true);load.releaseAll()
        val router=HotkeyRouter();router.bindings=mapOf("save" to save.candidate,"load" to load.candidate)
        var saves=0;var loads=0
        router.onAction={when(it){"save"->{assertTrue(slots.save(2));saves++};"load"->{assertTrue(slots.load(2));loads++}}}
        assertFalse(router.key(100,true));router.key(100,false)
        router.key(110,true);router.key(100,true);router.key(100,true);router.key(100,false);router.key(110,false)
        assertEquals(1,saves);assertTrue(slots.file(2).isFile);assertFalse(slots.file(1).exists())
        router.key(99,true);router.key(110,true);router.key(110,false);router.key(99,false)
        assertEquals(1,loads);assertEquals("snapshot",restored)
    }
    @Test fun failedSavePreservesPreviousStateAndEmptySlotsDoNotLoad() {
        val root=temp.newFolder();var canWrite=true;var read=false
        val slots=StateSlots(File(root,"game.state.auto").path,{if(canWrite){File(it).writeText("good");true}else false},{read=true;true})
        assertTrue(slots.save(1));canWrite=false;assertFalse(slots.save(1))
        assertEquals("good",slots.file(1).readText());assertFalse(slots.load(3));assertFalse(read)
    }
}
