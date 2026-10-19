package ohi.andre.consolelauncher.commands.tuixt

import android.graphics.Color
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.SettingsPreviewFixture
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPreviewHostLifecycleTest {
    @Test fun cardsStayReadableAndDraftsDoNotWriteSavedSettings() {
        with(SettingsPreviewFixture) {
            withHome {
                val editor = open(XMLPrefsManager.XMLPrefsRoot.THEME)
                try {
                    val before = appearanceState()
                    val cards = preview(editor)
                    instrumentation.runOnMainSync {
                        assertEquals(5, cards.pager.adapter!!.itemCount)
                        assertEquals(1, cards.childCount)
                        cards.pager.setCurrentItem(1, false)
                    }
                    settle()
                    search(editor, "output_text_color")
                    edit(editor, "#FF32AB67")
                    instrumentation.runOnMainSync {
                        assertEquals(Color.parseColor("#FF32AB67"), cards.findViewById<TextView>(R.id.terminal_view).currentTextColor)
                        cards.pager.setCurrentItem(2, false)
                    }
                    settle()
                    instrumentation.runOnMainSync {
                        assertNotNull(cards.findViewById<TextView>(R.id.music_song_title))
                        assertFalse(cards.findViewById<TextView>(R.id.music_play_pause).hasOnClickListeners())
                        cards.pager.setCurrentItem(3, false)
                    }
                    settle()
                    instrumentation.runOnMainSync {
                        assertNotNull(cards.findViewById<TextView>(R.id.offline_help_output))
                        assertFalse(cards.findViewById<TextView>(R.id.offline_help_input).isFocusable)
                        cards.pager.setCurrentItem(4, false)
                    }
                    settle()
                    instrumentation.runOnMainSync {
                        assertEquals("= 10", cards.findViewById<TextView>(R.id.calculator_preview).text.toString())
                        assertFalse(cards.findViewById<TextView>(R.id.calculator_equals).hasOnClickListeners())
                        assertFalse(cards.findViewById<TextView>(R.id.calculator_expression).isFocusable)
                    }
                    before.assertUnchanged()
                } finally { instrumentation.runOnMainSync { editor.finish() } }
            }
        }
    }
}
