package ohi.andre.consolelauncher.tuils

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class ThirdPartyNoticesIntegrationTest {
    @Test fun runtimeInventoryAndRequiredLegalTextsSurvivePackaging() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        fun text(path: String) = assets.open("licenses/$path").bufferedReader().use { it.readText() }
        val inventory = JSONArray(text("third_party/runtime-libraries.json"))
        assertTrue(inventory.length() > 0)
        val coordinates = mutableSetOf<String>()
        for (index in 0 until inventory.length()) {
            val entry = inventory.getJSONObject(index)
            assertTrue(coordinates.add(entry.getString("coordinate")))
            val notices = entry.getJSONArray("license_files")
            assertTrue(notices.length() > 0)
            for (notice in 0 until notices.length()) {
                assertTrue(text("LICENSES/${notices.getString(notice)}").isNotBlank())
            }
        }
        assertTrue(coordinates.contains("com.joaomgcd:taskerpluginlibrary:0.4.10"))
        assertTrue(text("LICENSES/Tasker-GPL-3.0.txt").contains("GNU GENERAL PUBLIC LICENSE"))
        assertTrue(text("LICENSES/Tabler-MIT.txt").contains("Paweł Kuna"))
        assertTrue(text("THIRD_PARTY_NOTICES.md").contains("libsuperuser"))
        val sourcePins = JSONArray(text("third_party/sources.json"))
        assertEquals(1, sourcePins.length())
        assertEquals("ba80eb174cfef264ad9e77d7481dd040ebd74f8c", sourcePins.getJSONObject(0).getString("revision"))
        assertTrue(text("third_party/taskerpluginlibrary-0.4.10-build.patch").contains("0.4.10"))
    }
}
