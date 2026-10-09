package ohi.andre.consolelauncher.managers.termux

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Wiring guards complement the rendered workspace and loopback input fixtures. */
class TermuxWorkspaceReleaseContractTest {
    private val root = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main").isDirectory }
    private val activity = File(root, "app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceActivity.kt").readText()

    @Test fun settingsNotifyWorkspaceAfterFontInvalidationBeforeContextGate() {
        val settings = File(root, "app/src/main/java/ohi/andre/consolelauncher/managers/settings/LauncherSettings.kt").readText()
            .substringAfter("private fun onSettingChanged(")
        val refresh = settings.indexOf("TermuxWorkspaceActivity.refreshIfRunning()")
        assertTrue("Foreground workspace is not notified of setting changes", refresh >= 0)
        assertTrue("Workspace refresh must use the invalidated font cache", refresh > settings.indexOf("UIUtils.cancelFont()"))
        assertTrue("Context-free settings must notify the foreground workspace", refresh < settings.indexOf("if (context == null)"))
    }

    @Test fun appearanceRefreshOnlySchedulesChangedGeometryWithoutInputOrTransportReset() {
        val style = activity.substringAfter("private fun style()").substringBefore("private fun panelDrawable(")
        for (call in listOf("setTerminalTypeface", "setTerminalTextSizeSp", "updateThemeColors", "bindKeys()", "RetuiThemeBridge.sendToKeyboard"))
            assertTrue("Missing live appearance operation: $call", style.contains(call))
        assertTrue(style.contains("scheduleGeometryCapture()"))
        val resize = activity.substringAfter("private fun scheduleGeometryCapture()").substringBefore("private fun mutation(")
        assertTrue(resize.contains("if (geometryCapturePending) return"))
        assertTrue(resize.contains("if (!visible || !tiles.isAttachedToWindow) return"))
        assertTrue(resize.contains("geometryCaptureListener = tiles.doOnPreDraw"))
        assertTrue(resize.contains("tiles.doOnPreDraw"))
        assertTrue(resize.contains("geometryCapturePending = false"))
        assertTrue(resize.contains("selection.visibleTiles.forEach { capture(it, onlyIfChanged = true) }"))
        val capture = activity.substringAfter("private fun capture(").substringBefore("private fun scheduleGeometryCapture(")
        for (guard in listOf("visible", "client?.connected == true", "!creating[tile]", "tile in selection.visibleTiles", "size != lastGeometry[tile]"))
            assertTrue("Geometry refresh lost guard: $guard", capture.contains(guard))
        for (call in listOf("startBridge(", "connectTile(", "closeTile(", "applyLayout(", "setText(", "setSelection(",
            "runPendingCommand(", "command(", "mutation(", "directText(", "selection.select(", "selection.focus(", "localDraft =", "pendingCommand =", "modifiers.clear("))
            assertFalse("Appearance resize has a state/transport side effect: $call", (style + resize + capture).contains(call))
        for (call in listOf("capture(", "client."))
            assertFalse("Appearance refresh has a state/transport side effect: $call", style.contains(call))
    }

    @Test fun pendingGeometryIsCancelledOnPauseAndDetachAndRetriedOnAttach() {
        val cancel = activity.substringAfter("private fun cancelGeometryCapture()").substringBefore("private fun mutation(")
        for (operation in listOf("geometryCaptureListener?.removeListener()", "geometryCaptureListener = null", "geometryCapturePending = false"))
            assertTrue("Missing pre-draw cancellation: $operation", cancel.contains(operation))
        val pause = activity.substringAfter("override fun onPause()").substringBefore("override fun onWindowFocusChanged(")
        assertTrue(pause.contains("cancelGeometryCapture()"))
        val attach = activity.substringAfter("tiles.addOnAttachStateChangeListener(").substringBefore("output.addView(tiles")
        assertTrue(attach.substringAfter("onViewAttachedToWindow").substringBefore("onViewDetachedFromWindow").contains("scheduleGeometryCapture()"))
        assertTrue(attach.substringAfter("onViewDetachedFromWindow").contains("cancelGeometryCapture()"))
    }

    @Test fun acceptedCreationFrameSchedulesChangedGeometryAfterUpdatingTargetAndFrame() {
        val frame = activity.substringAfter("override fun onFrame(raw: String)").substringBefore("override fun onStatus(")
        val completed = frame.indexOf("val creationCompleted = creating[tile]")
        assertTrue(completed >= 0 && completed < frame.indexOf("creating[tile] = false"))
        val schedule = frame.indexOf("if (creationCompleted) scheduleGeometryCapture()")
        assertTrue(schedule > frame.indexOf("selection.select(tile, target)"))
        assertTrue(schedule > frame.indexOf("grids[tile]?.setFrame"))
        assertTrue(schedule > frame.indexOf("runPendingCommand()"))
    }

    @Test fun workspaceFontFixtureUsesEffectiveSettingAndRealNotificationHook() {
        val fixture = File(root, "app/src/androidTest/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspacePaneIntegrationTest.kt").readText()
            .substringAfter("fun liveAppearanceAndFadeRetainBothTilesDraftAndInputSelection()").substringBefore("@Test fun twoTilesRestore")
        assertTrue("Fixture changes an ineffective legacy size instead of the grid font offset", fixture.contains("Ui.font_size_offset"))
        assertFalse("Manual style masks missing live settings notification", fixture.contains("\"style\""))
        for (check in listOf("beforeMetrics", "beforeGeometry", "characterWidth()", "lineHeight()"))
            assertTrue("Fixture does not prove effective metric/geometry transition: $check", fixture.contains(check))
    }

    @Test fun unicodeIsLiteralTypeInputAndConsumesUnsupportedModifierLatch() {
        val direct = activity.substringAfter("private fun directText(").substringBefore("private fun inputBackspace(")
        assertTrue(direct.contains("text[0].code in 32..126"))
        assertTrue(direct.contains("modifiers.clear(); bindKeys()"))
        assertTrue(direct.contains("client.typeInput(text"))
        val hardware = activity.substringAfter("val unicode = event.getUnicodeChar").substringBefore("input.setOnEditorActionListener")
        assertTrue("Hardware modifiers must use the same ASCII KEY boundary", hardware.contains("text[0].code in 32..126"))
    }

    @Test fun launcherFadeFixtureActuallyRefreshesAPopulatedDraft() {
        val test = File(root, "app/src/androidTest/java/ohi/andre/consolelauncher/BreathOutIntegrationTest.kt").readText()
            .substringAfter("fun everyModeUsesSameFadeBoundaryAndRetainsDraftThroughRefresh()")
            .substringBefore("@Test fun repeatedForegroundSignal")
        val seed = test.indexOf("setText(\"retained $" + "mode draft\")")
        assertTrue("The fade fixture refreshes before populating its draft", seed >= 0 && seed < test.indexOf("launcher.refreshUiInPlace()"))
        assertTrue(test.contains("selectionStart")); assertTrue(test.contains("selectionEnd"))
    }

    @Test fun workspaceOwnsTransparentFadeRootAndForegroundLifecycle() {
        assertTrue("Dedicated workspace lost Breath out", activity.contains("screen = BreathOutLayout(this)"))
        val create = activity.substringAfter("override fun onCreate(").substringBefore("override fun onNewIntent(")
        assertTrue(create.indexOf("window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)") in 0 until create.indexOf("setContentView(screen)"))
        assertTrue(create.contains("ColorDrawable(android.graphics.Color.TRANSPARENT)"))
        assertTrue(activity.contains("screen.setBackgroundColor"))
        assertTrue(activity.contains("Behavior.breath_out_seconds"))
        assertTrue(activity.contains("FLAG_SHOW_WALLPAPER"))
        assertTrue(activity.contains("fun refreshIfRunning()"))
        assertTrue(activity.contains("WeakReference(this)"))
        assertTrue(activity.substringAfter("fun refreshIfRunning()").substringBefore("fun open(").contains("activity.style()"))
        assertTrue(activity.substringAfter("override fun onPause()").substringBefore("override fun onSaveInstanceState").contains("screen.setActive(false)"))
        assertTrue(activity.contains("screen.setActive(visible && hasFocus)"))
        assertTrue(activity.substringAfter("private fun directText(").substringBefore("private fun inputBackspace(").contains("screen.wake()"))
    }
}
