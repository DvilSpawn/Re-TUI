package ohi.andre.consolelauncher.managers

import android.app.Activity
import android.content.Context
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.help.ChangelogActivity

object ChangelogManager {
    private const val PREFS_NAME = "changelogPrefs"
    private const val LAST_SEEN = "last_seen_version_code"

    @JvmStatic
    fun open(context: Context) = ChangelogActivity.open(context)

    fun markSeen(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(LAST_SEEN, maxOf(BuildConfig.VERSION_CODE, prefs.getInt(LAST_SEEN, 0))).apply()
    }

    fun markSeenOnFirstRun(context: Context) {
        if (!context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).contains(LAST_SEEN)) markSeen(context)
    }

    fun maybeOpen(activity: Activity): Boolean {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val seen = if (prefs.contains(LAST_SEEN)) prefs.getInt(LAST_SEEN, 0) else null
        if (!ChangelogPolicy.shouldShow(BuildConfig.VERSION_CODE, seen)) return false
        // A failed launch must not consume the notice; the pane marks it seen on resume.
        open(activity)
        return true
    }
}
