package ohi.andre.consolelauncher.managers

internal object ChangelogPolicy {
    fun shouldShow(currentVersion: Int, lastSeenVersion: Int?): Boolean =
        lastSeenVersion == null || currentVersion > lastSeenVersion
}
