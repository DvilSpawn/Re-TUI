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
    @Test fun catalogueSkipsRowsThisBuildCannotUseWithoutDroppingTheRest() {
        fun row(id: String = "topo", path: String = "downloads/$id.retui-wallpaper", api: Int = 1, extra: String = "") =
            """{"id":"$id","api":$api,"name":"$id","description":"Contours","download":"$path","preview":"previews/$id.gif","sha256":"${"0".repeat(64)}"$extra}"""
        fun catalog(vararg rows: String, schema: Int = 2) = """{"schema":$schema,"wallpapers":[${rows.joinToString(",")}]}"""
        val skipped = listOf(row(id = "future-wallpaper"), row(path = "https://other.example/file"), row(path = "../file"),
            row(path = "%2e%2e/file"), row(api = 2), """{"id":"topo"}""", "42")
        for (bad in skipped) {
            val entries = WallpaperLibrary.parse(catalog(bad, row(id = "clouds")))
            assertEquals(bad, listOf("clouds"), entries.map { it.id })
        }
        // Unknown fields are ignored, and the first usable row per id wins so newer pack apis can sit before a fallback.
        val entries = WallpaperLibrary.parse(catalog(row(api = 2, path = "downloads/topo-v2.retui-wallpaper"),
            row(extra = ""","minLauncher":700,"tags":["calm"]"""), row(path = "downloads/duplicate.retui-wallpaper"), row(id = "astro"), row(id = "matrix")))
        assertEquals(listOf("topo", "astro", "matrix"), entries.map { it.id })
        assertTrue(entries.first().url.endsWith("/wallpaper-packs/downloads/topo.retui-wallpaper"))
        for (text in listOf(catalog(row(), schema = 1), catalog(row(), schema = 3), """{"schema":2}""", "[]",
            catalog(*Array(257) { row() })))
            try { WallpaperLibrary.parse(text); fail(text) } catch (_: Exception) { }
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
