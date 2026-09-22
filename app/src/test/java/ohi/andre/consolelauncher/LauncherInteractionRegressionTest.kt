package ohi.andre.consolelauncher

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherInteractionRegressionTest {
    private val root = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main").isDirectory }

    @Test
    fun terminalTapDefersImeAndDrawerFooterHasClearance() {
        val ui = File(root, "app/src/main/java/ohi/andre/consolelauncher/UIManager.kt").readText()
        val click = ui.substringAfter("inputView.setOnClickListener").substringBefore("applyRetuiKeyboardTheme(inputView")
        assertTrue(click.contains("activateTerminalInput(true)"))
        assertFalse(click.contains("showSoftInput"))

        val drawer = File(root, "app/src/main/res/layout/apps_drawer.xml").readText()
        val panelBottom = drawer.substringAfter("@+id/apps_drawer_container")
            .substringBefore("android:background")
            .let { Regex("layout_marginBottom=\"(-?\\d+)dp\"").find(it)!!.groupValues[1].toInt() }
        val footerBottom = drawer.substringAfter("@+id/apps_drawer_footer")
            .substringBefore("android:background")
            .let { Regex("layout_marginBottom=\"(-?\\d+)dp\"").find(it)!!.groupValues[1].toInt() }
        assertTrue("Drawer footer extends below its root", panelBottom + footerBottom >= 0)
    }
}
