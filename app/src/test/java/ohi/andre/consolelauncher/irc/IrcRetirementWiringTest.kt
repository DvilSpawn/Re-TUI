package ohi.andre.consolelauncher.irc

import ohi.andre.consolelauncher.SourceFiles
import org.junit.Assert.*
import org.junit.Test

class IrcRetirementWiringTest {

    @Test fun launcherHasOnlyNativeIrcMessagingEntry() {
        val manifest = SourceFiles.repo("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".irc.IrcActivity"))
        assertFalse(manifest.contains(".community."))
        assertFalse(SourceFiles.kotlin("CommandGroup.kt").readText().contains("\"community\""))
        for (path in listOf("app/src/main/java/ohi/andre/consolelauncher/community",
            "app/src/main/java/ohi/andre/consolelauncher/commands/main/raw/community.kt",
            "app/src/main/res/layout/community_surface.xml", "app/src/main/res/values/strings_community.xml")) {
            val file = SourceFiles.repo(path)
            assertFalse("Retired implementation: $path", file.isFile || file.listFiles()?.isNotEmpty() == true)
        }
        assertFalse(SourceFiles.repo("app/src/debug/res/xml/community_fixture_network.xml").exists())
        assertFalse(SourceFiles.repo("server/community").walkTopDown().any { it.isFile })
    }

    @Test fun ircPersistsAtPauseLikeOtherPanesNotDelayedStop() {
        val source = SourceFiles.kotlin("IrcActivity.kt").readText()
        assertTrue(source.contains("override fun onPause() { windowPanel.persist(); super.onPause() }"))
        val stop = source.substringAfter("override fun onStop() {").substringBefore("override fun onSaveInstanceState")
        assertFalse(stop.contains("windowPanel.persist()"))
    }
}
