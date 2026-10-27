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

    @Test fun bankFindsBankNamesAndPaymentAppsWithoutACategory() {
        // Name and package matches rank first, then apps of the same type.
        assertEquals(1, AppFinder.rank("HDFC Bank", "com.hdfcbank.android.now", "", "bank"))
        assertEquals(3, AppFinder.rank("iMobile", "com.csam.icici.bank.imobile", "", "bank"))
        assertEquals(4, AppFinder.rank("PhonePe", "com.phonepe.app", "", "bank"))
        assertEquals(4, AppFinder.rank("Google Pay", "com.google.android.apps.nbu.paisa.user", "", "bank"))
        assertNull(AppFinder.rank("Swiggy", "in.swiggy.android", "", "bank"))
    }

    @Test fun shortTypeFragmentsMustBeWholePackageSegments() {
        assertEquals(4, AppFinder.rank("Gmail", "com.google.android.gm", "", "email"))
        assertNull(AppFinder.rank("Play services", "com.google.android.gms", "", "email"))
        assertEquals(4, AppFinder.rank("Ola", "com.olacabs.customer", "", "taxi"))
    }
}
