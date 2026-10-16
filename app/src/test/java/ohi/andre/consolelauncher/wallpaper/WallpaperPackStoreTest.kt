package ohi.andre.consolelauncher.wallpaper

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.*
import java.util.concurrent.CancellationException
import java.util.Base64

class WallpaperPackStoreTest {
    private fun pack(vararg extra: String): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            for ((name, text) in listOf("wallpaper.json" to """{"id":"topo","api":1,"engine":"topo noise"}""", "LICENSE.txt" to "License") + extra.map { it to "unexpected" }) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            }
        }
    }.toByteArray()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    @Test fun installAndFailedUpdatePreserveThePreviousPack() {
        val root = Files.createTempDirectory("wallpaper-packs").toFile()
        val store = WallpaperPackStore(root)
        try {
            val good = pack(); store.install("topo", good, hash(good)); assertTrue(store.installed("topo"))
            for (name in listOf("../escape", "plugin.dex", "runtime.so", "script.lua", "assets/unknown.png")) {
                val bad = pack(name)
                try { store.install("topo", bad, hash(bad)); fail(name) } catch (_: IllegalArgumentException) { }
                assertTrue(store.installed("topo"))
                assertFalse(root.resolve("topo.partial").exists())
            }
            try { store.install("topo", good, "0".repeat(64)); fail("Checksum accepted") } catch (_: IllegalArgumentException) { }
            assertTrue(store.installed("topo"))
            try { store.install("clouds", good, hash(good)); fail("Incomplete pack accepted") } catch (_: IllegalArgumentException) { }
            assertFalse(store.installed("clouds"))
        } finally { root.deleteRecursively() }
    }
    @Test fun catalogueRejectsUnknownIdsExternalPathsDuplicatesAndFutureApis() {
        fun catalog(id: String = "topo", path: String = "downloads/topo.retui-wallpaper", api: Int = 1, duplicate: Boolean = false): String {
            val row = """{"id":"$id","api":$api,"name":"Topo","description":"Contours","download":"$path","preview":"previews/topo.gif","sha256":"${"0".repeat(64)}"}"""
            return """{"schema":1,"wallpapers":[$row${if (duplicate) ",$row" else ""}]}"""
        }
        assertEquals("topo", WallpaperLibrary.parse(catalog()).single().id)
        val bad = listOf(catalog(id = "untrusted"), catalog(path = "https://other.example/file"), catalog(path = "../file"), catalog(path = "%2e%2e/file"), catalog(api = 2), catalog(duplicate = true))
        for (text in bad) try { WallpaperLibrary.parse(text); fail(text) } catch (_: IllegalArgumentException) { }
    }
    @Test fun retainsOnlyVerifiedArchiveAndCancellationPreservesInstalledPack() {
        val root = Files.createTempDirectory("wallpaper-transaction").toFile()
        try {
            val store = WallpaperPackStore(root.resolve("private"))
            val downloads = root.resolve("launcher/wallpaper-downloads")
            val bytes = pack()
            val archive = store.install("topo", bytes, hash(bytes), downloads)
            assertEquals("topo-${hash(bytes).take(12)}.retui-wallpaper", archive.name)
            assertArrayEquals(bytes, archive.readBytes())
            var checkpoints = 0
            try {
                store.install("topo", bytes, hash(bytes), downloads) { ++checkpoints > 4 }
                fail("Cancellation was ignored")
            } catch (_: CancellationException) { }
            assertTrue(store.installed("topo"))
            assertArrayEquals(bytes, archive.readBytes())
            assertFalse(root.resolve("private/topo.partial").exists())
            assertEquals(listOf(archive.name), downloads.listFiles()!!.map { it.name })
            val bad = pack("plugin.dex")
            try { store.install("topo", bad, hash(bad), downloads); fail("Unexpected file accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(listOf(archive.name), downloads.listFiles()!!.map { it.name })
            // A process stopped between the two directory renames still has a usable previous pack.
            assertTrue(root.resolve("private/topo").renameTo(root.resolve("private/topo.previous")))
            assertTrue(store.installed("topo"))
            store.install("topo", bytes, hash(bytes), downloads)
            assertTrue(store.installed("topo"))
            assertFalse(root.resolve("private/topo.previous").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun imagePacksValidateHeaderDimensionsAndManifestBeforeReplacing() {
        fun pond(image: ByteArray, engine: String = "nagomi"): ByteArray = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                val files = linkedMapOf(
                    "wallpaper.json" to """{"id":"nagomi","api":1,"engine":"$engine"}""".toByteArray(),
                    "LICENSE.txt" to "License".toByteArray(), "nagomi_pond_bed.png" to image)
                for ((name, bytes) in files) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                }
            }
        }.toByteArray()
        val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aZl8AAAAASUVORK5CYII=")
        val root = Files.createTempDirectory("wallpaper-images").toFile()
        try {
            val store = WallpaperPackStore(root)
            val good = pond(png); store.install("nagomi", good, hash(good))
            assertArrayEquals(png, store.asset("nagomi", "nagomi_pond_bed.png").readBytes())
            val oversized = png.copyOf().apply { this[16] = 0x7f }
            for (bad in listOf(pond(ByteArray(40)), pond(oversized), pond(png, "clouds"))) {
                try { store.install("nagomi", bad, hash(bad)); fail("Invalid image pack accepted") }
                catch (_: IllegalArgumentException) { }
                assertTrue(store.installed("nagomi"))
                assertArrayEquals(png, store.asset("nagomi", "nagomi_pond_bed.png").readBytes())
            }
        } finally { root.deleteRecursively() }
    }

}
