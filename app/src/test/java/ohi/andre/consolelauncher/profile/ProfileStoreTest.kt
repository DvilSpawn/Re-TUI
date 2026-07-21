package ohi.andre.consolelauncher.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileStoreTest {
    @Test
    fun phoneIsMaskedExceptForLastTwoCharacters() {
        assertEquals("********08", ProfileStore.maskedPhone("9876543208"))
        assertEquals("NOT SET", ProfileStore.maskedPhone(""))
    }
}
