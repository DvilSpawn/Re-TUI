package ohi.andre.consolelauncher.presentation

import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.SettingsPreviewFixture
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PassivePreviewInputDeviceTest {
    @Test fun appearanceRebindKeepsInputPassiveAndNeverCallsTheSourceOrOpensIme() = with(SettingsPreviewFixture) {
        withHome {
            val home = checkNotNull(LauncherActivity.instance)
            val persisted = appearanceState()
            var sourceClicks = 0
            var sourceEditorActions = 0
            lateinit var scene: ViewGroup
            lateinit var snapshot: LauncherHomeSnapshot
            instrumentation.runOnMainSync {
                val input = home.findViewById<TextView>(R.id.input_view)
                input.setOnClickListener { sourceClicks++ }
                input.setOnEditorActionListener { _, _, _ -> sourceEditorActions++; true }
                snapshot = checkNotNull(LauncherActivity.captureHomePreview())
                scene = snapshot.createDraft(home, snapshot.saved)
                home.addContentView(scene, FrameLayout.LayoutParams(snapshot.width, snapshot.height))
            }
            settle()
            try {
                repeat(3) { pass ->
                    instrumentation.runOnMainSync {
                        val changes = mapOf<XMLPrefsSave, String>(Ui.font_size_offset to (pass + 1).toString(),
                            Ui.input_output_size to (15 + pass).toString(), Ui.input_field_margins to "$pass,2,3,4")
                        snapshot.bindDraft(scene, snapshot.saved.overlay({ changes[it] }, changes::containsKey))
                        val input = checkNotNull(scene.findViewById<TextView>(R.id.input_view))
                        assertFalse("Preview input must never be an editable Android widget", input is EditText)
                        assertFalse(input.isFocusable)
                        assertFalse(input.isFocusableInTouchMode)
                        assertFalse(input.hasOnClickListeners())
                        assertFalse(input.isLongClickable)
                        assertFalse(input.onCheckIsTextEditor())
                        assertNull("Passive input must not expose an IME connection", input.onCreateInputConnection(EditorInfo()))
                        assertFalse("A read-only preview cannot acquire focus", input.requestFocus())
                        assertFalse("A read-only preview must not execute a click", input.performClick())
                        assertFalse(input.hasFocus())
                        assertEquals(0, sourceClicks)
                        assertEquals(0, sourceEditorActions)
                    }
                    settle()
                    instrumentation.runOnMainSync {
                        assertFalse("Inspecting preview input opened Android's keyboard",
                            ViewCompat.getRootWindowInsets(home.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true)
                    }
                    persisted.assertUnchanged()
                }
            } finally {
                instrumentation.runOnMainSync { (scene.parent as? ViewGroup)?.removeView(scene) }
            }
        }
    }
}
