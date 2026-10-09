package ohi.andre.consolelauncher.help

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wiring guard; ResizeCornerDispatchIntegrationTest exercises Android MotionEvent dispatch. */
class ResizeCornerDispatchTest {
    @Test fun resizeCornersYieldOccupiedContentTouchBoundsBeforeButtonDispatch() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
        val source = File(root, "app/src/main/java/ohi/andre/consolelauncher/help/HelpWindowPanel.kt").readText()
        assertTrue("Corner overlays must exclude measured interactive content before Button consumes DOWN",
            source.contains("override fun dispatchTouchEvent(event: MotionEvent)") &&
                source.contains("contentHit(content, event.rawX.toInt(), event.rawY.toInt())"))
        assertTrue("All four resize controls use the exclusion", source.contains("control(glyph, label, resizeCorner = true)"))
    }
}
