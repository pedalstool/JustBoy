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

    fun resolve(customPath: String?, primaryRoot: File): File {
        val custom = customPath?.trim().orEmpty()
        return if (custom.isEmpty()) defaultDirectory(primaryRoot) else File(custom)
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
