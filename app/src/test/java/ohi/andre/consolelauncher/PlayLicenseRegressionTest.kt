package ohi.andre.consolelauncher

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayLicenseRegressionTest {
    private val root = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main").isDirectory }

    @Test
    fun playstoreFlavorDoesNotGateLauncherStartupOnRuntimePlayLicense() {
        val playstoreManifest = File(root, "app/src/playstore/AndroidManifest.xml").readText()
        assertTrue(playstoreManifest.contains("com.android.vending.CHECK_LICENSE"))
        assertTrue(playstoreManifest.contains("tools:node=\"remove\""))

        val launcher = File(root, "app/src/main/java/ohi/andre/consolelauncher/LauncherActivity.kt").readText()
        val startup = launcher.substringAfter("public override fun onCreate").substringBefore("override fun onResume")
        assertFalse(startup.contains("market://"))
        assertFalse(startup.contains("play.google.com/store"))
        assertFalse(startup.contains("CHECK_LICENSE"))
    }
}
