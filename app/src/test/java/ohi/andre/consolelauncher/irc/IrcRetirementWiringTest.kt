package ohi.andre.consolelauncher.irc

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class IrcRetirementWiringTest {
    private val root = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main").isDirectory }

    @Test fun launcherHasOnlyNativeIrcMessagingEntry() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".irc.IrcActivity"))
        assertFalse(manifest.contains(".community."))
        assertFalse(File(root, "app/src/main/java/ohi/andre/consolelauncher/commands/CommandGroup.kt").readText().contains("\"community\""))
        for (path in listOf("app/src/main/java/ohi/andre/consolelauncher/community",
            "app/src/main/java/ohi/andre/consolelauncher/commands/main/raw/community.kt",
            "app/src/main/res/layout/community_surface.xml", "app/src/main/res/values/strings_community.xml")) {
            val file = File(root, path)
            assertFalse("Retired implementation: $path", file.isFile || file.listFiles()?.isNotEmpty() == true)
        }
        assertFalse(File(root, "app/src/debug/res/xml/community_fixture_network.xml").exists())
        assertFalse(File(root, "server/community").walkTopDown().any { it.isFile })
    }

    @Test fun ircPersistsAtPauseLikeOtherPanesNotDelayedStop() {
        val source = File(root, "app/src/main/java/ohi/andre/consolelauncher/irc/IrcActivity.kt").readText()
        assertTrue(source.contains("override fun onPause() { windowPanel.persist(); super.onPause() }"))
        val stop = source.substringAfter("override fun onStop() {").substringBefore("override fun onSaveInstanceState")
        assertFalse(stop.contains("windowPanel.persist()"))
    }
}
