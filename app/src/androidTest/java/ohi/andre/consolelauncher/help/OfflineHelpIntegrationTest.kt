package ohi.andre.consolelauncher.help

import android.content.Intent
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.widget.*
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.commands.CommandTuils
import org.junit.Assert.*
import org.junit.Test

class OfflineHelpIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun withHelp(id: String? = null, check: (OfflineHelpActivity) -> Unit) {
        val intent = Intent(context, OfflineHelpActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (id != null) intent.putExtra("topic", id)
        val activity = instrumentation.startActivitySync(intent) as OfflineHelpActivity
        instrumentation.waitForIdleSync()
        try { instrumentation.runOnMainSync { check(activity) } }
        finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    private fun renderedBody(body: String): String {
        val text = android.text.SpannableStringBuilder()
        HelpText.body(text, body, 0, 12)
        return text.toString().trimEnd()
    }
    private fun fixturePack(): ohi.andre.consolelauncher.commands.main.MainPack {
        // Avoid constructing account/contact/app managers for read-only command tests.
        val type = Class.forName("sun.misc.Unsafe")
        val field = type.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        val pack = type.getMethod("allocateInstance", Class::class.java).invoke(unsafe,
            ohi.andre.consolelauncher.commands.main.MainPack::class.java) as ohi.andre.consolelauncher.commands.main.MainPack
        pack.context = context; pack.res = context.resources
        pack.commandGroup = CommandGroup(context, "ohi.andre.consolelauncher.commands.main.raw")
        pack.cmdPrefs = ohi.andre.consolelauncher.commands.CommandsPreferences()
        pack.currentDirectory = context.cacheDir
        return pack
    }
    @Test fun undocumentedCommandsShowFallbackInQuickAndManualReference() = withHelp("reference") { activity ->
        val pack = fixturePack().apply { context = activity }
        pack.clear()
        val quick = CommandTuils.parse("help credits", pack)!!.exec(pack)
        assertTrue(quick!!.contains("No command reference is available"))
        val commands = OfflineHelpReference.commands(context)
        val commandIndex = commands.indexOfFirst { it.javaClass.simpleName == "credits" }
        val index = OfflineHelpCatalog.topics.count { it.category == "reference" } + commandIndex
        assertTrue(commandIndex >= 0)
        assertEquals(0, commands[commandIndex].helpRes())
        val input = activity.findViewById<EditText>(R.id.offline_help_input)
        fun enter(value: String) { input.setText(value); activity.findViewById<View>(R.id.offline_help_enter).performClick() }
        repeat(index / 7) { enter("9") }
        enter((index % 7 + 1).toString())
        assertTrue(activity.findViewById<TextView>(R.id.offline_help_output).text.contains("No command reference is available"))
    }
    @Test fun parserDispatchesBareHelpAndTopicsButKeepsCommandQuickReference() {
        ohi.andre.consolelauncher.tuils.Tuils.init(context)
        ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.loadCommons(context)
        val pack = fixturePack()
        fun execute(raw: String): String? {
            pack.clear()
            return CommandTuils.parse(raw, pack)!!.exec(pack)
        }
        val monitor = instrumentation.addMonitor(OfflineHelpActivity::class.java.name, null, false)
        try {
            val quick = execute("help apps")
            assertTrue(quick!!.contains(context.getString(ohi.andre.consolelauncher.commands.main.raw.apps().helpRes())))
            assertEquals(context.getString(R.string.output_commandnotfound), execute("help not-a-command"))
            assertEquals(context.getString(R.string.output_commandnotfound), execute("help shell"))
            assertEquals(context.getString(R.string.output_commandnotfound), execute("help -topic missing"))
            assertNull(execute("help"))
            val bare = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as OfflineHelpActivity
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertTrue(bare.findViewById<TextView>(R.id.offline_help_output).text.contains("Getting started")) }
            pack.context = bare
            instrumentation.addMonitor(monitor)
            assertNull(execute("help -topic terminal-basics"))
            val article = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as OfflineHelpActivity
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertTrue(article.findViewById<TextView>(R.id.offline_help_output).text.contains(renderedBody(context.getString(OfflineHelpCatalog.find("terminal-basics")!!.body)))) }
            pack.context = article
            instrumentation.addMonitor(monitor)
            pack.clear()
            assertNull(CommandTuils.parse("tutorial", pack)!!.exec(pack))
            val tutorial = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as OfflineHelpActivity
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue(tutorial.findViewById<TextView>(R.id.offline_help_output).text.contains("Terminal basics"))
                tutorial.finish(); article.finish(); bare.finish()
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun suggestionsUseCatalogLabelsAndReplaceWholeInputInBothPaths() = withHelp { activity ->
        val pack = fixturePack().apply { context = activity }
        val terminalOutput = TextView(activity)
        val terminalScroll = ScrollView(activity).apply { addView(terminalOutput) }
        val terminalInput = EditText(activity)
        ohi.andre.consolelauncher.managers.TimeManager(activity)
        val dispatched = mutableListOf<String?>()
        val terminal = ohi.andre.consolelauncher.managers.TerminalManager(terminalOutput, terminalInput,
            TextView(activity), null, null, null, null, null, activity, pack,
            ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter { command, _ -> dispatched.add(command) })
        val suggestions = LinearLayout(activity)
        LinearLayout(activity).addView(suggestions)
        val manager = ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager(suggestions, pack, terminal)
        val guide = context.getSharedPreferences("retui_guide", android.content.Context.MODE_PRIVATE)
        val wasActive = guide.contains("active")
        val savedActive = guide.getBoolean("active", false)
        try {
            guide.edit().putBoolean("active", true).commit()
            val roots = OfflineHelpCatalog.topics.filter { it.category == null }
            listOf("" to "help", "help" to "").forEach { (before, last) ->
                val previousDispatches = dispatched.size
                val suggestions = manager.getSuggestions(before, last).filterNotNull()
                assertEquals(previousDispatches, dispatched.size)
                assertEquals(roots.map { context.getString(it.title) }, suggestions.map { it.text })
                assertEquals(roots.map { "help -topic ${it.id}" }, suggestions.map { it.`object` })
                terminal.input = "help "
                manager.clickSuggestion(suggestions.first())
                assertEquals("help -topic getting-started", dispatched.last())
            }
            val results = manager.getSearchModeResults("help")
            assertEquals(roots.map { context.getString(it.title) }, results.map { it.title })
            assertTrue(manager.getSearchModeResults("help -topic terminal").any { it.title == "Terminal basics" })
            assertTrue(manager.getSuggestions("help", "cal").filterNotNull().any { it.`object` == "help calc" })
            assertTrue(manager.getSearchModeResults("help cal").any { it.title == "help calc" })
            val generic = ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager.Suggestion(null,
                "help", false, ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager.Suggestion.TYPE_COMMAND)
            val count = dispatched.size
            manager.clickSuggestion(generic)
            assertEquals(count, dispatched.size)
            assertEquals("help ", terminal.input)
            terminal.restoreState(ohi.andre.consolelauncher.managers.TerminalManager.State("launcher draft", "launcher output", listOf("help apps"), 0, false))
            val saved = terminal.snapshotState()
            val prefsBefore = guide.all.toMap()
            activity.findViewById<EditText>(R.id.offline_help_input).setText("1")
            activity.findViewById<View>(R.id.offline_help_enter).performClick()
            activity.onBackPressedDispatcher.onBackPressed()
            assertEquals(saved.input, terminal.input)
            assertEquals(saved.output.toString(), terminal.terminalText)
            assertEquals(saved.commands, terminal.snapshotState().commands)
            assertEquals(saved.historyPosition, terminal.snapshotState().historyPosition)
            assertEquals(prefsBefore, guide.all)
        } finally {
            if (wasActive) guide.edit().putBoolean("active", savedActive).commit() else guide.edit().remove("active").commit()
        }
    }
    @Test fun typingBareHelpDoesNotOpenUntilTerminalEnter() {
        val host = instrumentation.startActivitySync(Intent(context, OfflineHelpActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OfflineHelpActivity
        instrumentation.waitForIdleSync()
        val monitor = instrumentation.addMonitor(OfflineHelpActivity::class.java.name, null, false)
        var opened: OfflineHelpActivity? = null
        try {
            instrumentation.runOnMainSync {
                val pack = fixturePack().apply { context = host }
                val input = EditText(host).apply { setSingleLine(true) }
                val output = TextView(host)
                ScrollView(host).addView(output)
                ohi.andre.consolelauncher.managers.TimeManager(host)
                val terminal = ohi.andre.consolelauncher.managers.TerminalManager(output, input,
                    TextView(host), null, null, null, null, null, host, pack,
                    ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter { command, _ ->
                        pack.clear(); CommandTuils.parse(command!!, pack)!!.exec(pack)
                    })
                val suggestions = LinearLayout(host)
                LinearLayout(host).addView(suggestions)
                val manager = ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager(suggestions, pack, terminal)
                terminal.input = "help"
                manager.getSuggestions("", "help")
                assertEquals(0, monitor.hits)
                input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
            }
            opened = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as? OfflineHelpActivity
            assertNotNull("Enter must open the manual", opened)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertTrue(opened!!.findViewById<TextView>(R.id.offline_help_output).text.contains(context.getString(OfflineHelpCatalog.topics.first().title))) }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { opened?.finish(); host.finish() }
        }
    }
    @Test fun everyAuthoredArticleIsReachableThroughPagedReadOnlyCategories() = withHelp { activity ->
        val input = activity.findViewById<EditText>(R.id.offline_help_input)
        val output = activity.findViewById<TextView>(R.id.offline_help_output)
        fun enter(value: Int) { input.setText(value.toString()); activity.findViewById<View>(R.id.offline_help_enter).performClick() }
        fun configSnapshot(): Map<String, List<Byte>> = ohi.andre.consolelauncher.tuils.Tuils.getFolder()
            .walkTopDown().filter { it.isFile && it.extension == "xml" }
            .associate { it.relativeTo(ohi.andre.consolelauncher.tuils.Tuils.getFolder()).path to it.readBytes().toList() }
        val before = configSnapshot()
        val roots = OfflineHelpCatalog.topics.filter { it.category == null }
        roots.forEachIndexed { rootIndex, root ->
            repeat(rootIndex / 7) { enter(9) }
            enter(rootIndex % 7 + 1)
            val articles = OfflineHelpCatalog.topics.filter { it.category == root.id }
            articles.forEachIndexed { index, topic ->
                if (index > 0 && index % 7 == 0) enter(9)
                enter(index % 7 + 1)
                assertTrue("Missing ${topic.id}", output.text.contains(renderedBody(activity.getString(topic.body))))
                activity.onBackPressedDispatcher.onBackPressed()
                assertTrue(output.text.contains(activity.getString(topic.title)))
            }
            activity.onBackPressedDispatcher.onBackPressed()
            repeat(rootIndex / 7) { enter(8) }
        }
        assertEquals(before, configSnapshot())
    }
    @Test fun referenceIncludesAuthoredArticlesBeforeLiveCommands() {
        val field = OfflineHelpCatalog::class.java.getDeclaredField("topics").apply { isAccessible = true }
        val original = OfflineHelpCatalog.topics
        val fixture = HelpTopic("reference-fixture", R.string.offline_help_article_terminal_basics_title,
            R.string.offline_help_article_terminal_basics_body, "reference")
        field.set(null, original.filterNot { it.category == "reference" } + fixture)
        try {
            withHelp("reference") { activity ->
                val input = activity.findViewById<EditText>(R.id.offline_help_input)
                val output = activity.findViewById<TextView>(R.id.offline_help_output)
                assertTrue(output.text.contains(activity.getString(fixture.title)))
                input.setText("1"); activity.findViewById<View>(R.id.offline_help_enter).performClick()
                assertTrue(output.text.contains(renderedBody(activity.getString(fixture.body))))
                input.setText("3"); activity.findViewById<View>(R.id.offline_help_enter).performClick()
                assertTrue(output.text.contains(OfflineHelpReference.text(activity, OfflineHelpReference.commands(activity).first())))
            }
        } finally { field.set(null, original) }
    }
    @Test fun numericDockReplacesAndDeletesReversedSelection() = withHelp { activity ->
        val input = activity.findViewById<EditText>(R.id.offline_help_input)
        val dock = activity.findViewById<GridLayout>(R.id.offline_help_keys)
        input.setText("123"); input.setSelection(2, 0)
        dock.getChildAt(0).performClick()
        assertEquals("13", input.text.toString())
        input.setSelection(2, 0)
        dock.getChildAt(5).performClick()
        assertEquals("", input.text.toString())
    }
    @Test fun invalidChoiceReportsFeedbackWithoutLeavingPageOrLosingDraft() = withHelp { activity ->
        val input = activity.findViewById<EditText>(R.id.offline_help_input)
        val output = activity.findViewById<TextView>(R.id.offline_help_output)
        val before = output.text.toString()
        input.setText("42")
        activity.findViewById<View>(R.id.offline_help_enter).performClick()
        assertNotNull(input.error)
        assertEquals("42", input.text.toString())
        assertEquals(before, output.text.toString())
        input.setText("1")
        activity.findViewById<View>(R.id.offline_help_enter).performClick()
        assertNull(input.error)
    }
    @Test fun numericDockAndHierarchyAreReadOnly() = withHelp { activity ->
        val output = activity.findViewById<TextView>(R.id.offline_help_output)
        val input = activity.findViewById<EditText>(R.id.offline_help_input)
        val dock = activity.findViewById<GridLayout>(R.id.offline_help_keys)
        assertEquals(12, dock.childCount)
        assertEquals(listOf("1", "2", "3", "4", "5", "⌫", "6", "7", "8", "9", "0", "↵"),
            (0 until dock.childCount).map { (dock.getChildAt(it) as TextView).text.toString() })
        for (index in 0 until dock.childCount) assertEquals(ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, 48), dock.getChildAt(index).height)
        dock.getChildAt(0).performClick(); assertEquals("1", input.text.toString())
        dock.getChildAt(5).performClick(); assertEquals("", input.text.toString())
        assertNull(input.background)
        assertFalse(input.showSoftInputOnFocus)
        assertTrue(output.text.contains("Getting started"))
        val links = (output.text as Spanned).getSpans(0, output.length(), ClickableSpan::class.java)
        links.first().onClick(output)
        assertTrue(output.text.contains("Terminal basics"))
        input.setText("1")
        input.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
        input.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
        assertTrue(output.text.contains(renderedBody(context.getString(OfflineHelpCatalog.find("terminal-basics")!!.body))))
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(output.text.contains("Terminal basics"))
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(output.text.contains("Getting started"))
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(activity.isFinishing)
    }
    @Test fun recreationKeepsTopicDraftSelectionAndLandscapePromptVisible() {
        val activity = instrumentation.startActivitySync(Intent(context, OfflineHelpActivity::class.java)
            .putExtra("topic", "terminal-basics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OfflineHelpActivity
        instrumentation.waitForIdleSync()
        val monitor = instrumentation.addMonitor(OfflineHelpActivity::class.java.name, null, false)
        var recreated: OfflineHelpActivity? = null
        try {
            instrumentation.runOnMainSync {
                activity.findViewById<EditText>(R.id.offline_help_input).apply { setText("19"); setSelection(0, 1) }
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            recreated = instrumentation.waitForMonitorWithTimeout(monitor, 10000) as? OfflineHelpActivity
            assertNotNull(recreated)
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(400)
            instrumentation.runOnMainSync {
                val current = recreated!!
                val field = current.findViewById<EditText>(R.id.offline_help_input)
                assertEquals("19", field.text.toString()); assertEquals(0, field.selectionStart); assertEquals(1, field.selectionEnd)
                assertTrue(current.findViewById<TextView>(R.id.offline_help_output).text.contains(renderedBody(context.getString(OfflineHelpCatalog.find("terminal-basics")!!.body))))
                val visible = android.graphics.Rect()
                assertTrue(field.getGlobalVisibleRect(visible)); assertEquals(field.height, visible.height())
            }
            java.io.File(context.cacheDir, "offline-help-landscape.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            instrumentation.runOnMainSync {
                recreated!!.findViewById<EditText>(R.id.offline_help_input).apply { setSelection(length()); clearFocus() }
            }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val title = recreated!!.findViewById<TextView>(R.id.offline_help_output).text.lineSequence().first().toString()
                assertEquals("Back reaches the category, not a matching phrase in the article", "Getting started", title)
            }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val title = recreated!!.findViewById<TextView>(R.id.offline_help_output).text.lineSequence().first().toString()
                assertEquals("Back reaches root navigation without a duplicate title", "1 Getting started", title)
                recreated!!.findViewById<EditText>(R.id.offline_help_input).setText("9")
                recreated!!.findViewById<View>(R.id.offline_help_enter).performClick()
                recreated!!.findViewById<EditText>(R.id.offline_help_input).apply { setText("78"); setSelection(1) }
            }
            instrumentation.addMonitor(monitor)
            instrumentation.runOnMainSync { recreated!!.recreate() }
            recreated = instrumentation.waitForMonitorWithTimeout(monitor, 10000) as? OfflineHelpActivity
            assertNotNull(recreated)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val current = recreated!!
                val field = current.findViewById<EditText>(R.id.offline_help_input)
                val pageText = current.findViewById<TextView>(R.id.offline_help_output).text.toString()
                assertTrue("Recreated second root page: $pageText", pageText.contains("8 Previous page"))
                assertFalse(current.findViewById<TextView>(R.id.offline_help_output).text.contains("Getting started"))
                assertEquals("78", field.text.toString()); assertEquals(1, field.selectionStart)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { recreated?.finish(); activity.finish() }
        }
    }
    @Test fun referenceLastPageAndArticleBoundariesDoNotWrap() = withHelp("reference") { activity ->
        val input = activity.findViewById<EditText>(R.id.offline_help_input)
        val output = activity.findViewById<TextView>(R.id.offline_help_output)
        fun enter(value: String) { input.setText(value); activity.findViewById<View>(R.id.offline_help_enter).performClick() }
        val commands = OfflineHelpReference.commands(context)
        val entryCount = commands.size + OfflineHelpCatalog.topics.count { it.category == "reference" }
        repeat((entryCount - 1) / 7) { enter("9") }
        assertFalse(output.text.contains("9 Next page"))
        val finalPage = output.text.toString(); enter("9"); assertEquals(finalPage, output.text.toString())
        enter(((entryCount - 1) % 7 + 1).toString())
        assertTrue(output.text.contains(OfflineHelpReference.text(context, commands.last())))
        assertFalse(output.text.contains("3 Next topic"))
        val last = output.text.toString(); enter("3"); assertEquals(last, output.text.toString())
    }
    @Test fun screenshotsCaptureRenderedRootTopicAndReference() {
        listOf("root" to null, "topic" to "terminal-basics", "reference" to "reference").forEach { (name, topic) ->
            val intent = Intent(context, OfflineHelpActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("topic", topic)
            val activity = instrumentation.startActivitySync(intent) as OfflineHelpActivity
            try {
                instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(500)
                java.io.File(context.cacheDir, "offline-help-$name.png").outputStream().use {
                    instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(200) }
        }
    }
    @Test fun runtimeReferenceUsesVisibleCommandHelpAndPages() = withHelp("reference") { activity ->
        val group = CommandGroup(context, "ohi.andre.consolelauncher.commands.main.raw")
        val expected = group.commands.filterNot { CommandTuils.isHiddenCommandName(it.javaClass.simpleName) }
        val reference = OfflineHelpReference.commands(context)
        assertEquals(expected.map { it.javaClass.simpleName }.sorted(), reference.map { it.javaClass.simpleName })
        val output = activity.findViewById<TextView>(R.id.offline_help_output)
        val input = activity.findViewById<EditText>(R.id.offline_help_input)
        assertTrue(output.text.contains("9 Next page"))
        input.setText("9"); activity.findViewById<View>(R.id.offline_help_enter).performClick()
        assertTrue(output.text.contains("8 Previous page"))
        input.setText("1"); activity.findViewById<View>(R.id.offline_help_enter).performClick()
        assertTrue(output.text.contains(let { val authored = OfflineHelpCatalog.topics.filter { it.category == "reference" };
            if (authored.size > 7) renderedBody(context.getString(authored[7].body)) else renderedBody(OfflineHelpReference.text(context, reference[7 - authored.size])) }))
    }
}
