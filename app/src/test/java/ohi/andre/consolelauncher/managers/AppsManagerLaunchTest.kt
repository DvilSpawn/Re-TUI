package ohi.andre.consolelauncher.managers

import android.content.ActivityNotFoundException
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsManagerLaunchTest {
    @Test
    fun appIconLaunchUsesMainLauncherContract() {
        assertEquals(Intent.ACTION_MAIN, AppsManager.APP_LAUNCH_ACTION)
        assertEquals(Intent.CATEGORY_LAUNCHER, AppsManager.APP_LAUNCH_CATEGORY)
    }

    @Test
    fun missingOrDisabledActivityDoesNotEscapeLaunch() {
        assertFalse(AppsManager.startActivitySafely { throw ActivityNotFoundException("missing") })
        assertFalse(AppsManager.startActivitySafely { throw SecurityException("disabled") })
        assertFalse(AppsManager.startActivitySafely { error("unexpected") })
        assertTrue(AppsManager.startActivitySafely { })
    }

    @Test
    fun staleLauncherActivityFallsBackToCurrentPackageActivity() {
        val attempts = mutableListOf<String>()

        val launched = AppsManager.firstLaunchable("OldAlias", "CurrentAlias", String::equals) {
            attempts.add(it)
            it == "CurrentAlias"
        }

        assertEquals("CurrentAlias", launched)
        assertEquals(listOf("OldAlias", "CurrentAlias"), attempts)
    }
}
