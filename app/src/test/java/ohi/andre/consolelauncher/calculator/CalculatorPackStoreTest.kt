package ohi.andre.consolelauncher.calculator

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CalculatorPackStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun pack(version: String = "5.8.2", extra: String? = null, wrongHash: Boolean = false,
                     largeNotice: Boolean = false, deepManifest: Boolean = false): ByteArray {
        val files = linkedMapOf(
            "engine.js" to "var createQalculate = function() {};".toByteArray(),
            "engine.wasm" to byteArrayOf(0, 97, 115, 109, 1, 0, 0, 0),
            "LICENSE.txt" to "GPL-3.0-or-later".toByteArray(),
            "NOTICE.txt" to if (largeNotice) ByteArray(256 * 1024 + 1) else "Qalculate 5.8.2".toByteArray()
        )
        val hashes = JSONObject()
        files.forEach { (name, bytes) -> hashes.put(name, CalculatorPackStore.sha256(bytes)) }
        if (wrongHash) hashes.put("engine.wasm", "0".repeat(64))
        files["manifest.json"] = JSONObject().put("schema", 1).put("kind", "qalculate")
            .put("runtimeApi", 1).put("version", version).put("files", hashes).toString().toByteArray()
        if (deepManifest) files["manifest.json"] = ("{\"deep\":".repeat(1000) + "0" + "}".repeat(1000)).toByteArray()
        extra?.let { files[it] = "bad".toByteArray() }
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip -> files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            } }
        }.toByteArray()
    }
    private fun failure(block: () -> Unit) {
        try { block(); fail("Expected a rejected pack") } catch (_: IllegalArgumentException) { }
    }

    @Test fun installsAndRemovesOnePortableRuntime() {
        val store = CalculatorPackStorage(temporary.newFolder())
        assertNull(store.activeDirectory())
        assertEquals("5.8.2", store.install(pack().inputStream()))
        assertEquals("5.8.2", store.installedVersion())
        assertTrue(File(store.activeDirectory(), "engine.wasm").isFile)
        store.remove()
        assertNull(store.activeDirectory())
    }

    @Test fun downloadedArchiveRemainsInKnownFolderAfterPrivateInstallAndRemoval() {
        val downloads = temporary.newFolder("calculator-engines")
        val bytes = pack()
        val archive = CalculatorPackArchiveStorage(downloads).save("5.8.2", bytes, CalculatorPackStore.sha256(bytes))
        assertEquals(File(downloads, "qalculate-5.8.2.retui.calc.engine"), archive)
        assertArrayEquals(bytes, archive.readBytes())
        val store = CalculatorPackStorage(temporary.newFolder("private-runtime"))
        store.install(bytes.inputStream(), expectedVersion = "5.8.2")
        assertEquals("5.8.2", store.installedVersion())
        store.remove()
        assertTrue("Removing the runtime must preserve its downloaded archive", archive.isFile)
        assertArrayEquals(bytes, archive.readBytes())
    }

    @Test fun corruptOrCancelledArchiveDownloadPreservesEarlierSavedFile() {
        val downloads = temporary.newFolder("calculator-engines")
        val archives = CalculatorPackArchiveStorage(downloads)
        val original = pack()
        val saved = archives.save("5.8.2", original, CalculatorPackStore.sha256(original))
        failure { archives.save("5.8.2", pack("5.8.3"), "0".repeat(64)) }
        failure { archives.save("../escape", original, CalculatorPackStore.sha256(original)) }
        val replacement = ByteArray(32768) { 7 }
        try {
            archives.save("5.8.2", replacement, CalculatorPackStore.sha256(replacement)) {
                downloads.listFiles().orEmpty().any { it.name.startsWith(".download-") && it.length() > 0 }
            }
            fail("A cancelled download must not replace the saved archive")
        } catch (_: InterruptedIOException) { }
        assertArrayEquals(original, saved.readBytes())
        assertEquals(listOf(saved.name), downloads.listFiles().orEmpty().map { it.name })
    }

    @Test fun corruptOrUnexpectedPayloadsNeverReplaceTheWorkingPack() {
        val root = temporary.newFolder()
        val store = CalculatorPackStorage(root)
        store.install(pack().inputStream())
        val original = store.activeDirectory()
        for (bytes in listOf(pack(wrongHash = true), pack(extra = "../engine.js"),
                pack(extra = "classes.dex"), pack(extra = "runtime.so"), pack(largeNotice = true), pack(deepManifest = true))) {
            failure { store.install(bytes.inputStream()) }
            assertEquals(original, store.activeDirectory())
            assertEquals(1, root.listFiles()!!.count { it.isDirectory })
        }
    }

    @Test fun cancellationBeforeActivationRetainsTheOldVersion() {
        val root = temporary.newFolder()
        val store = CalculatorPackStorage(root)
        store.install(pack().inputStream())
        val old = store.activeDirectory()
        try {
            store.install(pack("5.8.3").inputStream(), cancelled = { File(root, "previous").exists() })
            fail("Cancellation should abort activation")
        } catch (_: InterruptedIOException) { }
        assertEquals(old, store.activeDirectory())
        assertEquals("5.8.2", store.installedVersion())
        assertEquals(1, root.listFiles()!!.count { it.isDirectory })
    }

    @Test fun interruptedOrCorruptActivePointerRecoversThePreviousValidRuntime() {
        val root = temporary.newFolder()
        val store = CalculatorPackStorage(root)
        store.install(pack().inputStream())
        store.install(pack("5.8.3").inputStream())
        assertEquals("5.8.3", store.installedVersion())
        File(root, "active").writeText("../../outside")
        assertEquals("5.8.2", CalculatorPackStorage(root).installedVersion())
    }

    @Test fun corruptedActivePayloadRecoversPreviousAndCatalogueVersionMustMatch() {
        val root = temporary.newFolder()
        val store = CalculatorPackStorage(root)
        store.install(pack().inputStream())
        failure { store.install(pack("5.8.3").inputStream(), expectedVersion = "5.8.4") }
        assertEquals("5.8.2", store.installedVersion())
        store.install(pack("5.8.3").inputStream())
        File(store.activeDirectory(), "engine.wasm").writeText("broken")
        assertEquals("5.8.2", store.installedVersion())
    }

    @Test fun duplicateZipEntriesAreRejected() {
        val store = CalculatorPackStorage(temporary.newFolder())
        val duplicate = String(pack(extra = "engine.jx"), Charsets.ISO_8859_1)
            .replace("engine.jx", "engine.js").toByteArray(Charsets.ISO_8859_1)
        failure { store.install(duplicate.inputStream()) }
        assertNull(store.activeDirectory())
    }

    @Test fun removalWaitsForAnUpdateAndCannotLeaveAPartialRuntime() {
        val store = CalculatorPackStorage(temporary.newFolder())
        store.install(pack().inputStream())
        val copying = CountDownLatch(1)
        val continueCopy = CountDownLatch(1)
        val error = AtomicReference<Throwable?>()
        val install = thread {
            try {
                store.install(pack("5.8.3").inputStream(), progress = { _, _ ->
                    copying.countDown()
                    check(continueCopy.await(5, TimeUnit.SECONDS))
                })
            } catch (failure: Throwable) { error.set(failure) }
        }
        assertTrue(copying.await(5, TimeUnit.SECONDS))
        val statusReady = CountDownLatch(1)
        val version = AtomicReference<String?>()
        val status = thread { version.set(store.installedVersion()); statusReady.countDown() }
        try {
            assertTrue("Status must remain responsive while provider input is blocked", statusReady.await(1, TimeUnit.SECONDS))
            assertEquals("5.8.2", version.get())
        } catch (failure: Throwable) {
            continueCopy.countDown()
            throw failure
        }
        status.join(1000)
        val remove = thread { store.remove() }
        continueCopy.countDown()
        install.join(5000); remove.join(5000)
        assertFalse(install.isAlive); assertFalse(remove.isAlive)
        assertNull(error.get())
        assertNull(store.activeDirectory())
    }

    @Test fun unusableRuntimeRollsBackWithoutUndoingALaterInstallation() {
        val store = CalculatorPackStorage(temporary.newFolder())
        store.install(pack().inputStream())
        val working = store.activeDirectory()!!
        store.install(pack("5.8.3").inputStream())
        val unusable = store.activeDirectory()!!
        assertTrue(store.rollback(unusable))
        assertEquals(working, store.activeDirectory())
        assertFalse(unusable.exists())
        store.install(pack("5.8.4").inputStream())
        val newer = store.activeDirectory()
        assertFalse(store.rollback(unusable))
        assertEquals(newer, store.activeDirectory())
    }

    @Test fun unusableFirstImportIsDisabledWhenThereIsNoPreviousPack() {
        val store = CalculatorPackStorage(temporary.newFolder())
        store.install(pack().inputStream())
        val unusable = store.activeDirectory()!!
        assertFalse(store.rollback(unusable))
        assertNull(store.activeDirectory())
        assertNull(store.installedVersion())
        assertFalse(unusable.exists())
    }

    @Test fun catalogueCannotRedirectTrustToAnotherHostOrDirectory() {
        val catalogue = CalculatorPackStore.safeCatalogUri("https://re-tui.pages.dev/calculator-packs/catalog.json")
        assertEquals("https://re-tui.pages.dev/calculator-packs/q.retui.calc.engine",
            CalculatorPackStore.safePackUri(catalogue, "q.retui.calc.engine").toString())
        for (url in listOf("https://evil.test/calculator-packs/q.retui.calc.engine", "../q.retui.calc.engine",
                "https://re-tui.pages.dev/calculator-packs/%2e%2e/q.retui.calc.engine",
                "https://re-tui.pages.dev:443/calculator-packs/q.retui.calc.engine",
                "q.retui.calc.engine?query=1", "q.retui.calc.engine#fragment")) {
            failure { CalculatorPackStore.safePackUri(catalogue, url) }
        }
        failure { CalculatorPackStore.safeCatalogUri("http://re-tui.pages.dev/calculator-packs/catalog.json") }
    }
}
