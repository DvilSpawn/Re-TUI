package ohi.andre.consolelauncher.community

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** XML clearance contract; Android instrumentation checks measured targets and dispatch. */
class CommunityCornerClearanceTest {
    @Test fun contentAndHeaderReserveTheFullResizeCornerTargets() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(root, "app/src/main/res/layout/community_surface.xml"))
        val surface = document.documentElement
        val elements = document.getElementsByTagName("*")
        fun named(id: String) = (0 until elements.length).map { elements.item(it) as Element }
            .first { it.getAttributeNS("http://schemas.android.com/apk/res/android", "id") == "@+id/$id" }
        fun dp(element: Element, attribute: String) = element.getAttributeNS("http://schemas.android.com/apk/res/android", attribute)
            .removeSuffix("dp").toIntOrNull() ?: 0
        val panel = named("community_panel")
        assertTrue("Dock must end above the full 48dp bottom corner hit bounds",
            dp(surface, "paddingBottom") + dp(panel, "layout_marginBottom") + dp(panel, "paddingBottom") >= 48)
        assertTrue("Rail/body must start below the full 48dp top corner hit bounds",
            dp(surface, "paddingTop") + dp(panel, "layout_marginTop") + dp(panel, "paddingTop") >= 48)
        val header = named("community_header_row")
        for (edge in listOf("Start", "End")) assertTrue("Header actions must reserve both top corners",
            dp(surface, "padding$edge") + dp(header, "layout_margin$edge") >= 48)
        val send = named("community_send")
        assertTrue(dp(send, "minWidth") >= 48); assertTrue(dp(send, "minHeight") >= 48)
    }
}
