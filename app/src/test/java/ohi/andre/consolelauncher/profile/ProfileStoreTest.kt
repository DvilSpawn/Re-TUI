package ohi.andre.consolelauncher.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileStoreTest {
    @Test
    fun phoneIsMaskedExceptForLastTwoCharacters() {
        assertEquals("********08", ProfileStore.maskedPhone("9876543208"))
        assertEquals("NOT SET", ProfileStore.maskedPhone(""))
    }

    @Test
    fun contactVCardContainsEscapedNameAndPhone() {
        assertEquals(
            "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Dvil\\, Spawn\r\nTEL;TYPE=CELL:+91 12345\r\nEND:VCARD",
            ProfileStore.contactVCard("Dvil, Spawn", "+91 12345")
        )
    }

    @Test
    fun blocksYouTubeAndShortenedQrLinks() {
        assertEquals(
            "YouTube links are not allowed in profile QR codes.",
            ProfileStore.qrValueValidationError("https://www.youtube.com/watch?v=abc")
        )
        assertEquals(
            "YouTube links are not allowed in profile QR codes.",
            ProfileStore.qrValueValidationError("youtu.be/abc")
        )
        assertEquals(
            "Shortened links are not allowed. Use the full destination URL.",
            ProfileStore.qrValueValidationError("https://bit.ly/example")
        )
        assertNull(ProfileStore.qrValueValidationError("https://github.com/Dvil-Spawns"))
        assertNull(ProfileStore.qrValueValidationError("Wi-Fi guest access"))
    }
}
