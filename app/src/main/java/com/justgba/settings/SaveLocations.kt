package com.justgba.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import java.io.File

object SaveLocations {
    const val FOLDER_NAME = "Saves"

    fun primaryRoot(): File = Environment.getExternalStorageDirectory()

    fun defaultDirectory(primaryRoot: File): File = File(primaryRoot, FOLDER_NAME)

    fun storageRoots(context: Context): List<File> {
        val mounted = context.getExternalFilesDirs(null).mapNotNull { files ->
            files?.parentFile?.parentFile?.parentFile?.parentFile?.takeIf { it.isDirectory }
        }
        return (listOf(primaryRoot()) + mounted).distinctBy { it.absolutePath }
    }

    fun chooseDefaultDirectory(roots: List<File>): File {
        val primary = roots.firstOrNull() ?: primaryRoot()
        val saves = roots.map { File(it, FOLDER_NAME) }
        fun hasSaveFile(dir: File) = dir.isDirectory &&
            dir.listFiles().orEmpty().any { it.isFile && it.name.endsWith(".sav", ignoreCase = true) }
        val withSaves = saves.filter { hasSaveFile(it) }
        val secondaryWithSaves = withSaves.firstOrNull { it.parentFile?.absolutePath != primary.absolutePath }
        if (secondaryWithSaves != null) return secondaryWithSaves
        val secondaryExisting = saves.firstOrNull {
            it.parentFile?.absolutePath != primary.absolutePath && it.isDirectory
        }
        if (secondaryExisting != null) return secondaryExisting
        return withSaves.firstOrNull() ?: File(primary, FOLDER_NAME)
    }

    fun defaultDirectory(context: Context): File = chooseDefaultDirectory(storageRoots(context))

    fun resolve(customPath: String?, primaryRoot: File): File {
        val custom = customPath?.trim().orEmpty()
        return if (custom.isEmpty()) defaultDirectory(primaryRoot) else File(custom)
    }

    fun resolve(context: Context, customPath: String?): File {
        val custom = customPath?.trim().orEmpty()
        return if (custom.isEmpty()) defaultDirectory(context) else File(custom)
    }

    fun systemDirectory(saveRoot: File, displayName: String?, sourceHint: String?, romFile: File? = null): File {
        val system = systemFolder(displayName, sourceHint, romFile)
        if (saveRoot.name.equals(system, ignoreCase = true)) return saveRoot
        return File(saveRoot, system)
    }

    fun systemFolder(displayName: String?, sourceHint: String?, romFile: File? = null): String {
        val hint = try {
            java.net.URLDecoder.decode(sourceHint.orEmpty(), Charsets.UTF_8.name())
        } catch (_: Exception) {
            sourceHint.orEmpty()
        }
        val segments = hint.replace('\\', '/').split('/', ':').map { it.trim() }.filter { it.isNotEmpty() }
        val fileName = displayName?.takeIf { it.isNotBlank() } ?: segments.lastOrNull().orEmpty()
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val romExtension = romFile?.extension?.lowercase().orEmpty()
        if (extension == "gba" || extension == "agb" || romExtension == "gba" || romExtension == "agb") return "GBA"
        if (extension == "gbc" || extension == "cgb" || romExtension == "gbc" || romExtension == "cgb") return "GBC"
        if (isColorCartridge(romFile)) return "GBC"
        for (dir in segments.dropLast(1).asReversed()) {
            directorySystem(dir)?.let { return it }
        }
        if (extension == "gb" || extension == "sgb" || extension == "dmg" ||
            romExtension == "gb" || romExtension == "sgb" || romExtension == "dmg"
        ) {
            return "GB"
        }
        return "GBA"
    }

    private fun directorySystem(dir: String): String? {
        val name = dir.lowercase()
        return when {
            name == "gbc" || name == "cgb" || name.endsWith("game boy color") -> "GBC"
            name == "gba" || name == "agb" || name.endsWith("game boy advance") -> "GBA"
            name == "gb" || name == "dmg" || name == "sgb" || name == "gameboy" || name.endsWith("game boy") -> "GB"
            else -> null
        }
    }

    fun isColorCartridge(file: File?): Boolean {
        if (file == null || !file.isFile || file.length() <= 0x143) return false
        return try {
            file.inputStream().use { input ->
                val bytes = ByteArray(0x344)
                val read = input.read(bytes)
                colorFlag(bytes, read, 0x143) || colorFlag(bytes, read, 0x143 + 512)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun colorFlag(bytes: ByteArray, read: Int, offset: Int): Boolean {
        if (offset >= read) return false
        val flag = bytes[offset].toInt() and 0xFF
        return flag == 0x80 || flag == 0xC0
    }

    fun ensureDirectory(dir: File): Boolean = dir.isDirectory || dir.mkdirs()

    fun isWritable(dir: File): Boolean {
        if (!ensureDirectory(dir)) return false
        val probe = File(dir, ".justboy-write-test")
        return try {
            probe.writeText("1")
            probe.isFile
        } catch (_: Exception) {
            false
        } finally {
            runCatching { probe.delete() }
        }
    }

    fun isSaveFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".sav") ||
            lower.endsWith(".state.auto") ||
            lower.endsWith(".state.1") ||
            lower.endsWith(".state.2") ||
            lower.endsWith(".state.3") ||
            lower.endsWith(".cheats.json")
    }

    fun copyMissingSaves(from: File, to: File): Int {
        if (!from.isDirectory || from.absolutePath == to.absolutePath) return 0
        if (!ensureDirectory(to)) return 0
        var copied = copySaveFiles(from, to)
        for (system in listOf("GBA", "GB", "GBC")) {
            copied += copySaveFiles(File(from, system), File(to, system))
        }
        return copied
    }

    private fun copySaveFiles(from: File, to: File): Int {
        if (!from.isDirectory) return 0
        if (!ensureDirectory(to)) return 0
        var copied = 0
        for (file in from.listFiles().orEmpty()) {
            if (!file.isFile || !isSaveFile(file.name)) continue
            val target = File(to, file.name)
            if (target.exists()) continue
            file.copyTo(target, overwrite = false)
            copied++
        }
        return copied
    }

    fun pathFromDocumentId(documentId: String, primaryRoot: File): String? {
        val separator = documentId.indexOf(':')
        if (separator <= 0) return null
        val volume = documentId.substring(0, separator)
        val relative = documentId.substring(separator + 1).trim().trim('/')
        val root = if (volume == "primary") primaryRoot else File("/storage/$volume")
        return (if (relative.isEmpty()) root else File(root, relative)).absolutePath
    }

    fun pathFromTreeUri(uri: Uri, primaryRoot: File): String? {
        val documentId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (_: Exception) {
            null
        } ?: return null
        return pathFromDocumentId(documentId, primaryRoot)
    }

    fun documentIdForPath(path: String, primaryRoot: File): String? {
        val absolute = File(path).absolutePath
        val primary = primaryRoot.absolutePath.trimEnd(File.separatorChar, '/')
        if (absolute == primary) return "primary:"
        val prefix = primary + File.separator
        if (absolute.startsWith(prefix)) {
            return "primary:" + absolute.substring(prefix.length).replace('\\', '/')
        }
        val normalized = absolute.replace('\\', '/')
        val marker = "/storage/"
        val index = normalized.indexOf(marker)
        if (index < 0) return null
        val rest = normalized.substring(index + marker.length)
        val slash = rest.indexOf('/')
        val volume = if (slash < 0) rest else rest.substring(0, slash)
        val relative = if (slash < 0) "" else rest.substring(slash + 1)
        if (volume.isEmpty() || volume == "emulated" || volume == "self") return null
        return "$volume:$relative"
    }

    fun initialDocumentUri(path: String, primaryRoot: File): Uri? {
        val documentId = documentIdForPath(path, primaryRoot) ?: return null
        return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentId)
    }

    fun requestStorageAccess(context: Context, requestLegacyWrite: () -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestLegacyWrite()
            return true
        }
        val host = context.findActivity() ?: context
        val intents = listOf(
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        )
        for (intent in intents) {
            try {
                host.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            }
        }
        return false
    }

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
