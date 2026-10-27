package ohi.andre.consolelauncher.managers

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppFinderTest {
    @Test fun namesRankAheadOfPackagesAndCategories() {
        assertEquals(0, AppFinder.rank("Camera", "com.vivo.camera", "", "cam"))
        assertEquals(1, AppFinder.rank("Adobe Scan", "com.adobe.scan.android", "", "scan"))
        assertEquals(2, AppFinder.rank("WhatsApp", "com.whatsapp", "", "app"))
        assertEquals(3, AppFinder.rank("Snap", "com.vivo.camera", "", "camera"))
        assertEquals(4, AppFinder.rank("Spotify", "com.spotify.music.x", AppFinder.categoryKeywords(ApplicationInfo.CATEGORY_AUDIO), "songs"))
    }

    @Test fun categoryWordsFindAppsByWhatTheyDo() {
        val audio = AppFinder.categoryKeywords(ApplicationInfo.CATEGORY_AUDIO)
        val maps = AppFinder.categoryKeywords(ApplicationInfo.CATEGORY_MAPS)

        assertEquals(4, AppFinder.rank("Spotify", "com.spotify.a", audio, "music"))
        assertEquals(4, AppFinder.rank("Ola", "com.olacabs.customer", maps, "nav"))
        assertNull(AppFinder.rank("Spotify", "com.spotify.a", audio, "maps"))
        assertEquals("", AppFinder.categoryKeywords(ApplicationInfo.CATEGORY_UNDEFINED))
    }

    @Test fun shortQueriesOnlyMatchNames() {
        assertNull(AppFinder.rank("Spotify", "com.spotify.music", AppFinder.categoryKeywords(ApplicationInfo.CATEGORY_AUDIO), "mu"))
        assertEquals(0, AppFinder.rank("Music", "com.example", "", "mu"))
    }

    @Test fun matchingIgnoresCaseAndSpaces() {
        assertEquals(0, AppFinder.rank("YouTube Music", "com.google.android.apps.youtube.music", "", "  YOU "))
        assertEquals(1, AppFinder.rank("YouTube Music", "com.google.android.apps.youtube.music", "", "Mus"))
    }
}
