package ohi.andre.consolelauncher.managers

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import ohi.andre.consolelauncher.managers.AppsManager.LaunchInfo
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds apps by part of their name, their package, or what they do, for people who don't
 * remember an app's exact name.
 */
object AppFinder {
    /** Shortest query matched against packages and categories, so two letters don't match everything. */
    private const val MIN_KEYWORD_QUERY = 3

    private val categoryWords = ConcurrentHashMap<String, String>()

    /** Words for Android's app categories, as people would type them. */
    internal fun categoryKeywords(category: Int): String = when (category) {
        ApplicationInfo.CATEGORY_GAME -> "game games"
        ApplicationInfo.CATEGORY_AUDIO -> "music audio songs podcast podcasts radio"
        ApplicationInfo.CATEGORY_VIDEO -> "video videos movies tv streaming"
        ApplicationInfo.CATEGORY_IMAGE -> "photo photos camera gallery pictures images"
        ApplicationInfo.CATEGORY_SOCIAL -> "social chat messages messaging"
        ApplicationInfo.CATEGORY_NEWS -> "news magazines reading"
        ApplicationInfo.CATEGORY_MAPS -> "maps map navigation directions travel"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity office documents docs notes work"
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> "accessibility"
        else -> ""
    }

    /**
     * How well [query] matches an app, lower is better, or null when it doesn't match:
     * 0 label starts with it, 1 a label word starts with it, 2 label contains it,
     * 3 package contains it, 4 the app's category matches it.
     */
    internal fun rank(label: String?, packageName: String?, categoryWords: String, query: String): Int? {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) return 0
        val name = label.orEmpty().lowercase(Locale.getDefault())
        if (name.startsWith(q)) return 0
        if (name.split(' ', '-', '_', '.').any { it.startsWith(q) }) return 1
        if (name.contains(q)) return 2
        if (q.length < MIN_KEYWORD_QUERY) return null
        if (packageName.orEmpty().lowercase(Locale.ROOT).contains(q)) return 3
        if (categoryWords.split(' ').any { it.startsWith(q) }) return 4
        return null
    }

    /** Matching apps, best first; ties keep alphabetical order. */
    fun find(context: Context, apps: List<LaunchInfo>, query: String): List<LaunchInfo> {
        val ranked = apps.mapNotNull { app ->
            val pkg = app.componentName?.packageName
            rank(app.publicLabel, pkg, wordsFor(context, pkg), query)?.let { it to app }
        }
        return ranked.sortedWith(
            compareBy<Pair<Int, LaunchInfo>> { it.first }
                .thenBy { it.second.publicLabel.orEmpty().lowercase(Locale.getDefault()) }
        ).map { it.second }
    }

    /** Apps that only match by package or category, for suggestions that already matched names. */
    fun findByKeyword(context: Context, apps: List<LaunchInfo>, query: String): List<LaunchInfo> =
        apps.filter { app ->
            val pkg = app.componentName?.packageName
            (rank(app.publicLabel, pkg, wordsFor(context, pkg), query) ?: 0) >= 3
        }.sortedBy { it.publicLabel.orEmpty().lowercase(Locale.getDefault()) }

    private fun wordsFor(context: Context, packageName: String?): String {
        if (packageName == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return ""
        return categoryWords.getOrPut(packageName) {
            try {
                categoryKeywords(context.packageManager.getApplicationInfo(packageName, 0).category)
            } catch (e: PackageManager.NameNotFoundException) {
                ""
            }
        }
    }
}
