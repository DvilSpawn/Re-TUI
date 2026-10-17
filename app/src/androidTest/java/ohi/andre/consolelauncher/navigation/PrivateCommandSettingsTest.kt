package ohi.andre.consolelauncher.navigation

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.commands.CommandTuils
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.Param
import ohi.andre.consolelauncher.commands.main.raw.`private`
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtAdapter
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class PrivateCommandSettingsTest {
    @Test fun noticeCancelConfirmSaveAndDisableControlPrivateCommand() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Only change settings in a disposable emulator", Build.HARDWARE == "ranchu")
        val setting = Behavior.enable_private_command
        Tuils.init(context)
        XMLPrefsManager.loadCommons(context)
        val original = XMLPrefsManager.get(setting)
        LauncherSettings.set(context, setting, "false")
        val activity = instrumentation.startActivitySync(Intent(context, TuixtActivity::class.java)
            .putExtra(TuixtActivity.PATH, File(Tuils.getFolder(), "behavior.xml").absolutePath)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TuixtActivity
        fun main(run: () -> Unit) = instrumentation.runOnMainSync(run)
        fun waitFor(label: String, ready: () -> Boolean) {
            val end = SystemClock.uptimeMillis() + 10000
            while (!ready() && SystemClock.uptimeMillis() < end) Thread.sleep(100)
            assertTrue(label, ready())
        }
        fun node(text: String): AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow
            ?.findAccessibilityNodeInfosByText(text)?.firstOrNull { it.text?.toString().equals(text, ignoreCase = true) }
        fun dialogClick(text: String) {
            waitFor("Dialog action: $text") { node(text)?.isClickable == true }
            assertTrue(node(text)!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
        }
        fun textView(view: View, text: String): TextView? {
            if (view is TextView && view.text.toString() == text) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) textView(view.getChildAt(i), text)?.let { return it }
            return null
        }
        fun save() {
            main { assertTrue(textView(activity.window.decorView,
                context.getString(R.string.editor_tuixtactivity_save_50815))!!.performClick()) }
            instrumentation.waitForIdleSync()
        }
        fun capture(name: String) {
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                File(context.filesDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        try {
            instrumentation.waitForIdleSync()
            val adapter = TuixtActivity::class.java.getDeclaredField("adapter").apply { isAccessible = true }
                .get(activity) as TuixtAdapter
            main { TuixtActivity::class.java.getDeclaredMethod("filter", String::class.java)
                .apply { isAccessible = true }.invoke(activity, "enable_private_command") }
            instrumentation.waitForIdleSync()
            fun toggle() {
                main { assertTrue(activity.findViewById<View>(R.id.setting_switch).performClick()) }
                instrumentation.waitForIdleSync()
            }
            toggle()
            waitFor("Compatibility notice shown") { node(context.getString(R.string.private_space_compatibility_title)) != null }
            assertNotNull(node(context.getString(R.string.private_space_compatibility_notice)))
            assertFalse(XMLPrefsManager.getBoolean(setting))
            assertFalse(adapter.hasPendingChanges())
            capture("private-command-notice")
            dialogClick(context.getString(android.R.string.cancel))
            assertFalse(adapter.hasPendingChanges())
            assertTrue(CommandTuils.isHiddenCommandName("private"))
            toggle()
            dialogClick(context.getString(R.string.private_space_enable_command))
            assertEquals("true", adapter.draftValue(setting))
            assertFalse("Approval stays a draft until Save", XMLPrefsManager.getBoolean(setting))
            save()
            assertTrue(XMLPrefsManager.getBoolean(setting))
            assertFalse(CommandTuils.isHiddenCommandName("private"))
            capture("private-command-enabled")
            toggle()
            assertTrue("Turning off also waits for Save", XMLPrefsManager.getBoolean(setting))
            save()
            assertFalse(XMLPrefsManager.getBoolean(setting))
            assertTrue(CommandTuils.isHiddenCommandName("private"))
            val pack = object : ExecutePack(CommandGroup("ohi.andre.consolelauncher.commands.main.raw")) {}
                .apply { this.context = context }
            val command = `private`()
            for (label in command.params()) {
                pack.set(arrayOf(object : Param {
                    override fun label(): String = label!!
                    override fun args(): IntArray = intArrayOf()
                    override fun exec(pack: ExecutePack): String = error("Disabled private action executed: $label")
                    override fun onNotArgEnough(pack: ExecutePack, n: Int): String? = null
                    override fun onArgNotFound(pack: ExecutePack, index: Int): String? = null
                }))
                assertEquals(context.getString(R.string.output_private_command_disabled), command.exec(pack))
            }
        } finally {
            main { activity.finish() }
            instrumentation.waitForIdleSync()
            LauncherSettings.set(context, setting, original)
        }
    }
}
