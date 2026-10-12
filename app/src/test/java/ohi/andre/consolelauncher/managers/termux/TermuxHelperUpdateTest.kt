package ohi.andre.consolelauncher.managers.termux

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TermuxHelperUpdateTest {
    @Test fun onlyExactSuccessfulCallbackConfirmsInstallation() {
        assertTrue(TermuxHelperUpdate.succeeded("__RETUI_UPDATE_OK__ 0.4.2\n", 0))
        for ((output, code) in listOf("__RETUI_UPDATE_OK__ 0.4.2" to 1,
            "__RETUI_UPDATE_OK__ 0.4.2" to Int.MIN_VALUE, "__RETUI_UPDATE_OK__ 0.3.4" to 0,
            "__RETUI_UPDATE_OK__ 0.4.2\nextra" to 0, "permission denied" to 0))
            assertFalse(TermuxHelperUpdate.succeeded(output, code))
    }
    @Test fun checksumRejectsEmptyOrOversizedPayloadAndMatchesBundledSource() {
        assertEquals("YWJj", TermuxHelperUpdate.payload("abc".toByteArray()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", TermuxHelperUpdate.checksum("abc".toByteArray()))
        for (bytes in listOf(byteArrayOf(), ByteArray(TermuxHelperUpdate.MAX_BYTES + 1)))
            assertThrows(IllegalArgumentException::class.java) { TermuxHelperUpdate.checksum(bytes) }
        val root = File("..").takeIf { File(it, "termux/retui/bin/retui").isFile } ?: File(".")
        val helper = File(root, "termux/retui/bin/retui").readText()
        assertTrue(helper.contains("RETUI_VERSION=\"${TermuxHelperUpdate.VERSION}\""))
        assertTrue(helper.contains(TermuxHelperUpdate.PROTOCOL))
        assertTrue(TermuxHelperUpdate.probeScript.contains("RETUI_NO_CREATE=1"))
        assertFalse(TermuxHelperUpdate.probeScript.contains("curl"))
        val installer = File(root, "termux/retui/update.sh").readText()
        assertFalse(installer.contains("kill-server")); assertFalse(installer.contains("kill-session"))
        assertFalse(installer.contains("curl")); assertFalse(installer.contains("wget"))
    }
}
