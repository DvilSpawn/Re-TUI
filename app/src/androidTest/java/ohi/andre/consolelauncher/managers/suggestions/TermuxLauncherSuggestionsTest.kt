package ohi.andre.consolelauncher.managers.suggestions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.commands.CommandsPreferences
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.help.OfflineHelpActivity
import ohi.andre.consolelauncher.managers.AliasManager
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceLauncherManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class TermuxLauncherSuggestionsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun <T> emptyInstance(type: Class<T>): T {
        val unsafeType = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeType.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        @Suppress("UNCHECKED_CAST")
        return unsafeType.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type) as T
    }

    @Test fun classicAndSearchSuggestionsReplaceTypedPrefixesWithWorkspaceLaunches() {
        check(context.packageName.endsWith("debug")) { "Use a disposable test application" }
        assumeTrue(ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
        Tuils.init(context)
        XMLPrefsManager.loadCommons(context)
        val guide = context.getSharedPreferences("retui_guide", Context.MODE_PRIVATE)
        val hadActive = guide.contains("active")
        val active = guide.getBoolean("active", false)
        val oldNotes = TermuxWorkspaceLauncherManager.resolve(context, "notes")?.command
        val activity = instrumentation.startActivitySync(Intent(context, OfflineHelpActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OfflineHelpActivity
        instrumentation.waitForIdleSync()
        try {
            guide.edit().putBoolean("active", false).commit()
            assertTrue(TermuxWorkspaceLauncherManager.save(context, "notes", "nvim ~/notes"))
            instrumentation.runOnMainSync {
                val pack = emptyInstance(MainPack::class.java).apply {
                    context = activity
                    res = activity.resources
                    currentDirectory = activity.cacheDir
                    commandGroup = CommandGroup(activity, "ohi.andre.consolelauncher.commands.main.raw")
                    cmdPrefs = CommandsPreferences()
                    appsManager = emptyInstance(AppsManager::class.java).apply { groups = ArrayList() }
                    aliasManager = emptyInstance(AliasManager::class.java).apply {
                        AliasManager::class.java.getDeclaredField("aliases").apply { isAccessible = true }
                            .set(this, ArrayList<AliasManager.Alias>())
                    }
                }
                val output = TextView(activity)
                ScrollView(activity).addView(output)
                val input = EditText(activity)
                TimeManager(activity)
                val dispatched = mutableListOf<String?>()
                val terminal = TerminalManager(output, input, TextView(activity), null, null, null,
                    null, null, activity, pack, CommandExecuter { command, _ -> dispatched.add(command) })
                val chips = LinearLayout(activity)
                LinearLayout(activity).addView(chips)
                val manager = SuggestionsManager(chips, pack, terminal)
                listOf("sh" to "shell", "no" to "notes").forEach { (prefix, id) ->
                    val action = "tmux launch $id"
                    val classic = manager.getSuggestions("", prefix).filterNotNull().single { it.`object` == action }
                    assertEquals(id.replaceFirstChar { it.uppercase() }, classic.text)
                    terminal.input = prefix
                    manager.clickSuggestion(classic)
                    assertEquals(action, dispatched.last())
                    val search = manager.getSearchModeResults(prefix).single {
                        (it.payload as? SuggestionsManager.Suggestion)?.`object` == action
                    }
                    terminal.input = prefix
                    manager.clickSuggestion(search.payload as SuggestionsManager.Suggestion)
                    assertEquals(action, dispatched.last())
                }
                val notesCommand = manager.getSearchModeResults("notes").single {
                    it.title == "notes" && (it.payload as? SuggestionsManager.Suggestion)?.type == SuggestionsManager.Suggestion.TYPE_COMMAND
                }
                val notesSuggestion = notesCommand.payload as SuggestionsManager.Suggestion
                assertEquals("notes", notesSuggestion.getText())
                assertFalse(notesSuggestion.exec)
            }
        } finally {
            if (oldNotes == null) TermuxWorkspaceLauncherManager.remove(context, "notes")
            else TermuxWorkspaceLauncherManager.save(context, "notes", oldNotes)
            if (hadActive) guide.edit().putBoolean("active", active).commit() else guide.edit().remove("active").commit()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
