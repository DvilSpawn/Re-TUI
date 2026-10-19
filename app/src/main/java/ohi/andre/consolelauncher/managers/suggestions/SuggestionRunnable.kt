package ohi.andre.consolelauncher.managers.suggestions

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.Gravity
import ohi.andre.consolelauncher.presentation.HomeAppearance
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.AppsManager.LaunchInfo
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.FrameTarget

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

internal class SuggestionChipStyler(private val context: Context, private val appsManager: AppsManager? = null) {
    constructor(pack: MainPack) : this(pack.context, pack.appsManager)

    private val appearance=ohi.andre.consolelauncher.presentation.HomeAppearance.saved()

    internal data class Binding(val type: Int,val backgroundOverride: Int=Int.MAX_VALUE,val textOverride: Int=Int.MAX_VALUE)
    internal object TrailingSpace

    companion object {
        fun spacing(appearance: HomeAppearance = HomeAppearance.saved()): IntArray {
            val parts = appearance.raw(Suggestions.suggestions_spaces).orEmpty()
                .replace(Regex("[\\[\\]\\s]"), "").split(',')
            return IntArray(4) { parts.getOrNull(it)?.toIntOrNull() ?: 0 }
        }

        fun createView(context: Context, spaces: IntArray = spacing()): TextView =
            createView(context, HomeAppearance.saved(), spaces)

        fun createView(context: Context, appearance: HomeAppearance, spaces: IntArray = spacing(appearance)): TextView =
            TextView(context).apply {
                typeface = appearance.font(context)
                LauncherFontScale.setBaseSize(this, appearance.number(Suggestions.suggestions_size), appearance.number(Ui.font_size_offset).toInt())
                setPadding(spaces[2], spaces[3], spaces[2], spaces[3])
                setLines(1)
                maxLines = 1
            }

        /** Existing trailing breathing room; its asymmetric margins also reserve the strip's height. */
        fun createTrailingSpace(context: Context, appearance: HomeAppearance): TextView = createView(context,appearance).apply {
            visibility=View.INVISIBLE
            setTag(R.id.home_suggestion_binding,TrailingSpace)
            val spaces=spacing(appearance)
            layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(spaces[0],spaces[1],spaces[2],spaces[3])
            }
        }

        fun layoutParams(appearance: HomeAppearance): LinearLayout.LayoutParams {
            val spaces = spacing(appearance)
            return LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL
                setMargins(spaces[0], spaces[1], spaces[0], spaces[1])
            }
        }

        internal fun styleDraft(view: TextView,binding: Binding,
            appearance: HomeAppearance, saved: HomeAppearance?=null) {
            view.setTag(R.id.home_suggestion_binding,binding)
            view.typeface = appearance.font(view.context)
            LauncherFontScale.setBaseSize(view, appearance.number(Suggestions.suggestions_size), appearance.number(Ui.font_size_offset).toInt())
            val spaces = spacing(appearance)
            view.setPadding(spaces[2], spaces[3], spaces[2], spaces[3])
            val shadow=appearance.color(ohi.andre.consolelauncher.managers.xml.options.Theme.suggestions_text_shadow_color)
            if(Color.alpha(shadow)==0) view.setShadowLayer(0f,0f,0f,Color.TRANSPARENT)
            else {
                val params=ohi.andre.consolelauncher.managers.settings.TextShadows.parameters(appearance.raw(Ui.shadow_params))
                view.setShadowLayer(params[2],params[0],params[1],shadow)
            }
            val bg=if(binding.backgroundOverride!=Int.MAX_VALUE) binding.backgroundOverride
                else appearance.rawColor(colorKey(binding.type,false))
            view.background=if(binding.backgroundOverride==Int.MAX_VALUE && appearance.bool(Suggestions.transparent_suggestions))
                ColorDrawable(Color.TRANSPARENT) else colorBackground(view.context,bg,appearance)
            view.setTextColor(if(binding.textOverride!=Int.MAX_VALUE) binding.textOverride else textColor(binding.type,appearance))
        }

        private fun colorBackground(context: Context,color: Int,appearance: ohi.andre.consolelauncher.presentation.HomeAppearance): Drawable {
            if(appearance.bool(ohi.andre.consolelauncher.managers.xml.options.Behavior.enable_cyberdeck_mode)
                || TerminalBorderRuntime.customFrameActive(context,FrameTarget.SUGGESTIONS)) {
                return TerminalBorderRuntime.panelDrawablePx(context,color,
                    appearance.color(ohi.andre.consolelauncher.managers.xml.options.Theme.terminal_border_color),
                    1f,0f,true,target=FrameTarget.SUGGESTIONS,
                    style=appearance.borderStyle().let { it.copy(stroke=it.stroke*(1f/1.5f)) })
            }
            return GradientDrawable().apply {
                shape=GradientDrawable.RECTANGLE
                cornerRadius=Tuils.dpToPx(context,appearance.radius(ohi.andre.consolelauncher.managers.xml.options.Ui.module_corner_radius)).toFloat()
                setColor(color)
            }
        }

        internal fun textColor(type: Int,appearance: ohi.andre.consolelauncher.presentation.HomeAppearance): Int {
            val chosen=appearance.rawColor(colorKey(type,true))
            return if(chosen==Int.MAX_VALUE) appearance.rawColor(Suggestions.default_text_color) else chosen
        }

        private fun colorKey(type: Int,text: Boolean): Suggestions = when(type) {
            SuggestionsManager.Suggestion.TYPE_APP, SuggestionsManager.Suggestion.TYPE_APPGP ->
                if(text) Suggestions.apps_text_color else Suggestions.apps_background_color
            SuggestionsManager.Suggestion.TYPE_ALIAS -> if(text) Suggestions.alias_text_color else Suggestions.alias_background_color
            SuggestionsManager.Suggestion.TYPE_COMMAND, SuggestionsManager.Suggestion.TYPE_MODULE ->
                if(text) Suggestions.cmd_text_color else Suggestions.cmd_background_color
            SuggestionsManager.Suggestion.TYPE_CONTACT, SuggestionsManager.Suggestion.TYPE_CONTACT_ROOT ->
                if(text) Suggestions.contact_text_color else Suggestions.contact_background_color
            SuggestionsManager.Suggestion.TYPE_FILE, SuggestionsManager.Suggestion.TYPE_CONFIGFILE, SuggestionsManager.Suggestion.TYPE_FILE_ACTION ->
                if(text) Suggestions.file_text_color else Suggestions.file_background_color
            SuggestionsManager.Suggestion.TYPE_SONG -> if(text) Suggestions.song_text_color else Suggestions.song_background_color
            else -> if(text) Suggestions.default_text_color else Suggestions.default_background_color
        }
    }

    fun apply(view: TextView, type: Int, payload: Any?) = styleDraft(view, binding(type, payload), appearance)

    fun binding(type: Int, payload: Any?): Binding {
        var bgColor=Int.MAX_VALUE
        var textColor=Int.MAX_VALUE
        var styledPayload=payload
        if(type==SuggestionsManager.Suggestion.TYPE_APP || type==SuggestionsManager.Suggestion.TYPE_APPGP) {
            if(styledPayload is LaunchInfo)
                styledPayload=appsManager?.groups?.firstOrNull { it.contains(styledPayload) } ?: styledPayload
            if(styledPayload is AppsManager.Group) { bgColor=styledPayload.bgColor;textColor=styledPayload.foreColor }
        }
        return Binding(type,bgColor,textColor)
    }

    fun suggestionTextColor(type: Int): Int = textColor(type,appearance)
}
