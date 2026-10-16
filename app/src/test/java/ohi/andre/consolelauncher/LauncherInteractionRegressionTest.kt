package ohi.andre.consolelauncher

import java.io.File
import ohi.andre.consolelauncher.managers.ui.AppDrawerPaneManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherInteractionRegressionTest {
    private val root = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main").isDirectory }

    @Test
    fun workspaceRenderersDoNotReadModuleTextColors() {
        val ui = File(root, "app/src/main/java/ohi/andre/consolelauncher/UIManager.kt").readText()
        for (name in listOf("renderTermuxWorkspaceAnsi", "updateTermuxAppKeyMode", "addPodcastCard", "renderPodcastPlayer")) {
            val method = ui.substringAfter("private fun $name(").substringBefore("\n    private fun ")
            assertFalse("Module colors leaked into $name", method.contains("notificationWidgetTextColor()"))
            assertTrue("Missing workspace color in $name", method.contains("Theme.workspace_"))
        }
        val activity = File(root, "app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceActivity.kt").readText()
        val style = activity.substringAfter("private fun style()").substringBefore("private fun color(")
        assertFalse(style.contains("notificationWidgetTextColor()"))
        assertTrue(style.contains("Theme.workspace_"))
        assertTrue(style.contains("TerminalPaneIcon.apply"))
    }

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
        assertFalse("Detached workspace is not launcher-owned", handler.contains("termuxWorkspace"))
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:taskAffinity=\"${'$'}{applicationId}.workspace\""))
        assertTrue(handler.contains("closeTermuxConsole()"))
    }

    @Test
    fun modeCommandWritesChoiceSettingAndKeepsLegacyBooleanReadOnly() {
        val mode = File(root, "app/src/main/java/ohi/andre/consolelauncher/commands/main/raw/mode.kt").readText()
        assertTrue(mode.contains("LauncherMode.set(pack.context, mode)"))
        assertFalse(mode.contains("Behavior.search_only_mode"))
        assertTrue(mode.contains("LauncherMode.current()"))

        val commands = File(root, "app/src/main/res/values/strings_commands.xml").readText()
        assertTrue(commands.contains("mode classic|search"))
    }

    @Test
    fun appDrawerDetectsScrollableEndForAlphabetHighlight() {
        assertTrue(AppDrawerPaneManager.isAtScrollableEnd(120, 20, 140))
        assertTrue(AppDrawerPaneManager.isAtScrollableEnd(120, 25, 140))
        assertFalse(AppDrawerPaneManager.isAtScrollableEnd(0, 140, 140))
        assertFalse(AppDrawerPaneManager.isAtScrollableEnd(80, 20, 140))
    }

    @Test
    fun termuxSocketStartupWaitsForProtocolAndDoesNotReplayAfterFailure() {
        val bridge = File(root, "termux/retui/bin/retui").readText()
        val readiness = bridge.substringAfter("bridge_listener_ready()").substringBefore("bridge_socket_start()")
        assertTrue(readiness.contains("HELLO %s 20 8 PROBE - NO_CREATE"))
        assertTrue(readiness.contains("__RETUI_SOCKET_STATUS__ ok"))
        assertTrue(readiness.contains("__RETUI_SESSION__"))
        assertTrue(bridge.contains("ps -A -o pid,args"))
        assertTrue(bridge.substringAfter("bridge_socket_start()").substringBefore("bridge_socket_daemon()")
            .contains("if ! bridge_listener_ready \"\$token\"; then"))

        val activity = File(root, "app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceActivity.kt").readText()
        val socketListener = activity.substringAfter("TermuxWorkspaceSocketClient(object : TermuxWorkspaceSocketClient.Listener")
        val socketError = socketListener.substringAfter("override fun onError(message: String)").substringBefore("override fun onClosed()")
        assertTrue(socketError.contains("clients[tile] !== client"))
        assertTrue(socketError.contains("closeTile(tile)"))
        assertFalse(socketError.contains("startBridge()"))
        assertFalse(socketError.contains("startRunCommand"))
        val mutation = activity.substringAfter("private fun mutation(").substringBefore("private fun directText(")
        assertTrue(mutation.contains("workspace_bridge_input_not_sent"))
        assertFalse(mutation.contains("startBridge()"))
        assertFalse(mutation.contains("startRunCommand"))
    }
}
