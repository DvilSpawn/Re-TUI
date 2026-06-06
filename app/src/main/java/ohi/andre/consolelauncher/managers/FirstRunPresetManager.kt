package ohi.andre.consolelauncher.managers

import android.content.Context
import android.util.Log
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.XMLPrefsRoot
import java.io.File
import java.io.FileOutputStream

object FirstRunPresetManager {
    private const val TAG = "RETUI-FIRST-RUN"
    private const val ASSET_DIR = "first_run_terminal_green"
    private val SEEDED_ROOTS = arrayOf(XMLPrefsRoot.THEME, XMLPrefsRoot.SUGGESTIONS)

    fun seedIfNeeded(context: Context, folder: File?) {
        if (folder == null || !folder.exists() || !folder.isDirectory) {
            return
        }
        if (!isFirstConfigInstall(folder)) {
            return
        }

        try {
            for (root in SEEDED_ROOTS) {
                copyAsset(context, root.path, File(folder, root.path))
            }
            Log.i(TAG, "Seeded terminal green first-run colors")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to seed first-run colors", e)
        }
    }

    private fun isFirstConfigInstall(folder: File): Boolean {
        return XMLPrefsRoot.entries.none { root -> File(folder, root.path).isFile }
    }

    private fun copyAsset(context: Context, fileName: String, dest: File) {
        val parent = dest.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        context.assets.open("$ASSET_DIR/$fileName").use { input ->
            FileOutputStream(dest, false).use { output ->
                input.copyTo(output)
            }
        }
    }
}
