package ohi.andre.consolelauncher.managers.termux

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class TermuxWorkspaceSshSetupTest {
    @Test fun identityRemainsTheSameWithoutAPublicKeySidecarOrAfterInterruptedWrite() {
        val directory = Files.createTempDirectory("retui-pairing-test").toFile()
        try {
            val publicKey = TermuxWorkspaceSshSetup.ensureIdentity(directory)
            assertTrue(publicKey.startsWith("ecdsa-sha2-nistp256 "))
            val privateBytes = File(directory, "identity").readBytes()
            File(directory, "identity.pending").writeText("interrupted key replacement")
            assertEquals(publicKey, TermuxWorkspaceSshSetup.ensureIdentity(directory))
            assertArrayEquals(privateBytes, File(directory, "identity").readBytes())
            val pair = KeyPair.load(JSch(), File(directory, "identity").absolutePath)
            try { assertEquals(256, pair.keySize); assertFalse(pair.isEncrypted) } finally { pair.dispose() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun failedAtomicWritePreservesTheLastDurablePairingAndRemovesPartialSecret() {
        val directory = Files.createTempDirectory("retui-pairing-test").toFile()
        try {
            val file = File(directory, "identity")
            file.writeText("previous identity")
            try {
                TermuxWorkspaceSshSetup.atomicWrite(file) { it.write("partial replacement".toByteArray()); error("disk failure") }
                fail("An interrupted write must fail")
            } catch (_: IllegalStateException) { }
            assertEquals("previous identity", file.readText())
            assertFalse(File(directory, "identity.pending").exists())
            TermuxWorkspaceSshSetup.atomicWrite(file) { it.write("complete replacement".toByteArray()) }
            assertEquals("complete replacement", file.readText())
        } finally { directory.deleteRecursively() }
    }

    @Test fun damagedIdentityRequiresRepairRatherThanSilentlyRotatingKeys() {
        val directory = Files.createTempDirectory("retui-pairing-test").toFile()
        try {
            File(directory, "identity").writeText("damaged")
            try { TermuxWorkspaceSshSetup.ensureIdentity(directory); fail("Damaged identity must require repair") }
            catch (error: IllegalStateException) { assertTrue(error.message.orEmpty().contains("Repair connection")) }
            assertEquals("damaged", File(directory, "identity").readText())
        } finally { directory.deleteRecursively() }
    }

    @Test fun bootstrapMarkersRejectMissingDuplicatesErrorsAndOversizedResults() {
        val valid = "package progress\n__RETUI_SSH_USER__ u0_a1000\n__RETUI_SSH_PORT__ 8928\n__RETUI_SSH_HOST_KEY__ Zm9v\n"
        assertEquals("u0_a1000", TermuxWorkspaceSshSetup.readMarkers(valid)["__RETUI_SSH_USER__"])
        for (invalid in listOf("", valid.replace("__RETUI_SSH_PORT__ 8928", ""), valid + "__RETUI_SSH_USER__ stranger\n",
            valid.replace("__RETUI_SSH_PORT__ 8928", "__RETUI_SSH_PORT__ "), "x".repeat(1024 * 1024 + 1))) {
            try { TermuxWorkspaceSshSetup.readMarkers(invalid); fail("Malformed bootstrap must fail") }
            catch (_: IllegalArgumentException) { }
        }
        try { TermuxWorkspaceSshSetup.readMarkers(valid + "__RETUI_SSH_ERROR__ Package install cancelled\n"); fail("Setup error must win") }
        catch (error: IllegalStateException) { assertEquals("Package install cancelled", error.message) }
    }
}
