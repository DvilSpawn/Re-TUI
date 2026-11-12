package ohi.andre.consolelauncher.managers.modes

import ohi.andre.consolelauncher.managers.apps.AppsManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.wallpaper.pixel.DotHomeView

/** The apps a minimal home offers: the dot_apps setting (names or app ids), else the most-used apps. */
internal object HomeApps {
    fun picks(apps: AppsManager): List<AppsManager.LaunchInfo> {
        val shown = runCatching { apps.shownApps() }.getOrDefault(mutableListOf())
        val configured = LauncherSettings.get(Behavior.dot_apps).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .mapNotNull { token -> runCatching { apps.findLaunchInfo(token) }.getOrNull()
                ?: shown.firstOrNull { it.publicLabel.equals(token, ignoreCase = true) } }
        if (configured.isNotEmpty()) return configured.distinctBy { it.write() }.take(DotHomeView.MAX_APPS)
        val ranked = runCatching {
            apps.rankedApps(AppsManager.Ranking.MOST_USED, 5).map { it.first } + apps.rankedApps(AppsManager.Ranking.RECENT, 5).map { it.first }
        }.getOrDefault(emptyList())
        return (ranked + shown.sortedBy { it.publicLabel?.lowercase() }).distinctBy { it.write() }
            .take(DotHomeView.MAX_APPS)
    }
}
