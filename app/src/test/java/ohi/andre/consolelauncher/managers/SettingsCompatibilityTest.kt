package ohi.andre.consolelauncher.managers

import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.*
import org.junit.Assert.*
import org.junit.Test

class SettingsCompatibilityTest {
    private fun fixture(body: String, run: (java.io.File) -> Unit) {
        val file=Files.createTempFile("compatibility", ".xml").toFile()
        try { file.writeText(body); run(file); assertEquals(body, file.readText()) }
        finally { file.delete() }
    }
    @Test fun listsAndMarginsSurvivePresetNormalizationAndApplicationReading() {
        fixture("<THEME><status_lines_bg value=\"#FF123456,#FF654321\"/><ram_status_background_color value=\"#FFABCDEF\"/><status_lines_shadow_color value=\"#ff000000\"/></THEME>") { file ->
            val xml=PresetManager.sanitizeShareableXml(file,XMLPrefsManager.XMLPrefsRoot.THEME)
            assertTrue(xml.contains("ram_status_background_color value=\"#FFABCDEF\""))
            assertTrue(xml.contains("device_status_background_color value=\"#FF654321\""))
            assertTrue(xml.contains("ram_status_text_shadow_color value=\"#ff000000\""))
            val values=PresetManager.readAllowed(file,XMLPrefsManager.XMLPrefsRoot.THEME,Theme.entries.toTypedArray())
            assertEquals("#FF654321", values[Theme.device_status_background_color])
        }
        fixture("<UI><status_lines_alignment value=\"1,0,-1\"/><display_margin_mm value=\"1,2,3,4\"/></UI>") { file ->
            val xml=PresetManager.sanitizeShareableXml(file,XMLPrefsManager.XMLPrefsRoot.UI)
            assertTrue(xml.contains("ram_status_alignment value=\"1\""))
            assertTrue(xml.contains("device_status_alignment value=\"0\""))
            assertTrue(xml.contains("display_margin_top_section value=\"1,2,3,4\""))
            val values=PresetManager.readAllowed(file,XMLPrefsManager.XMLPrefsRoot.UI,Ui.entries.toTypedArray())
            assertEquals("1,2,3,4",values[Ui.display_margin_top_section])
        }
    }
    @Test fun clockCopiesWholeListToMissingConsumerAndRespectsPrivacy() {
        val format="[size=30]HH:mm[/size]%nEEE||HH:mm #FF0088"
        fixture("<BEHAVIOR><time_format value=\"$format\"/><time_format_separator value=\"||\"/><status_time_format value=\"HH:mm:ss\"/></BEHAVIOR>") { file ->
            val xml=PresetManager.sanitizeSelectedBehaviorXml(file,setOf("status_time_format","output_time_format","time_format_separator"))
            assertTrue(xml.contains("status_time_format value=\"HH:mm:ss\""))
            assertTrue(xml.contains("output_time_format value=\"$format\""))
            val safe=PresetManager.sanitizeShareableXml(file,XMLPrefsManager.XMLPrefsRoot.BEHAVIOR)
            assertFalse(safe.contains("output_time_format"))
            assertFalse(safe.contains("status_time_format"))
        }
    }
    @Test fun currentAliasWinsButConflictingCurrentDuplicatesFail() {
        fixture("<THEME><input_color value=\"#112233\"/><input_text_color value=\"#445566\"/></THEME>") { file ->
            assertTrue(PresetManager.sanitizeShareableXml(file,XMLPrefsManager.XMLPrefsRoot.THEME).contains("input_text_color value=\"#445566\""))
        }
        fixture("<THEME><input_text_color value=\"#112233\"/><input_text_color value=\"#445566\"/></THEME>") { file ->
            assertThrows(IllegalArgumentException::class.java) { PresetManager.sanitizeShareableXml(file,XMLPrefsManager.XMLPrefsRoot.THEME) }
            assertThrows(IllegalArgumentException::class.java) { PresetManager.readAllowed(file,XMLPrefsManager.XMLPrefsRoot.THEME,Theme.entries.toTypedArray()) }
        }
    }
    @Test fun everyRenameMigratesIdempotently() {
        val method=XMLPrefsManager::class.java.getDeclaredMethod("renamesFor",XMLPrefsManager.XMLPrefsRoot::class.java).apply { isAccessible=true }
        for (root in XMLPrefsManager.XMLPrefsRoot.entries) {
            @Suppress("UNCHECKED_CAST") val aliases=method.invoke(XMLPrefsManager,root) as Array<Pair<String,String>>
            for ((old,new) in aliases) {
                val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
                val element=doc.createElement(root.name);doc.appendChild(element)
                element.appendChild(doc.createElement(old).apply { setAttribute("value","preserve exact bytes") })
                assertTrue(XMLPrefsManager.migrateRootValues(doc,element,root))
                assertEquals("preserve exact bytes",(element.getElementsByTagName(new).item(0) as org.w3c.dom.Element).getAttribute("value"))
                assertFalse(XMLPrefsManager.migrateRootValues(doc,element,root))
            }
        }
    }
    @Test fun unknownRepeatableXmlIsPreservedWhenAddingSectionComments() {
        val document=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            "<UI><input_output_size value=\"14\"/><future_option value=\"first\"/><future_option value=\"second\"/></UI>".byteInputStream())
        val root=document.documentElement
        assertFalse(XMLPrefsManager.migrateRootValues(document,root,XMLPrefsManager.XMLPrefsRoot.UI))
        val sections=XMLPrefsManager::class.java.getDeclaredMethod("ensureDefaultSectionComments",
            org.w3c.dom.Document::class.java,org.w3c.dom.Element::class.java,XMLPrefsManager.XMLPrefsRoot::class.java).apply {isAccessible=true}
        sections.invoke(XMLPrefsManager,document,root,XMLPrefsManager.XMLPrefsRoot.UI)
        val future=root.getElementsByTagName("future_option")
        assertEquals(2,future.length)
        assertEquals("first",(future.item(0) as org.w3c.dom.Element).getAttribute("value"))
        assertEquals("second",(future.item(1) as org.w3c.dom.Element).getAttribute("value"))
    }

    @Test fun validBlackIsNeverAnInvalidSettingValue() {
        for (setting in Theme.entries) for (color in listOf("#ff000000","#FF000000","#000000","#00000000")) {
            assertFalse(setting.name,setting.invalidValues()?.contains(color)==true)
        }
    }
}
