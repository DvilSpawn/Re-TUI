package ohi.andre.consolelauncher.commands.tuixt

import org.junit.Assert.*
import org.junit.Test

class CommunityPresetsTest {
    private val sample = """{"presets":[
        {"id":"0f6c1d2e-aaaa-4bbb-8ccc-123456789abc","name":"Kois","description":"Calm\u0007 water","tags":["calm","water"],
         "appVersion":"6","screenshotUrl":"/api/presets/assets/0f6c1d2e-aaaa-4bbb-8ccc-123456789abc/screenshot?v=presets%2Fx",
         "downloadUrl":"/api/presets/assets/0f6c1d2e-aaaa-4bbb-8ccc-123456789abc/preset",
         "wallpaperUrl":"/api/presets/assets/0f6c1d2e-aaaa-4bbb-8ccc-123456789abc/wallpaper",
         "presetSize":2048,"wallpaperSize":900000,"submitterName":null,"fontUrl":"https://fonts.example/kois","downloadCount":12},
        {"id":"bad id","name":"Broken","presetSize":10,"screenshotUrl":"/a","downloadUrl":"/b"},
        {"id":"abc","name":"Elsewhere","presetSize":10,"screenshotUrl":"https://evil.test/api/presets/assets/abc/screenshot",
         "downloadUrl":"/api/presets/assets/abc/preset"}
    ]}"""

    @Test fun keepsGoodEntriesAndDropsUnsafeOnes() {
        val items = CommunityPresets.parse(sample)
        assertEquals(listOf("Kois"), items.map { it.name })
        val kois = items.single()
        assertEquals("Calm water", kois.description)
        assertNull(kois.submitter)
        assertEquals(listOf("calm", "water"), kois.tags)
        assertEquals("https://re-tui.pages.dev/api/presets/assets/0f6c1d2e-aaaa-4bbb-8ccc-123456789abc/preset", kois.presetUrl)
        assertNotNull(kois.wallpaperUrl)
        assertEquals("https://fonts.example/kois", kois.fontUrl)
        assertEquals(12, kois.downloads)
    }

    @Test fun fetchesOnlyTheSitesOwnPresetAssets() {
        assertEquals("https://re-tui.pages.dev/api/presets/assets/a/screenshot?v=1", CommunityPresets.resolve("/api/presets/assets/a/screenshot?v=1"))
        listOf("https://evil.test/api/presets/assets/a/preset", "http://re-tui.pages.dev/api/presets/assets/a/preset",
            "/api/presets/../presets/assets/a", "/api/other", "/api/presets/assets/a/preset#x", "%2e%2e/a", "a\\b",
            "https://user@re-tui.pages.dev/api/presets/assets/a/preset").forEach { path ->
            try { CommunityPresets.resolve(path); fail("Accepted $path") } catch (_: IllegalArgumentException) { }
        }
    }
}
