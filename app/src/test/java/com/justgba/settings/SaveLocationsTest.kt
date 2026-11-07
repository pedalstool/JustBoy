package com.justgba.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveLocationsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun blankPathUsesSavesOnThePrimaryRoot() {
        val primary = temp.newFolder("sdcard")
        assertEquals(
            File(primary, "Saves").absolutePath,
            SaveLocations.resolve("  ", primary).absolutePath,
        )
        assertEquals(File(primary, "Saves").absolutePath, SaveLocations.defaultDirectory(primary).absolutePath)
    }

    @Test fun customPathIsKept() {
        val chosen = temp.newFolder("chosen")
        assertEquals(chosen.absolutePath, SaveLocations.resolve(chosen.absolutePath, temp.root).absolutePath)
    }

    @Test fun primaryDocumentIdMapsUnderThePrimaryRoot() {
        val primary = temp.newFolder("sdcard")
        assertEquals(
            File(primary, "Saves/Pokemon").absolutePath,
            SaveLocations.pathFromDocumentId("primary:Saves/Pokemon", primary),
        )
        assertEquals(primary.absolutePath, SaveLocations.pathFromDocumentId("primary:", primary))
    }

    @Test fun physicalVolumeMapsUnderStorage() {
        val path = SaveLocations.pathFromDocumentId("1234-ABCD:Saves", temp.root)
        assertTrue(path!!.replace('\\', '/').endsWith("/storage/1234-ABCD/Saves"))
    }

    @Test fun documentIdRoundTripsAPrimaryFolder() {
        val primary = temp.newFolder("sdcard")
        val saves = File(primary, "Saves")
        assertEquals("primary:Saves", SaveLocations.documentIdForPath(saves.path, primary))
    }

    @Test fun rejectsADocumentIdWithoutAVolume() {
        assertNull(SaveLocations.pathFromDocumentId("Saves", temp.root))
    }

    @Test fun aReplacedBatterySaveWinsOverAnOlderAutosave() {
        assertTrue(SaveLocations.preferBatteryOverAutosave(10_000L, 7_000L))
        assertFalse(SaveLocations.preferBatteryOverAutosave(10_000L, 9_000L))
        assertFalse(SaveLocations.preferBatteryOverAutosave(8_000L, 10_000L))
    }

    @Test fun copiesMissingSaveFilesAndLeavesExistingOnes() {
        val from = temp.newFolder("from")
        val to = temp.newFolder("to")
        File(from, "Game.sav").writeText("battery")
        File(from, "Game.state.auto").writeText("auto")
        File(from, "Game.state.2").writeText("slot")
        File(from, "Game.cheats.json").writeText("[]")
        File(from, "notes.txt").writeText("no")
        File(to, "Game.sav").writeText("keep")

        assertEquals(3, SaveLocations.copyMissingSaves(from, to))
        assertEquals("keep", File(to, "Game.sav").readText())
        assertEquals("auto", File(to, "Game.state.auto").readText())
        assertEquals("slot", File(to, "Game.state.2").readText())
        assertEquals("[]", File(to, "Game.cheats.json").readText())
        assertFalse(File(to, "notes.txt").exists())
        assertEquals(0, SaveLocations.copyMissingSaves(from, from))
    }
}
