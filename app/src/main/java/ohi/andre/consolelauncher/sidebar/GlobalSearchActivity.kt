package ohi.andre.consolelauncher.sidebar

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.apps.AppsManager
import ohi.andre.consolelauncher.managers.terminal.TerminalManager
import ohi.andre.consolelauncher.managers.suggestions.SuggestionTextWatcher
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager
import ohi.andre.consolelauncher.presentation.HomeAppearance
import ohi.andre.consolelauncher.presentation.HomeChromePresentation
import ohi.andre.consolelauncher.presentation.HomeLayoutPresentation
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter
import ohi.andre.consolelauncher.tuils.interfaces.OnTextChanged

/**
 * Launcher search over whatever app is open: the Search mode bar and results in a see-through window.
 * It borrows the running launcher's apps, commands and settings rather than loading its own; when the
 * launcher is not running it opens the launcher instead.
 */
class GlobalSearchActivity : Activity() {
    private var input: EditText? = null
    private var suggestions: SuggestionsManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pack = LauncherActivity.instance?.uiManager?.pack
        if (pack == null) { openLauncher(); finish(); return }

        setContentView(R.layout.global_search)
        val root = findViewById<LinearLayout>(R.id.global_search_root)
        val results = findViewById<LinearLayout>(R.id.search_mode_results_group).apply {
            // Empty space above the results closes search, like a tap outside a dialog.
            setOnClickListener { finish() }
        }
        val field = findViewById<EditText>(R.id.input_view)
        val prefix = findViewById<TextView>(R.id.prefix_view)
        val submit = findViewById<ImageView>(R.id.submit_tv)

        // TerminalManager and SuggestionsManager expect an output view and a chip row; search shows neither.
        val output = TextView(this)
        val chips = LinearLayout(this)
        root.addView(LinearLayout(this).apply {
            visibility = View.GONE
            addView(ScrollView(context).apply { addView(output) })
            addView(LinearLayout(context).apply { addView(chips) })
        }, 0)

        val appearance = HomeAppearance.saved()
        HomeChromePresentation.styleSurface(findViewById(R.id.input_group), appearance)
        HomeLayoutPresentation.styleSearchInput(root, appearance)
        HomeLayoutPresentation.styleSearchClock(root, appearance)
        // The same bottom spacing as the launcher, so the bar sits at the same height over the keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val margin = if (ime.bottom > 0 && XMLPrefsManager.wasChanged(Ui.keyboard_visible_margin_override, false))
                Ui.keyboard_visible_margin_override else Ui.display_margin_bottom_section
            val bottom = XMLPrefsManager.getListOfIntValues(XMLPrefsManager.get(margin), 4, 0)[3]
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom) + Tuils.mmToPx(resources.displayMetrics, bottom))
            WindowInsetsCompat.CONSUMED
        }

        val terminal = TerminalManager(output, field, prefix, submit, null, null, null, null, this, pack, CommandExecuter(::execute))
        suggestions = SuggestionsManager(chips, pack, terminal, results, true).also { manager ->
            field.addTextChangedListener(SuggestionTextWatcher(manager, OnTextChanged { _, _ -> }))
            manager.setSearchInputActive(true)
            manager.requestSuggestion("")
        }
        field.setHint(R.string.search_mode_hint)
        input = field
    }

    override fun onResume() {
        super.onResume()
        val field = input ?: return
        field.requestFocus()
        field.post { WindowCompat.getInsetsController(window, field).show(WindowInsetsCompat.Type.ime()) }
    }

    override fun onPause() {
        super.onPause()
        // Search is a glance: once another app or the launcher takes over, it is done.
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        suggestions?.dispose()
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    /** App launches open the app; anything else runs in the launcher's terminal, which comes forward to show it. */
    private fun execute(command: String?, obj: Any?) {
        val launcher = LauncherActivity.instance
        if (obj is AppsManager.LaunchInfo) {
            launcher?.commandExecuter()?.execute(command, obj) ?: openLauncher()
        } else if (!command.isNullOrBlank()) {
            openLauncher()
            launcher?.let { home -> home.runOnUiThread { home.uiManager?.executeFromSearch(command) } }
        }
        finish()
    }

    private fun openLauncher() {
        startActivity(Intent(this, LauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }
}
