package ohi.andre.consolelauncher.managers.suggestions

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.AppsManager.LaunchInfo
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleCornerRadius
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings

/*Copyright Francesco Andreuzzi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

class SuggestionRunnable(
    pack: MainPack,
    private val suggestionsView: ViewGroup,
    suggestionViewParams: LinearLayout.LayoutParams,
    parent: HorizontalScrollView?,
    spaces: IntArray
) : Runnable {
    private val chipStyler = SuggestionChipStyler(pack)
    private val suggestionViewParams: LinearLayout.LayoutParams?

    private val scrollView: HorizontalScrollView?
    private val scrollRunnable: Runnable = object : Runnable {
        override fun run() {
            scrollView!!.fullScroll(View.FOCUS_LEFT)
        }
    }
    private var lastSuggestionSignature = ""

    private var n = 0
    private var toRecycle: Array<TextView?> = emptyArray()
    private var toAdd: Array<TextView?> = emptyArray()

    private var suggestions: MutableList<SuggestionsManager.Suggestion?>? = null

    private var interrupted = false

    init {
        this.suggestionViewParams = suggestionViewParams
        this.scrollView = parent

        suggestionViewParams.setMargins(spaces[0], spaces[1], spaces[0], spaces[1])

        reset()
    }

    fun setN(n: Int) {
        this.n = n
    }

    fun setToRecycle(toRecycle: Array<TextView?>) {
        this.toRecycle = toRecycle
    }

    fun setToAdd(toAdd: Array<TextView?>) {
        this.toAdd = toAdd
    }

    fun setSuggestions(suggestions: MutableList<SuggestionsManager.Suggestion?>?) {
        this.suggestions = suggestions
    }

    override fun run() {
        val nextSignature = suggestionSignature()
        val sameSuggestions = nextSignature == lastSuggestionSignature
        val previousScrollX = if (scrollView == null) 0 else scrollView.getScrollX()

        if (n < 0) {
            var count = toRecycle.size
            while (count < suggestionsView.getChildCount()) {
                suggestionsView.removeViewAt(count--)
                count++
            }
        }

        val length = suggestions!!.size

        for (count in suggestions!!.indices) {
            if (interrupted) {
                return
            }

            val s = suggestions!!.get(count) ?: continue

            val text = s.text

            var sggView: TextView? = null
            if (count < toRecycle.size) {
                sggView = toRecycle[count]
            } else {
                val space = length - (count + 1)
                if (space < toAdd.size) {
                    sggView = toAdd[space]

                    if (toAdd[space]!!.getParent() == null) {
                        suggestionsView.addView(toAdd[space], suggestionViewParams)
                    }
                }
            }

            if (sggView != null) {
                sggView.setTag(R.id.suggestion_id, s)

                sggView.setText(text)

                chipStyler.apply(sggView, s.type, s.`object`)
                if (s.type == SuggestionsManager.Suggestion.TYPE_CONTACT) {
                    sggView.setLongClickable(true)
                    (sggView.getContext() as Activity).registerForContextMenu(sggView)
                } else {
                    (sggView.getContext() as Activity).unregisterForContextMenu(sggView)
                }
            }
        }

        lastSuggestionSignature = nextSignature
        if (scrollView != null) {
            if (sameSuggestions) {
                scrollView.post(Runnable { scrollView.scrollTo(previousScrollX, 0) })
            } else {
                scrollView.post(scrollRunnable)
            }
        }
    }

    private fun suggestionSignature(): String {
        if (suggestions == null || suggestions!!.isEmpty()) {
            return ""
        }
        val out = StringBuilder()
        for (suggestion in suggestions) {
            if (suggestion == null) continue
            if (out.length > 0) out.append('\n')
            out.append(suggestion.type)
                .append('|')
                .append(if (suggestion.text == null) "" else suggestion.text)
                .append('|')
                .append(if (suggestion.`object` == null) "" else suggestion.`object`.toString())
        }
        return out.toString()
    }

    fun interrupt() {
        interrupted = true
    }

    fun reset() {
        interrupted = false
    }

}

internal class SuggestionChipStyler(private val pack: MainPack) {
    private val transparentSuggestions = XMLPrefsManager.getBoolean(Suggestions.transparent_suggestions)
    private val suggAppBg = XMLPrefsManager.getColor(Suggestions.apps_background_color)
    private val suggAliasBg = XMLPrefsManager.getColor(Suggestions.alias_background_color)
    private val suggCmdBg = XMLPrefsManager.getColor(Suggestions.cmd_background_color)
    private val suggContactBg = XMLPrefsManager.getColor(Suggestions.contact_background_color)
    private val suggFileBg = XMLPrefsManager.getColor(Suggestions.file_background_color)
    private val suggSongBg = XMLPrefsManager.getColor(Suggestions.song_background_color)
    private val suggDefaultBg = XMLPrefsManager.getColor(Suggestions.default_background_color)
    private val suggAppText = XMLPrefsManager.getColor(Suggestions.apps_text_color)
    private val suggAliasText = XMLPrefsManager.getColor(Suggestions.alias_text_color)
    private val suggCmdText = XMLPrefsManager.getColor(Suggestions.cmd_text_color)
    private val suggContactText = XMLPrefsManager.getColor(Suggestions.contact_text_color)
    private val suggFileText = XMLPrefsManager.getColor(Suggestions.file_text_color)
    private val suggSongText = XMLPrefsManager.getColor(Suggestions.song_text_color)
    private val suggDefaultText = XMLPrefsManager.getColor(Suggestions.default_text_color)

    fun apply(view: TextView, type: Int, payload: Any?) {
        var bgColor = Int.MAX_VALUE
        var textColor = Int.MAX_VALUE
        var styledPayload = payload
        if (type == SuggestionsManager.Suggestion.TYPE_APP || type == SuggestionsManager.Suggestion.TYPE_APPGP) {
            if (styledPayload is LaunchInfo) {
                styledPayload = pack.appsManager.groups.firstOrNull { it.contains(styledPayload) } ?: styledPayload
            }
            if (styledPayload is AppsManager.Group) {
                bgColor = styledPayload.bgColor
                textColor = styledPayload.foreColor
            }
        }
        view.background = if (bgColor == Int.MAX_VALUE) suggestionBg(type) else suggestionColorBg(bgColor)
        view.setTextColor(if (textColor == Int.MAX_VALUE) suggestionTextColor(type) else textColor)
    }

    private fun suggestionBg(type: Int): Drawable {
        if (transparentSuggestions) {
            return ColorDrawable(Color.TRANSPARENT)
        } else {
            when (type) {
                SuggestionsManager.Suggestion.TYPE_APP, SuggestionsManager.Suggestion.TYPE_APPGP -> return suggestionColorBg(suggAppBg)
                SuggestionsManager.Suggestion.TYPE_ALIAS -> return suggestionColorBg(suggAliasBg)
                SuggestionsManager.Suggestion.TYPE_COMMAND, SuggestionsManager.Suggestion.TYPE_MODULE -> return suggestionColorBg(suggCmdBg)
                SuggestionsManager.Suggestion.TYPE_CONTACT, SuggestionsManager.Suggestion.TYPE_CONTACT_ROOT -> return suggestionColorBg(suggContactBg)

                SuggestionsManager.Suggestion.TYPE_FILE,
                SuggestionsManager.Suggestion.TYPE_CONFIGFILE,
                SuggestionsManager.Suggestion.TYPE_FILE_ACTION -> return suggestionColorBg(suggFileBg)
                SuggestionsManager.Suggestion.TYPE_SONG -> return suggestionColorBg(suggSongBg)
                else -> return suggestionColorBg(suggDefaultBg)
            }
        }
    }

    private fun suggestionColorBg(color: Int): Drawable {
        val context = pack.context
        if (AppearanceSettings.cyberdeckMode() || TerminalBorderRuntime.customFrameActive(context, FrameTarget.SUGGESTIONS)) {
            return TerminalBorderRuntime.panelDrawable(
                context,
                color,
                AppearanceSettings.terminalBorderColor(),
                1.0f,
                0,
                true,
                target = FrameTarget.SUGGESTIONS
            )
        }

        val bg = GradientDrawable()
        bg.setShape(GradientDrawable.RECTANGLE)
        bg.setCornerRadius(Tuils.dpToPx(context, moduleCornerRadius()).toFloat())
        bg.setColor(color)
        return bg
    }

    fun suggestionTextColor(type: Int): Int {
        var chosen: Int

        when (type) {
            SuggestionsManager.Suggestion.TYPE_APP, SuggestionsManager.Suggestion.TYPE_APPGP -> chosen =
                suggAppText

            SuggestionsManager.Suggestion.TYPE_ALIAS -> chosen = suggAliasText
            SuggestionsManager.Suggestion.TYPE_COMMAND, SuggestionsManager.Suggestion.TYPE_MODULE -> chosen =
                suggCmdText

            SuggestionsManager.Suggestion.TYPE_CONTACT, SuggestionsManager.Suggestion.TYPE_CONTACT_ROOT -> chosen =
                suggContactText

            SuggestionsManager.Suggestion.TYPE_FILE,
            SuggestionsManager.Suggestion.TYPE_CONFIGFILE,
            SuggestionsManager.Suggestion.TYPE_FILE_ACTION -> chosen =
                suggFileText

            SuggestionsManager.Suggestion.TYPE_SONG -> chosen = suggSongText
            else -> chosen = suggDefaultText
        }

        if (chosen == Int.Companion.MAX_VALUE) chosen = suggDefaultText
        return chosen
    }
}
