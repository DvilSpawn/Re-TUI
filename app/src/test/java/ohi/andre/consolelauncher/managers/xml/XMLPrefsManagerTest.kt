package ohi.andre.consolelauncher.managers.xml

import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.xml.options.Apps
import org.junit.Assert.assertEquals
import org.junit.Test

class XMLPrefsManagerTest {
    @Test
    fun disposePreservesLiveAppPreferences() {
        val previous = AppsManager.instance
        AppsManager.instance = null
        val values = XMLPrefsManager.XMLPrefsRoot.APPS.getValues()!!
        values.add(Apps.default_app_n1.label(), Apps.MOST_USED)

        try {
            XMLPrefsManager.dispose()
            assertEquals(Apps.MOST_USED, values[Apps.default_app_n1]?.value)
        } finally {
            values.list.clear()
            AppsManager.instance = previous
        }
    }
}
