package ohi.andre.consolelauncher.managers

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceManagerTest {
    @Test
    fun acceptsOnlyTheCompleteExpectedWrite() {
        val file = Files.createTempFile("retui-space", ".properties").toFile()
        try {
            file.writeText("complete")

            assertTrue(SpaceManager.hasExactText(file, "complete"))
            assertFalse(SpaceManager.hasExactText(file, "truncated"))
        } finally {
            file.delete()
        }
    }
}
