package ohi.andre.consolelauncher.managers.suggestions

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import it.andreuzzi.comparestring2.AlgMap
import it.andreuzzi.comparestring2.AlgMap.Alg
import it.andreuzzi.comparestring2.CompareObjects
import it.andreuzzi.comparestring2.CompareStrings
import it.andreuzzi.comparestring2.StringableObject
import it.andreuzzi.comparestring2.algs.interfaces.Algorithm
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.Command
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandTuils
import ohi.andre.consolelauncher.commands.CommandTuils.duoOptions
import ohi.andre.consolelauncher.commands.CommandTuils.isHiddenCommandName
import ohi.andre.consolelauncher.commands.CommandTuils.parse
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.Param
import ohi.andre.consolelauncher.commands.main.raw.tbridge
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.commands.main.specific.PermanentSuggestionCommand
import ohi.andre.consolelauncher.managers.AliasManager
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.AppsManager.Group.GroupLaunchInfo
import ohi.andre.consolelauncher.managers.AppsManager.LaunchInfo
import ohi.andre.consolelauncher.managers.ContactManager.Contact
import ohi.andre.consolelauncher.managers.FileManager.cd
import ohi.andre.consolelauncher.managers.PresetManager.listAllPresetNames
import ohi.andre.consolelauncher.managers.PresetManager.listBuiltInPresets
import ohi.andre.consolelauncher.managers.PresetManager.listPresets
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.file.FileBackendManager
import ohi.andre.consolelauncher.managers.file.FileBackendManager.activeBackend
import ohi.andre.consolelauncher.managers.modules.ModuleManager.displayTitle
import ohi.andre.consolelauncher.managers.modules.ModuleManager.getDock
import ohi.andre.consolelauncher.managers.modules.ModuleManager.isKnown
import ohi.andre.consolelauncher.managers.modules.ModuleManager.listAll
import ohi.andre.consolelauncher.managers.modules.ModuleManager.normalize
import ohi.andre.consolelauncher.managers.music.Song
import ohi.andre.consolelauncher.managers.notifications.reply.BoundApp
import ohi.andre.consolelauncher.managers.notifications.reply.ReplyManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.getBoolean
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache.dirs
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache.files
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache.shouldRequest
import ohi.andre.consolelauncher.managers.termux.TermuxAppManager
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager.dispatchShell
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetEngine
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.XMLPrefsRoot
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Apps
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Reply
import ohi.andre.consolelauncher.managers.xml.options.Rss
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.io.FilenameFilter
import java.util.Arrays
import java.util.Collections
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max
import android.os.Build
import java.util.ArrayList
import java.util.Comparator
import java.util.Iterator
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Map
import java.util.Set
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Matcher
import ohi.andre.consolelauncher.managers.ContactManager
import ohi.andre.consolelauncher.managers.FileManager
import ohi.andre.consolelauncher.managers.PresetManager
import ohi.andre.consolelauncher.managers.RssManager
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.WebhookManager
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.onboarding.GuideManager
import ohi.andre.consolelauncher.commands.CommandTuils.xmlPrefsEntrys
import ohi.andre.consolelauncher.commands.CommandTuils.xmlPrefsFiles

/**
 * Created by francescoandreuzzi on 25/12/15.
 */
class SuggestionsManager(
    private val suggestionsView: LinearLayout,
    private var pack: MainPack,
    private val mTerminalAdapter: TerminalManager
) {
    private var hideViewValue: HideSuggestionViewValues? = null

    private val FIRST_INTERVAL = 6

    private val SPLITTERS = arrayOf<String?>(Tuils.SPACE)
    private val FILE_SPLITTERS = arrayOf<String?>(Tuils.SPACE, "-", "_")
    private val XML_PREFS_SPLITTERS = arrayOf<String?>("_")
    private val showAliasDefault: Boolean
    private val clickToLaunch: Boolean
    private val showAppsGpDefault: Boolean
    private var enabled: Boolean
    private val minCmdPriority: Int

    private val multipleCmdSeparator: String

    private val doubleSpaceFirstSuggestion: Boolean
    private var suggestionRunnable: SuggestionRunnable? = null
    private var suggestionViewParams: LinearLayout.LayoutParams? = null
    private var lastFirst: Suggestion? = null

    private val luaSuggestionEngines = LinkedHashMap<String?, LuaWidgetEngine?>()
    private val suggestionRequestId = AtomicLong(0L)

    private val clickListener = View.OnClickListener { v: View? ->
        val suggestion = v!!.getTag(R.id.suggestion_id) as Suggestion
        clickSuggestion(suggestion)
    }

    private var lastSuggestionThread: StoppableThread? = null
    private val handler: Handler? = Handler()

    private val spaces: IntArray

    var counts: IntArray?
    var noInputCounts: IntArray?

    private val rmQuotes: Pattern = Pattern.compile("['\"]")

    var suggestionsPerCategory: Int
    var suggestionsDeadline: Float

    private val comparator: CustomComparator

    private var algInstance: Algorithm? = null
    private var alg: Alg? = null

    private val quickCompare: Int

    private fun setAlgorithm(id: Int) {
        when (id) {
            0 -> alg = AlgMap.DistAlg.LCS
            1 -> alg = AlgMap.DistAlg.OSA
            2 -> alg = AlgMap.DistAlg.QGRAM
            4 -> alg = AlgMap.NormDistAlg.COSINE
            5 -> alg = AlgMap.NormDistAlg.JACCARD
            6 -> alg = AlgMap.NormDistAlg.JAROWRINKLER
            7 -> alg = AlgMap.NormDistAlg.METRICLCS
            8 -> alg = AlgMap.NormDistAlg.NGRAM
            9 -> alg = AlgMap.NormDistAlg.NLEVENSHTEIN
            10 -> alg = AlgMap.NormDistAlg.SORENSENDICE
            11 -> alg = AlgMap.NormSimAlg.COSINE
            12 -> alg = AlgMap.NormSimAlg.JACCARD
            13 -> alg = AlgMap.NormSimAlg.JAROWRINKLER
            14 -> alg = AlgMap.NormSimAlg.NLEVENSHTEIN
            15 -> alg = AlgMap.NormSimAlg.SORENSENDICE
            16 -> alg = AlgMap.MetricDistAlg.DAMERAU
            17 -> alg = AlgMap.MetricDistAlg.JACCARD
            18 -> alg = AlgMap.MetricDistAlg.LEVENSHTEIN
            19 -> alg = AlgMap.MetricDistAlg.METRICLCS
        }

        algInstance = alg!!.buildAlg(id)
    }

    fun getSuggestionView(context: Context): TextView {
        val textView = TextView(context)
        textView.setOnClickListener(clickListener)

        textView.setFocusable(false)
        textView.setLongClickable(false)
        textView.setClickable(true)

        textView.setTypeface(Tuils.getTypeface(context))
        textView.setTextSize(XMLPrefsManager.getInt(Suggestions.suggestions_size).toFloat())

        textView.setPadding(spaces[2], spaces[3], spaces[2], spaces[3])

        textView.setLines(1)
        textView.setMaxLines(1)

        return textView
    }

    private fun stop() {
        handler!!.removeCallbacksAndMessages(null)
        if (lastSuggestionThread != null) lastSuggestionThread!!.interrupt()
    }

    private fun cancelPendingSuggestionRequests() {
        suggestionRequestId.incrementAndGet()
        stop()
    }

    private fun isLatestSuggestionRequest(requestId: Long): Boolean {
        return suggestionRequestId.get() == requestId
    }

    fun dispose() {
        cancelPendingSuggestionRequests()
    }

    fun clear() {
        cancelPendingSuggestionRequests()
        suggestionsView.removeAllViews()
    }

    var hideRunnable: Runnable = object : Runnable {
        override fun run() {
            suggestionsView.setVisibility(View.GONE)

            stop()
        }
    }

    fun hide() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            hideRunnable.run()
        } else {
            (mTerminalAdapter.mContext as Activity).runOnUiThread(hideRunnable)
        }
    }

    var showRunnable: Runnable = object : Runnable {
        override fun run() {
            suggestionsView.setVisibility(View.VISIBLE)
        }
    }

    init {
        setAlgorithm(XMLPrefsManager.getInt(Suggestions.suggestions_algorithm))

        quickCompare = XMLPrefsManager.getInt(Suggestions.suggestions_quickcompare_n)

        this.suggestionsPerCategory = XMLPrefsManager.getInt(Suggestions.suggestions_per_category)
        this.suggestionsDeadline = XMLPrefsManager.get(Suggestions.suggestions_deadline).toFloat()

        doubleSpaceFirstSuggestion =
            XMLPrefsManager.getBoolean(Suggestions.double_space_click_first_suggestion)
        Suggestion.Companion.appendQuotesBeforeFile =
            XMLPrefsManager.getBoolean(Behavior.append_quote_before_file)
        multipleCmdSeparator = XMLPrefsManager.get(Behavior.multiple_cmd_separator)

        enabled = true

        showAliasDefault = XMLPrefsManager.getBoolean(Suggestions.suggest_alias_default)
        showAppsGpDefault = XMLPrefsManager.getBoolean(Suggestions.suggest_appgp_default)
        clickToLaunch = XMLPrefsManager.getBoolean(Suggestions.click_to_launch)

        minCmdPriority = XMLPrefsManager.getInt(Suggestions.noinput_min_command_priority)

        spaces = XMLPrefsManager.getListOfIntValues(
            XMLPrefsManager.get(Suggestions.suggestions_spaces),
            4,
            0
        )

        try {
            hideViewValue = HideSuggestionViewValues.valueOf(
                XMLPrefsManager.get(Suggestions.hide_suggestions_when_empty).uppercase(
                    Locale.getDefault()
                )
            )
        } catch (e: Exception) {
            hideViewValue = HideSuggestionViewValues.valueOf(
                Suggestions.hide_suggestions_when_empty.defaultValue()!!.uppercase(
                    Locale.getDefault()
                )
            )
        }

        var s = XMLPrefsManager.get(Suggestions.suggestions_order)
        var orderPattern = Pattern.compile("(\\d+)\\((\\d+)\\)")
        var m = orderPattern.matcher(s)

        var indexes: IntArray? = IntArray(4)
        counts = IntArray(4)

        var index = 0
        while (m.find() && index < indexes!!.size) {
            val type = m.group(1).toInt()

            if (type >= indexes.size) {
                Tuils.sendOutput(Color.RED, pack.context, "Invalid suggestion type: " + type)

                indexes = null
                counts = null

                break
            }

            val count = m.group(2).toInt()

            indexes[type] = index
            counts!![type] = count

            index++
        }

        s = XMLPrefsManager.get(Suggestions.noinput_suggestions_order)
        orderPattern = Pattern.compile("(\\d+)\\((\\d+)\\)")
        m = orderPattern.matcher(s)

        var noInputIndexes: IntArray? = IntArray(4)
        noInputCounts = IntArray(4)

        index = 0
        while (m.find() && index < noInputIndexes!!.size) {
            val type = m.group(1).toInt()

            if (type >= noInputIndexes.size) {
                Tuils.sendOutput(Color.RED, pack.context, "Invalid suggestion type: " + type)

                noInputIndexes = null
                noInputCounts = null

                break
            }

            val count = m.group(2).toInt()

            noInputIndexes[type] = index
            noInputCounts!![type] = count

            index++
        }

        comparator = CustomComparator(noInputIndexes ?: IntArray(0), indexes ?: IntArray(0))

        val uselessView = getSuggestionView(pack.context)
        uselessView.setVisibility(View.INVISIBLE)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(spaces[0], spaces[1], spaces[2], spaces[3])

        (suggestionsView.getParent() as LinearLayout).addView(uselessView, params)
    }

    fun show() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            showRunnable.run()
        } else {
            (mTerminalAdapter.mContext as Activity).runOnUiThread(showRunnable)
        }
    }

    fun enable() {
        enabled = true

        show()
    }

    fun disable() {
        enabled = false

        clear()
        suggestionsView.setVisibility(View.GONE)
    }

    fun clickSuggestion(suggestion: Suggestion) {
        val execOnClick = suggestion.exec

        val text = suggestion.getText()
        if (suggestion.type == Suggestion.Companion.TYPE_MODULE && execOnClick) {
            mTerminalAdapter.executeQuietly(text, suggestion.`object`)
            return
        }

        val input = mTerminalAdapter.input

        if (suggestion.type == Suggestion.Companion.TYPE_PERMANENT) {
            mTerminalAdapter.input = input + text
        } else {
            val addSpace =
                suggestion.type != Suggestion.Companion.TYPE_FILE && suggestion.type != Suggestion.Companion.TYPE_COLOR

            if (multipleCmdSeparator.length > 0) {
//                try to understand if the user is using a multiple cmd
                val split =
                    input.split(multipleCmdSeparator.toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()

                //                not using it
                if (split.size <= 1) mTerminalAdapter.setInput(
                    text + (if (addSpace) Tuils.SPACE else Tuils.EMPTYSTRING),
                    suggestion.`object`
                )
                else {
                    split[split.size - 1] = Tuils.EMPTYSTRING

                    var beforeInputs = Tuils.EMPTYSTRING
                    for (count in 0..<split.size - 1) {
                        beforeInputs = beforeInputs + split[count] + multipleCmdSeparator
                    }

                    mTerminalAdapter.setInput(
                        beforeInputs + text + (if (addSpace) Tuils.SPACE else Tuils.EMPTYSTRING),
                        suggestion.`object`
                    )
                }
            } else {
                mTerminalAdapter.setInput(
                    text + (if (addSpace) Tuils.SPACE else Tuils.EMPTYSTRING),
                    suggestion.`object`
                )
            }
        }

        if (execOnClick) {
            mTerminalAdapter.simulateEnter()
        } else {
            mTerminalAdapter.focusInputEnd()
        }
    }

    fun requestSuggestion(input: String) {
        if (!enabled) return
        val requestId = suggestionRequestId.incrementAndGet()

        if (suggestionViewParams == null) {
            suggestionViewParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            suggestionViewParams!!.setMargins(15, 0, 15, 0)
            suggestionViewParams!!.gravity = Gravity.CENTER_VERTICAL
        }

        if (suggestionRunnable == null) {
            suggestionRunnable = SuggestionRunnable(
                pack,
                suggestionsView,
                suggestionViewParams!!,
                suggestionsView.getParent().getParent() as HorizontalScrollView?,
                spaces
            )
        }

        if (lastSuggestionThread != null) {
            lastSuggestionThread!!.interrupt()
            suggestionRunnable!!.interrupt()
            if (handler != null) {
                handler.removeCallbacks(suggestionRunnable!!)
            }
        }

        try {
            val l = input.length
            if (doubleSpaceFirstSuggestion && l > 0 && input.get(l - 1) == ' ') {
                if (input.get(l - 2) == ' ') {
//                    double space
                    if (lastFirst == null && suggestionsView.getChildCount() > 0) {
                        val s =
                            suggestionsView.getChildAt(0).getTag(R.id.suggestion_id) as Suggestion
                        if (!input.trim { it <= ' ' }.endsWith(s.getText()!!)) lastFirst = s
                    }

                    if (lastFirst != null) {
                        val s = lastFirst
                        mTerminalAdapter.input =
                            if (0 == l - 2) Tuils.EMPTYSTRING else input.substring(0, l - 2)
                        clickSuggestion(s!!)
                        return
                    }
                } else if (suggestionsView.getChildCount() > 0) {
//                    single space
                    lastFirst =
                        suggestionsView.getChildAt(0).getTag(R.id.suggestion_id) as Suggestion?
                    if (lastFirst!!.getText() == input.trim { it <= ' ' }) {
                        lastFirst = null
                    }
                }
            } else {
                lastFirst = null
            }
        } catch (e: Exception) {
//            this will trigger an error when there's a single space in the input field, but it's not a problem
            Tuils.log(e)
            Tuils.toFile(e)
        }

        lastSuggestionThread = object : StoppableThread() {
            override fun run() {
                super.run()

                val before: String?
                val lastWord: String?
                val lastInput: String?
                if (multipleCmdSeparator.length > 0) {
                    val split =
                        input.split(multipleCmdSeparator.toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    if (split.size == 0) lastInput = input
                    else lastInput = split[split.size - 1]
                } else {
                    lastInput = input
                }

                val lastSpace = lastInput.lastIndexOf(Tuils.SPACE)
                if (lastSpace == -1) {
                    before = Tuils.EMPTYSTRING
                    lastWord = lastInput
                } else {
                    before = lastInput.substring(0, lastSpace)
                    lastWord = lastInput.substring(lastSpace + 1, lastInput.length)
                }

                val suggestions: MutableList<Suggestion?>
                try {
                    suggestions = getSuggestions(before, lastWord)
                } catch (e: Exception) {
                    Tuils.log(e)
                    Tuils.toFile(e)
                    return
                }

                if (!isLatestSuggestionRequest(requestId) || interrupted()) {
                    suggestionRunnable!!.interrupt()
                    return
                }

                if (suggestions.size == 0) {
                    (pack.context as Activity).runOnUiThread(Runnable {
                        if (isLatestSuggestionRequest(requestId)) {
                            suggestionsView.removeAllViews()
                        }
                    })

                    if (hideViewValue == HideSuggestionViewValues.ALWAYS || (hideViewValue == HideSuggestionViewValues.TRUE && input.length == 0)) {
                        hide()
                    }

                    return
                } else {
                    show()
                }

                if (interrupted()) {
                    suggestionRunnable!!.interrupt()
                    return
                }

                val existingViews = arrayOfNulls<TextView>(suggestionsView.getChildCount())
                for (count in existingViews.indices) {
                    existingViews[count] = suggestionsView.getChildAt(count) as TextView?
                }

                if (interrupted()) {
                    suggestionRunnable!!.interrupt()
                    return
                }

                val n = suggestions.size - existingViews.size
                var toAdd: Array<TextView?>? = null
                var toRecycle: Array<TextView?>? = null
                if (n == 0) {
                    toRecycle = existingViews
                    toAdd = emptyArray()
                } else if (n > 0) {
                    toRecycle = existingViews
                    toAdd = arrayOfNulls<TextView>(n)
                    for (count in toAdd.indices) {
                        toAdd[count] = getSuggestionView(pack.context)
                    }
                } else if (n < 0) {
                    toAdd = emptyArray()
                    toRecycle = arrayOfNulls<TextView>(suggestions.size)
                    System.arraycopy(existingViews, 0, toRecycle, 0, toRecycle.size)
                }

                if (interrupted()) {
                    suggestionRunnable!!.interrupt()
                    return
                }

                suggestionRunnable!!.setN(n)
                suggestionRunnable!!.setSuggestions(suggestions)
                suggestionRunnable!!.setToAdd(toAdd!!)
                suggestionRunnable!!.setToRecycle(toRecycle!!)
                suggestionRunnable!!.reset()
                (pack.context as Activity).runOnUiThread(Runnable {
                    if (isLatestSuggestionRequest(requestId)) {
                        suggestionRunnable!!.run()
                    }
                })
            }
        }

        try {
            lastSuggestionThread!!.start()
        } catch (e: InternalError) {
            Tuils.log(e)
            Tuils.toFile(e)
        }
    }

    //    there's always a space between beforelastspace and lastword
    fun getSuggestions(beforeLastSpace: String, lastWord: String): MutableList<Suggestion?> {
        var beforeLastSpace = beforeLastSpace
        var lastWord = lastWord
        val suggestionList: MutableList<Suggestion?> = ArrayList<Suggestion?>()

        beforeLastSpace = beforeLastSpace.trim { it <= ' ' }
        lastWord = lastWord.trim { it <= ' ' }

        if (GuideManager.isActive(pack.context)) {
            comparator.noInput = beforeLastSpace.length == 0 && lastWord.length == 0
            if (!suggestActiveGuide(suggestionList)) {
                suggestGuideRootActions(suggestionList)
            }
            Collections.sort<Suggestion?>(suggestionList, comparator)
            return suggestionList
        }

        //        lastword = 0
        if (lastWord.length == 0) {
            //            lastword = 0 && beforeLastSpace = 0

            if (beforeLastSpace.length == 0) {
                comparator.noInput = true

                suggestOrientationCommand(suggestionList)

                val apps = pack.appsManager.suggestedApps
                if (apps != null) {
                    var count = 0
                    while (count < apps.size && count < noInputCounts!![Suggestion.Companion.TYPE_APP]) {
                        if (apps[count] == null) {
                            count++
                            continue
                        }

                        suggestionList.add(
                            SuggestionsManager.Suggestion(
                                beforeLastSpace,
                                apps[count]!!.publicLabel!!,
                                clickToLaunch,
                                Suggestion.Companion.TYPE_APP,
                                apps[count]
                            )
                        )
                        count++
                    }
                }

                suggestFirstPresetAction(suggestionList)
                suggestCommand(pack, suggestionList, null)

                if (showAliasDefault) suggestAlias(pack.aliasManager, suggestionList, lastWord)
                if (showAppsGpDefault) suggestAppGroup(
                    pack,
                    suggestionList,
                    lastWord,
                    beforeLastSpace
                )
            } else {
                comparator.noInput = false

                if (isHelpQuickstart(beforeLastSpace)) {
                    suggestHelpQuickstartActions(suggestionList)
                    Collections.sort<Suggestion?>(suggestionList, comparator)
                    return suggestionList
                }

                if (isGuideCommand(beforeLastSpace)) {
                    suggestGuideSubcommands(suggestionList)
                    Collections.sort<Suggestion?>(suggestionList, comparator)
                    return suggestionList
                }

                if (suggestContactsFlow(pack, suggestionList, beforeLastSpace, lastWord)) {
                    Collections.sort<Suggestion?>(suggestionList, comparator)
                    return suggestionList
                }

                //                check if this is a command
                var cmd: Command? = null
                try {
                    cmd = parse(beforeLastSpace, pack)
                } catch (e: Exception) {
                }

                if (cmd != null) {
                    if (cmd.cmd is PermanentSuggestionCommand) {
                        suggestPermanentSuggestions(
                            suggestionList,
                            cmd.cmd as PermanentSuggestionCommand
                        )
                    }

                    if (isFileOpenCommand(beforeLastSpace)) {
                        suggestOpenableFile(pack, suggestionList, null, beforeLastSpace)
                        Collections.sort<Suggestion?>(suggestionList, comparator)
                        return suggestionList
                    }

                    if (cmd.mArgs != null && cmd.mArgs!!.size > 0 && cmd.cmd is ParamCommand && cmd.nArgs >= 1 && cmd.mArgs!![0] is Param && (cmd.mArgs!![0] as Param).args()!!.size + 1 == cmd.nArgs) {
//                        nothing
                    } else {
                        if (cmd.cmd is ParamCommand && (cmd.mArgs == null || cmd.mArgs!!.size == 0 || cmd.mArgs!![0] is String)) suggestParams(
                            pack,
                            suggestionList,
                            cmd.cmd as ParamCommand,
                            beforeLastSpace,
                            null
                        )
                        else suggestArgs(pack, cmd.nextArg(), suggestionList, beforeLastSpace)
                    }
                } else {
                    val split = rmQuotes.matcher(beforeLastSpace).replaceAll(Tuils.EMPTYSTRING)
                        .split(Tuils.SPACE.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    var isShellCmd = false
                    for (s in split) {
                        if (needsFileSuggestion(s)) {
                            isShellCmd = true
                            break
                        }
                    }

                    if (isShellCmd) {
                        suggestFile(pack, suggestionList, Tuils.EMPTYSTRING, beforeLastSpace)
                    } else {
//                        ==> app
                        if (!suggestAppInsideGroup(
                                pack,
                                suggestionList,
                                Tuils.EMPTYSTRING,
                                beforeLastSpace,
                                false
                            )
                        ) suggestApp(
                            pack,
                            suggestionList,
                            beforeLastSpace + Tuils.SPACE,
                            Tuils.EMPTYSTRING
                        )
                    }
                }
            }
        } else {
            comparator.noInput = false

            if (beforeLastSpace.length > 0) {
//                lastword > 0 && beforeLastSpace  > 0
                if (suggestContactsFlow(pack, suggestionList, beforeLastSpace, lastWord)) {
                    Collections.sort<Suggestion?>(suggestionList, comparator)
                    return suggestionList
                }

                var cmd: Command? = null
                try {
                    cmd = parse(beforeLastSpace, pack)
                } catch (e: Exception) {
                }

                if (cmd != null) {
                    if (cmd.cmd is PermanentSuggestionCommand) {
                        suggestPermanentSuggestions(
                            suggestionList,
                            cmd.cmd as PermanentSuggestionCommand
                        )
                    }

                    if (isFileOpenCommand(beforeLastSpace)) {
                        suggestOpenableFile(pack, suggestionList, lastWord, beforeLastSpace)
                        Collections.sort<Suggestion?>(suggestionList, comparator)
                        return suggestionList
                    }

                    //                    if (cmd.cmd.maxArgs() == 1 && beforeLastSpace .contains(Tuils.SPACE)) {
//                        int index = cmd.cmd.getClass().getSimpleName().length() + 1;
//
//                        lastWord = beforeLastSpace .substring(index) + lastWord;
//                    }
                    if (cmd.cmd is ParamCommand && (cmd.mArgs == null || cmd.mArgs!!.size == 0 || cmd.mArgs!![0] is String)) {
                        suggestParams(
                            pack,
                            suggestionList,
                            cmd.cmd as ParamCommand,
                            beforeLastSpace,
                            lastWord
                        )
                    } else suggestArgs(
                        pack,
                        cmd.nextArg(),
                        suggestionList,
                        lastWord,
                        beforeLastSpace
                    )
                } else {
                    val split = beforeLastSpace.replace("['\"]".toRegex(), Tuils.EMPTYSTRING)
                        .split(Tuils.SPACE.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    var isShellCmd = false
                    for (s in split) {
                        if (needsFileSuggestion(s)) {
                            isShellCmd = true
                            break
                        }
                    }

                    if (isShellCmd) {
                        suggestFile(pack, suggestionList, lastWord, beforeLastSpace)
                    } else {
                        if (!suggestAppInsideGroup(
                                pack,
                                suggestionList,
                                lastWord,
                                beforeLastSpace,
                                false
                            )
                        ) suggestApp(
                            pack,
                            suggestionList,
                            beforeLastSpace + Tuils.SPACE + lastWord,
                            Tuils.EMPTYSTRING
                        )
                    }
                }

                //                lastword > 0 && beforeLastSpace  = 0
            } else {
                if (isHelpQuickstart(lastWord)) {
                    suggestHelpQuickstartActions(suggestionList)
                    Collections.sort<Suggestion?>(suggestionList, comparator)
                    return suggestionList
                }

                if (isGuideCommand(lastWord)) {
                    suggestGuideRootActions(suggestionList)
                    Collections.sort<Suggestion?>(suggestionList, comparator)
                    return suggestionList
                }

                suggestCommand(pack, suggestionList, lastWord, beforeLastSpace)
                suggestLuaScripts(suggestionList, lastWord)
                suggestAlias(pack.aliasManager, suggestionList, lastWord)
                suggestApp(pack, suggestionList, lastWord, Tuils.EMPTYSTRING)
                suggestAppGroup(pack, suggestionList, lastWord, beforeLastSpace)
                suggestClockCommandRoots(suggestionList, lastWord)
            }
        }

        Collections.sort<Suggestion?>(suggestionList, comparator)
        return suggestionList
    }

    private fun suggestOrientationCommand(suggestions: MutableList<Suggestion?>) {
        suggestions.add(Suggestion(null, "orientation", false, Suggestion.Companion.TYPE_PERMANENT))
    }

    private fun needsFileSuggestion(cmd: String): Boolean {
        return cmd.equals("ls", ignoreCase = true) || cmd.equals(
            "cd",
            ignoreCase = true
        ) || cmd.equals("mv", ignoreCase = true) || cmd.equals(
            "cp",
            ignoreCase = true
        ) || cmd.equals("rm", ignoreCase = true) || cmd.equals("cat", ignoreCase = true)
    }

    private fun suggestLuaScripts(suggestions: MutableList<Suggestion?>, query: String?) {
        val safeQuery = if (query == null) Tuils.EMPTYSTRING else query.trim { it <= ' ' }
        if (safeQuery.length == 0) {
            return
        }
        var added = 0
        for (id in LuaWidgetManager.listIds()) {
            try {
                val script = LuaWidgetManager.readScript(id)
                val type = LuaWidgetManager.metadata(script).get("type")
                if (!"suggest".equals(type, ignoreCase = true) && !"command".equals(
                        type,
                        ignoreCase = true
                    )
                ) {
                    continue
                }
                if (!LuaWidgetManager.isEnabled(id)) {
                    continue
                }
                if (!LuaWidgetManager.isTrusted(id)) {
                    continue
                }
                val engine = luaSuggestionEngine(id, script)
                val result = engine.suggest(safeQuery)
                for (action in result.suggestions) {
                    val renderAction = action ?: continue
                    suggestions.add(
                        Suggestion(
                            null,
                            renderAction.label ?: renderAction.command ?: Tuils.EMPTYSTRING,
                            true,
                            Suggestion.Companion.TYPE_MODULE,
                            renderAction.command
                        )
                    )
                    added++
                    if (added >= 12) {
                        return
                    }
                }
            } catch (e: Exception) {
                Tuils.log(e)
            }
        }
    }

    @Synchronized
    private fun luaSuggestionEngine(id: String?, script: String?): LuaWidgetEngine {
        val normalized = LuaWidgetManager.normalizeId(id)
        val version = LuaWidgetManager.version(normalized)
        var engine = luaSuggestionEngines.get(normalized)
        if (engine == null || engine.version() != version) {
            engine = LuaWidgetEngine(pack.context, normalized, script, version, null)
            luaSuggestionEngines.put(normalized, engine)
            while (luaSuggestionEngines.size > MAX_LUA_SUGGESTION_ENGINES) {
                val iterator: MutableIterator<MutableMap.MutableEntry<String?, LuaWidgetEngine?>?> =
                    luaSuggestionEngines.entries.iterator()
                if (!iterator.hasNext()) {
                    break
                }
                iterator.next()
                iterator.remove()
            }
        }
        return engine
    }

    private fun isHelpQuickstart(value: String?): Boolean {
        return value != null && "help".equals(value.trim { it <= ' ' }, ignoreCase = true)
    }

    private fun isGuideCommand(value: String?): Boolean {
        return value != null && "guide".equals(value.trim { it <= ' ' }, ignoreCase = true)
    }

    private fun suggestHelpQuickstartActions(suggestions: MutableList<Suggestion?>) {
        suggestions.add(Suggestion(null, "apps -ls", true, Suggestion.Companion.TYPE_PERMANENT))
        suggestions.add(Suggestion(null, "alias -add", false, Suggestion.Companion.TYPE_PERMANENT))
        suggestions.add(Suggestion(null, "apps -hide", false, Suggestion.Companion.TYPE_PERMANENT))
        suggestions.add(
            Suggestion(
                null,
                "wallpaper -auto",
                true,
                Suggestion.Companion.TYPE_PERMANENT
            )
        )
        suggestions.add(
            Suggestion(
                null,
                "preset -save",
                false,
                Suggestion.Companion.TYPE_PERMANENT
            )
        )
        suggestions.add(Suggestion(null, "module -ls", true, Suggestion.Companion.TYPE_PERMANENT))
    }

    private fun suggestActiveGuide(suggestions: MutableList<Suggestion?>): Boolean {
        val guideSuggestions = GuideManager.activeSuggestions(pack.context)
        if (guideSuggestions.isEmpty()) {
            return false
        }
        for (suggestion in guideSuggestions) {
            suggestions.add(
                Suggestion(
                    null,
                    suggestion.command,
                    suggestion.execute,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
        }
        return true
    }

    private fun suggestGuideRootActions(suggestions: MutableList<Suggestion?>) {
        for (suggestion in GuideManager.rootSuggestions(pack.context)) {
            suggestions.add(
                Suggestion(
                    null,
                    suggestion.command,
                    suggestion.execute,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
        }
    }

    private fun suggestGuideSubcommands(suggestions: MutableList<Suggestion?>) {
        for (suggestion in GuideManager.subcommandSuggestions()) {
            suggestions.add(Suggestion(null, suggestion, false, Suggestion.Companion.TYPE_PERMANENT))
        }
    }

    private fun suggestFirstPresetAction(suggestions: MutableList<Suggestion?>) {
        try {
            if (listPresets().isEmpty()) {
                suggestions.add(
                    Suggestion(
                        null,
                        "wallpaper -auto",
                        true,
                        Suggestion.Companion.TYPE_COMMAND
                    )
                )
            }
        } catch (e: Exception) {
            Tuils.log(e)
        }
    }

    private fun suggestContactsFlow(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?,
        lastWord: String?
    ): Boolean {
        val before =
            if (beforeLastSpace == null) Tuils.EMPTYSTRING else beforeLastSpace.trim { it <= ' ' }
        val last = if (lastWord == null) Tuils.EMPTYSTRING else lastWord.trim { it <= ' ' }
        if (before.length == 0) {
            return false
        }

        val command = firstWord(before)
        if (!isContactsCommand(command)) {
            return false
        }

        val selectedOrPartial = afterFirstWord(before)
        if (selectedOrPartial.startsWith("-")) {
            return false
        }

        var query = selectedOrPartial
        if (last.length > 0) {
            query = if (query.length == 0) last else query + Tuils.SPACE + last
        }

        if (last.length == 0 && selectedOrPartial.length > 0) {
            val contact = findExactContact(info.contacts.getContacts(), selectedOrPartial)
            if (contact != null && addContactActions(suggestions, contact)) {
                return true
            }
        }

        suggestContactsForQuery(info, suggestions, command, query)
        return suggestions.size > 0
    }

    private fun firstWord(value: String): String {
        val space = value.indexOf(Tuils.SPACE)
        return if (space == -1) value else value.substring(0, space)
    }

    private fun afterFirstWord(value: String): String {
        val space = value.indexOf(Tuils.SPACE)
        return if (space == -1) Tuils.EMPTYSTRING else value.substring(space + 1).trim { it <= ' ' }
    }

    private fun isContactsCommand(value: String?): Boolean {
        return value != null && ("contacts".equals(value, ignoreCase = true) || "cntcts".equals(
            value,
            ignoreCase = true
        ))
    }

    private fun suggestContactsForQuery(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        command: String?,
        query: String?
    ) {
        val contacts: MutableList<Contact> = info.contacts.getContacts()
        if (contacts == null || contacts.size == 0) {
            return
        }

        val filter = if (query == null) Tuils.EMPTYSTRING else query.trim { it <= ' ' }
        val lower = filter.lowercase(Locale.getDefault())
        val max = max(1, suggestionsPerCategory)
        var added = 0

        for (contact in contacts) {
            if (added >= max) {
                return
            }
            if (lower.length == 0 || contact.lowercaseString.startsWith(lower)) {
                suggestions.add(
                    Suggestion(
                        command,
                        contact.string,
                        false,
                        Suggestion.Companion.TYPE_CONTACT_ROOT,
                        contact
                    )
                )
                added++
            }
        }

        if (added >= max || lower.length == 0) {
            return
        }

        val matches: Array<Contact?> = CompareObjects.topMatchesWithDeadline(
            Contact::class.java,
            filter,
            contacts.size,
            contacts,
            max - added,
            suggestionsDeadline,
            SPLITTERS,
            algInstance,
            alg
        )
        for (contact in matches) {
            if (contact == null) {
                break
            }
            suggestions.add(
                Suggestion(
                    command,
                    contact.string,
                    false,
                    Suggestion.Companion.TYPE_CONTACT_ROOT,
                    contact
                )
            )
        }
    }

    private fun addContactActions(
        suggestions: MutableList<Suggestion?>,
        contact: Contact?
    ): Boolean {
        if (contact == null || contact.numbers == null || contact.numbers.size == 0) {
            return false
        }

        var selected = contact.selectedNumber
        if (selected >= contact.numbers.size) {
            selected = 0
        }
        val number = contact.numbers.get(selected)

        suggestions.add(Suggestion(null, "call " + number, true, Suggestion.Companion.TYPE_COMMAND))
        suggestions.add(
            Suggestion(
                null,
                "contacts -l " + number,
                true,
                Suggestion.Companion.TYPE_COMMAND
            )
        )
        suggestions.add(
            Suggestion(
                null,
                "contacts -edit " + number,
                true,
                Suggestion.Companion.TYPE_COMMAND
            )
        )
        return true
    }


    private fun suggestPermanentSuggestions(
        suggestions: MutableList<Suggestion?>,
        cmd: PermanentSuggestionCommand
    ) {
        for (s in cmd.permanentSuggestions()!!) {
            val sugg = Suggestion(null, s, false, Suggestion.Companion.TYPE_PERMANENT)
            suggestions.add(sugg)
        }
    }

    private fun suggestAlias(
        aliasManager: AliasManager,
        suggestions: MutableList<Suggestion?>,
        lastWord: String?
    ) {
        var canInsert =
            if (lastWord == null || lastWord.length == 0) noInputCounts!![Suggestion.Companion.TYPE_ALIAS] else counts!![Suggestion.Companion.TYPE_ALIAS]

        for (a in aliasManager.getAliases(true, AliasManager.SCOPE_APP)) {
            if (lastWord!!.length == 0 || a!!.name.startsWith(lastWord)) {
                if (canInsert == 0) return
                canInsert--

                suggestions.add(
                    Suggestion(
                        Tuils.EMPTYSTRING,
                        a!!.name,
                        clickToLaunch && !a.isParametrized,
                        Suggestion.Companion.TYPE_ALIAS
                    )
                )
            }
        }
    }

    private fun suggestParams(
        pack: MainPack,
        suggestions: MutableList<Suggestion?>,
        cmd: ParamCommand,
        beforeLastSpace: String?,
        lastWord: String?
    ) {
        val params: Array<out String?> = cmd.params()
        if (params == null) {
            return
        }

        if (lastWord == null || lastWord.length == 0) {
            for (s in cmd.params()) {
                val p = cmd.getParam(pack, s!!).value
                if (p == null) continue

                suggestions.add(
                    SuggestionsManager.Suggestion(
                        beforeLastSpace,
                        s,
                        p.args()!!.size == 0 && clickToLaunch,
                        0
                    )
                )
            }
        } else {
            for (s in cmd.params()) {
                val p = cmd.getParam(pack, s!!).value
                if (p == null) continue

                if (s.startsWith(lastWord) || s.replace("-", Tuils.EMPTYSTRING)
                        .startsWith(lastWord)
                ) {
                    suggestions.add(
                        SuggestionsManager.Suggestion(
                            beforeLastSpace,
                            s,
                            p.args()!!.size == 0 && clickToLaunch,
                            0
                        )
                    )
                }
            }
        }
    }

    private fun suggestArgs(
        info: MainPack,
        type: Int,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String
    ) {
        when (type) {
            CommandAbstraction.FILE -> suggestFile(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.VISIBLE_PACKAGE -> suggestApp(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.COMMAND -> suggestCommand(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.CONTACTNUMBER -> suggestContact(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.SONG -> suggestSong(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.BOOLEAN -> suggestBoolean(suggestions, beforeLastSpace)
            CommandAbstraction.HIDDEN_PACKAGE -> suggestHiddenApp(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.COLOR -> suggestColor(suggestions, afterLastSpace, beforeLastSpace)
            CommandAbstraction.CONFIG_ENTRY -> suggestConfigEntry(
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.CONFIG_FILE -> suggestConfigFile(
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.DEFAULT_APP -> suggestDefaultApp(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.ALL_PACKAGES -> suggestAllPackages(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.APP_GROUP -> suggestAppGroup(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.APP_INSIDE_GROUP -> suggestAppInsideGroup(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace,
                true
            )

            CommandAbstraction.BOUND_REPLY_APP -> suggestBoundReplyApp(
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.DATASTORE_PATH_TYPE -> suggestDataStoreType(
                suggestions,
                beforeLastSpace
            )

            CommandAbstraction.THEME_PRESET -> suggestThemePresets(
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.PRESET_NAME -> suggestSavedPresetNames(
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.TEXTLIST -> suggestWebhookHistory(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )
        }

        suggestClockCommandArgs(info, suggestions, afterLastSpace, beforeLastSpace)
    }

    private fun suggestClockCommandRoots(suggestions: MutableList<Suggestion?>, lastWord: String?) {
        if (lastWord == null) {
            return
        }

        val lower = lastWord.lowercase(Locale.getDefault())
        if ("timer".startsWith(lower)) {
            suggestions.add(
                Suggestion(
                    null,
                    "timer -add",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "timer -stop",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "timer -status",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            for (quick in this.timerQuickSuggestions) {
                suggestions.add(
                    Suggestion(
                        null,
                        "timer " + quick,
                        true,
                        Suggestion.Companion.TYPE_PERMANENT
                    )
                )
            }
        }

        if ("stopwatch".startsWith(lower)) {
            suggestions.add(
                Suggestion(
                    null,
                    "stopwatch",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            for (option in arrayOf<String>(
                "stopwatch -stop",
                "stopwatch -reset",
                "stopwatch -status"
            )) {
                suggestions.add(Suggestion(null, option, true, Suggestion.Companion.TYPE_PERMANENT))
            }
        }

        if ("termux".startsWith(lower)) {
            suggestions.add(Suggestion(null, "termux", true, Suggestion.Companion.TYPE_PERMANENT))
            suggestions.add(
                Suggestion(
                    null,
                    "termux -status",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -setup",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -open",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -apps",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -run",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -app",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -app-add",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -add-app",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -app-info",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -app-sync",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -app-action",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -app-actions",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -app-action-rm",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -app-rm",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -rm-app",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
        }

        suggestTermuxAppRoots(suggestions, lower)

        if ("lua".startsWith(lower)) {
            suggestions.add(Suggestion(null, "lua -apps", true, Suggestion.Companion.TYPE_PERMANENT))
            suggestions.add(Suggestion(null, "lua -new app", false, Suggestion.Companion.TYPE_PERMANENT))
            suggestions.add(Suggestion(null, "lua -app", false, Suggestion.Companion.TYPE_PERMANENT))
            suggestions.add(Suggestion(null, "lua -edit", false, Suggestion.Companion.TYPE_PERMANENT))
            suggestions.add(Suggestion(null, "lua -check", false, Suggestion.Companion.TYPE_PERMANENT))
        }

        if ("tbridge".startsWith(lower)) {
            suggestions.add(
                Suggestion(
                    null,
                    "tbridge -status",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "tbridge -setup",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "tbridge -probe",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
        }

        if ("shell".startsWith(lower)) {
            suggestions.add(Suggestion(null, "shell", false, Suggestion.Companion.TYPE_PERMANENT))
            suggestions.add(
                Suggestion(
                    null,
                    "shell pwd",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(Suggestion(null, "shell ls", true, Suggestion.Companion.TYPE_PERMANENT))
            suggestions.add(
                Suggestion(
                    null,
                    "shell cd",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "shell cd ..",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
        }

        if ("retui-token".startsWith(lower) || "retuitoken".startsWith(lower)) {
            suggestions.add(
                Suggestion(
                    null,
                    "retui-token -status",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "retui-token -show",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "retui-token -rotate",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "retui-token -off",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
        }

        if ("module".startsWith(lower)) {
            suggestions.add(
                Suggestion(
                    null,
                    "module -ls",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "module -add",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "module -refresh",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "module -rm",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "module -show",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "module -close",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "module -dock add",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "module -dock remove",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
        }

        if ("orientation".startsWith(lower)) {
            suggestions.add(
                Suggestion(
                    null,
                    "orientation",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            for (option in arrayOf<String>("portrait", "landscape", "auto")) {
                suggestions.add(
                    Suggestion(
                        null,
                        "orientation " + option,
                        true,
                        Suggestion.Companion.TYPE_PERMANENT
                    )
                )
            }
        }

        if (getBoolean(Behavior.duo_mode) && CommandTuils.DUO_COMMAND.startsWith(lower)) {
            suggestions.add(
                Suggestion(
                    null,
                    CommandTuils.DUO_COMMAND,
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            for (option in duoOptions()) {
                suggestions.add(
                    Suggestion(
                        null,
                        CommandTuils.DUO_COMMAND + " " + option,
                        true,
                        Suggestion.Companion.TYPE_PERMANENT
                    )
                )
            }
        }
    }

    private fun suggestClockCommandArgs(
        pack: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        if (beforeLastSpace == null || beforeLastSpace.isEmpty()) {
            return
        }

        val normalized = beforeLastSpace.trim { it <= ' ' }.lowercase(Locale.getDefault())
        if ("timer" == normalized) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-add",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-stop",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-status",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            for (quick in this.timerQuickSuggestions) {
                if (afterLastSpace == null || afterLastSpace.isEmpty() || quick.startsWith(
                        afterLastSpace.lowercase(
                            Locale.getDefault()
                        )
                    )
                ) {
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            quick,
                            true,
                            Suggestion.Companion.TYPE_COMMAND
                        )
                    )
                }
            }
        } else if ("timer -add" == normalized) {
            for (quick in this.timerQuickSuggestions) {
                if (afterLastSpace == null || afterLastSpace.isEmpty() || quick.startsWith(
                        afterLastSpace.lowercase(
                            Locale.getDefault()
                        )
                    )
                ) {
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            quick,
                            true,
                            Suggestion.Companion.TYPE_COMMAND
                        )
                    )
                }
            }
        } else if ("stopwatch" == normalized) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-stop",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-reset",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-status",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
        } else if ("termux" == normalized) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-status",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-setup",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-open",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-apps",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-run",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-app",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-app-add",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-add-app",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-app-info",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-app-sync",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-app-actions",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-app-action",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-app-action-rm",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-app-rm",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-rm-app",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
        } else if ("lua" == normalized) {
            for (option in arrayOf<String>(
                "-apps",
                "-new app",
                "-app",
                "-edit",
                "-config",
                "-check",
                "-info",
                "-approve",
                "-export",
                "-disable",
                "-enable"
            )) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        option,
                        "-apps" == option,
                        Suggestion.Companion.TYPE_COMMAND
                    )
                )
            }
        } else if ("lua -app" == normalized || "lua app" == normalized
            || "lua -edit" == normalized || "lua edit" == normalized
            || "lua -config" == normalized || "lua config" == normalized
            || "lua -check" == normalized || "lua check" == normalized
            || "lua -info" == normalized || "lua info" == normalized
            || "lua -approve" == normalized || "lua approve" == normalized
            || "lua -export" == normalized || "lua export" == normalized
            || "lua -disable" == normalized || "lua disable" == normalized
            || "lua -enable" == normalized || "lua enable" == normalized
        ) {
            suggestLuaAppIds(suggestions, afterLastSpace, beforeLastSpace)
        } else if ("termux -run" == normalized || "termux run" == normalized) {
            suggestScopedAliases(
                pack.aliasManager,
                suggestions,
                afterLastSpace,
                beforeLastSpace,
                AliasManager.SCOPE_SCRIPT
            )
        } else if ("termux -app" == normalized || "termux app" == normalized) {
            suggestTermuxAppIds(suggestions, afterLastSpace, beforeLastSpace)
        } else if ("termux -app-actions" == normalized || "termux app-actions" == normalized
            || "termux -app-action" == normalized || "termux app-action" == normalized
            || "termux -app-action-rm" == normalized || "termux app-action-rm" == normalized
            || "termux -app-info" == normalized || "termux app-info" == normalized
            || "termux -app-sync" == normalized || "termux app-sync" == normalized
            || "termux -app-rm" == normalized || "termux app-rm" == normalized
            || "termux -rm-app" == normalized || "termux rm-app" == normalized
        ) {
            suggestTermuxAppIds(suggestions, afterLastSpace, beforeLastSpace)
        } else if ("tbridge" == normalized) {
            for (option in arrayOf<String>(
                "-status",
                "-setup",
                "-probe",
                "-ls",
                "-dirs",
                "-files"
            )) {
                if (afterLastSpace == null || afterLastSpace.isEmpty() || option.startsWith(
                        afterLastSpace.lowercase(
                            Locale.getDefault()
                        )
                    )
                ) {
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            option,
                            option == "-status" || option == "-setup" || option == "-probe",
                            Suggestion.Companion.TYPE_COMMAND
                        )
                    )
                }
            }
        } else if ("shell" == normalized) {
            for (option in arrayOf<String>(
                "pwd",
                "ls",
                "cd",
                "cd ..",
                "echo",
                "cat",
                "grep",
                "find"
            )) {
                if (afterLastSpace == null || afterLastSpace.isEmpty() || option.startsWith(
                        afterLastSpace.lowercase(
                            Locale.getDefault()
                        )
                    )
                ) {
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            option,
                            (option != "cd") && (option != "echo") && (option != "cat") && (option != "grep") && (option != "find"),
                            Suggestion.Companion.TYPE_COMMAND
                        )
                    )
                }
            }
        } else if ("retui-token" == normalized || "retuitoken" == normalized) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-status",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-show",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-rotate",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-on",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-off",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
        } else if ("module" == normalized) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-ls",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-add",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-new",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-edit",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-config",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-check",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-approve",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-refresh",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-rm",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-show",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-hide",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-dock",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "-close",
                    true,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
        } else if ("module -show" == normalized
            || "module -hide" == normalized
            || "module -refresh" == normalized
            || "module -rm" == normalized
        ) {
            suggestModules(suggestions, afterLastSpace, beforeLastSpace)
        } else if ("module -new" == normalized) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "lua",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
        } else if ("module -edit" == normalized
            || "module -config" == normalized
            || "module -prefs" == normalized
            || "module -check" == normalized
            || "module -info" == normalized
            || "module -approve" == normalized
            || "module -trust" == normalized
            || "module -copy-error" == normalized
            || "module -export" == normalized
            || "module -disable" == normalized
            || "module -enable" == normalized
            || "module -rename" == normalized
        ) {
            suggestWidgetIds(suggestions, afterLastSpace, beforeLastSpace, false)
        } else if ("module -expand" == normalized
            || "module -collapse" == normalized
            || "module -toggle" == normalized
            || "module -click" == normalized
        ) {
            suggestWidgetIds(suggestions, afterLastSpace, beforeLastSpace, true)
        } else if ("module -add" == normalized) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "server termux:/data/data/com.termux/files/home/retui/server-health.sh",
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
        } else if ("module -dock" == normalized || normalized.startsWith("module -dock ")) {
            suggestDockCommand(suggestions, afterLastSpace, beforeLastSpace)
        } else if ("widget" == normalized) {
            suggestWidgetOptions(suggestions, afterLastSpace, beforeLastSpace)
        } else if ("widget -edit" == normalized
            || "widget -config" == normalized
            || "widget -prefs" == normalized
            || "widget -check" == normalized
            || "widget -info" == normalized
            || "widget -approve" == normalized
            || "widget -trust" == normalized
            || "widget -copy-error" == normalized
            || "widget -export" == normalized
            || "widget -disable" == normalized
            || "widget -enable" == normalized
            || "widget -rename" == normalized
            || "widget -rm" == normalized
            || "widget -remove" == normalized
        ) {
            suggestWidgetIds(suggestions, afterLastSpace, beforeLastSpace, false)
        } else if ("widget -show" == normalized
            || "widget -refresh" == normalized
            || "widget -expand" == normalized
            || "widget -collapse" == normalized
            || "widget -toggle" == normalized
            || "widget -click" == normalized
        ) {
            suggestWidgetIds(suggestions, afterLastSpace, beforeLastSpace, true)
        } else if ("orientation" == normalized) {
            suggestOrientationOptions(suggestions, afterLastSpace, beforeLastSpace)
        } else if (getBoolean(Behavior.duo_mode) && CommandTuils.DUO_COMMAND == normalized) {
            suggestDuoOptions(suggestions, afterLastSpace, beforeLastSpace)
        }
    }

    private fun suggestDuoOptions(
        suggestions: MutableList<Suggestion?>,
        lastWord: String?,
        beforeLastSpace: String?
    ) {
        val filter = if (lastWord == null) Tuils.EMPTYSTRING else lastWord.lowercase()
        for (option in duoOptions()) {
            if (filter.length == 0 || option!!.startsWith(filter)) {
                suggestions.add(
                    SuggestionsManager.Suggestion(
                        beforeLastSpace,
                        option!!,
                        true,
                        Suggestion.Companion.TYPE_COMMAND
                    )
                )
            }
        }
    }

    private fun suggestOrientationOptions(
        suggestions: MutableList<Suggestion?>,
        lastWord: String?,
        beforeLastSpace: String?
    ) {
        val filter =
            if (lastWord == null) Tuils.EMPTYSTRING else lastWord.lowercase(Locale.getDefault())
        for (option in arrayOf<String>("portrait", "landscape", "auto")) {
            if (filter.length == 0 || option.startsWith(filter)) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        option,
                        true,
                        Suggestion.Companion.TYPE_COMMAND
                    )
                )
            }
        }
    }

    private fun suggestWidgetOptions(
        suggestions: MutableList<Suggestion?>,
        lastWord: String?,
        beforeLastSpace: String?
    ) {
        val filter =
            if (lastWord == null) Tuils.EMPTYSTRING else lastWord.lowercase(Locale.getDefault())
        addWidgetOption(suggestions, beforeLastSpace, "-ls", true, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-add", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-new", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-edit", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-show", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-refresh", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-check", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-info", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-approve", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-copy-error", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-disable", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-enable", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-export", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-expand", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-collapse", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-toggle", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-rename", false, filter)
        addWidgetOption(suggestions, beforeLastSpace, "-rm", false, filter)
    }

    private fun addWidgetOption(
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?,
        option: String,
        exec: Boolean,
        filter: String?
    ) {
        val normalizedFilter =
            if (filter == null) Tuils.EMPTYSTRING else filter.replace("-", Tuils.EMPTYSTRING)
        val normalizedOption = option.replace("-", Tuils.EMPTYSTRING)
        if (filter == null || filter.length == 0 || option.startsWith(filter) || normalizedOption.startsWith(
                normalizedFilter
            )
        ) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    option,
                    exec && clickToLaunch,
                    Suggestion.Companion.TYPE_COMMAND
                )
            )
        }
    }

    private fun suggestTermuxAppRoots(suggestions: MutableList<Suggestion?>, lower: String) {
        if (lower.length == 0) {
            return
        }
        for (app in TermuxAppManager.list(pack.context)) {
            if (app.id.startsWith(lower)) {
                suggestions.add(
                    Suggestion(
                        null,
                        app.id,
                        true,
                        Suggestion.Companion.TYPE_COMMAND
                    )
                )
            }
        }
    }

    private fun suggestWidgetIds(
        suggestions: MutableList<Suggestion?>,
        lastWord: String?,
        beforeLastSpace: String?,
        dockableOnly: Boolean
    ) {
        val filter =
            if (lastWord == null) Tuils.EMPTYSTRING else LuaWidgetManager.normalizeId(lastWord)
        val prefix =
            if (beforeLastSpace == null) Tuils.EMPTYSTRING else beforeLastSpace.trim { it <= ' ' }
        for (id in LuaWidgetManager.listIds()) {
            if (dockableOnly && !LuaWidgetManager.isDockable(id)) {
                continue
            }
            val widgetId = id ?: continue
            val label = LuaWidgetManager.getName(widgetId) ?: widgetId
            val normalizedLabel = LuaWidgetManager.normalizeId(label)
            if (filter.length == 0 || widgetId.startsWith(filter) || normalizedLabel.startsWith(filter)) {
                val command = if (prefix.length == 0) widgetId else prefix + Tuils.SPACE + widgetId
                suggestions.add(
                    Suggestion(
                        null,
                        label,
                        false,
                        Suggestion.Companion.TYPE_MODULE,
                        command
                    )
                )
            }
        }
    }

    private fun suggestModules(
        suggestions: MutableList<Suggestion?>,
        lastWord: String?,
        beforeLastSpace: String?
    ) {
        val filter =
            if (lastWord == null) Tuils.EMPTYSTRING else lastWord.lowercase(Locale.getDefault())
        val prefix =
            if (beforeLastSpace == null) Tuils.EMPTYSTRING else beforeLastSpace.trim { it <= ' ' }
        val modules = LinkedHashSet<String?>()
        modules.addAll(listAll(pack.context))
        modules.addAll(LuaWidgetManager.listIds())
        for (module in modules) {
            val label = displayTitle(pack.context, module)
            val normalizedLabel = normalize(label)
            if (filter.length == 0 || module!!.startsWith(filter) || normalizedLabel.startsWith(
                    filter
                )
            ) {
                val command = if (prefix.length == 0) module else prefix + Tuils.SPACE + module
                suggestions.add(
                    SuggestionsManager.Suggestion(
                        null,
                        label!!,
                        false,
                        Suggestion.Companion.TYPE_MODULE,
                        command
                    )
                )
            }
        }
    }

    private fun suggestDockCommand(
        suggestions: MutableList<Suggestion?>,
        lastWord: String?,
        beforeLastSpace: String?
    ) {
        val prefix =
            if (beforeLastSpace == null) Tuils.EMPTYSTRING else beforeLastSpace.trim { it <= ' ' }
        val typed =
            if (lastWord == null) Tuils.EMPTYSTRING else lastWord.trim { it <= ' ' }.lowercase(
                Locale.getDefault()
            )
        val parts: Array<String?> =
            if (prefix.length == 0) arrayOfNulls<String>(0) else prefix.split("\\s+".toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()

        if (parts.size <= 2) {
            suggestDockAction(suggestions, "add", typed, prefix)
            suggestDockAction(suggestions, "remove", typed, prefix)
            return
        }

        val mode = parts[2]!!.lowercase(Locale.getDefault())
        if ("add" == mode || "-add" == mode) {
            suggestDockModules(suggestions, lastWord, beforeLastSpace, true)
        } else if ("remove" == mode || "-remove" == mode || "rm" == mode || "-rm" == mode) {
            suggestDockModules(suggestions, lastWord, beforeLastSpace, false)
        }
    }

    private fun suggestDockAction(
        suggestions: MutableList<Suggestion?>,
        action: String,
        typed: String,
        prefix: String?
    ) {
        if (typed.length == 0 || action.startsWith(typed)) {
            suggestions.add(Suggestion(prefix, action, false, Suggestion.Companion.TYPE_COMMAND))
        }
    }

    private fun suggestDockModules(
        suggestions: MutableList<Suggestion?>,
        lastWord: String?,
        beforeLastSpace: String?,
        addMode: Boolean
    ) {
        val prefix =
            if (beforeLastSpace == null) Tuils.EMPTYSTRING else beforeLastSpace.trim { it <= ' ' }
        val typed =
            if (lastWord == null) Tuils.EMPTYSTRING else lastWord.trim { it <= ' ' }.lowercase(
                Locale.getDefault()
            )
        val selected: MutableSet<String?> = LinkedHashSet<String?>()

        val parts = prefix.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val moduleStart = 3
        if (addMode) {
            selected.addAll(getDock(pack.context))
        }
        for (i in moduleStart..<parts.size) {
            val id = normalize(parts[i])
            if (isKnown(pack.context, id)) {
                selected.add(id)
            }
        }

        val typedModule = normalize(typed)
        val typedIsCompleteModule = typed.length > 0 && isKnown(pack.context, typedModule)
        var suggestionPrefix = prefix
        var filter = typed

        if (typedIsCompleteModule) {
            selected.add(typedModule)
            suggestionPrefix = (prefix + Tuils.SPACE + typed).trim { it <= ' ' }
            filter = Tuils.EMPTYSTRING
        }

        val candidates: MutableList<String> =
            (if (addMode) listAll(pack.context) else getDock(pack.context)).filterNotNull().toMutableList()
        for (module in candidates) {
            if (selected.contains(module)) {
                continue
            }
            val label = displayTitle(pack.context, module)
            val normalizedLabel = normalize(label)
            if (filter.length == 0 || module.startsWith(filter) || normalizedLabel.startsWith(filter)) {
                val command: String? =
                    if (suggestionPrefix.length == 0) module else suggestionPrefix + Tuils.SPACE + module
                suggestions.add(
                    SuggestionsManager.Suggestion(
                        null,
                        label!!,
                        false,
                        Suggestion.Companion.TYPE_MODULE,
                        command
                    )
                )
            }
        }
    }

    private fun suggestScopedAliases(
        aliasManager: AliasManager?,
        suggestions: MutableList<Suggestion?>,
        lastWord: String?,
        beforeLastSpace: String?,
        scope: String?
    ) {
        if (aliasManager == null) {
            return
        }

        val filter = if (lastWord == null) Tuils.EMPTYSTRING else lastWord
        var canInsert =
            if (filter.length == 0) noInputCounts!![Suggestion.Companion.TYPE_ALIAS] else counts!![Suggestion.Companion.TYPE_ALIAS]

        for (a in aliasManager.getAliases(true, scope)) {
            if (filter.length == 0 || a!!.name.startsWith(filter)) {
                if (canInsert == 0) return
                canInsert--

                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        a!!.name,
                        false,
                        Suggestion.Companion.TYPE_ALIAS
                    )
                )
            }
        }
    }

    private fun suggestTermuxAppIds(
        suggestions: MutableList<Suggestion?>,
        lastWord: String?,
        beforeLastSpace: String?
    ) {
        val filter = if (lastWord == null) Tuils.EMPTYSTRING else lastWord.lowercase(Locale.getDefault())
        for (app in TermuxAppManager.list(pack.context)) {
            if (filter.length == 0 || app.id.startsWith(filter)) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        app.id,
                        true,
                        Suggestion.Companion.TYPE_COMMAND
                    )
                )
            }
        }
    }

    private fun suggestLuaAppIds(
        suggestions: MutableList<Suggestion?>,
        lastWord: String?,
        beforeLastSpace: String?
    ) {
        val filter =
            if (lastWord == null) Tuils.EMPTYSTRING else LuaWidgetManager.normalizeId(lastWord)
        for (id in LuaWidgetManager.listIds()) {
            val appId = id ?: continue
            if ("app" != LuaWidgetManager.getScriptType(appId)) {
                continue
            }
            val label = LuaWidgetManager.getName(appId) ?: appId
            val normalizedLabel = LuaWidgetManager.normalizeId(label)
            if (filter.length == 0 || appId.startsWith(filter) || normalizedLabel.startsWith(filter)) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        appId,
                        true,
                        Suggestion.Companion.TYPE_COMMAND
                    )
                )
            }
        }
    }

    private val timerQuickSuggestions: Array<String>
        get() = arrayOf<String>("5m", "15m", "30m", "60m")

    private fun suggestWebhookHistory(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        if (beforeLastSpace == null || beforeLastSpace.isEmpty()) return

        val split = beforeLastSpace.split(Tuils.SPACE.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        if (split.size < 1 || !split[0].equals("webhook", ignoreCase = true)) return

        if (split.size == 1) {
            val subs = arrayOf<String?>("-add", "-rm", "-ls")
            for (s in subs) {
                if (afterLastSpace == null || afterLastSpace.isEmpty() || s!!.startsWith(
                        afterLastSpace
                    )
                ) {
                    suggestions.add(
                        SuggestionsManager.Suggestion(
                            beforeLastSpace,
                            s!!,
                            false,
                            Suggestion.Companion.TYPE_COMMAND
                        )
                    )
                }
            }

            val hooks = info.webhookManager.getWebhooks()
            for (h in hooks) {
                if (afterLastSpace == null || afterLastSpace.isEmpty() || h.name.startsWith(
                        afterLastSpace
                    )
                ) {
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            h.name,
                            false,
                            Suggestion.Companion.TYPE_COMMAND
                        )
                    )
                }
            }
            return
        }

        val webhookName = split[1]
        if (webhookName.startsWith("-")) return

        val history: MutableList<String>? = info.historyManager.getHistory(webhookName)?.toMutableList()
        if (history == null || history.isEmpty()) return

        for (entry in history) {
            if (afterLastSpace == null || afterLastSpace.isEmpty() || entry.startsWith(
                    afterLastSpace
                )
            ) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        entry,
                        clickToLaunch,
                        Suggestion.Companion.TYPE_WEBHOOK_HISTORY
                    )
                )
            }
        }
    }

    private fun suggestThemePresets(
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        for (p in listBuiltInPresets()) {
            if (afterLastSpace == null || afterLastSpace.length == 0 || p.startsWith(afterLastSpace)) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        p,
                        true,
                        Suggestion.Companion.TYPE_PERMANENT
                    )
                )
            }
        }
    }

    private fun suggestSavedPresetNames(
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        val applyCommand = beforeLastSpace != null && beforeLastSpace.lowercase(Locale.getDefault())
            .contains(" -apply")
        val suggested: MutableSet<String?> = LinkedHashSet<String?>()
        for (preset in listAllPresetNames()) {
            val displayName = presetDisplayName(preset)
            if (!suggested.add(displayName.lowercase(Locale.getDefault()))) {
                continue
            }
            if (afterLastSpace == null || afterLastSpace.length == 0 || displayName.lowercase(Locale.getDefault())
                    .startsWith(
                        afterLastSpace.lowercase(
                            Locale.getDefault()
                        )
                    )
            ) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        displayName,
                        applyCommand && clickToLaunch,
                        Suggestion.Companion.TYPE_COMMAND
                    )
                )
            }
        }
    }

    private fun presetDisplayName(preset: String?): String {
        val suffix = ".retui-preset"
        if (preset != null && preset.lowercase(Locale.getDefault()).endsWith(suffix)) {
            return preset.substring(0, preset.length - suffix.length)
        }
        return preset!!
    }

    private fun suggestArgs(
        info: MainPack,
        type: Int,
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String
    ) {
        suggestArgs(info, type, suggestions, null, beforeLastSpace)
    }

    private fun findExactContact(
        contacts: MutableList<Contact>?,
        selectedContactName: String?
    ): Contact? {
        if (contacts == null || selectedContactName == null) {
            return null
        }

        val normalized = selectedContactName.trim { it <= ' ' }
        for (contact in contacts) {
            if (contact.string.equals(normalized, ignoreCase = true)) {
                return contact
            }
        }
        return null
    }

    private fun suggestBoolean(suggestions: MutableList<Suggestion?>, beforeLastSpace: String?) {
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "true",
                clickToLaunch,
                Suggestion.Companion.TYPE_BOOLEAN
            )
        )
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "false",
                clickToLaunch,
                Suggestion.Companion.TYPE_BOOLEAN
            )
        )
    }

    private fun suggestFile(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        var afterLastSpace = afterLastSpace
        if (isCdCommand(beforeLastSpace)) {
            suggestDirectory(info, suggestions, afterLastSpace, beforeLastSpace)
            return
        }
        if (isFileOpenCommand(beforeLastSpace)) {
            suggestOpenableFile(info, suggestions, afterLastSpace, beforeLastSpace)
            return
        }

        val noAfterLastSpace = afterLastSpace == null || afterLastSpace.length == 0
        val afterLastSpaceNotEndsWithSeparator =
            noAfterLastSpace || !afterLastSpace.endsWith(File.separator)

        if (noAfterLastSpace || afterLastSpaceNotEndsWithSeparator) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    File.separator,
                    false,
                    Suggestion.Companion.TYPE_FILE,
                    afterLastSpace
                )
            )
        }

        if (Suggestion.Companion.appendQuotesBeforeFile && !noAfterLastSpace && !afterLastSpace.endsWith(
                SINGLE_QUOTE
            ) && !afterLastSpace.endsWith(DOUBLE_QUOTES)
        ) suggestions.add(
            Suggestion(
                beforeLastSpace,
                SINGLE_QUOTE,
                false,
                Suggestion.Companion.TYPE_FILE,
                afterLastSpace
            )
        )

        if (noAfterLastSpace) {
            suggestFilesInDir(null, suggestions, info.currentDirectory, beforeLastSpace)
            return
        }

        if (!afterLastSpace.contains(File.separator)) {
            suggestFilesInDir(
                suggestions,
                info.currentDirectory,
                afterLastSpace,
                beforeLastSpace,
                null
            )
        } else {
            //            if it's ../../

            if (!afterLastSpaceNotEndsWithSeparator) {
                val total = beforeLastSpace + Tuils.SPACE + afterLastSpace
                val quotesCount: Int =
                    total.length - total.replace(DOUBLE_QUOTES, Tuils.EMPTYSTRING).replace(
                        SINGLE_QUOTE, Tuils.EMPTYSTRING
                    ).length

                if (quotesCount > 0) {
                    val singleQIndex: Int = total.lastIndexOf(SINGLE_QUOTE)
                    val doubleQIndex: Int = total.lastIndexOf(DOUBLE_QUOTES)

                    val lastQuote = max(singleQIndex, doubleQIndex)

                    val file = total.substring(lastQuote + abs(quotesCount % 2 - 2))
                    val dirInfo = cd(info.currentDirectory, file)
                    suggestFilesInDir(afterLastSpace, suggestions, dirInfo.file, beforeLastSpace)
                } else {
//                    removes the /
                    afterLastSpace = afterLastSpace.substring(0, afterLastSpace.length - 1)
                    val dirInfo = cd(info.currentDirectory, afterLastSpace)
                    suggestFilesInDir(
                        afterLastSpace + File.separator,
                        suggestions,
                        dirInfo.file,
                        beforeLastSpace
                    )
                }
            } else {
                val originalAfterLastSpace: String? = afterLastSpace
                afterLastSpace = rmQuotes.matcher(afterLastSpace).replaceAll(Tuils.EMPTYSTRING)

                val index = afterLastSpace.lastIndexOf(File.separator)
                val dirInfo = cd(info.currentDirectory, afterLastSpace.substring(0, index))

                val originalIndex = originalAfterLastSpace!!.lastIndexOf(File.separator)

                val alsals = originalAfterLastSpace.substring(0, originalIndex + 1)
                val als = originalAfterLastSpace.substring(originalIndex + 1)

                //                beforeLastSpace  = beforeLastSpace + Tuils.SPACE + hold;
                suggestFilesInDir(suggestions, dirInfo.file, als, beforeLastSpace, alsals)
            }
        }
    }

    private fun isCdCommand(beforeLastSpace: String?): Boolean {
        if (beforeLastSpace == null) {
            return false
        }
        return "cd".equals(beforeLastSpace.trim { it <= ' ' }, ignoreCase = true)
    }

    private fun isFileOpenCommand(beforeLastSpace: String?): Boolean {
        if (beforeLastSpace == null) {
            return false
        }
        val command = beforeLastSpace.trim { it <= ' ' }.lowercase(Locale.getDefault())
        return "open" == command || "termux-open" == command || "share" == command
    }

    private fun suggestOpenableFile(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        if (activeBackend(info.context) == FileBackendManager.Active.TERMUX) {
            suggestTermuxFiles(info, suggestions, afterLastSpace, beforeLastSpace)
            return
        }

        val noAfterLastSpace = afterLastSpace == null || afterLastSpace.length == 0
        if (noAfterLastSpace) {
            suggestFilesOnlyInDir(null, suggestions, info.currentDirectory, beforeLastSpace)
            return
        }

        if (!afterLastSpace.contains(File.separator)) {
            suggestFilesOnlyInDir(
                suggestions,
                info.currentDirectory,
                afterLastSpace,
                beforeLastSpace,
                null
            )
            return
        }

        if (afterLastSpace.endsWith(File.separator)) {
            val base = afterLastSpace.substring(0, afterLastSpace.length - 1)
            val dirInfo =
                cd(info.currentDirectory, rmQuotes.matcher(base).replaceAll(Tuils.EMPTYSTRING))
            suggestFilesOnlyInDir(afterLastSpace, suggestions, dirInfo.file, beforeLastSpace)
            return
        }

        val clean = rmQuotes.matcher(afterLastSpace).replaceAll(Tuils.EMPTYSTRING)
        val index = clean.lastIndexOf(File.separator)
        if (index < 0) {
            suggestFilesOnlyInDir(suggestions, info.currentDirectory, clean, beforeLastSpace, null)
            return
        }

        val dirInfo = cd(info.currentDirectory, clean.substring(0, index))
        val holder = afterLastSpace.substring(0, afterLastSpace.lastIndexOf(File.separator) + 1)
        val leaf = clean.substring(index + 1)
        suggestFilesOnlyInDir(suggestions, dirInfo.file, leaf, beforeLastSpace, holder)
    }

    private fun suggestDirectory(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        if (activeBackend(info.context) == FileBackendManager.Active.TERMUX) {
            suggestTermuxDirs(info, suggestions, afterLastSpace, beforeLastSpace)
            return
        }

        val noAfterLastSpace = afterLastSpace == null || afterLastSpace.length == 0
        if (noAfterLastSpace || "..".startsWith(afterLastSpace)) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "..",
                    false,
                    Suggestion.Companion.TYPE_FILE
                )
            )
        }
        if (noAfterLastSpace || File.separator.startsWith(afterLastSpace)) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    File.separator,
                    false,
                    Suggestion.Companion.TYPE_FILE,
                    afterLastSpace
                )
            )
        }

        if (noAfterLastSpace) {
            suggestDirsInDir(null, suggestions, info.currentDirectory, beforeLastSpace)
            return
        }

        if (!afterLastSpace.contains(File.separator)) {
            suggestDirsInDir(
                suggestions,
                info.currentDirectory,
                afterLastSpace,
                beforeLastSpace,
                null
            )
            return
        }

        if (afterLastSpace.endsWith(File.separator)) {
            val base = afterLastSpace.substring(0, afterLastSpace.length - 1)
            val dirInfo =
                cd(info.currentDirectory, rmQuotes.matcher(base).replaceAll(Tuils.EMPTYSTRING))
            suggestDirsInDir(afterLastSpace, suggestions, dirInfo.file, beforeLastSpace)
            return
        }

        val clean = rmQuotes.matcher(afterLastSpace).replaceAll(Tuils.EMPTYSTRING)
        val index = clean.lastIndexOf(File.separator)
        if (index < 0) {
            suggestDirsInDir(suggestions, info.currentDirectory, clean, beforeLastSpace, null)
            return
        }

        val dirInfo = cd(info.currentDirectory, clean.substring(0, index))
        val holder = afterLastSpace.substring(0, afterLastSpace.lastIndexOf(File.separator) + 1)
        val leaf = clean.substring(index + 1)
        suggestDirsInDir(suggestions, dirInfo.file, leaf, beforeLastSpace, holder)
    }

    private fun suggestTermuxDirs(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        val noAfterLastSpace = afterLastSpace == null || afterLastSpace.length == 0
        if (noAfterLastSpace || "..".startsWith(afterLastSpace)) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "..",
                    false,
                    Suggestion.Companion.TYPE_FILE
                )
            )
        }
        if (noAfterLastSpace || File.separator.startsWith(afterLastSpace)) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    File.separator,
                    false,
                    Suggestion.Companion.TYPE_FILE,
                    afterLastSpace
                )
            )
        }

        val target = termuxTarget(info.currentDirectory, afterLastSpace)
        requestTermuxListing(info, "dirs", target.dir)
        addTermuxMatches(dirs(target.dir), target.leaf, suggestions, beforeLastSpace, target.holder)
    }

    private fun suggestTermuxFiles(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        val target = termuxTarget(info.currentDirectory, afterLastSpace)
        requestTermuxListing(info, "files", target.dir)
        addTermuxMatches(
            files(target.dir),
            target.leaf,
            suggestions,
            beforeLastSpace,
            target.holder
        )
    }

    private fun requestTermuxListing(info: MainPack, type: String, path: String) {
        if (!shouldRequest(type, path)) {
            return
        }
        val script = if ("dirs" == type) tbridge.LIST_DIRS_SCRIPT else tbridge.LIST_FILES_SCRIPT
        dispatchShell(
            info.context,
            type + " " + path,
            script,
            TermuxBridgeManager.TERMUX_HOME,
            path
        )
    }

    private fun termuxTarget(
        currentDirectory: File,
        afterLastSpace: String?
    ): TermuxSuggestionTarget {
        if (afterLastSpace == null || afterLastSpace.trim { it <= ' ' }.length == 0) {
            return TermuxSuggestionTarget(
                currentDirectory.getAbsolutePath(),
                Tuils.EMPTYSTRING,
                null
            )
        }

        val original: String? = afterLastSpace
        val clean = rmQuotes.matcher(afterLastSpace).replaceAll(Tuils.EMPTYSTRING)
        if (clean.endsWith(File.separator)) {
            val dir =
                if (clean.startsWith(File.separator)) File(clean) else File(currentDirectory, clean)
            return TermuxSuggestionTarget(dir.getAbsolutePath(), Tuils.EMPTYSTRING, original)
        }

        val index = clean.lastIndexOf(File.separator)
        if (index < 0) {
            return TermuxSuggestionTarget(currentDirectory.getAbsolutePath(), clean, null)
        }

        val base = clean.substring(0, index)
        val holder = original!!.substring(0, original.lastIndexOf(File.separator) + 1)
        val leaf = clean.substring(index + 1)
        val dir = if (base.startsWith(File.separator)) File(base) else File(currentDirectory, base)
        return TermuxSuggestionTarget(dir.getAbsolutePath(), leaf, holder)
    }

    private fun addTermuxMatches(
        values: List<String>,
        leaf: String?,
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?,
        holder: String?
    ) {
        val temp = if (leaf == null) Tuils.EMPTYSTRING else leaf
        val counter = quickCompare(
            temp,
            values.toTypedArray(),
            suggestions,
            beforeLastSpace,
            suggestionsPerCategory,
            false,
            Suggestion.Companion.TYPE_FILE,
            false
        )
        if (suggestionsPerCategory - counter <= 0) {
            return
        }
        val matches = CompareStrings.topMatchesWithDeadline(
            temp,
            values.toTypedArray(),
            suggestionsPerCategory - counter,
            suggestionsDeadline,
            FILE_SPLITTERS,
            algInstance,
            alg
        )
        for (match in matches) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    match,
                    false,
                    Suggestion.Companion.TYPE_FILE,
                    holder
                )
            )
        }
    }

    private class TermuxSuggestionTarget(val dir: String, val leaf: String?, val holder: String?)

    private fun suggestFilesInDir(
        suggestions: MutableList<Suggestion?>,
        dir: File?,
        afterLastSeparator: String?,
        beforeLastSpace: String?,
        afterLastSpaceWithoutALS: String?
    ) {
        if (dir == null || !dir.isDirectory()) return

        if (afterLastSeparator == null || afterLastSeparator.length == 0) {
            suggestFilesInDir(null, suggestions, dir, beforeLastSpace)
            return
        }

        val files = dir.list()
        if (files == null) {
            return
        }

        //        Tuils.log("bls", beforeLastSpace);
//        Tuils.log("als", afterLastSeparator);
//        Tuils.log("alsals", afterLastSpaceWithoutALS);
        val temp = rmQuotes.matcher(afterLastSeparator).replaceAll(Tuils.EMPTYSTRING)

        val counter = quickCompare(
            temp,
            files,
            suggestions,
            beforeLastSpace,
            suggestionsPerCategory,
            false,
            Suggestion.Companion.TYPE_FILE,
            false
        )
        if (suggestionsPerCategory - counter <= 0) return

        val fs = CompareStrings.topMatchesWithDeadline(
            temp,
            files,
            suggestionsPerCategory - counter,
            suggestionsDeadline,
            FILE_SPLITTERS,
            algInstance,
            alg
        )
        for (f in fs) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    f,
                    false,
                    Suggestion.Companion.TYPE_FILE,
                    afterLastSpaceWithoutALS
                )
            )
        }
    }

    private fun suggestDirsInDir(
        suggestions: MutableList<Suggestion?>,
        dir: File?,
        afterLastSeparator: String?,
        beforeLastSpace: String?,
        afterLastSpaceWithoutALS: String?
    ) {
        if (dir == null || !dir.isDirectory()) return

        if (afterLastSeparator == null || afterLastSeparator.length == 0) {
            suggestDirsInDir(null, suggestions, dir, beforeLastSpace)
            return
        }

        val dirs = dir.list(FilenameFilter { current: File?, name: String? ->
            File(
                current,
                name
            ).isDirectory()
        })
        if (dirs == null) {
            return
        }

        val temp = rmQuotes.matcher(afterLastSeparator).replaceAll(Tuils.EMPTYSTRING)
        val counter = quickCompare(
            temp,
            dirs,
            suggestions,
            beforeLastSpace,
            suggestionsPerCategory,
            false,
            Suggestion.Companion.TYPE_FILE,
            false
        )
        if (suggestionsPerCategory - counter <= 0) return

        val matches = CompareStrings.topMatchesWithDeadline(
            temp,
            dirs,
            suggestionsPerCategory - counter,
            suggestionsDeadline,
            FILE_SPLITTERS,
            algInstance,
            alg
        )
        for (match in matches) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    match,
                    false,
                    Suggestion.Companion.TYPE_FILE,
                    afterLastSpaceWithoutALS
                )
            )
        }
    }

    private fun suggestFilesOnlyInDir(
        suggestions: MutableList<Suggestion?>,
        dir: File?,
        afterLastSeparator: String?,
        beforeLastSpace: String?,
        afterLastSpaceWithoutALS: String?
    ) {
        if (dir == null || !dir.isDirectory()) return

        if (afterLastSeparator == null || afterLastSeparator.length == 0) {
            suggestFilesOnlyInDir(null, suggestions, dir, beforeLastSpace)
            return
        }

        val files = dir.list(FilenameFilter { current: File?, name: String? ->
            File(
                current,
                name
            ).isFile()
        })
        if (files == null) {
            return
        }

        val temp = rmQuotes.matcher(afterLastSeparator).replaceAll(Tuils.EMPTYSTRING)
        val counter = quickCompare(
            temp,
            files,
            suggestions,
            beforeLastSpace,
            suggestionsPerCategory,
            false,
            Suggestion.Companion.TYPE_FILE,
            false
        )
        if (suggestionsPerCategory - counter <= 0) return

        val matches = CompareStrings.topMatchesWithDeadline(
            temp,
            files,
            suggestionsPerCategory - counter,
            suggestionsDeadline,
            FILE_SPLITTERS,
            algInstance,
            alg
        )
        for (match in matches) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    match,
                    false,
                    Suggestion.Companion.TYPE_FILE,
                    afterLastSpaceWithoutALS
                )
            )
        }
    }

    private fun quickCompare(
        s1: String,
        ss: Array<String>,
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?,
        max: Int,
        exec: Boolean,
        type: Int,
        tag: Any?
    ): Int {
        if (s1.length > quickCompare) return 0

        val lower = s1.lowercase(Locale.getDefault())
        var counter = 0

        for (c in ss.indices) {
            if (counter >= max) break

            if (s1.length <= quickCompare && ss[c].lowercase(Locale.getDefault())
                    .startsWith(lower)
            ) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        ss[c],
                        exec,
                        type,
                        if (tag is Boolean) (if (tag) ss[c] else null) else tag
                    )
                )

                ss[c] = Tuils.EMPTYSTRING

                counter++
            }
        }

        return counter
    }

    private fun quickCompare(
        s1: String,
        ss: MutableList<out StringableObject>,
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?,
        max: Int,
        exec: Boolean,
        type: Int,
        tag: Any?
    ): Int {
        if (s1.length > quickCompare) return 0

        val lower = s1.lowercase(Locale.getDefault())
        var counter = 0

        val it: MutableIterator<out StringableObject> = ss.iterator()

        while (it.hasNext()) {
            if (counter >= max) break

            val o = it.next()

            if (s1.length <= quickCompare && o.getLowercaseString().startsWith(lower)) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        o.getString(),
                        exec,
                        type,
                        if (tag is Boolean) (if (tag) o else null) else tag
                    )
                )

                it.remove()

                counter++
            }
        }

        return counter
    }

    private fun suggestFilesInDir(
        afterLastSpaceHolder: String?,
        suggestions: MutableList<Suggestion?>,
        dir: File?,
        beforeLastSpace: String?
    ) {
        if (dir == null || !dir.isDirectory()) {
            return
        }

        try {
            val files = dir.list()
            if (files == null) {
                return
            }
            Arrays.sort(files)
            for (s in files) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        s,
                        false,
                        Suggestion.Companion.TYPE_FILE,
                        afterLastSpaceHolder
                    )
                )
            }
        } catch (e: NullPointerException) {
            Tuils.log(e)
        }
    }

    private fun suggestDirsInDir(
        afterLastSpaceHolder: String?,
        suggestions: MutableList<Suggestion?>,
        dir: File?,
        beforeLastSpace: String?
    ) {
        if (dir == null || !dir.isDirectory()) {
            return
        }

        try {
            val dirs = dir.list(FilenameFilter { current: File?, name: String? ->
                File(
                    current,
                    name
                ).isDirectory()
            })
            if (dirs == null) {
                return
            }
            Arrays.sort(dirs)
            for (s in dirs) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        s,
                        false,
                        Suggestion.Companion.TYPE_FILE,
                        afterLastSpaceHolder
                    )
                )
            }
        } catch (e: NullPointerException) {
            Tuils.log(e)
        }
    }

    private fun suggestFilesOnlyInDir(
        afterLastSpaceHolder: String?,
        suggestions: MutableList<Suggestion?>,
        dir: File?,
        beforeLastSpace: String?
    ) {
        if (dir == null || !dir.isDirectory()) {
            return
        }

        try {
            val files = dir.list(FilenameFilter { current: File?, name: String? ->
                File(
                    current,
                    name
                ).isFile()
            })
            if (files == null) {
                return
            }
            Arrays.sort(files)
            for (s in files) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        s,
                        false,
                        Suggestion.Companion.TYPE_FILE,
                        afterLastSpaceHolder
                    )
                )
            }
        } catch (e: NullPointerException) {
            Tuils.log(e)
        }
    }

    private fun suggestContact(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        val contacts: MutableList<Contact> = info.contacts.getContacts()
        if (contacts == null || contacts.size == 0) return

        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (contact in contacts) suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    contact.string,
                    true,
                    Suggestion.Companion.TYPE_CONTACT,
                    contact
                )
            )
        } else {
            val counter: Int = quickCompare(
                afterLastSpace,
                contacts,
                suggestions,
                beforeLastSpace,
                suggestionsPerCategory,
                true,
                Suggestion.Companion.TYPE_CONTACT,
                true
            )
            if (suggestionsPerCategory - counter <= 0) return

            val cts: Array<Contact?> = CompareObjects.topMatchesWithDeadline(
                Contact::class.java,
                afterLastSpace,
                contacts.size,
                contacts,
                suggestionsPerCategory - counter,
                suggestionsDeadline,
                SPLITTERS,
                algInstance,
                alg
            )
            for (c in cts) {
                if (c == null) break
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        c.string,
                        clickToLaunch,
                        Suggestion.Companion.TYPE_CONTACT,
                        c
                    )
                )
            }
        }
    }

    private fun suggestDataStoreType(
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?
    ) {
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "json",
                false,
                Suggestion.Companion.TYPE_BOOLEAN
            )
        )
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "xpath",
                false,
                Suggestion.Companion.TYPE_BOOLEAN
            )
        )
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "format",
                false,
                Suggestion.Companion.TYPE_BOOLEAN
            )
        )
    }

    private fun suggestSong(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        if (info.player == null) return

        val songs: MutableList<Song>? = info.player!!.getSongs()
        if (songs == null || songs.size == 0) return

        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (s in songs) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        s.getTitle(),
                        clickToLaunch,
                        Suggestion.Companion.TYPE_SONG
                    )
                )
            }
        } else {
            val counter = quickCompare(
                afterLastSpace,
                songs,
                suggestions,
                beforeLastSpace,
                suggestionsPerCategory,
                clickToLaunch,
                Suggestion.Companion.TYPE_SONG,
                false
            )
            if (suggestionsPerCategory - counter <= 0) return

            val ss = CompareObjects.topMatchesWithDeadline<Song?>(
                Song::class.java,
                afterLastSpace,
                songs.size,
                songs,
                suggestionsPerCategory - counter,
                suggestionsDeadline,
                SPLITTERS,
                algInstance,
                alg
            )
            for (s in ss) {
                if (s == null) break
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        s.getTitle(),
                        clickToLaunch,
                        Suggestion.Companion.TYPE_SONG
                    )
                )
            }
        }
    }

    private fun suggestCommand(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        var afterLastSpace = afterLastSpace
        if (afterLastSpace == null || afterLastSpace.length == 0) {
            suggestCommand(info, suggestions, beforeLastSpace)
            return
        }

        if (afterLastSpace.length <= FIRST_INTERVAL) {
            afterLastSpace = afterLastSpace.lowercase(Locale.getDefault()).trim { it <= ' ' }

            val cmds = info.commandGroup.commandNames
            if (cmds == null) return

            var canInsert = counts!![Suggestion.Companion.TYPE_COMMAND]
            for (s in cmds) {
                if (canInsert == 0 || Thread.currentThread().isInterrupted()) return
                if (isHiddenCommandName(s)) continue
                if (HIDDEN_SUGGESTION_COMMAND.equals(s, ignoreCase = true)) continue

                if (s.startsWith(afterLastSpace)) {
                    val cmd = info.commandGroup.getCommandByName(s)
                    val args = cmd!!.argType()
                    val exec = args == null || args.size == 0
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            s,
                            exec && clickToLaunch,
                            Suggestion.Companion.TYPE_COMMAND
                        )
                    )
                    canInsert--
                }
            }
        }
    }

    private fun suggestCommand(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?
    ) {
        val cmds = info.commandGroup.commands
        if (cmds == null) return

        //        if there's a beforelastspace -> help ...
        var canInsert =
            if (beforeLastSpace != null && beforeLastSpace.length > 0) Int.Companion.MAX_VALUE else noInputCounts!![Suggestion.Companion.TYPE_COMMAND]

        for (cmd in cmds) {
            if (canInsert == 0 || Thread.currentThread().isInterrupted()) return
            if (isHiddenCommandName(cmd.javaClass.getSimpleName())) continue
            if (HIDDEN_SUGGESTION_COMMAND.equals(
                    cmd.javaClass.getSimpleName(),
                    ignoreCase = true
                )
            ) continue

            if (info.cmdPrefs.getPriority(cmd) >= minCmdPriority) {
                val args = cmd.argType()
                val exec = args == null || args.size == 0

                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        cmd.javaClass.getSimpleName(),
                        exec && clickToLaunch,
                        Suggestion.Companion.TYPE_COMMAND
                    )
                )
                canInsert--
            }
        }
    }

    private fun suggestColor(
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        if (afterLastSpace == null || afterLastSpace.length == 0 || (afterLastSpace.length == 1 && afterLastSpace.get(
                0
            ) != '#')
        ) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "#",
                    false,
                    Suggestion.Companion.TYPE_COLOR
                )
            )
        }
    }

    private fun suggestApp(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        suggestApp(info.appsManager.shownApps(), suggestions, afterLastSpace, beforeLastSpace, true)
    }

    private fun suggestHiddenApp(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        suggestApp(
            info.appsManager.hiddenApps(),
            suggestions,
            afterLastSpace,
            beforeLastSpace,
            false
        )
    }

    private fun suggestAllPackages(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        val apps: MutableList<LaunchInfo> = ArrayList<LaunchInfo>(info.appsManager.shownApps())
        apps.addAll(info.appsManager.hiddenApps())
        suggestApp(apps, suggestions, afterLastSpace, beforeLastSpace, true)
    }

    private fun suggestDefaultApp(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "most_used",
                false,
                Suggestion.Companion.TYPE_PERMANENT
            )
        )
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "null",
                false,
                Suggestion.Companion.TYPE_PERMANENT
            )
        )

        suggestApp(info.appsManager.shownApps(), suggestions, afterLastSpace, beforeLastSpace, true)
    }

    private fun suggestApp(
        apps: MutableList<LaunchInfo>?,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?,
        canClickToLaunch: Boolean
    ) {
        var apps = apps
        if (apps == null || apps.size == 0) return

        apps = ArrayList<LaunchInfo>(apps)

        var canInsert = counts!![Suggestion.Companion.TYPE_APP]
        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (l in apps) {
                if (canInsert == 0) return
                canInsert--

                suggestions.add(
                    SuggestionsManager.Suggestion(
                        beforeLastSpace,
                        l.publicLabel!!,
                        canClickToLaunch && clickToLaunch,
                        Suggestion.Companion.TYPE_APP,
                        l
                    )
                )
            }
        } else {
            val counter = quickCompare(
                afterLastSpace,
                apps,
                suggestions,
                beforeLastSpace,
                canInsert,
                canClickToLaunch && clickToLaunch,
                Suggestion.Companion.TYPE_APP,
                canClickToLaunch && clickToLaunch
            )
            if (canInsert - counter <= 0) return

            val infos = CompareObjects.topMatchesWithDeadline<LaunchInfo?>(
                LaunchInfo::class.java,
                afterLastSpace,
                apps.size,
                apps,
                canInsert - counter,
                suggestionsDeadline,
                SPLITTERS,
                algInstance,
                alg
            )
            for (i in infos) {
                if (i == null) break

                if (canInsert == 0) return
                canInsert--

                suggestions.add(
                    SuggestionsManager.Suggestion(
                        beforeLastSpace,
                        i.publicLabel!!,
                        canClickToLaunch && clickToLaunch,
                        Suggestion.Companion.TYPE_APP,
                        if (canClickToLaunch && clickToLaunch) i else null
                    )
                )
            }
        }
    }

    private fun suggestConfigEntry(
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        if (xmlPrefsEntrys == null) {
            xmlPrefsEntrys = ArrayList<XMLPrefsSave>()

            for (element in XMLPrefsRoot.entries.toTypedArray()) xmlPrefsEntrys!!.addAll(
                element.enums
            )

            Collections.addAll<XMLPrefsSave>(xmlPrefsEntrys, *Apps.values())
            Collections.addAll<XMLPrefsSave>(xmlPrefsEntrys, *Notifications.values())
            Collections.addAll<XMLPrefsSave>(xmlPrefsEntrys, *Rss.values())
            Collections.addAll<XMLPrefsSave>(xmlPrefsEntrys, *Reply.values())
        }

        val list: MutableList<XMLPrefsSave> = ArrayList<XMLPrefsSave>(xmlPrefsEntrys!!)

        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (s in list) {
                val sg = SuggestionsManager.Suggestion(
                    beforeLastSpace,
                    s.label()!!,
                    false,
                    Suggestion.Companion.TYPE_COMMAND
                )
                suggestions.add(sg)
            }
        } else {
            val counter = quickCompare(
                afterLastSpace,
                list,
                suggestions,
                beforeLastSpace,
                suggestionsPerCategory,
                false,
                Suggestion.Companion.TYPE_COMMAND,
                false
            )
            if (suggestionsPerCategory - counter <= 0) return

            val saves = CompareObjects.topMatchesWithDeadline<XMLPrefsSave?>(
                XMLPrefsSave::class.java,
                afterLastSpace,
                list.size,
                list,
                suggestionsPerCategory - counter,
                suggestionsDeadline,
                XML_PREFS_SPLITTERS,
                algInstance,
                alg
            )
            for (s in saves) {
                suggestions.add(
                    SuggestionsManager.Suggestion(
                        beforeLastSpace,
                        s.label()!!,
                        false,
                        Suggestion.Companion.TYPE_COMMAND
                    )
                )
            }
        }
    }

    private fun suggestConfigFile(
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        var afterLastSpace = afterLastSpace
        if (xmlPrefsFiles == null) {
            xmlPrefsFiles = ArrayList<String>(
                mutableListOf<String>(
                    "theme.xml",
                    "ui.xml",
                    "behavior.xml",
                    "cmd.xml",
                    "suggestions.xml",
                    "toolbar.xml",
                    "notifications.xml",
                    "apps.xml",
                    "rss.xml"
                )
            )
        }

        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (s in xmlPrefsFiles!!) {
                val sg = Suggestion(
                    beforeLastSpace,
                    s,
                    false,
                    Suggestion.Companion.TYPE_CONFIGFILE,
                    afterLastSpace
                )
                suggestions.add(sg)
            }
        } else {
            afterLastSpace = afterLastSpace.trim { it <= ' ' }.lowercase(Locale.getDefault())
            for (s in xmlPrefsFiles!!) {
                if (Thread.currentThread().isInterrupted()) return

                if (s.startsWith(afterLastSpace)) {
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            s,
                            false,
                            Suggestion.Companion.TYPE_CONFIGFILE,
                            afterLastSpace
                        )
                    )
                }
            }
        }
    }

    private fun suggestAppGroup(
        pack: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        val groups: MutableList<AppsManager.Group> =
            ArrayList<AppsManager.Group>(pack.appsManager.groups)
        if (groups.size == 0) return

        var canInsert: Int
        if (afterLastSpace == null || afterLastSpace.length == 0) {
            canInsert = noInputCounts!![Suggestion.Companion.TYPE_APPGP]
            for (g in groups) {
                if (canInsert == 0) return
                canInsert--

                val sg =
                    Suggestion(beforeLastSpace, g.name(), false, Suggestion.Companion.TYPE_APPGP, g)
                suggestions.add(sg)
            }
        } else {
            canInsert = counts!![Suggestion.Companion.TYPE_APPGP]

            val counter = quickCompare(
                afterLastSpace,
                groups,
                suggestions,
                beforeLastSpace,
                canInsert,
                false,
                Suggestion.Companion.TYPE_APPGP,
                true
            )
            if (canInsert - counter <= 0) return

            val gps = CompareObjects.topMatchesWithDeadline<AppsManager.Group?>(
                AppsManager.Group::class.java,
                afterLastSpace,
                groups.size,
                groups,
                canInsert,
                suggestionsDeadline,
                SPLITTERS,
                algInstance,
                alg
            )
            for (g in gps) {
                if (g == null) break
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        g.name(),
                        false,
                        Suggestion.Companion.TYPE_APPGP,
                        g
                    )
                )
            }
        }
    }

    private fun suggestAppInsideGroup(
        pack: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String,
        keepGroupName: Boolean
    ): Boolean {
        var beforeLastSpace = beforeLastSpace
        var index = -1

        var app: String? = Tuils.EMPTYSTRING

        if (!beforeLastSpace.contains(Tuils.SPACE)) {
            index = Tuils.find(beforeLastSpace, pack.appsManager.groups)
            app = afterLastSpace
            if (!keepGroupName) beforeLastSpace = Tuils.EMPTYSTRING
        } else {
            val split = beforeLastSpace.split(Tuils.SPACE.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            var count = 0
            while (count < split.size) {
                index = Tuils.find(split[count], pack.appsManager.groups)
                if (index != -1) {
                    beforeLastSpace = Tuils.EMPTYSTRING
                    var i = 0
                    while ((if (keepGroupName) i <= count else i < count)) {
                        beforeLastSpace = beforeLastSpace + split[i] + Tuils.SPACE
                        i++
                    }
                    beforeLastSpace = beforeLastSpace.trim { it <= ' ' }

                    count += 1
                    while (count < split.size) {
                        app = app + split[count] + Tuils.SPACE
                        count++
                    }
                    if (afterLastSpace != null) app = app + Tuils.SPACE + afterLastSpace
                    app = app!!.trim { it <= ' ' }

                    break
                }
                count++
            }
        }

        if (index == -1) return false

        val g = pack.appsManager.groups.get(index)

        val apps: MutableList<GroupLaunchInfo> =
            ArrayList<GroupLaunchInfo>(g.members() as MutableList<GroupLaunchInfo?>)
        if (apps.size > 0) {
            if (app == null || app.length == 0) {
                for (o in apps) {
                    suggestions.add(
                        SuggestionsManager.Suggestion(
                            beforeLastSpace,
                            o.publicLabel!!,
                            clickToLaunch,
                            Suggestion.Companion.TYPE_APP,
                            o
                        )
                    )
                }
            } else {
                val counter = quickCompare(
                    app,
                    apps,
                    suggestions,
                    beforeLastSpace,
                    Int.Companion.MAX_VALUE,
                    clickToLaunch,
                    Suggestion.Companion.TYPE_APP,
                    true
                )
                if (counter == apps.size) return true

                val infos = CompareObjects.topMatchesWithDeadline<GroupLaunchInfo?>(
                    GroupLaunchInfo::class.java,
                    app,
                    apps.size,
                    apps,
                    apps.size,
                    suggestionsDeadline,
                    SPLITTERS,
                    algInstance,
                    alg
                )
                for (gli in infos) {
                    if (gli == null) break
                    suggestions.add(
                        SuggestionsManager.Suggestion(
                            beforeLastSpace,
                            gli.publicLabel!!,
                            clickToLaunch,
                            Suggestion.Companion.TYPE_APP,
                            gli
                        )
                    )
                }
            }
        }

        return true
    }

    private fun suggestBoundReplyApp(
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ): Boolean {
        val apps: MutableList<BoundApp> = ArrayList<BoundApp>(ReplyManager.boundApps)
        if (apps.size == 0) return false

        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (b in apps) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        b.label,
                        false,
                        Suggestion.Companion.TYPE_APP
                    )
                )
            }
        } else {
            val counter = quickCompare(
                afterLastSpace,
                apps,
                suggestions,
                beforeLastSpace,
                suggestionsPerCategory,
                false,
                Suggestion.Companion.TYPE_APP,
                false
            )
            if (suggestionsPerCategory - counter <= 0) return true

            val b = CompareObjects.topMatchesWithDeadline<BoundApp?>(
                BoundApp::class.java,
                afterLastSpace,
                apps.size,
                apps,
                suggestionsPerCategory - counter,
                suggestionsDeadline,
                SPLITTERS,
                algInstance,
                alg
            )
            for (ba in b) {
                if (ba == null) break
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        ba.label,
                        false,
                        Suggestion.Companion.TYPE_APP
                    )
                )
            }
        }

        return true
    }

    class Suggestion {
        @get:JvmName("getRawText")
        @set:JvmName("setRawText")
        var text: String?
        var textBefore: String?

        var exec: Boolean
        var type: Int

        var `object`: Any?

        constructor(beforeLastSpace: String?, text: String, exec: Boolean, type: Int) {
            this.textBefore = beforeLastSpace
            this.text = text

            this.exec = exec
            this.type = type

            this.`object` = null
        }

        constructor(beforeLastSpace: String?, text: String, exec: Boolean, type: Int, tag: Any?) {
            this.textBefore = beforeLastSpace
            this.text = text

            this.exec = exec
            this.type = type

            this.`object` = tag
        }

        fun getText(): String? {
            if (type == TYPE_CONTACT_ROOT) {
                return if (textBefore == null || textBefore!!.length == 0) text else textBefore + Tuils.SPACE + text
            } else if (type == TYPE_CONTACT) {
                val c = `object` as Contact

                if (c.numbers.size <= c.selectedNumber) c.selectedNumber = 0

                return textBefore + Tuils.SPACE + c.numbers.get(c.selectedNumber)
            } else if (type == TYPE_PERMANENT) {
                return text
            } else if (type == TYPE_MODULE) {
                return if (`object` is String) `object` as String else text
            } else if (type == TYPE_FILE) {
                var lastWord = if (`object` == null) null else `object` as String
                if (lastWord == null) {
                    lastWord = Tuils.EMPTYSTRING
                }

                val textIsSpecial =
                    (text == File.separator || text == DOUBLE_QUOTES || text == SINGLE_QUOTE)
                val appendLastWord = lastWord.endsWith(File.separator) || textIsSpecial

                //                Tuils.log("-------------");
//                Tuils.log("tspe", textIsSpecial);
//                Tuils.log("tbe", textBefore.replaceAll(" ", "#"));
//                Tuils.log("lw", lastWord);
//                Tuils.log("txt", text);
                return textBefore +
                        Tuils.SPACE +
                        (if (appendLastWord) lastWord else Tuils.EMPTYSTRING) +
                        (if (appendQuotesBeforeFile && !appendLastWord) SINGLE_QUOTE else Tuils.EMPTYSTRING) +
                        text
            }

            if (textBefore == null || textBefore!!.length == 0) {
                return text
            } else {
                return textBefore + Tuils.SPACE + text
            }
        }

        override fun toString(): String {
            return text!!
        }

        companion object {
            //        these suggestions will appear together
            const val TYPE_APP: Int = 0
            const val TYPE_ALIAS: Int = 1
            const val TYPE_COMMAND: Int = 2
            const val TYPE_APPGP: Int = 3

            //        these suggestions will appear only in some special moments, ALONE
            const val TYPE_FILE: Int = 10
            const val TYPE_BOOLEAN: Int = 11
            const val TYPE_SONG: Int = 12
            const val TYPE_CONTACT: Int = 13
            const val TYPE_COLOR: Int = 14
            const val TYPE_PERMANENT: Int = 15
            const val TYPE_CONFIGFILE: Int = 16
            const val TYPE_WEBHOOK_HISTORY: Int = 17
            const val TYPE_CONTACT_ROOT: Int = 18
            const val TYPE_MODULE: Int = 19

            var appendQuotesBeforeFile: Boolean = false
        }
    }

    private inner class CustomComparator(var noInputIndexes: IntArray, var inputIndexes: IntArray) :
        Comparator<Suggestion?> {
        var noInput: Boolean = false

        override fun compare(o1: Suggestion?, o2: Suggestion?): Int {
            if (o1 == null || o2 == null) return 0
            if (o1.type == o2.type) return 0

            if (noInput) {
                return noInputRank(o1.type) - noInputRank(o2.type)
            } else {
                return inputRank(o1.type) - inputRank(o2.type)
            }
        }

        fun noInputRank(type: Int): Int {
            if (type >= 0 && type < noInputIndexes.size) {
                return noInputIndexes[type]
            }
            return noInputIndexes.size
        }

        fun inputRank(type: Int): Int {
            if (type >= 0 && type < inputIndexes.size) {
                return inputIndexes[type]
            }
            return inputIndexes.size
        }
    }

    companion object {
        const val SINGLE_QUOTE: String = "'"
        const val DOUBLE_QUOTES: String = "\""
        private const val HIDDEN_SUGGESTION_COMMAND = "time"
        private const val MAX_LUA_SUGGESTION_ENGINES = 8
    }
}
