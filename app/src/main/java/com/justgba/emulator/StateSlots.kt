package com.justgba.emulator

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException

/** Shared by menu actions and hotkeys, independent of Android UI and injectable in tests. */
class StateSlots(private val autoPath: String, private val writer: (String)->Boolean, private val reader: (String)->Boolean) {
    fun file(slot: Int): File { require(slot in 1..3); return File(autoPath.removeSuffix(".state.auto")+".state.$slot") }
    @Synchronized fun save(slot: Int): Boolean = runCatching {
        val target=file(slot);target.parentFile?.mkdirs();val temp=File(target.path+".tmp")
        if(!writer(temp.path))return false
        try { Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE) }
        catch(e:AtomicMoveNotSupportedException){ Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING) }
        true
    }.getOrDefault(false)
    @Synchronized fun load(slot: Int): Boolean = runCatching { val target=file(slot);target.isFile && target.length()>0 && reader(target.path) }.getOrDefault(false)
}
