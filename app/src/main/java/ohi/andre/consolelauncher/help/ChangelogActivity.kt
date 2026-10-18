package ohi.andre.consolelauncher.help

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout
import ohi.andre.consolelauncher.localization.LocalizedComponentActivity
import ohi.andre.consolelauncher.managers.ChangelogManager
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.LauncherSystemUi
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils

/** Offline release highlights, using the same chrome and geometry as Help. */
class ChangelogActivity : LocalizedComponentActivity() {
    companion object {
        fun open(context: Context) {
            val intent = Intent(context, ChangelogActivity::class.java)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
    private lateinit var root: View
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView
    private lateinit var header: TextView
    private lateinit var close: Button
    private lateinit var windowPanel: HelpWindowPanel
    private var restoredScroll: Int? = null

    override fun onCreate(state: Bundle?) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this)
        super.onCreate(state)
        Tuils.init(this); XMLPrefsManager.loadCommons(this); LauncherSystemUi.applyFullscreen(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val screen = FrameLayout(this).apply { isClickable = true }
        val safeHost = TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        val viewport = FrameLayout(this)
        safeHost.addView(viewport, ViewGroup.LayoutParams(-1, -1))
        root = layoutInflater.inflate(R.layout.offline_help_surface, viewport, false)
        header = root.findViewById(R.id.offline_help_header)
        header.setText(R.string.changelog_pane_title)
        windowPanel = HelpWindowPanel(viewport, root, state, statePrefix = "changelog", floatingInLandscape = false)
        output = root.findViewById(R.id.offline_help_output)
        output.setTextIsSelectable(true)
        scroll = root.findViewById(R.id.offline_help_scroll)
        (root.findViewById<EditText>(R.id.offline_help_input).parent as View).visibility = View.GONE
        val keys = root.findViewById<GridLayout>(R.id.offline_help_keys)
        keys.removeAllViews(); keys.columnCount = 1; keys.rowCount = 1
        keys.layoutParams.height = Tuils.dpToPx(this, 48)
        val bottom = BottomCornerRow(this)
        close = Button(this).apply {
            id = R.id.changelog_close
            setText(R.string.changelog_continue)
            isAllCaps = false
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0); background = null
            setOnClickListener { finish() }
        }
        bottom.addKey(close)
        keys.addView(bottom, GridLayout.LayoutParams().apply { width = 0; height = Tuils.dpToPx(this@ChangelogActivity, 48); columnSpec = GridLayout.spec(0, 1f) })
        windowPanel.reserveBottomCorners(bottom)
        restoredScroll = state?.getInt("changelog_scroll")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(screen) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(screen)
    }

    override fun onResume() {
        super.onResume()
        val offset = restoredScroll ?: scroll.scrollY
        restoredScroll = null
        val textColor = ThemeColorResolver.color(Theme.workspace_panel_text_color)
        output.typeface = Tuils.getTypeface(this); output.textSize = LauncherFontScale.terminalTextSizeSp()
        output.setTextColor(textColor); output.setLineSpacing(Tuils.dpToPx(this, 4).toFloat(), 1f)
        header.typeface = Typeface.create(Tuils.getTypeface(this), Typeface.BOLD)
        header.includeFontPadding = false; header.textSize = AppearanceSettings.outputHeaderTextSize().toFloat()
        header.setTextColor(textColor)
        header.background = TerminalBorderRuntime.tabDrawable(this, ThemeColorResolver.color(Theme.workspace_header_background_color), FrameTarget.OVERLAYS)
        root.findViewById<View>(R.id.offline_help_panel).background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_panel_background_color), ThemeColorResolver.color(Theme.workspace_panel_border_color),
            1.5f, AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
        root.findViewById<View>(R.id.offline_help_main_pane).background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_output_background_color),
            ThemeColorResolver.withMaxAlpha(ThemeColorResolver.color(Theme.workspace_panel_border_color), 210),
            1.2f, AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
        TerminalBorderRuntime.bind(root.findViewById(R.id.offline_help_panel), header)
        close.typeface = Tuils.getTypeface(this); close.textSize = LauncherFontScale.terminalTextSizeSp()
        close.setTextColor(ThemeColorResolver.color(Theme.workspace_control_text_color))
        val text = SpannableStringBuilder()
        HelpText.title(text, getString(R.string.changelog_release_heading, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        HelpText.body(text, ohi.andre.consolelauncher.localization.LanguagePacks.bundledText(resources, R.string.changelog_release_highlights).toString(),
            ThemeColorResolver.color(Theme.workspace_header_background_color), Tuils.dpToPx(this, 12))
        output.text = text
        windowPanel.refreshTheme()
        scroll.post { scroll.scrollTo(0, offset) }
        ChangelogManager.markSeen(this)
    }

    override fun onPause() { windowPanel.persist(); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) {
        out.putInt("changelog_scroll", scroll.scrollY)
        windowPanel.save(out)
        super.onSaveInstanceState(out)
    }
}
