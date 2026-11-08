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

    @Test fun defaultPrefersTheSdCardSavesFolderThatAlreadyHasFiles() {
        val primary = temp.newFolder("internal")
        val sd = temp.newFolder("sd")
        File(primary, "Saves").mkdirs()
        val sdSaves = File(sd, "Saves")
        sdSaves.mkdirs()
        File(sdSaves, "Golden Sun (USA, Europe).sav").writeBytes(byteArrayOf(1))
        assertEquals(
            sdSaves.absolutePath,
            SaveLocations.chooseDefaultDirectory(listOf(primary, sd)).absolutePath,
        )
    }

    @Test fun defaultUsesAnExistingSdCardSavesFolder() {
        val primary = temp.newFolder("internal-empty")
        val sd = temp.newFolder("sd-empty")
        val sdSaves = File(sd, "Saves")
        sdSaves.mkdirs()
        assertEquals(
            sdSaves.absolutePath,
            SaveLocations.chooseDefaultDirectory(listOf(primary, sd)).absolutePath,
        )
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

    @Test fun gbaRomSavesUnderSavesGba() {
        val saves = temp.newFolder("ROMs", "Saves")
        val dir = SaveLocations.systemDirectory(
            saves,
            "Golden Sun (USA, Europe).gba",
            "/storage/1234-ABCD/ROMs/GBA/Golden Sun (USA, Europe).gba",
        )
        assertEquals(File(saves, "GBA").absolutePath, dir.absolutePath)
    }

    @Test fun extensionPicksGbAndGbcFolders() {
        val saves = temp.newFolder("Saves")
        assertEquals(File(saves, "GB").absolutePath, SaveLocations.systemDirectory(saves, "Tetris.gb", null).absolutePath)
        assertEquals(File(saves, "GBC").absolutePath, SaveLocations.systemDirectory(saves, "Oracle.gbc", null).absolutePath)
    }

    @Test fun romFolderWinsOverTheFileExtension() {
        val saves = temp.newFolder("Saves")
        val dir = SaveLocations.systemDirectory(saves, "hack.bin", "primary:ROMs/GBC/hack.bin")
        assertEquals(File(saves, "GBC").absolutePath, dir.absolutePath)
    }

    @Test fun gameBoyColorFolderSavesUnderGbc() {
        val saves = temp.newFolder("ROMs", "Saves")
        val dir = SaveLocations.systemDirectory(
            saves,
            "Crystal.gb",
            "/storage/1234-ABCD/ROMs/Game Boy Color/Crystal.gb",
        )
        assertEquals(File(saves, "GBC").absolutePath, dir.absolutePath)
    }

    @Test fun colorCartridgeWithAGbExtensionSavesUnderGbc() {
        val saves = temp.newFolder("Saves")
        val rom = temp.newFile("Crystal.gb")
        val header = ByteArray(0x144)
        header[0x143] = 0xC0.toByte()
        rom.writeBytes(header)
        val dir = SaveLocations.systemDirectory(saves, "Crystal.gb", rom.absolutePath, rom)
        assertEquals(File(saves, "GBC").absolutePath, dir.absolutePath)
    }

    @Test fun aPlainGameBoyCartridgeStaysInGb() {
        val saves = temp.newFolder("plain-saves")
        val rom = temp.newFile("Tetris.gb")
        rom.writeBytes(ByteArray(0x144))
        val dir = SaveLocations.systemDirectory(saves, "Tetris.gb", rom.absolutePath, rom)
        assertEquals(File(saves, "GB").absolutePath, dir.absolutePath)
    }

    @Test fun aSaveFolderAlreadyNamedForTheSystemIsNotNested() {
        val gba = temp.newFolder("Saves", "GBA")
        assertEquals(gba.absolutePath, SaveLocations.systemDirectory(gba, "Game.gba", null).absolutePath)
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
