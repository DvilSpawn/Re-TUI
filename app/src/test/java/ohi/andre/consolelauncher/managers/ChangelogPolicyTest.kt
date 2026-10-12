package ohi.andre.consolelauncher.managers

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ChangelogPolicyTest {
    @Test fun firstInstallShowsNotesUntilAcknowledged() {
        assertTrue(ChangelogPolicy.shouldShow(500, null))
        assertFalse(ChangelogPolicy.shouldShow(500, 500))
    }
    @Test fun existingInstallWithoutNewVersionTrackingGetsOneNotice() {
        assertTrue(ChangelogPolicy.shouldShow(500, null))
        assertFalse(ChangelogPolicy.shouldShow(500, 500))
    }
    @Test fun upgradeShowsAgainButSameVersionAndDowngradeDoNot() {
        assertTrue(ChangelogPolicy.shouldShow(501, 500))
        assertFalse(ChangelogPolicy.shouldShow(500, 500))
        assertFalse(ChangelogPolicy.shouldShow(499, 500))
    }
    @Test fun offlineCommandAndStartupUseTheSamePaneWithIndependentGeometry() {
        val root = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
        fun source(path: String) = File(root, "app/src/main/java/ohi/andre/consolelauncher/$path").readText()
        val manager = source("managers/ChangelogManager.kt")
        assertFalse(manager.contains("pastebin")); assertFalse(manager.contains("OkHttp"))
        assertFalse(manager.substringAfter("// A failed launch").contains("markSeen("))
        assertTrue(source("commands/main/raw/changelog.kt").contains("ChangelogManager.open(pack.context)"))
        val activity = source("help/ChangelogActivity.kt")
        assertTrue(activity.contains("statePrefix = \"changelog\""))
        assertTrue(activity.contains("windowPanel.reserveBottomCorners(bottom)"))
        assertTrue(activity.contains("windowPanel.persist()"))
        val launcher = source("LauncherActivity.kt")
        assertTrue(launcher.contains("StartupMenuManager.isFirstInstallPending(this)"))
        assertFalse(launcher.contains("ChangelogManager.seedFreshInstall(this)"))
        val automatic = launcher.substringAfter("private fun maybeShowChangelog").substringBefore("override fun onResume")
        assertTrue(automatic.indexOf("StartupMenuManager.isFirstInstallPending(this)") < automatic.indexOf("ChangelogManager.maybeOpen(this)"))
        assertTrue(automatic.substringBefore("ChangelogManager.maybeOpen(this)").contains("return false"))
        assertTrue(launcher.contains("if (maybeShowChangelog()) return"))
        assertTrue(File(root, "app/src/main/AndroidManifest.xml").readText().contains(".help.ChangelogActivity\" android:exported=\"false\""))
    }
}
