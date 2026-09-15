package ohi.andre.consolelauncher.localization

import android.content.Intent
import android.content.res.Configuration
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class LanguagePackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val catalog by lazy { JSONObject(context.assets.open("localization/catalog.json").bufferedReader().use { it.readText() }) }
    internal fun archive(id: String, version: Int = 1, text: String = "اختبار اللغة", xml: String? = null, extra: String? = null): ByteArray {
        val manifest = JSONObject().put("schema", 1).put("target", "retui-launcher").put("id", id)
            .put("languageTag", "ar").put("name", "Localization test").put("nativeName", "اختبار")
            .put("version", version).put("direction", "rtl")
        return zip(mapOf("manifest.json" to manifest.toString().toByteArray(), "strings.xml" to (xml ?: """
            <resources>
                <string name="language_packs_title">$text</string>
                <string name="ui_file_console_refresh">تحديث</string>
                <string name="hint_file_console_input">أدخل أمراً</string>
                <string name="notes_rememberactivity_markdown_30d86">صيغة</string>
                <plurals name="surface_notes_count"><item quantity="one">ONE %1${'$'}d</item><item quantity="two">TWO %1${'$'}d</item><item quantity="other">OTHER %1${'$'}d</item></plurals>
            </resources>
        """.trimIndent()).toByteArray()) + if (extra == null) emptyMap() else mapOf(extra to byteArrayOf(1)))
    }
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()

    @Test fun exportedTemplateParsesAndMatchesBundledText() {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(context.assets.open("localization/english-template.zip")).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; if (entry.name != "README.md") files[entry.name] = zip.readBytes() }
        }
        val pack = LanguagePackArchive.parse(zip(files), catalog)
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }).resources
        // AAPT retains trailing indentation after some inline quote spans; the plain XML export normalizes it.
        val mismatches = mutableListOf<String>()
        for ((key, entry) in pack.texts) {
            val definition = catalog.getJSONObject(key)
            val id = english.getIdentifier(key, definition.getString("type"), context.packageName)
            assertTrue("Resource was stripped: $key", id != 0)
            if (definition.getString("type") == "string" && english.getString(id).trimEnd() != entry.forms.getValue("other").trimEnd()) mismatches.add(key + " expected=" + JSONObject.quote(english.getString(id)) + " actual=" + JSONObject.quote(entry.forms.getValue("other")))
        }
        assertTrue("Template mismatch: ${mismatches.take(20)} (${mismatches.size} total)", mismatches.isEmpty())
    }

    @Test fun importSwitchUpdateFallbackXmlAndRemoval() {
        val id = "test-${System.nanoTime()}"
        val original = LanguagePacks.selectedId()
        var activity: LanguagePackActivity? = null
        try {
            LanguagePacks.import(archive(id).inputStream())
            LanguagePacks.select(id)
            activity = instrumentation.startActivitySync(Intent(context, LanguagePackActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LanguagePackActivity
            val screen = activity
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals("اختبار اللغة", screen.getString(R.string.language_packs_title))
                assertEquals("An unknown error occurred", screen.getString(R.string.output_error))
                assertEquals("صيغة", screen.getString(R.string.notes_rememberactivity_markdown_30d86))
                assertTrue(screen.resources.getQuantityString(R.plurals.surface_notes_count, 2, 2).startsWith("TWO "))
                val view = screen.layoutInflater.inflate(R.layout.file_console, null)
                assertEquals("تحديث", view.findViewById<TextView>(R.id.file_refresh).text.toString())
                assertEquals("أدخل أمراً", view.findViewById<TextView>(R.id.file_input).hint.toString())
                assertEquals(android.view.View.LAYOUT_DIRECTION_RTL, screen.resources.configuration.layoutDirection)
            }
            android.os.SystemClock.sleep(1000)
            java.io.File(context.cacheDir, "language-pack-screen-check.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            LanguagePacks.import(archive(id, 2, "Updated translation").inputStream())
            assertEquals("Updated translation", screen.getString(R.string.language_packs_title))
            try { LanguagePacks.import(archive(id, 1).inputStream()); fail("Downgrade accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(2, LanguagePacks.active!!.version)
            LanguagePacks.remove(id)
            assertEquals("en", LanguagePacks.selectedId())
            assertEquals("Language packs", screen.getString(R.string.language_packs_title))
        } finally {
            instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }
            if (LanguagePacks.installed().any { it.id == id }) LanguagePacks.remove(id)
            LanguagePacks.select(original)
        }
    }

    @Test fun notesAndLauncherRemainFunctionalWithPack() {
        val id = "test-${System.nanoTime()}"
        val original = LanguagePacks.selectedId()
        try {
            LanguagePacks.import(archive(id).inputStream())
            LanguagePacks.select(id)
            assertEquals("اختبار اللغة", context.applicationContext.getString(R.string.language_packs_title))
            val notes = ohi.andre.consolelauncher.notes.LauncherNotesIntegrationTest()
            notes.notesTaskSurvivesLauncherReloadAndRecreation()
            notes.transparentLibraryAndDocumentReplaceTheSamePane()
        } finally {
            if (LanguagePacks.installed().any { it.id == id }) LanguagePacks.remove(id)
            LanguagePacks.select(original)
        }
    }

    @Test fun malformedPacksCannotReplaceInstalledTranslation() {
        val id = "test-${System.nanoTime()}"
        val original = LanguagePacks.selectedId()
        try {
            LanguagePacks.import(archive(id).inputStream())
            for (xml in listOf(
                "<resources><string name=\"not_a_launcher_key\">Bad</string></resources>",
                "<resources><string name=\"ui_token_48bb4ccc18\">Bad</string></resources>",
                "<resources><string name=\"language_packs_active\">%1\$d</string></resources>",
                "<!DOCTYPE resources [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><resources/>",
                "<resources><string name=\"language_packs_title\">A</string><string name=\"language_packs_title\">B</string></resources>",
                "<resources><plurals name=\"surface_notes_count\"><item quantity=\"one\">%1${'$'}d</item></plurals></resources>"
            )) {
                try { LanguagePacks.import(archive(id, 2, xml = xml).inputStream()); fail("Invalid XML accepted: $xml") } catch (_: Exception) { }
            }
            try { LanguagePacks.import(archive(id, 2, extra = "../payload").inputStream()); fail("Unexpected entry accepted") } catch (_: Exception) { }
            assertEquals(1, LanguagePacks.installed().first { it.id == id }.version)
        } finally {
            if (LanguagePacks.installed().any { it.id == id }) LanguagePacks.remove(id)
            LanguagePacks.select(original)
        }
    }

    @Test fun documentButtonsExportAndImport() {
        val id = "test-${System.nanoTime()}"
        val original = LanguagePacks.selectedId()
        val folder = java.io.File(context.cacheDir, "notes-exports").apply { mkdirs() }
        val source = java.io.File(folder, "$id.retui-launcher-lang").apply { writeBytes(archive(id)) }
        val exported = java.io.File(folder, "$id.zip")
        fun click(view: android.view.View, label: String): Boolean {
            if (view is TextView && view.text.toString() == label && view.isClickable) return view.performClick()
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) if (click(view.getChildAt(i), label)) return true
            return false
        }
        fun picker(action: String, file: java.io.File): android.app.Instrumentation.ActivityMonitor {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            return instrumentation.addMonitor(android.content.IntentFilter(action).apply {
                addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*")
            }, android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, Intent().setData(uri)), true)
        }
        try {
            androidx.test.core.app.ActivityScenario.launch(LanguagePackActivity::class.java).use { scenario ->
                val exportMonitor = picker(Intent.ACTION_CREATE_DOCUMENT, exported)
                try {
                    scenario.onActivity { assertTrue(click(it.window.decorView, it.getString(R.string.language_packs_template))) }
                    instrumentation.waitForIdleSync()
                    assertEquals(1, exportMonitor.hits)
                    assertArrayEquals(context.assets.open("localization/english-template.zip").use { it.readBytes() }, exported.readBytes())
                } finally { instrumentation.removeMonitor(exportMonitor) }
                val importMonitor = picker(Intent.ACTION_OPEN_DOCUMENT, source)
                try {
                    scenario.onActivity { assertTrue(click(it.window.decorView, it.getString(R.string.language_packs_import))) }
                    val deadline = android.os.SystemClock.uptimeMillis() + 5000
                    while (LanguagePacks.selectedId() != id && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(50)
                    instrumentation.waitForIdleSync()
                    assertEquals(1, importMonitor.hits)
                    assertEquals(id, LanguagePacks.selectedId())
                    scenario.onActivity { assertEquals("اختبار اللغة", it.title.toString()) }
                } finally { instrumentation.removeMonitor(importMonitor) }
            }
        } finally {
            if (LanguagePacks.installed().any { it.id == id }) LanguagePacks.remove(id)
            LanguagePacks.select(original)
            source.delete(); exported.delete()
        }
    }
}
