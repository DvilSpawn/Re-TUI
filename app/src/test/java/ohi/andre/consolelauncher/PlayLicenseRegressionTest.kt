package ohi.andre.consolelauncher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayLicenseRegressionTest {

    @Test
    fun playstoreFlavorDoesNotGateLauncherStartupOnRuntimePlayLicense() {
        val playstoreManifest = SourceFiles.repo("app/src/playstore/AndroidManifest.xml").readText()
        assertTrue(playstoreManifest.contains("com.android.vending.CHECK_LICENSE"))
        assertTrue(playstoreManifest.contains("tools:node=\"remove\""))

        val launcher = SourceFiles.kotlin("LauncherActivity.kt").readText()
        val startup = launcher.substringAfter("public override fun onCreate").substringBefore("override fun onResume")
        assertFalse(startup.contains("market://"))
        assertFalse(startup.contains("play.google.com/store"))
        assertFalse(startup.contains("CHECK_LICENSE"))
    }
}
