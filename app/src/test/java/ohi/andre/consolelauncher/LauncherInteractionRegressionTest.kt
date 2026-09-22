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
        val click = ui.substringAfter("val openTerminalInput = View.OnClickListener").substringBefore("inputView.setOnClickListener")
        assertTrue(click.contains("activateTerminalInput(true)"))
        assertFalse(click.contains("showSoftInput"))
        assertTrue(ui.contains("inputGroup.setOnClickListener(openTerminalInput)"))
        val touch = ui.substringAfter("inputView.setOnTouchListener").substringBefore("inputView.setOnFocusChangeListener")
        assertTrue(touch.contains("MotionEvent.ACTION_DOWN ->"))
        assertTrue(touch.contains("inputView.setShowSoftInputOnFocus(true)"))
        assertTrue(touch.contains("MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL"))
        assertTrue(touch.contains("inputView.post { applyDisplayMarginsForConfigurationOnMainThread() }"))
        val focus = ui.substringAfter("inputView.setOnFocusChangeListener").substringBefore("if (inputView is OutlineEditText)")
        assertTrue(focus.contains("if (!inputTouchInProgress) applyDisplayMarginsForConfigurationOnMainThread()"))
        val layout = ui.substringAfter("private fun updateKeyboardLayoutState(").substringBefore("fun refreshDisplayMargins()")
        assertFalse(layout.contains("setShowSoftInputOnFocus(keyboardVisible)"))

        val drawer = File(root, "app/src/main/res/layout/apps_drawer.xml").readText()
        val panelBottom = drawer.substringAfter("@+id/apps_drawer_container")
            .substringBefore("android:background")
            .let { Regex("layout_marginBottom=\"(-?\\d+)dp\"").find(it)!!.groupValues[1].toInt() }
        val footerBottom = drawer.substringAfter("@+id/apps_drawer_footer")
            .substringBefore("android:background")
            .let { Regex("layout_marginBottom=\"(-?\\d+)dp\"").find(it)!!.groupValues[1].toInt() }
        assertTrue("Drawer footer extends below its root", panelBottom + footerBottom >= 0)
    }

    @Test
    fun homeIntentClosesLauncherOwnedTransientSurfaces() {
        assertTrue(
            shouldReturnToLauncherHome(
                "android.intent.action.MAIN",
                setOf("android.intent.category.HOME")
            )
        )
        assertFalse(shouldReturnToLauncherHome("android.intent.action.VIEW", setOf("android.intent.category.HOME")))
        assertFalse(shouldReturnToLauncherHome("android.intent.action.MAIN", setOf("android.intent.category.LAUNCHER")))

        val ui = File(root, "app/src/main/java/ohi/andre/consolelauncher/UIManager.kt").readText()
        val handler = ui.substringAfter("fun returnToLauncherHome()").substringBefore("private fun setWallpaperTouchEnabled")
        assertTrue(handler.contains("hideAndroidWidgetDrawer()"))
        assertTrue(handler.contains("hideAppsDrawer()"))
        assertTrue(handler.contains("openHomePage()"))
        assertTrue(handler.contains("closeTermuxConsole()"))
    }

    @Test
    fun termuxSocketStartupWaitsForProtocolAndFallsBackOnce() {
        val bridge = File(root, "termux/retui/bin/retui").readText()
        val readiness = bridge.substringAfter("bridge_listener_ready()").substringBefore("bridge_socket_start()")
        assertTrue(readiness.contains("HELLO %s 20 8 CAPTURE"))
        assertTrue(readiness.contains("__RETUI_SOCKET_STATUS__ ok"))
        assertTrue(readiness.contains("__RETUI_SESSION__"))
        assertTrue(bridge.contains("ps -A -o pid,args"))
        assertTrue(bridge.substringAfter("bridge_socket_start()").substringBefore("bridge_socket_daemon()")
            .contains("if ! bridge_listener_ready \"\$token\"; then"))

        val ui = File(root, "app/src/main/java/ohi/andre/consolelauncher/UIManager.kt").readText()
        val socketError = ui.substringAfter("override fun onError(message: String)").substringBefore("override fun onClosed()")
        assertTrue(socketError.contains("termuxWorkspaceSocketClient?.connected != true"))
        assertTrue(socketError.contains("buildTermuxWorkspaceLegacyCaptureScript()"))
    }
}
