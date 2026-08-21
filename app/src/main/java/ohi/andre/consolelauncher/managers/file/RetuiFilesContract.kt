package ohi.andre.consolelauncher.managers.file

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.tuils.FrameTarget

object RetuiFilesContract {
    const val PACKAGE = "com.dvil.retui.fm"
    const val OPEN_CONSOLE = "com.dvil.retui.fm.OPEN_CONSOLE"
    const val PROVIDER_AUTHORITY = "com.dvil.retui.fm.launcher"
    const val ACTION_OPEN = "open"
    const val ACTION_LIST = "ls"
    const val ACTION_SEARCH = "search"
    const val ACTION_SHARE = "share"

    private const val PREFS = "retui_files"
    private const val CURRENT_PATH = "current_path"

    data class Entry(val name: String, val path: String, val isDirectory: Boolean)

    @Suppress("DEPRECATION")
    fun currentPath(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CURRENT_PATH, null)
            ?: Environment.getExternalStorageDirectory().absolutePath

    fun rememberPath(context: Context, path: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CURRENT_PATH, path)
            .apply()
    }

    fun resolveDirectory(context: Context, target: String): String? {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(PROVIDER_AUTHORITY)
            .appendPath("resolve")
            .appendQueryParameter("path", currentPath(context))
            .appendQueryParameter("target", target)
            .build()
        return query(context, uri).firstOrNull { it.isDirectory }?.path
    }

    fun entries(context: Context, directoriesOnly: Boolean, prefix: String?): List<Entry> {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(PROVIDER_AUTHORITY)
            .appendPath("entries")
            .appendQueryParameter("path", currentPath(context))
            .appendQueryParameter("kind", if (directoriesOnly) "directory" else "file")
            .appendQueryParameter("query", prefix.orEmpty())
            .build()
        return query(context, uri)
    }

    fun launch(
        context: Context,
        path: String = currentPath(context),
        action: String? = null,
        target: String? = null,
        searchName: String? = null,
        searchType: String? = null
    ): String? {
        val intent = Intent(OPEN_CONSOLE)
            .setPackage(PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("path", path)
        action?.let { intent.putExtra("action", it) }
        target?.let { intent.putExtra("target", it) }
        searchName?.let { intent.putExtra("search_name", it) }
        searchType?.let { intent.putExtra("search_type", it) }
        RetuiThemeBridge.putLauncherThemeExtras(intent, context, FrameTarget.FILES, PACKAGE)

        return try {
            context.startActivity(intent)
            null
        } catch (_: ActivityNotFoundException) {
            "Re:T-UI Files is not installed."
        } catch (_: SecurityException) {
            "Re:T-UI Files could not be opened."
        } catch (_: RuntimeException) {
            "Re:T-UI Files could not be opened."
        }
    }

    private fun query(context: Context, uri: Uri): List<Entry> = try {
        context.contentResolver.query(uri, arrayOf("name", "path", "is_directory"), null, null, null)
            ?.use { cursor ->
                val name = cursor.getColumnIndexOrThrow("name")
                val path = cursor.getColumnIndexOrThrow("path")
                val directory = cursor.getColumnIndexOrThrow("is_directory")
                buildList {
                    while (cursor.moveToNext()) {
                        add(Entry(cursor.getString(name), cursor.getString(path), cursor.getInt(directory) != 0))
                    }
                }
            }
            .orEmpty()
    } catch (_: RuntimeException) {
        emptyList()
    }
}
