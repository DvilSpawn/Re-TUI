package ohi.andre.consolelauncher.commands.tuixt

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import ohi.andre.consolelauncher.managers.PresetManager
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class PresetMarketplaceIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun archive(name: String, color: String): ByteArray = ByteArrayOutputStream().apply {
        ZipOutputStream(this).use { zip ->
            mapOf("manifest.txt" to "type=retui-shareable-config\nschema=1\nprofile=shareable\nsections=theme,suggestions\npresetName=$name\n",
                "theme.xml" to "<THEME><input_text_color value=\"$color\"/></THEME>", "suggestions.xml" to "<SUGGESTIONS/>").forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
        }
    }.toByteArray()
    private fun entry(bytes: ByteArray) = PresetMarketplace.Entry("fixture", "Fixture",
        "https://re-tui.pages.dev/terminal-colors/fixture.retui", bytes.size,
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }, 0, -1, listOf(-1))

    @Test fun catalogueRejectsDuplicateIdsBadSizesHashesAndExternalUrls() {
        fun item() = JSONObject().put("id", "fixture").put("name", "Fixture").put("download", "fixture.retui")
            .put("bytes", 100).put("sha256", "a".repeat(64)).put("background", "#112233")
            .put("foreground", "#ffffff").put("swatches", JSONArray().put("#aabbcc"))
        fun catalogue(vararg items: JSONObject) = JSONObject().put("schema", 1)
            .put("themes", JSONArray().apply { items.forEach { put(it) } }).toString()
        assertEquals("fixture", PresetMarketplace.parse(catalogue(item())).single().id)
        listOf(catalogue(item(), item()), catalogue(item().put("bytes", 0)),
            catalogue(item().put("sha256", "bad")), catalogue(item().put("download", "https://evil.test/a"))).forEach {
            try { PresetMarketplace.parse(it); fail("Invalid catalogue accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun downloadsDeduplicatesKeepsCollisionsAndNeverApplies() {
        Tuils.init(context)
        val name = "Marketplace fixture " + UUID.randomUUID().toString().take(8)
        val originalClient = PresetMarketplace.client
        val beforePresets = PresetManager.listPresets().toSet()
        val liveFiles = listOf("theme.xml", "suggestions.xml", "ui.xml", "behavior.xml").associateWith {
            File(Tuils.getFolder(), it).takeIf(File::isFile)?.readBytes()
        }
        var bytes = archive(name, "#112233")
        PresetMarketplace.client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("re-tui.pages.dev", chain.request().url.host)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(bytes.toResponseBody("application/octet-stream".toMediaType())).build()
        }.build()
        val hashes = mutableSetOf<String>()
        try {
            val firstEntry = entry(bytes); hashes += firstEntry.hash
            val first = PresetMarketplace.install(context, firstEntry, { false }) {}
            assertEquals(first, PresetMarketplace.install(context, firstEntry, { false }) {})
            bytes = archive(name, "#445566")
            val secondEntry = entry(bytes); hashes += secondEntry.hash
            val second = PresetMarketplace.install(context, secondEntry, { false }) {}
            assertNotEquals(first, second)
            assertEquals(2, (PresetManager.listPresets().toSet() - beforePresets).size)
            try { PresetMarketplace.install(context, secondEntry.copy(hash = "0".repeat(64)), { false }) {}; fail("Bad hash accepted") }
            catch (_: IllegalArgumentException) { }
            try { PresetMarketplace.install(context, secondEntry, { true }) {}; fail("Cancellation ignored") }
            catch (_: CancellationException) { }
            liveFiles.forEach { (file, old) ->
                val current = File(Tuils.getFolder(), file)
                if (old == null) assertFalse(current.exists()) else assertArrayEquals(file, old, current.readBytes())
            }
        } finally {
            PresetMarketplace.client = originalClient
            (PresetManager.listPresets().toSet() - beforePresets).forEach { File(PresetManager.presetsDir, it).deleteRecursively() }
            context.getSharedPreferences("preset_marketplace", 0).edit().apply {
                hashes.forEach { remove(it); remove(it + ".contents") }
            }.commit()
        }
    }
}
