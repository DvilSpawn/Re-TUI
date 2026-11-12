package ohi.andre.consolelauncher.managers

import ohi.andre.consolelauncher.SourceFiles
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
        val manager = SourceFiles.kotlin("ChangelogManager.kt").readText()
        assertFalse(manager.contains("pastebin")); assertFalse(manager.contains("OkHttp"))
        assertFalse(manager.substringAfter("// A failed launch").contains("markSeen("))
        assertTrue(SourceFiles.kotlin("changelog.kt").readText().contains("ChangelogManager.open(pack.context)"))
        val activity = SourceFiles.kotlin("ChangelogActivity.kt").readText()
        assertTrue(activity.contains("statePrefix = \"changelog\""))
        assertTrue(activity.contains("windowPanel.reserveBottomCorners(bottom)"))
        assertTrue(activity.contains("windowPanel.persist()"))
        val launcher = SourceFiles.kotlin("LauncherActivity.kt").readText()
        assertTrue(launcher.contains("StartupMenuManager.isFirstInstallPending(this)"))
        assertFalse(launcher.contains("ChangelogManager.seedFreshInstall(this)"))
        val automatic = launcher.substringAfter("private fun maybeShowChangelog").substringBefore("override fun onResume")
        assertTrue(automatic.indexOf("StartupMenuManager.isFirstInstallPending(this)") < automatic.indexOf("ChangelogManager.maybeOpen(this)"))
        assertTrue(automatic.substringBefore("ChangelogManager.maybeOpen(this)").contains("return false"))
        assertTrue(launcher.contains("if (maybeShowChangelog()) return"))
        assertTrue(SourceFiles.repo("app/src/main/AndroidManifest.xml").readText().contains(".help.ChangelogActivity\" android:exported=\"false\""))
    }
}
