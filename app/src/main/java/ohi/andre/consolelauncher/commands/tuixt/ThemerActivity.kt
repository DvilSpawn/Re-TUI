package ohi.andre.consolelauncher.commands.tuixt

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.PorterDuff
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ConfirmAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ContentFactory
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.FormAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.FormField
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.FormValidator
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.InputAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ItemAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout.addFoldAwareHost
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.accentColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.borderColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.rect
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.surfaceColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleInput
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleIconButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleListItem
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.stylePanel
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleScreen
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleToggle
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.textColor
import ohi.andre.consolelauncher.managers.BackupManager
import ohi.andre.consolelauncher.managers.FocusFrictionStyle
import ohi.andre.consolelauncher.managers.LockdownManager
import ohi.andre.consolelauncher.managers.PresetManager
import ohi.andre.consolelauncher.managers.RetuiCreditManager
import ohi.andre.consolelauncher.managers.ToolbarShortcutManager
import ohi.andre.consolelauncher.managers.ToolbarShortcutManager.IconChoice
import ohi.andre.consolelauncher.managers.ToolbarShortcutManager.clearSlot
import ohi.andre.consolelauncher.managers.ToolbarShortcutManager.icons
import ohi.andre.consolelauncher.managers.ToolbarShortcutManager.saveSlot
import ohi.andre.consolelauncher.managers.ToolbarShortcutManager.slot
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.get
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.set
import ohi.andre.consolelauncher.managers.settings.MusicSettings.preferredPackage
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.requestNoTitleIfFullscreen
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.io.FileOutputStream
import java.io.FilenameFilter
import java.util.Arrays
import java.util.Collections
import java.util.Locale
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayList
import java.util.Comparator
import ohi.andre.consolelauncher.managers.settings.MusicSettings
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.tasker.TaskerIntegrationManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.wallpaper.RetuiWallpaperActivity
import ohi.andre.consolelauncher.wallpaper.RetuiWallpaperService
import ohi.andre.consolelauncher.tuils.LauncherSystemUi

class ThemerActivity : AppCompatActivity() {
    private var screenRoot: View? = null
    private var panelRoot: LinearLayout? = null
    private var recyclerView: RecyclerView? = null
    private var header: TextView? = null
    private var supportFooter: LinearLayout? = null
    private var sectionsAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = null
    private val sectionItems: MutableList<String> = ArrayList<String>()
    private val sectionBackStack = ArrayDeque<String>()
    private var section: String? = null
    private var pendingBackupPassword: String? = null
    private var backupExportPending = false
    private var pendingRestoreUri: Uri? = null
    private var pendingShareablePresetName: String? = null
    private var pendingFontSizeOffset: Int? = null
    private var pendingUseSystemFont: Boolean? = null
    private var pendingFontFileName: String? = null
    private var pendingTypographySizes: MutableMap<XMLPrefsSave, Int>? = null
    private var pendingFrameTarget: FrameTarget? = null
    private var frameEditSession: FrameManager.EditSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        requestNoTitleIfFullscreen(this)
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        applyFullscreen(this)

        section = if (getIntent() != null) getIntent().getStringExtra(EXTRA_SECTION) else null
        if (section == null || section!!.length == 0) {
            section = SECTION_HOME
        }

        val screen = FrameLayout(this)
        screenRoot = screen
        styleScreen(this, screen)
        screen.setFitsSystemWindows(true)
        val contentHost = addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)

        val root = LinearLayout(this)
        panelRoot = root
        root.setOrientation(LinearLayout.VERTICAL)
        root.setPadding(dp(this, 14f), dp(this, 50f), dp(this, 14f), dp(this, 14f))
        stylePanel(this, root)

        val panelLeft = dp(this, 28f)
        val panelTop = dp(this, 34f)
        val panelParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        panelParams.setMargins(panelLeft, panelTop, dp(this, 28f), dp(this, 28f))
        contentHost.addView(root, panelParams)

        header = TextView(this)
        header!!.setText(getHeaderText(section))
        TuixtTheme.styleHeader(this, header!!)
        val headerParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        headerParams.gravity = Gravity.TOP or Gravity.START
        headerParams.leftMargin = panelLeft + dp(this, 38f)
        headerParams.topMargin = panelTop - dp(this, 11f)
        contentHost.addView(header, headerParams)

        recyclerView = RecyclerView(this)
        recyclerView!!.setLayoutParams(
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        recyclerView!!.setLayoutManager(LinearLayoutManager(this))

        sectionItems.clear()
        sectionItems.addAll(getItemsForSection(section))

        sectionsAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemViewType(position: Int): Int =
                when {
                    sectionItems[position] == FONT_SCALE_PANEL -> VIEW_TYPE_FONT_SCALE
                    sectionItems[position] == FRAME_PANEL -> VIEW_TYPE_FRAME_PANEL
                    section == SECTION_FONTS && isFontFileName(sectionItems[position]) -> VIEW_TYPE_FONT
                    else -> VIEW_TYPE_STANDARD
                }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                if (viewType == VIEW_TYPE_FONT_SCALE) {
                    return createFontScaleViewHolder(parent)
                }
                if (viewType == VIEW_TYPE_FRAME_PANEL) {
                    return FramePanelViewHolder(LinearLayout(parent.context).apply {
                        orientation = LinearLayout.VERTICAL
                    })
                }
                if (viewType == VIEW_TYPE_FONT) {
                    val row = LinearLayout(parent.context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val label = TextView(parent.context)
                    val delete = TextView(parent.context)
                    row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(View(parent.context), LinearLayout.LayoutParams(dp(parent.context, 8f), 1))
                    row.addView(delete, LinearLayout.LayoutParams(dp(parent.context, 58f), dp(parent.context, 48f)))
                    return FontViewHolder(row, label, delete)
                }
                val tv = TextView(parent.getContext())
                tv.setLayoutParams(
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                return ViewHolder(tv)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val fileName = sectionItems.get(position)
                if (holder is FontScaleViewHolder) {
                    bindFontScalePanel(holder)
                    return
                }
                if (holder is FontViewHolder) {
                    bindFontRow(holder, fileName)
                    return
                }
                if (holder is FramePanelViewHolder) {
                    bindFramePanel(holder)
                    return
                }
                val itemView = holder.itemView as TextView
                itemView.setText(fileName.uppercase(Locale.getDefault()))
                val selected = section == SECTION_FONTS &&
                    fileName == "Default (System Font)" &&
                    pendingUseSystemFont == true
                styleListItem(this@ThemerActivity, itemView, selected)
                val params = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, dp(this@ThemerActivity, 8f))
                itemView.setLayoutParams(params)
                holder.itemView.setOnClickListener(View.OnClickListener { _: View? ->
                    if (fileName == "Appearance") {
                        openSection(SECTION_APPEARANCE)
                    } else if (fileName == "Behavior") {
                        openSection(SECTION_BEHAVIOR)
                    } else if (fileName == "Sounds") {
                        openSoundsSettings()
                    } else if (fileName == "Personalization") {
                        openSection(SECTION_PERSONALIZATION)
                    } else if (fileName == "ASCII Settings") {
                        openAsciiSettings()
                    } else if (fileName == "Integrations") {
                        openSection(SECTION_INTEGRATIONS)
                    } else if (fileName == "System & Support") {
                        openSection(SECTION_SYSTEM)
                    } else if (fileName == "Open Wallpaper Picker") {
                        launchWallpaperPicker()
                    } else if (fileName == "Open Live Wallpaper Picker") {
                        launchLiveWallpaperPicker()
                    } else if (fileName == "RETUI WALLPAPER") {
                        openSettingsChild(Intent(this@ThemerActivity, RetuiWallpaperActivity::class.java))
                    } else if (fileName.startsWith("Preferred Music App")) {
                        showPreferredMusicAppPicker()
                    } else if (fileName.startsWith("Tasker Integration")) {
                        showTaskerIntegrationDialog()
                    } else if (fileName == "Fonts") {
                        openSection(SECTION_FONTS)
                    } else if (fileName == "Typography") {
                        openSection(SECTION_TYPOGRAPHY)
                    } else if (fileName == "Presets") {
                        openSection(SECTION_PRESETS)
                    } else if (fileName == "Frames") {
                        openSection(SECTION_FRAMES)
                    } else if (section == SECTION_FRAMES) {
                        return@OnClickListener
                    } else if (section == SECTION_PRESETS && fileName == "Save Current as Preset") {
                        showSavePresetInput()
                    } else if (section == SECTION_PRESETS && fileName == "Apply Preset") {
                        openSection(SECTION_PRESET_APPLY)
                    } else if (section == SECTION_PRESETS && fileName == "Remove Preset") {
                        openSection(SECTION_PRESET_REMOVE)
                    } else if (section == SECTION_PRESET_APPLY) {
                        applyPreset(fileName)
                    } else if (section == SECTION_PRESET_REMOVE) {
                        confirmRemovePreset(fileName)
                    } else if (section == SECTION_FONTS && fileName == "Default (System Font)") {
                        applySystemFont()
                    } else if (section == SECTION_FONTS && fileName == "Import Font...") {
                        launchFontImportPicker()
                    } else if (fileName == "Toolbar Buttons") {
                        showToolbarButtonsDialog()
                    } else if (isDystopiaRow(fileName)) {
                        handleDystopiaOptIn()
                    } else if (fileName == "View Crash Log") {
                        val crashFile = File(Tuils.getFolder(), "crash.txt")
                        if (!crashFile.exists() || crashFile.length() == 0L) {
                            Toast.makeText(
                                this@ThemerActivity,
                                "No crash log found.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val intent = Intent(this@ThemerActivity, TuixtActivity::class.java)
                            intent.putExtra(TuixtActivity.PATH, crashFile.getAbsolutePath())
                            openSettingsChild(intent)
                        }
                    } else if (fileName == "Backup") {
                        showBackupProtectionDialog()
                    } else if (fileName == "Create Shareable Configuration") {
                        showShareableConfigurationSourcePicker()
                    } else if (fileName == "Restore") {
                        launchRestorePicker()
                    } else if (fileName == "Rate the App") {
                        openPlayStoreListing()
                    } else if (fileName == "GitHub") {
                        openExternalUrl(GITHUB_URL)
                    } else if (fileName == "Discord") {
                        openExternalUrl(DISCORD_URL)
                    } else if (fileName == "Reddit") {
                        openExternalUrl(REDDIT_URL)
                    } else if (fileName == "Send Feedback") {
                        openFeedbackEmail()
                    } else if (fileName == "Learn More") {
                        openLearnMore()
                    } else {
                        openConfigFile(fileName)
                    }
                })
            }

            override fun getItemCount(): Int {
                return sectionItems.size
            }
        }

        recyclerView!!.setAdapter(sectionsAdapter)

        root.addView(recyclerView)
        supportFooter = buildSupportFooter()
        root.addView(supportFooter)
        updateSupportFooter()
        setContentView(screen)
    }

    override fun onResume() {
        super.onResume()
        applyFullscreen(this)
        screenRoot?.let { styleScreen(this, it) }
        panelRoot?.let { stylePanel(this, it) }
        header?.let { TuixtTheme.styleHeader(this, it) }
        sectionsAdapter?.notifyDataSetChanged()
        supportFooter?.let { footer ->
            for (index in 0 until footer.childCount) {
                val button = footer.getChildAt(index) as? ImageButton ?: continue
                styleIconButton(this, button)
                button.setColorFilter(accentColor(), PorterDuff.Mode.SRC_IN)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFullscreen(this)
        }
    }

    private fun openPlayStoreListing() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_MARKET_URL)))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_WEB_URL)))
        }
    }

    private fun openFeedbackEmail() {
        val gmailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse(FEEDBACK_MAILTO_URI))
        gmailIntent.setPackage(GMAIL_PACKAGE)
        gmailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))

        try {
            startActivity(gmailIntent)
        } catch (e: ActivityNotFoundException) {
            val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse(FEEDBACK_MAILTO_URI))
            emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
            try {
                startActivity(emailIntent)
            } catch (fallbackError: ActivityNotFoundException) {
                Toast.makeText(this, "No email app found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openLearnMore() {
        openExternalUrl(LEARN_MORE_URL)
    }

    private fun openExternalUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No browser app found.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSavePresetInput() {
        TuixtDialog.showInput(
            this,
            "Save Preset",
            "Preset name",
            "Save",
            "Cancel",
            InputAction { value ->
                val name = value?.trim().orEmpty()
                if (name.isNotEmpty()) savePreset(name)
            }
        )
    }

    private fun confirmRemovePreset(name: String) {
        TuixtDialog.showConfirm(
            this,
            "Remove Preset",
            "Remove $name?",
            "Remove",
            "Cancel",
            ConfirmAction {
                try {
                    PresetManager.remove(name)
                    Toast.makeText(this, "Preset removed.", Toast.LENGTH_SHORT).show()
                    openSection(SECTION_PRESET_REMOVE)
                } catch (e: Exception) {
                    Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun savePreset(name: String?) {
        try {
            PresetManager.save(this, name ?: return)
            Toast.makeText(this@ThemerActivity, "Preset saved.", Toast.LENGTH_SHORT)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this@ThemerActivity, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyPreset(name: String?) {
        try {
            PresetManager.apply(name ?: return)

            Toast.makeText(this@ThemerActivity, "Preset applied! Reloading...", Toast.LENGTH_SHORT)
                .show()
            recyclerView!!.postDelayed(Runnable {
                LauncherActivity.preview(this)
            }, 500)
        } catch (e: Exception) {
            Toast.makeText(this@ThemerActivity, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getHeaderText(section: String?): String {
        if (SECTION_APPEARANCE == section) {
            return "Re:T-UI Appearance Settings"
        } else if (SECTION_BEHAVIOR == section) {
            return "Re:T-UI Behavior Settings"
        } else if (SECTION_PERSONALIZATION == section) {
            return "Re:T-UI Personalization Settings"
        } else if (SECTION_INTEGRATIONS == section) {
            return "Re:T-UI Integrations"
        } else if (SECTION_SYSTEM == section) {
            return "Re:T-UI System & Support"
        } else if (SECTION_FONTS == section) {
            return "Re:T-UI Fonts"
        } else if (SECTION_TYPOGRAPHY == section) {
            return "Re:T-UI Typography"
        } else if (SECTION_PRESETS == section) {
            return "Re:T-UI Presets"
        } else if (SECTION_FRAMES == section) {
            return "Re:T-UI Frames"
        } else if (SECTION_PRESET_APPLY == section) {
            return "Apply Preset"
        } else if (SECTION_PRESET_REMOVE == section) {
            return "Remove Preset"
        }
        return "Re:T-UI Settings Hub"
    }

    private fun getItemsForSection(section: String?): MutableList<String> {
        if (SECTION_APPEARANCE == section) {
            return mutableListOf(
                "theme.xml",
                "ui.xml",
                "toolbar.xml",
                "suggestions.xml",
                "Fonts",
                "Presets",
                "Frames",
                "Open Wallpaper Picker",
                "Open Live Wallpaper Picker"
            )
        } else if (SECTION_BEHAVIOR == section) {
            return mutableListOf(
                "Sounds",
                "behavior.xml",
                "apps.xml",
                "notifications.xml",
                "cmd.xml"
            )
        } else if (SECTION_PERSONALIZATION == section) {
            return mutableListOf(
                "RETUI WALLPAPER",
                dystopiaRowLabel(),
                "alias.txt",
                "Toolbar Buttons",
                "ASCII Settings",
                "rss.xml"
            )
        } else if (SECTION_INTEGRATIONS == section) {
            return mutableListOf(
                "Preferred Music App: " + this.preferredMusicAppSummary,
                "Tasker Integration: " + if (TaskerIntegrationManager.isEnabled(this)) "on" else "off"
            )
        } else if (SECTION_SYSTEM == section) {
            return mutableListOf(
                "Backup",
                "Create Shareable Configuration",
                "Restore",
                "Rate the App",
                "Send Feedback",
                "View Crash Log"
            )
        } else if (SECTION_FONTS == section) {
            ensurePendingFontChanges()
            return mutableListOf(
                "Typography",
                "Default (System Font)",
                "Import Font..."
            ).apply {
                addAll(listFontFiles(fontsDir).map { it.name })
            }
        } else if (SECTION_TYPOGRAPHY == section) {
            ensurePendingFontChanges()
            return mutableListOf(FONT_SCALE_PANEL)
        } else if (SECTION_PRESETS == section) {
            return mutableListOf("Save Current as Preset", "Apply Preset", "Remove Preset")
        } else if (SECTION_FRAMES == section) {
            return mutableListOf(FRAME_PANEL)
        } else if (SECTION_PRESET_APPLY == section) {
            return PresetManager.listAllPresetNames().toMutableList()
        } else if (SECTION_PRESET_REMOVE == section) {
            return PresetManager.listSavedPresetFolders().filterNotNull().toMutableList()
        }

        return mutableListOf(
            "Appearance",
            "Behavior",
            "Personalization",
            "Integrations",
            "System & Support"
        )
    }

    private fun openSection(targetSection: String?, addToHistory: Boolean = true) {
        if (targetSection == null) return
        if (addToHistory && section != null && section != targetSection) {
            sectionBackStack.addLast(section!!)
        }
        if (section in FONT_SECTIONS && targetSection !in FONT_SECTIONS) {
            pendingFontSizeOffset = null
            pendingUseSystemFont = null
            pendingFontFileName = null
            pendingTypographySizes = null
        }
        section = targetSection
        header!!.setText(getHeaderText(section))
        sectionItems.clear()
        sectionItems.addAll(getItemsForSection(section))
        sectionsAdapter!!.notifyDataSetChanged()
        recyclerView!!.scrollToPosition(0)
        updateSupportFooter()
    }

    private fun bindFramePanel(holder: FramePanelViewHolder) {
        val root = holder.root
        root.removeAllViews()
        root.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val session = frameSession()
        val applyAll = session.applyToAll
        val toggle = CheckBox(this).apply {
            text = "Apply one frame to all surfaces"
            isChecked = applyAll
            setTextColor(textColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
            setPadding(dp(this@ThemerActivity, 12f), dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 12f), dp(this@ThemerActivity, 10f))
            background = rect(this@ThemerActivity, surfaceColor(), borderColor(), 1.25f)
            buttonTintList = ColorStateList.valueOf(accentColor())
            setOnCheckedChangeListener { _, checked ->
                session.applyToAll = checked
                sectionsAdapter?.notifyDataSetChanged()
            }
        }
        root.addView(toggle, inputParams())

        root.addView(TextView(this).apply {
            text = if (applyAll) {
                "Import a square 3 x 3 PNG or a .retui-frame file. The imported frame replaces generated borders on every supported surface."
            } else {
                "Import a square 3 x 3 PNG or a .retui-frame file per surface. Button states, toggle states, and slider parts can be supplied independently; missing assignments keep their defaults."
            }
            setTextColor(textColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity))
            textSize = 12f
            setPadding(dp(this@ThemerActivity, 8f), 0, dp(this@ThemerActivity, 8f), dp(this@ThemerActivity, 10f))
        }, inputParams())

        root.addView(TextView(this).apply {
            text = "SAVE FRAME SETTINGS"
            styleButton(this@ThemerActivity, this, true)
            setOnClickListener { saveFrameChanges() }
        }, inputParams())

        val targets: List<FrameTarget?> = if (applyAll) listOf(null) else FrameTarget.entries
        for (target in targets) {
            root.addView(frameAssignmentRow(target), inputParams())
        }

        val packs = session.packs()
        if (packs.isNotEmpty()) {
            root.addView(TextView(this).apply {
                text = "FRAME PACKS"
                setTextColor(accentColor())
                setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
                textSize = 14f
                setPadding(dp(this@ThemerActivity, 8f), dp(this@ThemerActivity, 12f), 0, dp(this@ThemerActivity, 8f))
            }, inputParams())
            packs.forEach { root.addView(framePackRow(it), inputParams()) }
        }
    }

    private fun frameAssignmentRow(target: FrameTarget?): View {
        val session = frameSession()
        val hasBundle = session.hasAssignedFrame(target)
        val preview = session.previewBitmap(target)
        val invalid = session.assignedFrameIsInvalid(target)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 10f))
            background = rect(this@ThemerActivity, surfaceColor(), borderColor(), 1.25f)
        }
        row.addView(TextView(this).apply {
            text = (target?.label ?: "All surfaces").uppercase(Locale.getDefault())
            setTextColor(textColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
            textSize = 13f
        })

        val previewRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(this@ThemerActivity, 8f), 0, dp(this@ThemerActivity, 8f))
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            setBackgroundColor(ColorUtils.setAlphaComponent(surfaceColor(), 190))
            if (preview != null) {
                setImageBitmap(preview)
                contentDescription = "Original frame PNG preview"
            } else {
                setImageResource(if (invalid) android.R.drawable.ic_menu_report_image else android.R.drawable.ic_menu_gallery)
                alpha = if (invalid) 1f else 0.4f
                contentDescription = if (invalid) "Frame PNG missing or corrupt" else "No frame imported"
            }
        }
        previewRow.addView(image, LinearLayout.LayoutParams(dp(this, 104f), dp(this, 72f)))
        previewRow.addView(TextView(this).apply {
            text = when {
                invalid -> "PNG MISSING OR CORRUPT\nDEFAULT BORDER FALLBACK"
                preview != null -> "${session.assignedName(target) ?: "Imported frame"}\nORIGINAL PNG"
                else -> "NO FRAME\nDEFAULT BORDER FALLBACK"
            }
            setTextColor(if (invalid) Color.RED else textColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity))
            textSize = 11f
            setPadding(dp(this@ThemerActivity, 12f), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(previewRow)

        row.addView(TextView(this).apply {
            val selected = session.selectedAssetId(target)
            text = if (selected == null) "FRAME: DEFAULT BORDER" else
                "FRAME: ${session.assignedName(target) ?: "MISSING OR CORRUPT"}"
            styleButton(this@ThemerActivity, this, false)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 46f)))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(TextView(this).apply {
            text = if (hasBundle) "REPLACE" else "IMPORT"
            styleButton(this@ThemerActivity, this, false)
            setOnClickListener { launchFrameImportPicker(target) }
        }, LinearLayout.LayoutParams(0, dp(this, 46f), 1f))
        if (hasBundle) {
            actions.addView(View(this), LinearLayout.LayoutParams(dp(this, 8f), 1))
            actions.addView(TextView(this).apply {
                text = "USE DEFAULT"
                styleButton(this@ThemerActivity, this, false)
                setOnClickListener {
                    session.select(target, null)
                    reloadForFrame("Default border selected.")
                }
            }, LinearLayout.LayoutParams(0, dp(this, 46f), 1f))
        }
        row.addView(actions)
        return row
    }

    private fun framePackRow(pack: FrameManager.FramePack): View {
        val active = frameSession().activePackId() == pack.id
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 8f), dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 8f))
            background = rect(this@ThemerActivity, surfaceColor(), borderColor(), 1.25f)
        }
        row.addView(TextView(this).apply {
            text = buildString {
                append(pack.name)
                append("\n")
                append(pack.assignments.size).append(if (pack.assignments.size == 1) " custom frame" else " custom frames")
                if (active) append("  •  ACTIVE")
            }
            setTextColor(if (active) accentColor() else textColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
        })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(this@ThemerActivity, 8f), 0, 0)
        }
        actions.addView(TextView(this).apply {
            text = if (active) "APPLIED" else "APPLY"
            styleButton(this@ThemerActivity, this, false)
            isEnabled = !active
            alpha = if (active) 0.45f else 1f
            setOnClickListener { applyFramePack(pack) }
        }, LinearLayout.LayoutParams(0, dp(this, 46f), 1f))
        actions.addView(View(this), LinearLayout.LayoutParams(dp(this, 8f), 1))
        actions.addView(TextView(this).apply {
            text = "DELETE"
            styleButton(this@ThemerActivity, this, false)
            setOnClickListener { confirmDeleteFramePack(pack) }
        }, LinearLayout.LayoutParams(0, dp(this, 46f), 1f))
        row.addView(actions)
        return row
    }

    private fun bindFontRow(holder: FontViewHolder, fileName: String) {
        val font = File(fontsDir, fileName)
        holder.label.text = fileName.uppercase(Locale.getDefault())
        styleListItem(
            this,
            holder.label,
            pendingUseSystemFont == false && pendingFontFileName == fileName
        )
        holder.label.setOnClickListener { applyFont(font) }
        holder.delete.text = "X"
        styleButton(this, holder.delete, false)
        holder.delete.setOnClickListener { confirmDeleteFont(font) }
        holder.itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(this@ThemerActivity, 8f) }
    }

    private fun createFontScaleViewHolder(parent: ViewGroup): FontScaleViewHolder {
        val root = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12f), dp(context, 10f), dp(context, 12f), dp(context, 10f))
        }

        val previewRow = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val previewGlyphs = FONT_PREVIEW_SIZES.map { baseSize ->
            TextView(parent.context).apply {
                text = "A"
                includeFontPadding = false
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                contentDescription = "${baseSize.toInt()}sp font preview"
                setPadding(dp(context, 8f), 0, dp(context, 8f), 0)
                previewRow.addView(
                    this,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(context, 78f)
                    )
                )
            }
        }
        root.addView(
            HorizontalScrollView(parent.context).apply {
                isHorizontalScrollBarEnabled = false
                addView(previewRow)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(parent.context, 82f))
        )

        val scaleRow = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val smaller = TextView(parent.context).apply {
            text = "A"
            gravity = Gravity.CENTER
            contentDescription = "Decrease font size offset"
            isClickable = true
            isFocusable = true
        }
        val slider = SeekBar(parent.context).apply {
            max = LauncherFontScale.MAX_OFFSET - LauncherFontScale.MIN_OFFSET
            contentDescription = "Font size offset"
            TuixtTheme.styleSlider(parent.context, this)
        }
        val larger = TextView(parent.context).apply {
            text = "A"
            gravity = Gravity.CENTER
            contentDescription = "Increase font size offset"
            isClickable = true
            isFocusable = true
        }
        scaleRow.addView(smaller, LinearLayout.LayoutParams(dp(parent.context, 42f), dp(parent.context, 48f)))
        scaleRow.addView(slider, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        scaleRow.addView(larger, LinearLayout.LayoutParams(dp(parent.context, 42f), dp(parent.context, 48f)))
        root.addView(scaleRow)

        val surfaces = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(
            surfaces,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val footer = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val status = TextView(parent.context)
        val reset = TextView(parent.context).apply { text = "RESET ALL" }
        val save = TextView(parent.context).apply { text = "APPLY" }
        footer.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        footer.addView(reset, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(parent.context, 48f)))
        footer.addView(View(parent.context), LinearLayout.LayoutParams(dp(parent.context, 8f), 1))
        footer.addView(save, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(parent.context, 48f)))
        root.addView(footer)

        root.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(parent.context, 8f) }
        return FontScaleViewHolder(root, previewGlyphs, smaller, slider, larger, surfaces, status, reset, save)
    }

    private fun bindFontScalePanel(holder: FontScaleViewHolder) {
        ensurePendingFontChanges()
        val pending = pendingFontSizeOffset!!
        val typeface = pendingFontTypeface()
        val rowRefreshers = ArrayList<() -> Unit>()
        stylePanel(this, holder.itemView)
        holder.previewGlyphs.forEach { glyph ->
            glyph.setTextColor(textColor())
            glyph.setTypeface(typeface)
        }
        holder.smaller.apply {
            setTextColor(textColor())
            setTypeface(typeface, Typeface.BOLD)
            textSize = 12f
        }
        holder.larger.apply {
            setTextColor(textColor())
            setTypeface(typeface, Typeface.BOLD)
            textSize = 24f
        }
        holder.status.apply {
            setTextColor(accentColor())
            setTypeface(typeface, Typeface.BOLD)
            textSize = 13f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        styleButton(this, holder.reset, false)
        styleButton(this, holder.save, true)
        holder.slider.progressTintList = ColorStateList.valueOf(accentColor())
        holder.slider.thumbTintList = ColorStateList.valueOf(accentColor())
        holder.slider.setOnSeekBarChangeListener(null)
        holder.slider.progress = pending - LauncherFontScale.MIN_OFFSET
        holder.surfaces.removeAllViews()

        fun refreshSaveState() {
            val changed = hasPendingFontChanges()
            holder.save.isEnabled = changed
            holder.save.alpha = if (changed) 1f else 0.45f
        }

        TYPOGRAPHY_SETTINGS.forEach { spec ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 8f), dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 8f))
            }
            stylePanel(this, row)
            val title = TextView(this).apply {
                text = spec.label.uppercase(Locale.getDefault())
                setTextColor(accentColor())
                setTypeface(typeface, Typeface.BOLD)
                textSize = 12f
            }
            val sample = TextView(this).apply {
                text = spec.sample
                setTextColor(textColor())
                setTypeface(typeface)
                setPadding(0, dp(this@ThemerActivity, 5f), 0, dp(this@ThemerActivity, 5f))
            }
            val controls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val smaller = TextView(this).apply { text = "−" }
            val value = TextView(this).apply {
                gravity = Gravity.CENTER
                setTextColor(textColor())
                setTypeface(typeface, Typeface.BOLD)
            }
            val larger = TextView(this).apply { text = "+" }
            val reset = TextView(this).apply { text = "RESET" }
            styleButton(this, smaller, false)
            styleButton(this, larger, false)
            styleButton(this, reset, false)
            smaller.contentDescription = "Decrease ${spec.label} size"
            larger.contentDescription = "Increase ${spec.label} size"
            reset.contentDescription = "Reset ${spec.label} size"
            controls.addView(smaller, LinearLayout.LayoutParams(dp(this, 44f), dp(this, 44f)))
            controls.addView(value, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            controls.addView(larger, LinearLayout.LayoutParams(dp(this, 44f), dp(this, 44f)))
            controls.addView(View(this), LinearLayout.LayoutParams(dp(this, 8f), 1))
            controls.addView(reset, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(this, 44f)))
            row.addView(title)
            row.addView(sample)
            row.addView(controls)
            holder.surfaces.addView(row, inputParams())

            fun refreshRow() {
                val base = pendingTypographySizes!!.getValue(spec.setting)
                val effective = LauncherFontScale.effectiveSp(base, pendingFontSizeOffset!!, spec.followsMaster)
                sample.textSize = effective
                value.text = if (spec.followsMaster && pendingFontSizeOffset != 0) {
                    "${effective.toInt()}SP  ·  BASE ${base}"
                } else {
                    "${effective.toInt()}SP"
                }
            }

            fun move(delta: Int) {
                val current = pendingTypographySizes!!.getValue(spec.setting)
                pendingTypographySizes!![spec.setting] = LauncherFontScale.adjustedBaseSp(
                    current,
                    delta,
                    MIN_TYPOGRAPHY_SP,
                    MAX_TYPOGRAPHY_SP
                )
                refreshRow()
                refreshSaveState()
            }

            smaller.setOnClickListener { move(-1) }
            larger.setOnClickListener { move(1) }
            reset.setOnClickListener {
                pendingTypographySizes!![spec.setting] = defaultTypographySize(spec.setting)
                refreshRow()
                refreshSaveState()
            }
            rowRefreshers.add { refreshRow() }
            refreshRow()
        }

        fun preview(offset: Int) {
            pendingFontSizeOffset = offset
            holder.previewGlyphs.forEachIndexed { index, glyph ->
                glyph.textSize = LauncherFontScale.scaledSp(FONT_PREVIEW_SIZES[index], offset)
            }
            rowRefreshers.forEach { it() }
            val signed = if (offset > 0) "+$offset" else offset.toString()
            holder.status.text = "${pendingFontLabel()}  /  MASTER ${signed}SP"
            refreshSaveState()
        }

        fun move(delta: Int) {
            val next = ((pendingFontSizeOffset ?: savedFontSizeOffset()) + delta).coerceIn(
                LauncherFontScale.MIN_OFFSET,
                LauncherFontScale.MAX_OFFSET
            )
            holder.slider.progress = next - LauncherFontScale.MIN_OFFSET
            preview(next)
        }

        holder.slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) preview(progress + LauncherFontScale.MIN_OFFSET)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        holder.smaller.setOnClickListener { move(-1) }
        holder.larger.setOnClickListener { move(1) }
        holder.reset.setOnClickListener {
            holder.slider.progress = -LauncherFontScale.MIN_OFFSET
            TYPOGRAPHY_SETTINGS.forEach { spec ->
                pendingTypographySizes!![spec.setting] = defaultTypographySize(spec.setting)
            }
            preview(0)
        }
        holder.save.setOnClickListener {
            savePendingFontChanges()
        }
        preview(pending)
    }

    private fun savedFontSizeOffset(): Int =
        LauncherSettings.getInt(Ui.font_size_offset).coerceIn(
            LauncherFontScale.MIN_OFFSET,
            LauncherFontScale.MAX_OFFSET
        )

    private fun savedUseSystemFont(): Boolean = LauncherSettings.getBoolean(Ui.system_font)

    private fun savedFontFileName(): String = get(Ui.font_file)?.trim().orEmpty()

    private fun ensurePendingFontChanges() {
        if (pendingFontSizeOffset == null) pendingFontSizeOffset = savedFontSizeOffset()
        if (pendingUseSystemFont == null) pendingUseSystemFont = savedUseSystemFont()
        if (pendingFontFileName == null) pendingFontFileName = savedFontFileName()
        if (pendingTypographySizes == null) {
            pendingTypographySizes = TYPOGRAPHY_SETTINGS.associateTo(LinkedHashMap()) {
                it.setting to LauncherSettings.getInt(it.setting).coerceIn(MIN_TYPOGRAPHY_SP, MAX_TYPOGRAPHY_SP)
            }
        }
    }

    private fun defaultTypographySize(setting: XMLPrefsSave): Int =
        setting.defaultValue()?.toIntOrNull()?.coerceIn(MIN_TYPOGRAPHY_SP, MAX_TYPOGRAPHY_SP)
            ?: MIN_TYPOGRAPHY_SP

    private fun pendingFontTypeface(): Typeface {
        ensurePendingFontChanges()
        if (pendingUseSystemFont == true) return Typeface.DEFAULT

        val file = File(fontsDir, pendingFontFileName.orEmpty())
        return try {
            Typeface.createFromFile(file)
        } catch (_: Exception) {
            Tuils.getTypeface(this) ?: Typeface.DEFAULT
        }
    }

    private fun pendingFontLabel(): String {
        ensurePendingFontChanges()
        return if (pendingUseSystemFont == true) {
            "SYSTEM"
        } else {
            pendingFontFileName.orEmpty().uppercase(Locale.getDefault())
        }
    }

    private fun hasPendingFontChanges(): Boolean {
        ensurePendingFontChanges()
        return pendingFontSizeOffset != savedFontSizeOffset() ||
            pendingUseSystemFont != savedUseSystemFont() ||
            pendingFontFileName.orEmpty() != savedFontFileName() ||
            TYPOGRAPHY_SETTINGS.any {
                pendingTypographySizes!!.getValue(it.setting) !=
                    LauncherSettings.getInt(it.setting).coerceIn(MIN_TYPOGRAPHY_SP, MAX_TYPOGRAPHY_SP)
            }
    }

    private fun discardPendingFontChanges() {
        pendingFontSizeOffset = savedFontSizeOffset()
        pendingUseSystemFont = savedUseSystemFont()
        pendingFontFileName = savedFontFileName()
        pendingTypographySizes = TYPOGRAPHY_SETTINGS.associateTo(LinkedHashMap()) {
            it.setting to LauncherSettings.getInt(it.setting).coerceIn(MIN_TYPOGRAPHY_SP, MAX_TYPOGRAPHY_SP)
        }
    }

    private fun savePendingFontChanges() {
        ensurePendingFontChanges()
        if (!hasPendingFontChanges()) return

        val useSystem = pendingUseSystemFont == true
        val fileName = if (useSystem) "" else pendingFontFileName.orEmpty()
        val fontChanged = useSystem != savedUseSystemFont() || fileName != savedFontFileName()
        try {
            val source = if (useSystem) null else File(fontsDir, fileName)
            if (source != null) {
                check(source.exists() && source.isFile) { "Selected font is no longer available." }
                Typeface.createFromFile(source)
            }
            if (fontChanged) {
                sweepCurrentFonts()
                if (source != null) {
                    Tuils.copy(source, File(Tuils.getFolder(), source.name))
                }
            }

            set(this, Ui.system_font, useSystem.toString())
            set(this, Ui.font_file, fileName)
            set(this, Ui.font_size_offset, pendingFontSizeOffset!!.toString())
            TYPOGRAPHY_SETTINGS.forEach { spec ->
                val value = pendingTypographySizes!!.getValue(spec.setting)
                if (value != LauncherSettings.getInt(spec.setting)) {
                    set(this, spec.setting, value.toString())
                }
            }
            Tuils.cancelFont()
            Toast.makeText(this, "Typography saved. Applying...", Toast.LENGTH_SHORT).show()
            LauncherActivity.preview(this)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not apply font: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun dystopiaRowLabel(): String =
        "Sign up for Retui Credits: " + if (RetuiCreditManager.isDystopiaEnabled(this)) "on" else "off"

    private fun isDystopiaRow(label: String?): Boolean =
        label != null && label.startsWith("Sign up for Retui Credits")

    private fun handleDystopiaOptIn() {
        if (RetuiCreditManager.isDystopiaEnabled(this)) {
            RetuiCreditManager.setDystopiaEnabled(this, false)
            LockdownManager.getInstance(this).stop("Lockdown disabled.")
            Toast.makeText(this, "Retui Credits disabled.", Toast.LENGTH_SHORT).show()
            openSection(SECTION_PERSONALIZATION)
            return
        }
        showDystopiaConsentDialog()
    }

    private fun showDystopiaConsentDialog() {
        TuixtDialog.showCustom(this, "Sign up for Retui Credits", ContentFactory { dialog: Dialog? ->
            val content = LinearLayout(this)
            content.orientation = LinearLayout.VERTICAL
            content.gravity = Gravity.CENTER

            val description = TextView(this)
            description.text = "Enables local Retui Credits, breach keys, breach puzzles, paid Pomodoro exits, and Lockdown. Retui Credits are fictional app points only: no cash value, no purchase value, and nothing leaves this device."
            description.setTextColor(textColor())
            description.setTypeface(Tuils.getTypeface(this))
            description.textSize = 13f
            description.gravity = Gravity.CENTER
            description.setPadding(0, 0, 0, dp(this, 14f))
            content.addView(
                description,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            val prompt = TextView(this)
            prompt.text = "HOLD FINGERPRINT FOR 3 SECONDS"
            prompt.setTextColor(accentColor())
            prompt.setTypeface(Tuils.getTypeface(this), Typeface.BOLD)
            prompt.textSize = 12f
            prompt.gravity = Gravity.CENTER
            prompt.setPadding(0, 0, 0, dp(this, 10f))
            content.addView(
                prompt,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            val fingerprint = ImageButton(this)
            fingerprint.setImageResource(R.drawable.ic_dystopia_fingerprint_24)
            fingerprint.setColorFilter(accentColor(), PorterDuff.Mode.SRC_IN)
            styleIconButton(this, fingerprint)
            fingerprint.setPadding(dp(this, 18f), dp(this, 18f), dp(this, 18f), dp(this, 18f))
            fingerprint.contentDescription = "Hold to sign up for Retui Credits"
            fingerprint.setOnClickListener { }

            val handler = Handler(Looper.getMainLooper())
            val enable = Runnable {
                RetuiCreditManager.setDystopiaEnabled(this, true)
                dialog?.dismiss()
                Toast.makeText(this, "Retui Credits enabled. 1000 fake credits granted.", Toast.LENGTH_SHORT).show()
                openSection(SECTION_PERSONALIZATION)
            }
            fingerprint.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        FocusFrictionStyle.vibrate(this, DYSTOPIA_HOLD_PATTERN)
                        handler.postDelayed(enable, DYSTOPIA_HOLD_MS)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(enable)
                        FocusFrictionStyle.cancelVibration(this)
                        view.isPressed = false
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            view.performClick()
                        }
                        true
                    }
                    else -> true
                }
            }

            content.addView(
                fingerprint,
                LinearLayout.LayoutParams(dp(this, 88f), dp(this, 88f))
            )
            content
        })
    }

    private fun buildSupportFooter(): LinearLayout {
        val footer = LinearLayout(this)
        footer.orientation = LinearLayout.HORIZONTAL
        footer.gravity = Gravity.CENTER
        footer.setPadding(0, dp(this, 8f), 0, 0)

        addSupportButton(
            footer,
            R.drawable.ic_tuixt_github_24,
            "Open GitHub"
        ) { openExternalUrl(GITHUB_URL) }
        addSupportButton(
            footer,
            R.drawable.ic_tuixt_discord_24,
            "Open Discord"
        ) { openExternalUrl(DISCORD_URL) }
        addSupportButton(
            footer,
            R.drawable.ic_tuixt_reddit_24,
            "Open Reddit"
        ) { openExternalUrl(REDDIT_URL) }
        addSupportButton(
            footer,
            R.drawable.ic_tuixt_web_24,
            "Open Re:T-UI website"
        ) { openLearnMore() }

        footer.visibility = View.GONE
        return footer
    }

    private fun addSupportButton(
        container: LinearLayout,
        imageRes: Int,
        description: String,
        onClick: () -> Unit
    ) {
        val button = ImageButton(this)
        button.setImageResource(imageRes)
        button.setContentDescription(description)
        styleIconButton(this, button)
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE)
        button.setPadding(dp(this, 10f), dp(this, 10f), dp(this, 10f), dp(this, 10f))
        button.setColorFilter(accentColor(), PorterDuff.Mode.SRC_IN)
        button.setOnClickListener { onClick() }

        val params = LinearLayout.LayoutParams(0, dp(this, 52f), 1f)
        params.marginEnd = dp(this, 8f)
        container.addView(button, params)
    }

    private fun updateSupportFooter() {
        val footer = supportFooter ?: return
        val isSystemSection = SECTION_SYSTEM == section
        footer.visibility = if (isSystemSection) View.VISIBLE else View.GONE
        if (!isSystemSection) {
            return
        }

        for (index in 0 until footer.childCount) {
            val child = footer.getChildAt(index)
            val params = child.layoutParams as? LinearLayout.LayoutParams ?: continue
            params.marginEnd = if (index == footer.childCount - 1) 0 else dp(this, 8f)
            child.layoutParams = params
        }
    }

    @SuppressLint("GestureBackNavigation", "MissingSuperCall")
    override fun onBackPressed() {
        if (section == SECTION_FRAMES && frameEditSession?.hasChanges() == true) {
            TuixtDialog.showConfirm(
                this,
                "Discard Changes?",
                "Unsaved frame settings and imports will be lost.",
                "Discard",
                "Keep Editing",
                ConfirmAction {
                    discardFrameChanges()
                    onBackPressed()
                }
            )
            return
        }
        if (section == SECTION_FONTS && hasPendingFontChanges()) {
            TuixtDialog.showConfirm(
                this,
                "Discard Changes?",
                "Unsaved font and scale changes will be lost.",
                "Discard",
                "Keep Editing",
                ConfirmAction {
                    discardPendingFontChanges()
                    onBackPressed()
                }
            )
            return
        }
        if (sectionBackStack.isNotEmpty()) {
            openSection(sectionBackStack.removeLast(), false)
            return
        }
        if (SECTION_HOME != section) {
            openSection(SECTION_HOME, false)
            return
        }
        finishAndRemoveTask()
    }

    private fun openConfigFile(fileName: String) {
        val intent = Intent(this@ThemerActivity, TuixtActivity::class.java)
        intent.putExtra(TuixtActivity.PATH, File(Tuils.getFolder(), fileName).getAbsolutePath())
        if (fileName == "behavior.xml") {
            intent.putExtra(TuixtActivity.EXCLUDE_SECTION, "Sounds")
        }
        openSettingsChild(intent)
    }

    private fun openSoundsSettings() {
        val intent = Intent(this@ThemerActivity, TuixtActivity::class.java)
        intent.putExtra(TuixtActivity.PATH, File(Tuils.getFolder(), "behavior.xml").getAbsolutePath())
        intent.putExtra(TuixtActivity.ONLY_SECTION, "Sounds")
        openSettingsChild(intent)
    }

    private fun openAsciiSettings() {
        val intent = Intent(this@ThemerActivity, TuixtActivity::class.java)
        intent.putExtra(TuixtActivity.MODE, TuixtActivity.MODE_ASCII_SETTINGS)
        openSettingsChild(intent)
    }

    private fun openSettingsChild(intent: Intent) {
        startActivityForResult(intent, LauncherActivity.TUIXT_REQUEST)
        overridePendingTransition(0, 0)
    }

    private fun showToolbarButtonsDialog() {
        val options: MutableList<String?> = ArrayList<String?>()
        for (slot in 1..ToolbarShortcutManager.MAX_SLOTS) {
            options.add(toolbarSlotSummary(slot))
        }

        TuixtDialog.showOptions(
            this,
            "Toolbar Buttons",
            options,
            ItemAction { which: Int -> showToolbarButtonSlotDialog(which + 1) })
    }

    private fun toolbarSlotSummary(slot: Int): String {
        val current = slot(slot)
        if (!current.enabled) {
            return "Slot " + slot + ": off"
        }
        return "Slot " + slot + ": " + current.iconLabel + " -> " + current.command
    }

    private fun showToolbarButtonSlotDialog(slot: Int) {
        val current = slot(slot)
        val options: MutableList<String?> = ArrayList<String?>()
        options.add(if (current.enabled) "Disable slot" else "Enable slot")
        options.add("Set command: " + displayValue(current.command, "empty"))
        options.add("Set icon: " + current.iconLabel)
        options.add("Clear slot")

        TuixtDialog.showOptions(this, "Toolbar Slot " + slot, options, ItemAction { which: Int ->
            if (which == 0) {
                if (!current.enabled && current.command.length == 0) {
                    showToolbarButtonCommandDialog(slot, true)
                } else {
                    saveToolbarSlot(slot, !current.enabled, current.command, current.icon)
                    recyclerView!!.postDelayed(Runnable { this.showToolbarButtonsDialog() }, 250)
                }
            } else if (which == 1) {
                showToolbarButtonCommandDialog(slot, current.enabled)
            } else if (which == 2) {
                showToolbarButtonIconDialog(slot)
            } else {
                clearSlot(this, slot)
                reloadLauncherForToolbarButtons("Toolbar slot cleared.")
                recyclerView!!.postDelayed(Runnable { this.showToolbarButtonsDialog() }, 250)
            }
        })
    }

    private fun showToolbarButtonCommandDialog(slot: Int, enableAfterSave: Boolean) {
        val current = slot(slot)
        val content = LinearLayout(this)
        content.setOrientation(LinearLayout.VERTICAL)

        val help = TextView(this)
        help.setText("Enter the same text you would type at the prompt. Examples: whatsapp, notifications -open, ytm, module -show rss.")
        help.setTextColor(textColor())
        help.setTypeface(Tuils.getTypeface(this))
        help.setTextSize(13f)
        help.setPadding(0, 0, 0, dp(this, 10f))
        content.addView(
            help, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val input = commandInput("Command or app name")
        input.setText(current.command)
        input.setSelectAllOnFocus(true)
        content.addView(input, inputParams())

        TuixtDialog.showContent(this, "Toolbar Command", content, "Save", "Cancel", ConfirmAction {
            val command = input.getText().toString().trim { it <= ' ' }
            if (command.length == 0) {
                Toast.makeText(this, "Command is required.", Toast.LENGTH_SHORT).show()
                recyclerView!!.postDelayed(Runnable {
                    showToolbarButtonCommandDialog(
                        slot,
                        enableAfterSave
                    )
                }, 250)
                return@ConfirmAction
            }

            saveToolbarSlot(slot, enableAfterSave || current.enabled, command, current.icon)
            recyclerView!!.postDelayed(Runnable { showToolbarButtonSlotDialog(slot) }, 250)
        })
    }

    private fun showToolbarButtonIconDialog(slot: Int) {
        val current = slot(slot)
        val icons: MutableList<IconChoice> = icons().toMutableList()
        val labels: MutableList<String?> = ArrayList<String?>()
        for (icon in icons) {
            labels.add(icon.label)
        }

        TuixtDialog.showOptions(this, "Toolbar Icon", labels, ItemAction { which: Int ->
            val icon = icons.get(which)
            saveToolbarSlot(slot, current.enabled, current.command, icon.key)
            recyclerView!!.postDelayed(Runnable { showToolbarButtonSlotDialog(slot) }, 250)
        })
    }

    private fun saveToolbarSlot(slot: Int, enabled: Boolean, command: String?, icon: String?) {
        saveSlot(this, slot, enabled, command, icon)
        reloadLauncherForToolbarButtons(if (enabled) "Toolbar button saved." else "Toolbar button disabled.")
    }

    private fun reloadLauncherForToolbarButtons(message: String?) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        LauncherActivity.preview(this)
    }

    private fun commandInput(hint: String?): EditText {
        val input = EditText(this)
        input.setHint(hint)
        input.setSingleLine(true)
        input.setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        styleInput(this, input)
        return input
    }

    private fun displayValue(value: String?, fallback: String?): String? {
        return if (value == null || value.trim { it <= ' ' }.length == 0) fallback else value.trim { it <= ' ' }
    }

    private fun confirmDeleteFont(font: File) {
        TuixtDialog.showConfirm(
            this,
            "Delete Font",
            "Delete " + font.getName() + "?",
            "Delete",
            "Cancel",
            ConfirmAction {
                deleteFont(font)
            })
    }

    private fun deleteFont(font: File) {
        val deletedName = font.getName()
        if (!savedUseSystemFont() && savedFontFileName() == deletedName) {
            Toast.makeText(
                this,
                "Select and save another font before deleting the active font.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val deleted = !font.exists() || font.delete()

        val rootCopy = File(Tuils.getFolder(), deletedName)
        if (rootCopy.exists() && rootCopy.isFile()) {
            Tuils.insertOld(rootCopy)
        }

        if (!deleted) {
            Toast.makeText(this, "Could not delete font.", Toast.LENGTH_LONG).show()
            return
        }

        if (pendingUseSystemFont == false && pendingFontFileName == deletedName) {
            pendingUseSystemFont = savedUseSystemFont()
            pendingFontFileName = savedFontFileName()
        }

        Toast.makeText(this, "Font deleted.", Toast.LENGTH_SHORT).show()
        openSection(SECTION_FONTS, false)
    }

    private val fontsDir: File
        get() {
            val fontsDir = File(Tuils.getFolder(), "fonts")
            if (!fontsDir.exists() && !fontsDir.mkdirs()) {
                Log.e(
                    "TUI-THEMER",
                    "Unable to create fonts folder: " + fontsDir.getAbsolutePath()
                )
            }
            return fontsDir
        }

    private fun listFontFiles(fontsDir: File): Array<File> {
        val fonts =
            fontsDir.listFiles(FilenameFilter { dir: File?, name: String? -> isFontFileName(name) })
        if (fonts == null) {
            return emptyArray()
        }
        Arrays.sort<File?>(
            fonts,
            Comparator { left: File?, right: File? ->
                left!!.getName().compareTo(right!!.getName(), ignoreCase = true)
            })
        return fonts
    }

    private fun launchFontImportPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("*/*")
        intent.putExtra(
            Intent.EXTRA_MIME_TYPES, arrayOf<String>(
                "font/ttf",
                "font/otf",
                "application/x-font-ttf",
                "application/x-font-otf",
                "application/vnd.ms-opentype",
                "application/font-sfnt",
                "application/octet-stream"
            )
        )
        try {
            startActivityForResult(intent, FONT_IMPORT_REQUEST)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Font picker is unavailable on this device.", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun launchFrameImportPicker(target: FrameTarget?) {
        pendingFrameTarget = target
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/png", "application/zip", "application/octet-stream"))
        }
        try {
            startActivityForResult(intent, FRAME_IMPORT_REQUEST)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "File picker is unavailable on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun reloadForFrame(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        sectionsAdapter?.notifyDataSetChanged()
    }

    private fun frameSession(): FrameManager.EditSession =
        frameEditSession ?: FrameManager.beginEdit(this).also { frameEditSession = it }

    private fun saveFrameChanges() {
        val session = frameEditSession ?: return
        val choices = mutableListOf("Create new pack")
        if (session.packs().isNotEmpty()) choices.add("Replace existing pack")
        TuixtDialog.showOptions(this, "Save Frame Settings", choices, ItemAction { choice ->
            if (choice == 0) showCreateFramePack() else showReplaceFramePack()
        })
    }

    private fun showCreateFramePack() {
        val session = frameEditSession ?: return
        TuixtDialog.showValidatedForm(
            this,
            "Create Frame Pack",
            listOf(FormField("name", "Pack name", "My frame pack")),
            "Create",
            "Cancel",
            FormValidator { values -> session.packNameError(values["name"].orEmpty()) },
            FormAction { values ->
                try {
                    session.createPack(values["name"].orEmpty())
                    persistFrameSession("Frame pack created and applied.")
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not create frame pack: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun showReplaceFramePack() {
        val packs = frameEditSession?.packs().orEmpty()
        if (packs.isEmpty()) return
        TuixtDialog.showOptions(this, "Replace Frame Pack", packs.map { it.name }, ItemAction { index ->
            val pack = packs[index]
            TuixtDialog.showConfirm(
                this,
                "Replace ${pack.name}?",
                "Replace this pack with the complete current frame setup?",
                "Replace",
                "Cancel",
                ConfirmAction {
                    try {
                        frameEditSession?.replacePack(pack.id) ?: return@ConfirmAction
                        persistFrameSession("${pack.name} replaced and applied.")
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not replace frame pack: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )
        })
    }

    private fun applyFramePack(pack: FrameManager.FramePack) {
        withCleanFrameSession("Unsaved element edits will be discarded before applying ${pack.name}.") { session ->
            try {
                session.applyPack(pack.id)
                persistFrameSession("${pack.name} applied.")
            } catch (e: Exception) {
                Toast.makeText(this, "Could not apply frame pack: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDeleteFramePack(pack: FrameManager.FramePack) {
        val active = frameSession().activePackId() == pack.id
        val message = buildString {
            append("Delete ${pack.name}?")
            if (active) append(" The active frame setup will return to defaults.")
            if (frameSession().hasChanges()) append(" Unsaved element edits will be discarded.")
        }
        TuixtDialog.showConfirm(this, "Delete Frame Pack", message, "Delete", "Cancel", ConfirmAction {
            val session = if (frameSession().hasChanges()) {
                discardFrameChanges()
                frameSession()
            } else frameSession()
            try {
                session.deletePack(pack.id)
                persistFrameSession("${pack.name} deleted.")
            } catch (e: Exception) {
                Toast.makeText(this, "Could not delete frame pack: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun withCleanFrameSession(message: String, action: (FrameManager.EditSession) -> Unit) {
        val session = frameSession()
        if (!session.hasChanges()) {
            action(session)
            return
        }
        TuixtDialog.showConfirm(this, "Discard Unsaved Edits?", message, "Discard and Apply", "Cancel", ConfirmAction {
            discardFrameChanges()
            action(frameSession())
        })
    }

    private fun persistFrameSession(message: String) {
        val session = frameEditSession ?: return
        try {
            session.save()
            frameEditSession = null
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            LauncherActivity.preview(this)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save frame settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun discardFrameChanges() {
        frameEditSession?.discard()
        frameEditSession = null
    }

    private fun applySystemFont() {
        pendingUseSystemFont = true
        pendingFontFileName = ""
        openSection(SECTION_TYPOGRAPHY)
    }

    private fun applyFont(source: File) {
        try {
            Typeface.createFromFile(source)
            pendingUseSystemFont = false
            pendingFontFileName = source.name
            openSection(SECTION_TYPOGRAPHY)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not preview font: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun sweepCurrentFonts() {
        val tuiFolder = Tuils.getFolder()
        val currentFiles = tuiFolder.listFiles()
        if (currentFiles != null) {
            for (f in currentFiles) {
                val name = f.getName().lowercase()
                if (name.endsWith(".ttf") || name.endsWith(".otf")) {
                    Tuils.insertOld(f)
                }
            }
        }
    }

    private fun launchWallpaperPicker() {
        try {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SET_WALLPAPER),
                    "Select wallpaper"
                )
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Wallpaper picker is unavailable on this device.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun launchLiveWallpaperPicker() {
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@ThemerActivity, RetuiWallpaperService::class.java)
                    )
                }
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (fallback: Exception) {
                Toast.makeText(this, "Live wallpaper picker is unavailable on this device.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchBackupPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("application/zip")
        intent.putExtra(Intent.EXTRA_TITLE, BackupManager.defaultBackupName())
        try {
            startActivityForResult(intent, BACKUP_EXPORT_REQUEST)
        } catch (e: ActivityNotFoundException) {
            pendingBackupPassword = null
            backupExportPending = false
            Toast.makeText(this, "Backup picker is unavailable on this device.", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun showBackupProtectionDialog() {
        TuixtDialog.showOptions(
            this,
            "Backup Protection",
            mutableListOf<String?>("Encrypt with Password", "Export Without Password"),
            ItemAction { which: Int ->
                if (which == 0) {
                    showBackupPasswordDialog()
                } else {
                    pendingBackupPassword = null
                    backupExportPending = true
                    launchBackupPicker()
                }
            })
    }

    private fun showShareableConfigurationSourcePicker() {
        val presets = PresetManager.listSavedPresetFolders()
        val options: MutableList<String?> = ArrayList<String?>()
        options.add("Current Active Look")
        for (preset in presets) {
            options.add("Preset: " + preset)
        }

        TuixtDialog.showOptions(this, "Shareable Source", options, ItemAction { which: Int ->
            pendingShareablePresetName = if (which == 0) null else presets.get(which - 1)
            launchShareableConfigurationPicker()
        })
    }

    private fun launchShareableConfigurationPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("application/zip")
        intent.putExtra(
            Intent.EXTRA_TITLE,
            BackupManager.defaultShareableConfigurationName(pendingShareablePresetName)
        )
        try {
            startActivityForResult(intent, SHAREABLE_CONFIG_EXPORT_REQUEST)
        } catch (e: ActivityNotFoundException) {
            pendingShareablePresetName = null
            Toast.makeText(
                this,
                "Configuration picker is unavailable on this device.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun launchRestorePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("*/*")
        intent.putExtra(
            Intent.EXTRA_MIME_TYPES,
            arrayOf<String>("application/zip", "application/octet-stream")
        )
        try {
            startActivityForResult(intent, BACKUP_RESTORE_REQUEST)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Restore picker is unavailable on this device.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val preferredMusicAppSummary: String
        get() {
            val packageName = preferredPackage()
            if (packageName == null || packageName.length == 0) {
                return "Auto detect"
            }

            val packageManager = getPackageManager()
            try {
                val label = packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(
                        packageName,
                        0
                    )
                )
                if (label.isNotEmpty()) {
                    return label.toString() + " (" + packageName + ")"
                }
            } catch (ignored: Exception) {
            }

            return packageName
        }

    private fun showTaskerIntegrationDialog() {
        TuixtDialog.showCustom(this, "Tasker integration", ContentFactory { _: Dialog? ->
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            val status = TextView(this).apply {
                setTextColor(textColor())
                setTypeface(Tuils.getTypeface(this@ThemerActivity))
                textSize = 13f
                setPadding(dp(this@ThemerActivity, 12f), dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 12f), dp(this@ThemerActivity, 10f))
                background = rect(this@ThemerActivity, surfaceColor(), borderColor(), 1.25f)
            }

            fun updateStatus() {
                status.text = buildString {
                    append("INTEGRATION  ").append(if (TaskerIntegrationManager.isEnabled(this@ThemerActivity)) "ON" else "OFF")
                    append("\nTASKER       ").append(if (TaskerIntegrationManager.isTaskerInstalled(this@ThemerActivity)) "INSTALLED" else "NOT INSTALLED")
                    append("\nPERMISSION   ").append(if (TaskerIntegrationManager.hasRunTasksPermission(this@ThemerActivity)) "GRANTED" else "NOT GRANTED")
                    append("\nTASK STATUS  ").append(if (TaskerIntegrationManager.showTaskStatuses(this@ThemerActivity)) "SHOWN" else "HIDDEN")
                    append("\n\nPRESETS · THEME · MODULES · TERMINAL OUTPUT")
                }
            }
            updateStatus()
            content.addView(status, inputParams())

            val toggleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 8f), dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 8f))
                background = rect(this@ThemerActivity, surfaceColor(), borderColor(), 1.25f)
            }
            val toggleLabel = TextView(this).apply {
                text = "ENABLE INTEGRATION"
                setTextColor(textColor())
                setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
                textSize = 13f
            }
            val toggle = TextView(this)
            fun updateToggle() = styleToggle(this, toggle, TaskerIntegrationManager.isEnabled(this))
            updateToggle()
            toggle.setOnClickListener {
                val enable = !TaskerIntegrationManager.isEnabled(this)
                TaskerIntegrationManager.setEnabled(this, enable)
                if (enable && TaskerIntegrationManager.isTaskerInstalled(this) && !TaskerIntegrationManager.hasRunTasksPermission(this)) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(TaskerIntegrationManager.TASKER_PERMISSION_RUN_TASKS),
                        TASKER_PERMISSION_REQUEST
                    )
                }
                updateToggle()
                updateStatus()
                openSection(SECTION_INTEGRATIONS)
            }
            toggleRow.addView(toggleLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            toggleRow.addView(toggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            content.addView(toggleRow, inputParams())

            val statusToggleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 8f), dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 8f))
                background = rect(this@ThemerActivity, surfaceColor(), borderColor(), 1.25f)
            }
            val statusToggleLabel = TextView(this).apply {
                text = "SHOW TASK STATUSES"
                setTextColor(textColor())
                setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
                textSize = 13f
            }
            val statusToggle = TextView(this)
            fun updateStatusToggle() = styleToggle(this, statusToggle, TaskerIntegrationManager.showTaskStatuses(this))
            updateStatusToggle()
            statusToggle.setOnClickListener {
                TaskerIntegrationManager.setShowTaskStatuses(this, !TaskerIntegrationManager.showTaskStatuses(this))
                updateStatusToggle()
                updateStatus()
            }
            statusToggleRow.addView(statusToggleLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            statusToggleRow.addView(statusToggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            content.addView(statusToggleRow, inputParams())

            fun actionButton(label: String, action: () -> Unit): TextView = TextView(this).apply {
                text = label
                styleListItem(this@ThemerActivity, this, false)
                setOnClickListener { action() }
            }

            content.addView(actionButton("TEST INTEGRATION") {
                val result = TaskerIntegrationManager.execute(
                    this,
                    TaskerIntegrationManager.Request(TaskerIntegrationManager.ACTION_TERMINAL_OUTPUT, text = "Tasker integration test OK")
                )
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                updateStatus()
            }, inputParams())
            content.addView(actionButton("SETUP & EXAMPLES") { showTaskerSetupDialog() }, inputParams())
            content
        })
    }

    private fun showTaskerSetupDialog() {
        TuixtDialog.showCustom(this, "Tasker setup", ContentFactory { _: Dialog? ->
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val instructions = TextView(this).apply {
                text = "SETUP\n" +
                    "1. Enable Tasker integration in RETUI.\n" +
                    "2. Grant the Tasker run-task permission.\n" +
                    "3. In Tasker, enable Allow External Access.\n" +
                    "4. Add an action: Plugin → RETUI Action.\n\n" +
                    "TASKER → RETUI\n" +
                    "• Apply a night preset at sunset\n" +
                    "• Change a theme color from a profile\n" +
                    "• Show or refresh a module\n" +
                    "• Switch Spaces; RETUI saves the current Space automatically\n" +
                    "• Send text to the RETUI terminal\n\n" +
                    "RETUI → TASKER\n" +
                    "tasker Work\n" +
                    "tasker -run \"Evening Setup\""
                setTextColor(textColor())
                setTypeface(Tuils.getTypeface(this@ThemerActivity))
                textSize = 13f
                setPadding(dp(this@ThemerActivity, 12f), dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 12f), dp(this@ThemerActivity, 10f))
                background = rect(this@ThemerActivity, surfaceColor(), borderColor(), 1.25f)
            }
            content.addView(instructions, inputParams())
            val docs = TextView(this).apply {
                text = "OPEN FULL DOCUMENTATION"
                styleListItem(this@ThemerActivity, this, false)
                setOnClickListener { openExternalUrl(TASKER_HELP_URL) }
            }
            content.addView(docs, inputParams())
            content
        })
    }

    private fun showPreferredMusicAppPicker() {
        val choices = this.launchableAppChoices
        val labels: MutableList<String?> = ArrayList<String?>()
        labels.add("Auto detect")
        for (choice in choices) {
            labels.add(choice.label + " (" + choice.packageName + ")")
        }

        TuixtDialog.showOptions(this, "Preferred Music App", labels, ItemAction { which: Int ->
            if (which == 0) {
                set(this, Behavior.preferred_music_app, Tuils.EMPTYSTRING)
                Toast.makeText(
                    this,
                    "Preferred music app reset to automatic detection.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val choice = choices.get(which - 1)
                set(this, Behavior.preferred_music_app, choice.packageName)
                Toast.makeText(
                    this,
                    "Preferred music app set to " + choice.label + ".",
                    Toast.LENGTH_SHORT
                ).show()
            }
            recreate()
        })
    }

    private val launchableAppChoices: MutableList<AppChoice>
        get() {
            val packageManager = getPackageManager()
            val launcherIntent = Intent(Intent.ACTION_MAIN)
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER)

            val resolved =
                packageManager.queryIntentActivities(launcherIntent, 0)
            val choices: MutableList<AppChoice> =
                ArrayList<AppChoice>()
            val seenPackages: MutableList<String?> =
                ArrayList<String?>()

            for (info in resolved) {
                if (info.activityInfo == null) {
                    continue
                }

                val packageName = info.activityInfo.packageName
                if (seenPackages.contains(packageName)) {
                    continue
                }

                val loadedLabel = info.loadLabel(packageManager)
                choices.add(AppChoice(loadedLabel.toString(), packageName))
                seenPackages.add(packageName)
            }

            Collections.sort<AppChoice>(
                choices,
                object : Comparator<AppChoice> {
                    override fun compare(left: AppChoice, right: AppChoice): Int {
                        return left.label.compareTo(right.label, ignoreCase = true)
                    }
                })

            return choices
        }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LauncherActivity.TUIXT_REQUEST && resultCode == TuixtActivity.SAVE_PRESSED) {
            LauncherActivity.preview(this)
        } else if (requestCode == BACKUP_EXPORT_REQUEST) {
            handleBackupResult(resultCode, data)
        } else if (requestCode == SHAREABLE_CONFIG_EXPORT_REQUEST) {
            handleShareableConfigurationResult(resultCode, data)
        } else if (requestCode == BACKUP_RESTORE_REQUEST) {
            handleRestoreResult(resultCode, data)
        } else if (requestCode == FONT_IMPORT_REQUEST) {
            handleFontImportResult(resultCode, data)
        } else if (requestCode == FRAME_IMPORT_REQUEST) {
            handleFrameImportResult(resultCode, data)
        }
    }

    private fun handleFrameImportResult(resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            pendingFrameTarget = null
            Toast.makeText(this, "Frame import cancelled.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val target = pendingFrameTarget
            val asset = contentResolver.openInputStream(uri).use { input ->
                frameSession().importFrame(
                    target,
                    getDisplayName(uri) ?: uri.lastPathSegment,
                    requireNotNull(input) { "Unable to read the selected frame." }
                )
            }
            pendingFrameTarget = null
            reloadForFrame("Frame imported: ${asset.name}")
        } catch (e: Exception) {
            pendingFrameTarget = null
            Toast.makeText(this, "Frame import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleFontImportResult(resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            Toast.makeText(this, "Font import cancelled.", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = data.getData()
        var sourceName = getDisplayName(uri!!)
        if (sourceName == null || sourceName.trim { it <= ' ' }.length == 0) {
            sourceName = uri.getLastPathSegment()
        }

        val fileName = sanitizeFontFileName(sourceName)
        if (!isFontFileName(fileName)) {
            Toast.makeText(this, "Choose a .ttf or .otf font file.", Toast.LENGTH_LONG).show()
            return
        }

        val dest = uniqueFontFile(this.fontsDir, fileName)
        try {
            getContentResolver().openInputStream(uri).use { `in` ->
                FileOutputStream(dest).use { out ->
                    checkNotNull(`in`) { "Unable to read selected font." }
                    val buffer = ByteArray(8192)
                    var read: Int
                    while ((`in`.read(buffer).also { read = it }) != -1) {
                        out.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            if (dest.exists()) {
                dest.delete()
            }
            Toast.makeText(this, "Font import failed: " + e.message, Toast.LENGTH_LONG).show()
            return
        }

        if (!dest.exists() || dest.length() == 0L) {
            dest.delete()
            Toast.makeText(this, "Font import failed: empty file.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Font imported.", Toast.LENGTH_SHORT).show()
        applyFont(dest)
        openSection(SECTION_FONTS, false)
    }

    private fun getDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        try {
            cursor = getContentResolver().query(
                uri,
                arrayOf<String>(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index)
                }
            }
        } catch (ignored: Exception) {
        } finally {
            if (cursor != null) {
                cursor.close()
            }
        }
        return null
    }

    private fun sanitizeFontFileName(name: String?): String {
        var name = name
        if (name == null) {
            return "font.ttf"
        }

        name = name.replace('\\', '/')
        val slash = name.lastIndexOf('/')
        if (slash >= 0 && slash < name.length - 1) {
            name = name.substring(slash + 1)
        }

        name = name.trim { it <= ' ' }.replace("[^A-Za-z0-9._ -]".toRegex(), "_")
            .replace("\\s+".toRegex(), "_")
        if (name.length == 0) {
            return "font.ttf"
        }
        return name
    }

    private fun isFontFileName(name: String?): Boolean {
        if (name == null) {
            return false
        }
        val lower = name.lowercase()
        return lower.endsWith(".ttf") || lower.endsWith(".otf")
    }

    private fun uniqueFontFile(fontsDir: File?, fileName: String): File {
        val file = File(fontsDir, fileName)
        if (!file.exists()) {
            return file
        }

        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val extension = if (dot > 0) fileName.substring(dot) else ""
        var counter = 2
        while (true) {
            val candidate = File(fontsDir, base + "-" + counter + extension)
            if (!candidate.exists()) {
                return candidate
            }
            counter++
        }
    }

    private fun handleBackupResult(resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingBackupPassword = null
            backupExportPending = false
            Toast.makeText(this, "Backup cancelled.", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = data.getData() ?: return
        if (!backupExportPending) {
            deleteCreatedDocument(uri)
            Toast.makeText(this, "Backup cancelled before export.", Toast.LENGTH_LONG).show()
            return
        }
        exportBackup(uri, pendingBackupPassword)
    }

    private fun showBackupPasswordDialog() {
        val content = LinearLayout(this)
        content.setOrientation(LinearLayout.VERTICAL)

        val password = passwordInput("Password")
        val confirm = passwordInput("Confirm password")
        content.addView(password, inputParams())
        content.addView(confirm, inputParams())

        TuixtDialog.showContent(
            this,
            "Backup Password",
            content,
            "Export",
            "Cancel",
            ConfirmAction {
                val first = password.getText().toString()
                val second = confirm.getText().toString()
                if (first.length == 0) {
                    Toast.makeText(this, "Password is required.", Toast.LENGTH_SHORT).show()
                    recyclerView!!.postDelayed(Runnable { this.showBackupPasswordDialog() }, 250)
                    return@ConfirmAction
                }
                if (first != second) {
                    Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                    recyclerView!!.postDelayed(Runnable { this.showBackupPasswordDialog() }, 250)
                    return@ConfirmAction
                }
                pendingBackupPassword = first
                backupExportPending = true
                launchBackupPicker()
            })
    }

    private fun exportBackup(uri: Uri, password: String?) {
        try {
            BackupManager.exportBackup(this, uri, password)
            Toast.makeText(this, "Backup exported and verified.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            deleteCreatedDocument(uri)
            Toast.makeText(
                this,
                if (e.message == null) "Backup failed." else e.message,
                Toast.LENGTH_LONG
            ).show()
        } finally {
            pendingBackupPassword = null
            backupExportPending = false
        }
    }

    private fun deleteCreatedDocument(uri: Uri) {
        try {
            if (!DocumentsContract.deleteDocument(contentResolver, uri)) {
                contentResolver.delete(uri, null, null)
            }
        } catch (_: Exception) {
            try {
                contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
        }
    }

    private fun handleShareableConfigurationResult(resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingShareablePresetName = null
            Toast.makeText(this, "Configuration export cancelled.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            BackupManager.exportShareableConfiguration(
                this,
                data.getData() ?: return,
                pendingShareablePresetName
            )
            Toast.makeText(this, "Shareable configuration exported and verified.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            data.getData()?.let { deleteCreatedDocument(it) }
            Toast.makeText(
                this,
                if (e.message == null) "Configuration export failed." else e.message,
                Toast.LENGTH_LONG
            ).show()
        } finally {
            pendingShareablePresetName = null
        }
    }

    private fun handleRestoreResult(resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            Toast.makeText(this, "Restore cancelled.", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = data.getData()
        try {
            if ((data.getFlags() and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                getContentResolver().takePersistableUriPermission(
                    uri!!,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (ignored: Exception) {
        }

        pendingRestoreUri = uri
        try {
            if (BackupManager.isEncryptedBackup(this, uri)) {
                showRestorePasswordDialog()
            } else {
                restoreBackup(null)
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                if (e.message == null) "Restore failed." else e.message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showRestorePasswordDialog() {
        val content = LinearLayout(this)
        content.setOrientation(LinearLayout.VERTICAL)

        val password = passwordInput("Backup password")
        content.addView(password, inputParams())

        TuixtDialog.showContent(
            this,
            "Restore Password",
            content,
            "Restore",
            "Cancel",
            ConfirmAction {
                val value = password.getText().toString()
                if (value.length == 0) {
                    Toast.makeText(this, "Password is required.", Toast.LENGTH_SHORT).show()
                    recyclerView!!.postDelayed(Runnable { this.showRestorePasswordDialog() }, 250)
                    return@ConfirmAction
                }
                restoreBackup(value)
            })
    }

    private fun restoreBackup(password: String?) {
        try {
            val importedPreset = BackupManager.importBackup(this, pendingRestoreUri ?: return, password)
            pendingRestoreUri = null
            if (importedPreset != null) {
                Toast.makeText(this, "Preset imported: $importedPreset", Toast.LENGTH_LONG).show()
                return
            }
            Toast.makeText(this, "Backup restored. Reloading...", Toast.LENGTH_SHORT).show()
            recyclerView!!.postDelayed(Runnable {
                intent.putExtra(EXTRA_SECTION, SECTION_SYSTEM)
                recreate()
                LauncherActivity.preview(this)
            }, 500)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                if (e.message == null) "Restore failed." else e.message,
                Toast.LENGTH_LONG
            ).show()
            if (password != null && pendingRestoreUri != null) {
                recyclerView!!.postDelayed(Runnable { this.showRestorePasswordDialog() }, 500)
            }
        }
    }

    private fun passwordInput(hint: String?): EditText {
        val input = EditText(this)
        input.setHint(hint)
        input.setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        styleInput(this, input)
        return input
    }

    private fun inputParams(): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, dp(this, 10f))
        return params
    }

    private class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    private class FontViewHolder(
        itemView: View,
        val label: TextView,
        val delete: TextView
    ) : RecyclerView.ViewHolder(itemView)
    private class FontScaleViewHolder(
        itemView: View,
        val previewGlyphs: List<TextView>,
        val smaller: TextView,
        val slider: SeekBar,
        val larger: TextView,
        val surfaces: LinearLayout,
        val status: TextView,
        val reset: TextView,
        val save: TextView
    ) : RecyclerView.ViewHolder(itemView)
    private class FramePanelViewHolder(val root: LinearLayout) : RecyclerView.ViewHolder(root)

    private class AppChoice(val label: String, val packageName: String?)
    private data class TypographySetting(
        val label: String,
        val sample: String,
        val setting: XMLPrefsSave,
        val followsMaster: Boolean = true
    )
    companion object {
        @JvmStatic
        fun launchIntent(context: Context, section: String?): Intent =
            Intent(context, ThemerActivity::class.java).apply {
                putExtra(EXTRA_SECTION, if (section.isNullOrEmpty()) SECTION_HOME else section)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        const val EXTRA_SECTION: String = "section"
        const val SECTION_HOME: String = "home"
        const val SECTION_APPEARANCE: String = "appearance"
        const val SECTION_BEHAVIOR: String = "behavior"
        const val SECTION_PERSONALIZATION: String = "personalization"
        const val SECTION_INTEGRATIONS: String = "integrations"
        const val SECTION_SYSTEM: String = "system"
        const val SECTION_FONTS: String = "fonts"
        const val SECTION_TYPOGRAPHY: String = "typography"
        const val SECTION_PRESETS: String = "presets"
        const val SECTION_FRAMES: String = "frames"
        const val SECTION_PRESET_APPLY: String = "preset_apply"
        const val SECTION_PRESET_REMOVE: String = "preset_remove"
        private const val VIEW_TYPE_STANDARD = 0
        private const val VIEW_TYPE_FONT = 1
        private const val VIEW_TYPE_FONT_SCALE = 2
        private const val VIEW_TYPE_FRAME_PANEL = 3
        private const val FONT_SCALE_PANEL = "__font_scale_panel__"
        private const val FRAME_PANEL = "__frame_panel__"
        private const val MIN_TYPOGRAPHY_SP = 8
        private const val MAX_TYPOGRAPHY_SP = 64
        private val FONT_PREVIEW_SIZES = floatArrayOf(10f, 11f, 12f, 14f, 15f, 18f, 64f)
        private val FONT_SECTIONS = setOf(SECTION_FONTS, SECTION_TYPOGRAPHY)
        private val TYPOGRAPHY_SETTINGS = listOf(
            TypographySetting("Input and terminal output", "\$ help\nReady for the next command", Ui.input_output_size),
            TypographySetting("Suggestions", "apps   settings   files", Suggestions.suggestions_size),
            TypographySetting("Module headers", "WEATHER  [X]", Ui.module_header_text_size),
            TypographySetting("Module body", "Forecast: clear", Ui.module_body_text_size),
            TypographySetting("Output and overlay headers", "OUTPUT  ^", Ui.output_header_text_size),
            TypographySetting("RAM status", "RAM 42%", Ui.ram_size, false),
            TypographySetting("Battery status", "BAT 86%", Ui.battery_size, false),
            TypographySetting("Device status", "DEVICE ONLINE", Ui.device_size, false),
            TypographySetting("Time status", "22:08", Ui.time_size, false),
            TypographySetting("Storage status", "STORAGE 64%", Ui.storage_size, false),
            TypographySetting("Network status", "NETWORK WIFI", Ui.network_size, false),
            TypographySetting("Notes status", "NOTES READY", Ui.notes_size, false),
            TypographySetting("Weather status", "WEATHER 26°C", Ui.weather_size, false),
            TypographySetting("Unlock status", "UNLOCKS 4", Ui.unlock_size, false),
            TypographySetting("ASCII legacy size", "┌─ RE:TUI ─┐", Ui.ascii_size, false)
        )
        private const val BACKUP_EXPORT_REQUEST = 201
        private const val BACKUP_RESTORE_REQUEST = 202
        private const val SHAREABLE_CONFIG_EXPORT_REQUEST = 203
        private const val FONT_IMPORT_REQUEST = 204
        private const val TASKER_PERMISSION_REQUEST = 205
        private const val FRAME_IMPORT_REQUEST = 206
        private const val DYSTOPIA_HOLD_MS = 3000L
        private val DYSTOPIA_HOLD_PATTERN = longArrayOf(0L, 55L, 945L, 55L, 945L, 55L)
        private const val PLAY_STORE_PACKAGE_ID = "com.dvil.tui_renewed"
        private const val PLAY_STORE_MARKET_URL = "market://details?id=$PLAY_STORE_PACKAGE_ID"
        private const val PLAY_STORE_WEB_URL =
            "https://play.google.com/store/apps/details?id=$PLAY_STORE_PACKAGE_ID"
        private const val GITHUB_URL = "https://github.com/DvilSpawn/Re-TUI.git"
        private const val DISCORD_URL = "https://discord.gg/n6zsVYuV"
        private const val REDDIT_URL = "https://www.reddit.com/r/RE_TUI_launcher/"
        private const val FEEDBACK_EMAIL = "DvilSpawn@gmail.com"
        private const val FEEDBACK_MAILTO_URI = "mailto:$FEEDBACK_EMAIL"
        private const val GMAIL_PACKAGE = "com.google.android.gm"
        private const val LEARN_MORE_URL = "https://re-tui.pages.dev"
        private const val TASKER_HELP_URL = "https://github.com/DvilSpawn/Re-TUI#tasker-integration"
    }
}
