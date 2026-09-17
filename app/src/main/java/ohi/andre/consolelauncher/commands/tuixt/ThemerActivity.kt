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
import android.graphics.drawable.GradientDrawable
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
import ohi.andre.consolelauncher.tuils.displayMessage
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
import ohi.andre.consolelauncher.managers.KeyboardShortcutManager
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
import ohi.andre.consolelauncher.tuils.FrameSpec
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

class ThemerActivity : ohi.andre.consolelauncher.localization.LocalizedAppCompatActivity() {
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
    private var pendingShareableBehaviorLabels: Set<String>? = null
    private var pendingFontSizeOffset: Int? = null
    private var pendingUseSystemFont: Boolean? = null
    private var pendingFontFileName: String? = null
    private var pendingTypographySizes: MutableMap<XMLPrefsSave, Int>? = null
    private var pendingFrameTarget: FrameTarget? = null
    private var frameEditSession: FrameManager.EditSession? = null
    private var selectedKeyboardShortcutKey: Char = 'a'

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
                    sectionItems[position] == KEYBOARD_SHORTCUT_PANEL -> VIEW_TYPE_KEYBOARD_SHORTCUT_PANEL
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
                if (viewType == VIEW_TYPE_KEYBOARD_SHORTCUT_PANEL) {
                    return KeyboardShortcutPanelViewHolder(LinearLayout(parent.context).apply {
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
                if (holder is KeyboardShortcutPanelViewHolder) {
                    bindKeyboardShortcutPanel(holder)
                    return
                }
                val itemView = holder.itemView as TextView
                itemView.text = hubItemLabel(fileName).uppercase(Locale.getDefault())
                val selected = section == SECTION_FONTS &&
                    fileName == HubAction.DEFAULT_SYSTEM_FONT.name &&
                    pendingUseSystemFont == true
                styleListItem(this@ThemerActivity, itemView, selected)
                val params = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, dp(this@ThemerActivity, 8f))
                itemView.setLayoutParams(params)
                holder.itemView.setOnClickListener(View.OnClickListener { _: View? ->
                    if (section == SECTION_PRESET_APPLY) {
                        applyPreset(fileName)
                        return@OnClickListener
                    }
                    if (section == SECTION_PRESET_REMOVE) {
                        confirmRemovePreset(fileName)
                        return@OnClickListener
                    }
                    if (fileName == HubAction.APPEARANCE.name) {
                        openSection(SECTION_APPEARANCE)
                    } else if (fileName == HubAction.BEHAVIOR.name) {
                        openSection(SECTION_BEHAVIOR)
                    } else if (fileName == HubAction.SOUNDS.name) {
                        openSoundsSettings()
                    } else if (fileName == HubAction.PERSONALIZATION.name) {
                        openSection(SECTION_PERSONALIZATION)
                    } else if (fileName == HubAction.ASCII_SETTINGS.name) {
                        openAsciiSettings()
                    } else if (fileName == HubAction.INTEGRATIONS.name) {
                        openSection(SECTION_INTEGRATIONS)
                    } else if (fileName == HubAction.LANGUAGE_PACKS.name) {
                        openSettingsChild(Intent(this@ThemerActivity, ohi.andre.consolelauncher.localization.LanguagePackActivity::class.java))
                    } else if (fileName == HubAction.SYSTEM_SUPPORT.name) {
                        openSection(SECTION_SYSTEM)
                    } else if (fileName == HubAction.OPEN_WALLPAPER_PICKER.name) {
                        launchWallpaperPicker()
                    } else if (fileName == HubAction.OPEN_LIVE_WALLPAPER_PICKER.name) {
                        launchLiveWallpaperPicker()
                    } else if (fileName == HubAction.RETUI_WALLPAPER.name) {
                        openSettingsChild(Intent(this@ThemerActivity, RetuiWallpaperActivity::class.java))
                    } else if (fileName == HubAction.PREFERRED_MUSIC_APP.name) {
                        showPreferredMusicAppPicker()
                    } else if (fileName == HubAction.TASKER_INTEGRATION.name) {
                        showTaskerIntegrationDialog()
                    } else if (fileName == HubAction.RE_KEYBOARD_SHORTCUTS.name) {
                        openSection(SECTION_KEYBOARD_SHORTCUTS)
                    } else if (fileName == HubAction.FONTS.name) {
                        openSection(SECTION_FONTS)
                    } else if (fileName == HubAction.TYPOGRAPHY.name) {
                        openSection(SECTION_TYPOGRAPHY)
                    } else if (fileName == HubAction.PRESETS.name) {
                        openSection(SECTION_PRESETS)
                    } else if (fileName == HubAction.FRAMES.name) {
                        openSection(SECTION_FRAMES)
                    } else if (section == SECTION_FRAMES) {
                        return@OnClickListener
                    } else if (section == SECTION_PRESETS && fileName == HubAction.SAVE_CURRENT_AS_PRESET.name) {
                        showSavePresetInput()
                    } else if (section == SECTION_PRESETS && fileName == HubAction.APPLY_PRESET.name) {
                        openSection(SECTION_PRESET_APPLY)
                    } else if (section == SECTION_PRESETS && fileName == HubAction.REMOVE_PRESET.name) {
                        openSection(SECTION_PRESET_REMOVE)
                    } else if (section == SECTION_PRESET_APPLY) {
                        applyPreset(fileName)
                    } else if (section == SECTION_PRESET_REMOVE) {
                        confirmRemovePreset(fileName)
                    } else if (section == SECTION_FONTS && fileName == HubAction.DEFAULT_SYSTEM_FONT.name) {
                        applySystemFont()
                    } else if (section == SECTION_FONTS && fileName == HubAction.IMPORT_FONT.name) {
                        launchFontImportPicker()
                    } else if (fileName == HubAction.TOOLBAR_BUTTONS.name) {
                        showToolbarButtonsDialog()
                    } else if (isDystopiaRow(fileName)) {
                        handleDystopiaOptIn()
                    } else if (fileName == HubAction.VIEW_CRASH_LOG.name) {
                        val crashFile = File(Tuils.getFolder(), "crash.txt")
                        if (!crashFile.exists() || crashFile.length() == 0L) {
                            Toast.makeText(
                                this@ThemerActivity,
                                getString(R.string.themer_no_crash_log_found_79ebe),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val intent = Intent(this@ThemerActivity, TuixtActivity::class.java)
                            intent.putExtra(TuixtActivity.PATH, crashFile.getAbsolutePath())
                            openSettingsChild(intent)
                        }
                    } else if (fileName == HubAction.BACKUP.name) {
                        showBackupProtectionDialog()
                    } else if (fileName == HubAction.CREATE_SHAREABLE_CONFIGURATION.name) {
                        showShareableConfigurationSourcePicker()
                    } else if (fileName == HubAction.RESTORE.name) {
                        launchRestorePicker()
                    } else if (fileName == HubAction.RATE_THE_APP.name) {
                        openPlayStoreListing()
                    } else if (fileName == HubAction.GITHUB.name) {
                        openExternalUrl(GITHUB_URL)
                    } else if (fileName == HubAction.DISCORD.name) {
                        openExternalUrl(DISCORD_URL)
                    } else if (fileName == HubAction.REDDIT.name) {
                        openExternalUrl(REDDIT_URL)
                    } else if (fileName == HubAction.SEND_FEEDBACK.name) {
                        openFeedbackEmail()
                    } else if (fileName == HubAction.LEARN_MORE.name) {
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
                Toast.makeText(this, getString(R.string.themer_no_email_app_found_198b3), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.themer_no_browser_app_found_e360b), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSavePresetInput() {
        TuixtDialog.showInput(
            this,
            getString(R.string.themer_save_preset_008df),
            getString(R.string.themer_preset_name_eb39e),
            getString(R.string.themer_save_efc00),
            getString(R.string.themer_cancel_77dfd),
            InputAction { value ->
                val name = value?.trim().orEmpty()
                if (name.isNotEmpty()) savePreset(name)
            }
        )
    }

    private fun confirmRemovePreset(name: String) {
        TuixtDialog.showConfirm(
            this,
            getString(R.string.themer_remove_preset_2d12c),
            getString(R.string.themer_remove_436e1, name),
            getString(R.string.themer_remove_e9639),
            getString(R.string.themer_cancel_77dfd),
            ConfirmAction {
                try {
                    PresetManager.remove(name)
                    Toast.makeText(this, getString(R.string.themer_preset_removed_3a121), Toast.LENGTH_SHORT).show()
                    openSection(SECTION_PRESET_REMOVE)
                } catch (e: Exception) {
                    Toast.makeText(this, e.displayMessage(this@ThemerActivity), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun savePreset(name: String?) {
        try {
            PresetManager.save(this, name ?: return)
            Toast.makeText(this@ThemerActivity, getString(R.string.themer_preset_saved_bb391), Toast.LENGTH_SHORT)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this@ThemerActivity, e.displayMessage(this@ThemerActivity), Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyPreset(name: String?) {
        try {
            PresetManager.apply(name ?: return)

            Toast.makeText(this@ThemerActivity, getString(R.string.themer_preset_applied_reloading_5539d), Toast.LENGTH_SHORT)
                .show()
            recyclerView!!.postDelayed(Runnable {
                LauncherActivity.preview(this)
            }, 500)
        } catch (e: Exception) {
            Toast.makeText(this@ThemerActivity, e.displayMessage(this@ThemerActivity), Toast.LENGTH_SHORT).show()
        }
    }

    private enum class HubAction(val labelRes: Int) {
        LANGUAGE_PACKS(R.string.language_packs_title),
        DEFAULT_SYSTEM_FONT(R.string.hub_default_system_font),
        APPEARANCE(R.string.hub_appearance),
        BEHAVIOR(R.string.hub_behavior),
        SOUNDS(R.string.hub_sounds),
        PERSONALIZATION(R.string.hub_personalization),
        ASCII_SETTINGS(R.string.hub_ascii_settings),
        INTEGRATIONS(R.string.hub_integrations),
        SYSTEM_SUPPORT(R.string.hub_system_support),
        OPEN_WALLPAPER_PICKER(R.string.hub_open_wallpaper_picker),
        OPEN_LIVE_WALLPAPER_PICKER(R.string.hub_open_live_wallpaper_picker),
        RETUI_WALLPAPER(R.string.hub_retui_wallpaper),
        PREFERRED_MUSIC_APP(R.string.hub_preferred_music_app),
        TASKER_INTEGRATION(R.string.hub_tasker_integration),
        RE_KEYBOARD_SHORTCUTS(R.string.hub_re_keyboard_shortcuts),
        FONTS(R.string.hub_fonts),
        TYPOGRAPHY(R.string.hub_typography),
        PRESETS(R.string.hub_presets),
        FRAMES(R.string.hub_frames),
        SAVE_CURRENT_AS_PRESET(R.string.hub_save_current_as_preset),
        APPLY_PRESET(R.string.hub_apply_preset),
        REMOVE_PRESET(R.string.hub_remove_preset),
        IMPORT_FONT(R.string.hub_import_font),
        TOOLBAR_BUTTONS(R.string.hub_toolbar_buttons),
        VIEW_CRASH_LOG(R.string.hub_view_crash_log),
        BACKUP(R.string.hub_backup),
        CREATE_SHAREABLE_CONFIGURATION(R.string.hub_create_shareable_configuration),
        RESTORE(R.string.hub_restore),
        RATE_THE_APP(R.string.hub_rate_the_app),
        GITHUB(R.string.hub_github),
        DISCORD(R.string.hub_discord),
        REDDIT(R.string.hub_reddit),
        SEND_FEEDBACK(R.string.hub_send_feedback),
        LEARN_MORE(R.string.hub_learn_more),
        SIGN_UP_FOR_RETUI_CREDITS(R.string.hub_sign_up_for_retui_credits)
    }

    private fun hubItemLabel(id: String): String {
        if (section == SECTION_PRESET_APPLY || section == SECTION_PRESET_REMOVE) return id
        val action = HubAction.entries.firstOrNull { it.name == id } ?: return id
        return when (action) {
            HubAction.PREFERRED_MUSIC_APP -> getString(R.string.hub_music_summary, preferredMusicAppSummary)
            HubAction.TASKER_INTEGRATION -> getString(R.string.hub_tasker_summary, getString(if (TaskerIntegrationManager.isEnabled(this)) R.string.common_on else R.string.common_off))
            HubAction.SIGN_UP_FOR_RETUI_CREDITS -> getString(R.string.hub_credits_summary, getString(if (RetuiCreditManager.isDystopiaEnabled(this)) R.string.common_on else R.string.common_off))
            else -> getString(action.labelRes)
        }
    }

    private fun getHeaderText(section: String?): String {
        if (SECTION_APPEARANCE == section) {
            return getString(R.string.themer_re_t_ui_appearance_settings_f0df9)
        } else if (SECTION_BEHAVIOR == section) {
            return getString(R.string.themer_re_t_ui_behavior_settings_01843)
        } else if (SECTION_PERSONALIZATION == section) {
            return getString(R.string.themer_re_t_ui_personalization_settings_5032f)
        } else if (SECTION_INTEGRATIONS == section) {
            return getString(R.string.themer_re_t_ui_integrations_5c80d)
        } else if (SECTION_KEYBOARD_SHORTCUTS == section) {
            return getString(R.string.themer_re_keyboard_shortcuts_310c5)
        } else if (SECTION_SYSTEM == section) {
            return getString(R.string.themer_re_t_ui_system_support_84def)
        } else if (SECTION_FONTS == section) {
            return getString(R.string.themer_re_t_ui_fonts_45a98)
        } else if (SECTION_TYPOGRAPHY == section) {
            return getString(R.string.themer_re_t_ui_typography_3e40f)
        } else if (SECTION_PRESETS == section) {
            return getString(R.string.themer_re_t_ui_presets_b7e7f)
        } else if (SECTION_FRAMES == section) {
            return getString(R.string.themer_re_t_ui_frames_b462d)
        } else if (SECTION_PRESET_APPLY == section) {
            return getString(R.string.themer_apply_preset_72454)
        } else if (SECTION_PRESET_REMOVE == section) {
            return getString(R.string.themer_remove_preset_2d12c)
        }
        return getString(R.string.themer_re_t_ui_settings_hub_ed322)
    }

    private fun getItemsForSection(section: String?): MutableList<String> {
        if (SECTION_APPEARANCE == section) {
            return mutableListOf(
                "theme.xml",
                "ui.xml",
                "toolbar.xml",
                "suggestions.xml",
                HubAction.FONTS.name,
                HubAction.PRESETS.name,
                HubAction.FRAMES.name,
                HubAction.OPEN_WALLPAPER_PICKER.name,
                HubAction.OPEN_LIVE_WALLPAPER_PICKER.name
            )
        } else if (SECTION_BEHAVIOR == section) {
            return mutableListOf(
                HubAction.SOUNDS.name,
                "behavior.xml",
                "apps.xml",
                "notifications.xml",
                "cmd.xml"
            )
        } else if (SECTION_PERSONALIZATION == section) {
            return mutableListOf(
                HubAction.RETUI_WALLPAPER.name,
                HubAction.SIGN_UP_FOR_RETUI_CREDITS.name,
                "alias.txt",
                HubAction.TOOLBAR_BUTTONS.name,
                HubAction.ASCII_SETTINGS.name,
                "rss.xml"
            )
        } else if (SECTION_INTEGRATIONS == section) {
            return mutableListOf(
                HubAction.RE_KEYBOARD_SHORTCUTS.name,
                HubAction.PREFERRED_MUSIC_APP.name,
                HubAction.TASKER_INTEGRATION.name
            )
        } else if (SECTION_KEYBOARD_SHORTCUTS == section) {
            return mutableListOf(KEYBOARD_SHORTCUT_PANEL)
        } else if (SECTION_SYSTEM == section) {
            return mutableListOf(
                HubAction.LANGUAGE_PACKS.name,
                HubAction.BACKUP.name,
                HubAction.CREATE_SHAREABLE_CONFIGURATION.name,
                HubAction.RESTORE.name,
                HubAction.RATE_THE_APP.name,
                HubAction.SEND_FEEDBACK.name,
                HubAction.VIEW_CRASH_LOG.name
            )
        } else if (SECTION_FONTS == section) {
            ensurePendingFontChanges()
            return mutableListOf(
                HubAction.TYPOGRAPHY.name,
                HubAction.DEFAULT_SYSTEM_FONT.name,
                HubAction.IMPORT_FONT.name
            ).apply {
                addAll(listFontFiles(fontsDir).map { it.name })
            }
        } else if (SECTION_TYPOGRAPHY == section) {
            ensurePendingFontChanges()
            return mutableListOf(FONT_SCALE_PANEL)
        } else if (SECTION_PRESETS == section) {
            return mutableListOf(HubAction.SAVE_CURRENT_AS_PRESET.name, HubAction.APPLY_PRESET.name, HubAction.REMOVE_PRESET.name)
        } else if (SECTION_FRAMES == section) {
            return mutableListOf(FRAME_PANEL)
        } else if (SECTION_PRESET_APPLY == section) {
            return PresetManager.listAllPresetNames().toMutableList()
        } else if (SECTION_PRESET_REMOVE == section) {
            return PresetManager.listSavedPresetFolders().filterNotNull().toMutableList()
        }

        return mutableListOf(
            HubAction.APPEARANCE.name,
            HubAction.BEHAVIOR.name,
            HubAction.PERSONALIZATION.name,
            HubAction.INTEGRATIONS.name,
            HubAction.SYSTEM_SUPPORT.name
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
        root.addView(CheckBox(this).apply {
            text = getString(R.string.themer_enable_custom_frames_c7fdd)
            isChecked = FrameManager.isEnabled(this@ThemerActivity)
            setTextColor(textColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
            setPadding(dp(this@ThemerActivity, 12f), dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 12f), dp(this@ThemerActivity, 10f))
            background = rect(this@ThemerActivity, surfaceColor(), borderColor(), 1.25f)
            buttonTintList = ColorStateList.valueOf(accentColor())
            setOnCheckedChangeListener { _, checked ->
                FrameManager.setEnabled(this@ThemerActivity, checked)
                Toast.makeText(
                    this@ThemerActivity,
                    if (checked) getString(R.string.themer_custom_frames_enabled_4475d) else getString(R.string.themer_custom_frames_disabled_your_active_pack_is_preserved_a8170),
                    Toast.LENGTH_SHORT
                ).show()
                sectionsAdapter?.notifyDataSetChanged()
                LauncherActivity.preview(this@ThemerActivity)
            }
        }, inputParams())

        root.addView(TextView(this).apply {
            text = getString(R.string.themer_turn_frames_off_to_use_generated_borders_your_active_pack_26eea)
            setTextColor(textColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity))
            textSize = 12f
            setPadding(dp(this@ThemerActivity, 8f), 0, dp(this@ThemerActivity, 8f), dp(this@ThemerActivity, 10f))
        }, inputParams())

        val toggle = CheckBox(this).apply {
            text = getString(R.string.themer_apply_one_frame_to_all_surfaces_aa400)
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
                getString(R.string.themer_import_a_square_3_x_3_png_or_a_retui_frame_file_the_import_f8690)
            } else {
                getString(R.string.themer_import_a_square_3_x_3_png_or_a_retui_frame_file_per_surfac_67740)
            }
            setTextColor(textColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity))
            textSize = 12f
            setPadding(dp(this@ThemerActivity, 8f), 0, dp(this@ThemerActivity, 8f), dp(this@ThemerActivity, 10f))
        }, inputParams())

        root.addView(TextView(this).apply {
            text = getString(R.string.themer_import_ui_package_e6f87)
            styleButton(this@ThemerActivity, this, false)
            setOnClickListener { launchUiPackagePicker() }
        }, inputParams())

        root.addView(TextView(this).apply {
            text = getString(R.string.themer_import_a_retui_ui_zip_package_it_is_added_to_frame_packs_w_29903)
            setTextColor(textColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity))
            textSize = 12f
            setPadding(dp(this@ThemerActivity, 8f), 0, dp(this@ThemerActivity, 8f), dp(this@ThemerActivity, 10f))
        }, inputParams())

        root.addView(TextView(this).apply {
            text = getString(R.string.themer_save_frame_settings_796a7)
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
                text = getString(R.string.themer_frame_packs_086f2)
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
            text = (target?.let { getString(it.labelRes) } ?: getString(R.string.themer_all_surfaces_d0273)).uppercase(Locale.getDefault())
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
                contentDescription = getString(R.string.themer_original_frame_png_preview_86c26)
            } else {
                setImageResource(if (invalid) android.R.drawable.ic_menu_report_image else android.R.drawable.ic_menu_gallery)
                alpha = if (invalid) 1f else 0.4f
                contentDescription = if (invalid) getString(R.string.themer_frame_png_missing_or_corrupt_5b52d) else getString(R.string.themer_no_frame_imported_b07b6)
            }
        }
        previewRow.addView(image, LinearLayout.LayoutParams(dp(this, 104f), dp(this, 72f)))
        previewRow.addView(TextView(this).apply {
            text = when {
                invalid -> getString(R.string.themer_png_missing_or_corrupt_default_border_fallback_a1eb5)
                preview != null -> getString(R.string.themer_original_png_b57db, session.assignedName(target) ?: getString(R.string.themer_imported_frame_651d9))
                else -> getString(R.string.themer_no_frame_default_border_fallback_d21cf)
            }
            setTextColor(if (invalid) Color.RED else textColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity))
            textSize = 11f
            setPadding(dp(this@ThemerActivity, 12f), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(previewRow)

        row.addView(TextView(this).apply {
            val selected = session.selectedAssetId(target)
            text = if (selected == null) getString(R.string.themer_frame_default_border_03aae) else
                getString(R.string.themer_frame_9fec1, session.assignedName(target) ?: getString(R.string.themer_missing_or_corrupt_c7da0))
            styleButton(this@ThemerActivity, this, false)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 46f)))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(TextView(this).apply {
            text = if (hasBundle) getString(R.string.themer_replace_c336f) else getString(R.string.themer_import_b034f)
            styleButton(this@ThemerActivity, this, false)
            setOnClickListener { launchFrameImportPicker(target) }
        }, LinearLayout.LayoutParams(0, dp(this, 46f), 1f))
        if (hasBundle) {
            actions.addView(View(this), LinearLayout.LayoutParams(dp(this, 8f), 1))
            actions.addView(TextView(this).apply {
                text = getString(R.string.themer_edit_17865)
                styleButton(this@ThemerActivity, this, false)
                setOnClickListener { showFrameEditor(target) }
            }, LinearLayout.LayoutParams(0, dp(this, 46f), 1f))
            actions.addView(View(this), LinearLayout.LayoutParams(dp(this, 8f), 1))
            actions.addView(TextView(this).apply {
                text = getString(R.string.themer_use_default_8e95d)
                styleButton(this@ThemerActivity, this, false)
                setOnClickListener {
                    session.select(target, null)
                    reloadForFrame(getString(R.string.themer_default_border_selected_09de3))
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
                append(resources.getQuantityString(R.plurals.custom_frame_count, pack.assignments.size, pack.assignments.size))
                if (FrameManager.isBuiltInPack(pack.id)) append(getString(R.string.themer_built_in_9cfde))
                if (active) append(getString(R.string.themer_active_5b74a))
            }
            setTextColor(if (active) accentColor() else textColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
        })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(this@ThemerActivity, 8f), 0, 0)
        }
        actions.addView(TextView(this).apply {
            text = if (active) getString(R.string.themer_applied_d879d) else getString(R.string.themer_apply_4433c)
            styleButton(this@ThemerActivity, this, false)
            isEnabled = !active
            alpha = if (active) 0.45f else 1f
            setOnClickListener { applyFramePack(pack) }
        }, LinearLayout.LayoutParams(0, dp(this, 46f), 1f))
        if (!FrameManager.isBuiltInPack(pack.id)) {
            actions.addView(View(this), LinearLayout.LayoutParams(dp(this, 8f), 1))
            actions.addView(TextView(this).apply {
                text = getString(R.string.themer_delete_d6f56)
                styleButton(this@ThemerActivity, this, false)
                setOnClickListener { confirmDeleteFramePack(pack) }
            }, LinearLayout.LayoutParams(0, dp(this, 46f), 1f))
        }
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
                contentDescription = getString(R.string.themer_sp_font_preview_4055c, baseSize.toInt())
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
            contentDescription = getString(R.string.themer_decrease_font_size_offset_18761)
            isClickable = true
            isFocusable = true
        }
        val slider = SeekBar(parent.context).apply {
            max = LauncherFontScale.MAX_OFFSET - LauncherFontScale.MIN_OFFSET
            contentDescription = getString(R.string.themer_font_size_offset_27f1f)
            TuixtTheme.styleSlider(parent.context, this)
        }
        val larger = TextView(parent.context).apply {
            text = "A"
            gravity = Gravity.CENTER
            contentDescription = getString(R.string.themer_increase_font_size_offset_24b0b)
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
        val reset = TextView(parent.context).apply { text = getString(R.string.themer_reset_all_ca151) }
        val save = TextView(parent.context).apply { text = getString(R.string.themer_apply_4433c) }
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
                text = getString(spec.label).uppercase(Locale.getDefault())
                setTextColor(accentColor())
                setTypeface(typeface, Typeface.BOLD)
                textSize = 12f
            }
            val sample = TextView(this).apply {
                text = getString(spec.sample)
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
            val reset = TextView(this).apply { text = getString(R.string.themer_reset_995f2) }
            styleButton(this, smaller, false)
            styleButton(this, larger, false)
            styleButton(this, reset, false)
            smaller.contentDescription = getString(R.string.themer_decrease_size_469ef, getString(spec.label))
            larger.contentDescription = getString(R.string.themer_increase_size_1e8c3, getString(spec.label))
            reset.contentDescription = getString(R.string.themer_reset_size_51a7d, getString(spec.label))
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
                    getString(R.string.themer_sp_base_7639d, effective.toInt(), base)
                } else {
                    getString(R.string.themer_sp_ca43c, effective.toInt())
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
            holder.status.text = getString(R.string.themer_master_sp_d57ee, pendingFontLabel(), signed)
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
            getString(R.string.themer_system_29d43)
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
                check(source.exists() && source.isFile) { getString(R.string.themer_selected_font_is_no_longer_available_b914c) }
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
            Toast.makeText(this, getString(R.string.themer_typography_saved_applying_7cc74), Toast.LENGTH_SHORT).show()
            LauncherActivity.preview(this)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.themer_could_not_apply_font_8bf6d, e.displayMessage(this@ThemerActivity)), Toast.LENGTH_LONG).show()
        }
    }

    private fun isDystopiaRow(label: String?): Boolean =
        label == HubAction.SIGN_UP_FOR_RETUI_CREDITS.name

    private fun handleDystopiaOptIn() {
        if (RetuiCreditManager.isDystopiaEnabled(this)) {
            RetuiCreditManager.setDystopiaEnabled(this, false)
            LockdownManager.getInstance(this).stop(getString(R.string.themer_lockdown_disabled_5cdbb))
            Toast.makeText(this, getString(R.string.themer_retui_credits_disabled_e91ca), Toast.LENGTH_SHORT).show()
            openSection(SECTION_PERSONALIZATION)
            return
        }
        showDystopiaConsentDialog()
    }

    private fun showDystopiaConsentDialog() {
        TuixtDialog.showCustom(this, getString(R.string.themer_sign_up_for_retui_credits_99c9d), ContentFactory { dialog: Dialog? ->
            val content = LinearLayout(this)
            content.orientation = LinearLayout.VERTICAL
            content.gravity = Gravity.CENTER

            val description = TextView(this)
            description.text = getString(R.string.themer_enables_local_retui_credits_breach_keys_breach_puzzles_pai_408b6)
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
            prompt.text = getString(R.string.themer_hold_fingerprint_for_3_seconds_4acf7)
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
            fingerprint.contentDescription = getString(R.string.themer_hold_to_sign_up_for_retui_credits_cc650)
            fingerprint.setOnClickListener { }

            val handler = Handler(Looper.getMainLooper())
            val enable = Runnable {
                RetuiCreditManager.setDystopiaEnabled(this, true)
                dialog?.dismiss()
                Toast.makeText(this, getString(R.string.themer_retui_credits_enabled_1000_fake_credits_granted_51fe4), Toast.LENGTH_SHORT).show()
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
            getString(R.string.themer_open_github_0b4cb)
        ) { openExternalUrl(GITHUB_URL) }
        addSupportButton(
            footer,
            R.drawable.ic_tuixt_discord_24,
            getString(R.string.themer_open_discord_d6540)
        ) { openExternalUrl(DISCORD_URL) }
        addSupportButton(
            footer,
            R.drawable.ic_tuixt_reddit_24,
            getString(R.string.themer_open_reddit_f3fd5)
        ) { openExternalUrl(REDDIT_URL) }
        addSupportButton(
            footer,
            R.drawable.ic_tuixt_web_24,
            getString(R.string.themer_open_re_t_ui_website_3a242)
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
                getString(R.string.themer_discard_changes_f99ee),
                getString(R.string.themer_unsaved_frame_settings_and_imports_will_be_lost_05741),
                getString(R.string.themer_discard_36fff),
                getString(R.string.themer_keep_editing_ced7d),
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
                getString(R.string.themer_discard_changes_f99ee),
                getString(R.string.themer_unsaved_font_and_scale_changes_will_be_lost_18b63),
                getString(R.string.themer_discard_36fff),
                getString(R.string.themer_keep_editing_ced7d),
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
            intent.putExtra(TuixtActivity.EXCLUDE_SECTION, getString(R.string.themer_sounds_fb1c3))
        }
        openSettingsChild(intent)
    }

    private fun openSoundsSettings() {
        val intent = Intent(this@ThemerActivity, TuixtActivity::class.java)
        intent.putExtra(TuixtActivity.PATH, File(Tuils.getFolder(), "behavior.xml").getAbsolutePath())
        intent.putExtra(TuixtActivity.ONLY_SECTION, getString(R.string.themer_sounds_fb1c3))
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
            getString(R.string.themer_toolbar_buttons_8936a),
            options,
            ItemAction { which: Int -> showToolbarButtonSlotDialog(which + 1) })
    }

    private fun toolbarSlotSummary(slot: Int): String {
        val current = slot(slot)
        if (!current.enabled) {
            return getString(R.string.themer_slot_off_b2989, slot)
        }
        return getString(R.string.themer_slot_54b40, slot, getString(current.iconLabel), current.command)
    }

    private fun showToolbarButtonSlotDialog(slot: Int) {
        val current = slot(slot)
        val options: MutableList<String?> = ArrayList<String?>()
        options.add(if (current.enabled) getString(R.string.themer_disable_slot_8bc2b) else getString(R.string.themer_enable_slot_0673c))
        options.add(getString(R.string.themer_set_command_c7afe, displayValue(current.command, "empty")))
        options.add(getString(R.string.themer_set_icon_96d11, getString(current.iconLabel)))
        options.add(getString(R.string.themer_clear_slot_e4ba8))

        TuixtDialog.showOptions(this, getString(R.string.themer_toolbar_slot_b5a77, slot), options, ItemAction { which: Int ->
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
                reloadLauncherForToolbarButtons(getString(R.string.themer_toolbar_slot_cleared_ce6b8))
                recyclerView!!.postDelayed(Runnable { this.showToolbarButtonsDialog() }, 250)
            }
        })
    }

    private fun showToolbarButtonCommandDialog(slot: Int, enableAfterSave: Boolean) {
        val current = slot(slot)
        val content = LinearLayout(this)
        content.setOrientation(LinearLayout.VERTICAL)

        val help = TextView(this)
        help.setText(getString(R.string.themer_enter_the_same_text_you_would_type_at_the_prompt_examples_c3596))
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

        val input = commandInput(getString(R.string.themer_command_or_app_name_9daa1))
        input.setText(current.command)
        input.setSelectAllOnFocus(true)
        content.addView(input, inputParams())

        TuixtDialog.showContent(this, getString(R.string.themer_toolbar_command_f70c1), content, getString(R.string.themer_save_efc00), getString(R.string.themer_cancel_77dfd), ConfirmAction {
            val command = input.getText().toString().trim { it <= ' ' }
            if (command.length == 0) {
                Toast.makeText(this, getString(R.string.themer_command_is_required_475a6), Toast.LENGTH_SHORT).show()
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
            labels.add(getString(icon.labelRes))
        }

        TuixtDialog.showOptions(this, getString(R.string.themer_toolbar_icon_b7566), labels, ItemAction { which: Int ->
            val icon = icons.get(which)
            saveToolbarSlot(slot, current.enabled, current.command, icon.key)
            recyclerView!!.postDelayed(Runnable { showToolbarButtonSlotDialog(slot) }, 250)
        })
    }

    private fun saveToolbarSlot(slot: Int, enabled: Boolean, command: String?, icon: String?) {
        saveSlot(this, slot, enabled, command, icon)
        reloadLauncherForToolbarButtons(if (enabled) getString(R.string.themer_toolbar_button_saved_62678) else getString(R.string.themer_toolbar_button_disabled_412cc))
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
            getString(R.string.themer_delete_font_bdc67),
            getString(R.string.themer_delete_137cd, font.getName()),
            getString(R.string.themer_delete_f6fdb),
            getString(R.string.themer_cancel_77dfd),
            ConfirmAction {
                deleteFont(font)
            })
    }

    private fun deleteFont(font: File) {
        val deletedName = font.getName()
        if (!savedUseSystemFont() && savedFontFileName() == deletedName) {
            Toast.makeText(
                this,
                getString(R.string.themer_select_and_save_another_font_before_deleting_the_active_fo_add4a),
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
            Toast.makeText(this, getString(R.string.themer_could_not_delete_font_559bc), Toast.LENGTH_LONG).show()
            return
        }

        if (pendingUseSystemFont == false && pendingFontFileName == deletedName) {
            pendingUseSystemFont = savedUseSystemFont()
            pendingFontFileName = savedFontFileName()
        }

        Toast.makeText(this, getString(R.string.themer_font_deleted_3f979), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.themer_font_picker_is_unavailable_on_this_device_b8d21), Toast.LENGTH_SHORT)
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
            Toast.makeText(this, getString(R.string.themer_file_picker_is_unavailable_on_this_device_9a200), Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchUiPackagePicker() {
        if (frameSession().hasChanges()) {
            TuixtDialog.showConfirm(
                this,
                getString(R.string.themer_discard_unsaved_frame_edits_9bf85),
                getString(R.string.themer_importing_a_ui_package_installs_a_separate_saved_pack_disc_0b86a),
                getString(R.string.themer_discard_and_continue_6ee1b),
                getString(R.string.themer_cancel_77dfd),
                ConfirmAction {
                    discardFrameChanges()
                    openUiPackageZipPicker()
                }
            )
        } else {
            openUiPackageZipPicker()
        }
    }

    private fun openUiPackageZipPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/octet-stream"))
        }
        try {
            startActivityForResult(intent, UI_PACKAGE_ZIP_IMPORT_REQUEST)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.themer_file_picker_is_unavailable_on_this_device_9a200), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFrameEditor(target: FrameTarget?) {
        val session = frameSession()
        val details = session.assignedDetails(target) ?: run {
            Toast.makeText(this, getString(R.string.themer_this_frame_cannot_be_edited_73f23), Toast.LENGTH_SHORT).show()
            return
        }
        TuixtDialog.showCustom(this, getString(R.string.themer_edit_5fa4d, target?.let { getString(it.labelRes) } ?: getString(R.string.themer_all_surfaces_d0273)), ContentFactory { dialog ->
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            content.addView(TextView(this).apply {
                text = getString(R.string.themer_x_px_changes_stay_staged_until_save_frame_settings_d0a46, details.name, details.width, details.height)
                setTextColor(textColor())
                setTypeface(Tuils.getTypeface(this@ThemerActivity))
                textSize = 12f
                setPadding(0, 0, 0, dp(this@ThemerActivity, 10f))
            })

            val inputs = HashMap<String, EditText>()
            fun numberGrid(title: String, prefix: String, values: List<Number>, decimal: Boolean) {
                content.addView(TextView(this).apply {
                    text = title
                    setTextColor(accentColor())
                    setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
                    textSize = 12f
                    setPadding(0, dp(this@ThemerActivity, 8f), 0, dp(this@ThemerActivity, 4f))
                })
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                listOf("left" to R.string.frame_left, "top" to R.string.frame_top, "right" to R.string.frame_right, "bottom" to R.string.frame_bottom).forEachIndexed { index, (side, label) ->
                    val column = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        if (index > 0) setPadding(dp(this@ThemerActivity, 4f), 0, 0, 0)
                    }
                    column.addView(TextView(this).apply {
                        text = getString(label)
                        setTextColor(textColor())
                        setTypeface(Tuils.getTypeface(this@ThemerActivity))
                        textSize = 9f
                    })
                    val input = EditText(this).apply {
                        setText(values[index].toString())
                        inputType = InputType.TYPE_CLASS_NUMBER or
                                if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
                        setSelectAllOnFocus(true)
                        styleInput(this@ThemerActivity, this)
                    }
                    inputs["${prefix}_${side.lowercase(Locale.ROOT)}"] = input
                    column.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
                content.addView(row)
            }

            numberGrid(
                getString(R.string.themer_border_size_dp_42fb4), "border",
                listOf(details.spec.leftDp, details.spec.topDp, details.spec.rightDp, details.spec.bottomDp),
                true
            )
            numberGrid(
                getString(R.string.themer_image_slices_px_ff2d9), "slice",
                listOf(details.spec.leftPx, details.spec.topPx, details.spec.rightPx, details.spec.bottomPx),
                false
            )

            val modes = hashMapOf(
                "left" to details.spec.leftMode,
                "top" to details.spec.topMode,
                "right" to details.spec.rightMode,
                "bottom" to details.spec.bottomMode,
                "center" to details.spec.centerMode,
                "filtering" to details.spec.filtering
            )
            content.addView(TextView(this).apply {
                text = getString(R.string.themer_drawing_4c495)
                setTextColor(accentColor())
                setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
                textSize = 12f
                setPadding(0, dp(this@ThemerActivity, 12f), 0, dp(this@ThemerActivity, 4f))
            })
            val modeRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun modeButton(label: Int, key: String, choices: List<String>): TextView = TextView(this).apply {
                fun refresh() {
                    val modeLabel = when (modes.getValue(key)) {
                        "tile" -> R.string.frame_tile
                        "stretch" -> R.string.frame_stretch
                        "none" -> R.string.frame_none
                        "nearest" -> R.string.frame_nearest
                        else -> R.string.frame_linear
                    }
                    text = getString(R.string.frame_mode_label, getString(label), getString(modeLabel))
                }
                refresh()
                styleButton(this@ThemerActivity, this, false)
                setOnClickListener {
                    val current = modes.getValue(key)
                    modes[key] = choices[(choices.indexOf(current) + 1) % choices.size]
                    refresh()
                }
            }
            listOf(
                listOf(R.string.frame_left to "left", R.string.frame_top to "top"),
                listOf(R.string.frame_right to "right", R.string.frame_bottom to "bottom"),
                listOf(R.string.frame_center to "center", R.string.frame_filtering to "filtering")
            ).forEach { pair ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                pair.forEachIndexed { index, (label, key) ->
                    val choices = if (key == "center") listOf("stretch", "tile", "none") else
                        if (key == "filtering") listOf("nearest", "linear") else listOf("tile", "stretch")
                    val params = LinearLayout.LayoutParams(0, dp(this, 44f), 1f).apply {
                        if (index > 0) leftMargin = dp(this@ThemerActivity, 8f)
                        bottomMargin = dp(this@ThemerActivity, 8f)
                    }
                    row.addView(modeButton(label, key, choices), params)
                }
                modeRows.addView(row)
            }
            content.addView(modeRows)

            val error = TextView(this).apply {
                setTextColor(Color.RED)
                setTypeface(Tuils.getTypeface(this@ThemerActivity))
                textSize = 12f
                visibility = View.GONE
            }
            content.addView(error)
            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(this@ThemerActivity, 6f), 0, 0)
            }
            buttons.addView(TextView(this).apply {
                text = getString(R.string.themer_cancel_1507c)
                styleButton(this@ThemerActivity, this, false)
                setOnClickListener { dialog?.dismiss() }
            }, LinearLayout.LayoutParams(0, dp(this, 46f), 1f))
            buttons.addView(View(this), LinearLayout.LayoutParams(dp(this, 8f), 1))
            buttons.addView(TextView(this).apply {
                text = getString(R.string.themer_apply_edits_ec4da)
                styleButton(this@ThemerActivity, this, true)
                setOnClickListener {
                    try {
                        fun intValue(key: String) = requireNotNull(inputs[key]?.text?.toString()?.toIntOrNull()) {
                            getString(R.string.themer_slice_values_must_be_positive_whole_pixels_bcdc1)
                        }
                        fun floatValue(key: String) = requireNotNull(inputs[key]?.text?.toString()?.toFloatOrNull()) {
                            getString(R.string.themer_borders_must_be_between_0_and_256_dp_49480)
                        }
                        val spec = FrameSpec(
                            intValue("slice_left"), intValue("slice_top"), intValue("slice_right"), intValue("slice_bottom"),
                            floatValue("border_left"), floatValue("border_top"), floatValue("border_right"), floatValue("border_bottom"),
                            modes.getValue("top"), modes.getValue("right"), modes.getValue("bottom"), modes.getValue("left"),
                            modes.getValue("center"), modes.getValue("filtering")
                        )
                        FrameManager.frameSpecError(spec, details.width, details.height)?.let { throw it }
                        session.updateFrameSpec(target, spec)
                        dialog?.dismiss()
                        reloadForFrame(getString(R.string.themer_frame_controls_updated_b0b31))
                    } catch (e: Exception) {
                        error.text = e.displayMessage(this@ThemerActivity) ?: getString(R.string.themer_invalid_frame_settings_7301e)
                        error.visibility = View.VISIBLE
                    }
                }
            }, LinearLayout.LayoutParams(0, dp(this, 46f), 1f))
            content.addView(buttons)
            content
        })
    }

    private fun reloadForFrame(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        sectionsAdapter?.notifyDataSetChanged()
    }

    private fun frameSession(): FrameManager.EditSession {
        if (frameEditSession?.isStale() == true) {
            frameEditSession?.discard()
            frameEditSession = null
        }
        return frameEditSession ?: FrameManager.beginEdit(this).also { frameEditSession = it }
    }

    private fun saveFrameChanges() {
        val session = frameEditSession ?: return
        val choices = mutableListOf<String>()
        if (session.currentPackId() != null) choices.add(getString(R.string.themer_save_current_pack_72386))
        choices.add(getString(R.string.themer_create_new_pack_bc161))
        if (session.packs().any { !FrameManager.isBuiltInPack(it.id) }) choices.add(getString(R.string.themer_replace_existing_pack_8f193))
        TuixtDialog.showOptions(this, getString(R.string.themer_save_frame_settings_cd8c7), choices, ItemAction { choice ->
            when (choices[choice]) {
                getString(R.string.themer_save_current_pack_72386) -> saveCurrentFramePack()
                getString(R.string.themer_create_new_pack_bc161) -> showCreateFramePack()
                else -> showReplaceFramePack()
            }
        })
    }

    private fun saveCurrentFramePack() {
        val session = frameEditSession ?: return
        val packId = session.currentPackId() ?: return
        val pack = session.packs().firstOrNull { it.id == packId } ?: return
        try {
            session.replacePack(packId)
            persistFrameSession(getString(R.string.themer_saved_and_applied_57003, pack.name))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.themer_could_not_save_frame_pack_55234, e.displayMessage(this@ThemerActivity)), Toast.LENGTH_LONG).show()
        }
    }

    private fun showCreateFramePack() {
        val session = frameEditSession ?: return
        TuixtDialog.showValidatedForm(
            this,
            getString(R.string.themer_create_frame_pack_35416),
            listOf(FormField("name", getString(R.string.themer_pack_name_20f8a), getString(R.string.themer_my_frame_pack_a0f31))),
            getString(R.string.themer_create_6e157),
            getString(R.string.themer_cancel_77dfd),
            FormValidator { values -> session.packNameError(values["name"].orEmpty())?.displayMessage(this@ThemerActivity) },
            FormAction { values ->
                try {
                    session.createPack(values["name"].orEmpty())
                    persistFrameSession(getString(R.string.themer_frame_pack_created_and_applied_7c5de))
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.themer_could_not_create_frame_pack_f8989, e.displayMessage(this@ThemerActivity)), Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun showReplaceFramePack() {
        val packs = frameEditSession?.packs().orEmpty().filterNot { FrameManager.isBuiltInPack(it.id) }
        if (packs.isEmpty()) return
        TuixtDialog.showOptions(this, getString(R.string.themer_replace_frame_pack_f44f9), packs.map { it.name }, ItemAction { index ->
            val pack = packs[index]
            TuixtDialog.showConfirm(
                this,
                getString(R.string.themer_replace_e9bb0, pack.name),
                getString(R.string.themer_replace_this_pack_with_the_complete_current_frame_setup_576ad),
                getString(R.string.themer_replace_a7cf7),
                getString(R.string.themer_cancel_77dfd),
                ConfirmAction {
                    try {
                        frameEditSession?.replacePack(pack.id) ?: return@ConfirmAction
                        persistFrameSession(getString(R.string.themer_replaced_and_applied_997ba, pack.name))
                    } catch (e: Exception) {
                        Toast.makeText(this, getString(R.string.themer_could_not_replace_frame_pack_5928b, e.displayMessage(this@ThemerActivity)), Toast.LENGTH_LONG).show()
                    }
                }
            )
        })
    }

    private fun applyFramePack(pack: FrameManager.FramePack) {
        withCleanFrameSession(getString(R.string.themer_unsaved_element_edits_will_be_discarded_before_applying_98e49, pack.name)) { session ->
            try {
                session.applyPack(pack.id)
                persistFrameSession(getString(R.string.themer_applied_56d65, pack.name))
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.themer_could_not_apply_frame_pack_abfd3, e.displayMessage(this@ThemerActivity)), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDeleteFramePack(pack: FrameManager.FramePack) {
        val active = frameSession().activePackId() == pack.id
        val message = buildString {
            append(getString(R.string.themer_delete_137cd, pack.name))
            if (active) append(getString(R.string.themer_the_active_frame_setup_will_return_to_defaults_9a38a))
            if (frameSession().hasChanges()) append(getString(R.string.themer_unsaved_element_edits_will_be_discarded_38d5f))
        }
        TuixtDialog.showConfirm(this, getString(R.string.themer_delete_frame_pack_7c7f4), message, getString(R.string.themer_delete_f6fdb), getString(R.string.themer_cancel_77dfd), ConfirmAction {
            val session = if (frameSession().hasChanges()) {
                discardFrameChanges()
                frameSession()
            } else frameSession()
            try {
                session.deletePack(pack.id)
                persistFrameSession(getString(R.string.themer_deleted_b89bd, pack.name))
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.themer_could_not_delete_frame_pack_b1e2e, e.displayMessage(this@ThemerActivity)), Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun withCleanFrameSession(message: String, action: (FrameManager.EditSession) -> Unit) {
        val session = frameSession()
        if (!session.hasChanges()) {
            action(session)
            return
        }
        TuixtDialog.showConfirm(this, getString(R.string.themer_discard_unsaved_edits_a391b), message, getString(R.string.themer_discard_and_apply_bfec7), getString(R.string.themer_cancel_77dfd), ConfirmAction {
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
            sectionsAdapter?.notifyDataSetChanged()
            LauncherActivity.preview(this)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.themer_could_not_save_frame_settings_a3663, e.displayMessage(this@ThemerActivity)), Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, getString(R.string.themer_could_not_preview_font_c979a, e.displayMessage(this@ThemerActivity)), Toast.LENGTH_LONG).show()
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
                    getString(R.string.themer_select_wallpaper_4842b)
                )
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.themer_wallpaper_picker_is_unavailable_on_this_device_a5a4d),
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
                Toast.makeText(this, getString(R.string.themer_live_wallpaper_picker_is_unavailable_on_this_device_05a68), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.themer_backup_picker_is_unavailable_on_this_device_5eb44), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun showBackupProtectionDialog() {
        TuixtDialog.showOptions(
            this,
            getString(R.string.themer_backup_protection_6fea3),
            mutableListOf<String?>(getString(R.string.themer_encrypt_with_password_1b2c0), getString(R.string.themer_export_without_password_159da)),
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
        options.add(getString(R.string.themer_current_active_look_ffd5a))
        for (preset in presets) {
            options.add(getString(R.string.themer_preset_f6b28, preset))
        }

        TuixtDialog.showOptions(this, getString(R.string.themer_shareable_source_06d7c), options, ItemAction { which: Int ->
            pendingShareablePresetName = if (which == 0) null else presets.get(which - 1)
            showShareableBehaviorPicker()
        })
    }

    private fun showShareableBehaviorPicker() {
        pendingShareableBehaviorLabels = null
        val behaviorValues = try {
            PresetManager.shareableBehaviorValues(pendingShareablePresetName)
        } catch (error: Exception) {
            pendingShareablePresetName = null
            Toast.makeText(this, error.displayMessage(this@ThemerActivity) ?: getString(R.string.themer_unable_to_read_preset_behavior_e8a41), Toast.LENGTH_LONG).show()
            return
        }
        val defaults = PresetManager.defaultShareableBehaviorLabels()
        val selected = LinkedHashSet<String>()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@ThemerActivity).apply {
                text = getString(R.string.themer_choose_which_behavior_values_to_include_the_existing_safe_56372)
                setTextColor(textColor())
                setTypeface(Tuils.getTypeface(this@ThemerActivity))
                textSize = 12f
                setPadding(dp(this@ThemerActivity, 8f), dp(this@ThemerActivity, 4f), dp(this@ThemerActivity, 8f), dp(this@ThemerActivity, 10f))
            })
            behaviorValues.forEach { (setting, value) ->
                val label = setting.label().orEmpty()
                val checked = label in defaults
                if (checked) selected.add(label)
                addView(CheckBox(this@ThemerActivity).apply {
                    text = buildString {
                        append(label.replace('_', ' ').uppercase(Locale.getDefault()))
                        append("\n")
                        append(getString(R.string.themer_value_a89f9))
                        append(value)
                    }
                    isChecked = checked
                    setTextColor(textColor())
                    setTypeface(Tuils.getTypeface(this@ThemerActivity))
                    textSize = 12f
                    setPadding(dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 8f), dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 8f))
                    background = rect(this@ThemerActivity, surfaceColor(), borderColor(), 1.25f)
                    buttonTintList = ColorStateList.valueOf(accentColor())
                    setOnCheckedChangeListener { _, enabled ->
                        if (enabled) selected.add(label) else selected.remove(label)
                    }
                }, inputParams())
            }
        }
        TuixtDialog.showContent(
            this,
            getString(R.string.themer_behavior_sharing_441b1),
            content,
            getString(R.string.themer_continue_2e026),
            getString(R.string.themer_cancel_77dfd),
            ConfirmAction {
                pendingShareableBehaviorLabels = selected.toSet()
                launchShareableConfigurationPicker()
            },
            fillHeight = true,
            heightFraction = 0.88f
        )
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
            pendingShareableBehaviorLabels = null
            Toast.makeText(
                this,
                getString(R.string.themer_configuration_picker_is_unavailable_on_this_device_6c2c4),
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
                getString(R.string.themer_restore_picker_is_unavailable_on_this_device_95294),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val preferredMusicAppSummary: String
        get() {
            val packageName = preferredPackage()
            if (packageName == null || packageName.length == 0) {
                return getString(R.string.themer_auto_detect_85cd1)
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

    private fun bindKeyboardShortcutPanel(holder: KeyboardShortcutPanelViewHolder) {
        val root = holder.root
        root.removeAllViews()
        root.setPadding(0, dp(this, 4f), 0, dp(this, 12f))

        root.addView(TextView(this).apply {
            text = getString(R.string.themer_hold_to_configure_289b4)
            setTextColor(accentColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
            textSize = 11f
            letterSpacing = 0.12f
            setPadding(dp(this@ThemerActivity, 4f), 0, 0, dp(this@ThemerActivity, 8f))
        })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(this@ThemerActivity, 12f), dp(this@ThemerActivity, 12f), dp(this@ThemerActivity, 12f), dp(this@ThemerActivity, 12f))
            background = rect(this@ThemerActivity, surfaceColor(), borderColor(), 1.25f, 14)
        }
        card.addView(TextView(this).apply {
            text = getString(R.string.themer_long_press_a_key_80497)
            setTextColor(textColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
            textSize = 16f
        }, inputParams())
        card.addView(TextView(this).apply {
            text = getString(R.string.themer_hold_or_tap_a_letter_to_choose_up_to_two_apps_hold_that_ke_10a8c)
            setTextColor(textColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity))
            textSize = 12f
            setLineSpacing(0f, 1.08f)
        }, inputParams())

        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { letters ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            letters.forEach { key -> row.addView(shortcutKeyView(key)) }
            card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(this@ThemerActivity, 4f)
            })
        }

        card.addView(TextView(this).apply {
            text = getString(R.string.themer_1_app_2_apps_5e208)
            setTextColor(textColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
            textSize = 10f
            setPadding(dp(this@ThemerActivity, 2f), dp(this@ThemerActivity, 5f), 0, dp(this@ThemerActivity, 5f))
        })

        val selected = selectedKeyboardShortcutKey
        val mappings = KeyboardShortcutManager.mappings(this, selected)
        card.addView(TextView(this).apply {
            text = getString(R.string.themer_hold_05336, selected.uppercaseChar())
            setTextColor(accentColor())
            setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
            textSize = 11f
            setPadding(dp(this@ThemerActivity, 2f), dp(this@ThemerActivity, 6f), 0, dp(this@ThemerActivity, 5f))
        })
        repeat(KeyboardShortcutManager.MAX_PER_KEY) { slot ->
            val mapping = mappings.getOrNull(slot)
            card.addView(TextView(this).apply {
                text = getString(R.string.themer_slot_13d02, slot + 1, mapping?.label ?: getString(R.string.themer_choose_app_8234e))
                styleListItem(this@ThemerActivity, this, mapping != null)
                textSize = 12f
                setOnClickListener { showKeyboardShortcutAppPicker(selected, slot) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(this@ThemerActivity, 4f)
            })
        }
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun shortcutKeyView(key: Char): View {
        val selected = key == selectedKeyboardShortcutKey
        val occupied = KeyboardShortcutManager.mappings(this, key).size
        val size = dp(this, 25f)
        return FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.themer_app_shortcuts_b6cc6, key.uppercaseChar(), occupied)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(this@ThemerActivity, 5f).toFloat()
                setColor(Color.TRANSPARENT)
                val keyOutline = ColorUtils.blendARGB(surfaceColor(), textColor(), 0.45f)
                setStroke(dp(this@ThemerActivity, if (selected) 2f else 1f).coerceAtLeast(1), if (selected) Color.RED else keyOutline)
            }
            addView(TextView(this@ThemerActivity).apply {
                text = key.uppercaseChar().toString()
                gravity = Gravity.CENTER
                setTextColor(textColor())
                setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
                textSize = 10f
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            if (occupied > 0) {
                addView(TextView(this@ThemerActivity).apply {
                    text = "•".repeat(occupied.coerceAtMost(2))
                    gravity = Gravity.END
                    setTextColor(if (selected) Color.RED else accentColor())
                    setTypeface(Tuils.getTypeface(this@ThemerActivity), Typeface.BOLD)
                    textSize = 8f
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(this@ThemerActivity, 1f)
                    rightMargin = dp(this@ThemerActivity, 2f)
                })
            }
            val select = {
                selectedKeyboardShortcutKey = key
                sectionsAdapter?.notifyDataSetChanged()
            }
            setOnClickListener { select() }
            setOnLongClickListener { select(); true }
            layoutParams = LinearLayout.LayoutParams(size, dp(this@ThemerActivity, 38f)).apply {
                leftMargin = dp(this@ThemerActivity, 1.5f)
                rightMargin = dp(this@ThemerActivity, 1.5f)
            }
        }
    }

    private fun showKeyboardShortcutAppPicker(key: Char, slot: Int) {
        val apps = LauncherActivity.instance?.keyboardShortcutApps().orEmpty()
        if (apps.isEmpty()) {
            Toast.makeText(this, getString(R.string.themer_launcher_app_list_is_not_ready_return_home_and_reopen_sett_a05a4), Toast.LENGTH_SHORT).show()
            return
        }
        val occupied = KeyboardShortcutManager.mappings(this, key).getOrNull(slot) != null
        val labels = mutableListOf<String>()
        if (occupied) labels.add(getString(R.string.themer_clear_slot_e4ba8))
        labels.addAll(apps.map { app ->
            val label = app.publicLabel ?: app.componentName?.packageName ?: getString(R.string.themer_app_fc4a6)
            "$label (${app.componentName?.packageName.orEmpty()})"
        })
        TuixtDialog.showSearchableOptions(this, getString(R.string.themer_slot_aff9c, key.uppercaseChar(), slot + 1), labels, getString(R.string.themer_search_apps_ca3ce), ItemAction { which ->
            if (occupied && which == 0) {
                KeyboardShortcutManager.clear(this, key, slot)
            } else {
                val appIndex = which - if (occupied) 1 else 0
                apps.getOrNull(appIndex)?.let { KeyboardShortcutManager.save(this, key, slot, it) }
            }
            sectionsAdapter?.notifyDataSetChanged()
        })
    }

    private fun showTaskerIntegrationDialog() {
        TuixtDialog.showCustom(this, getString(R.string.themer_tasker_integration_50a1b), ContentFactory { _: Dialog? ->
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
                    append(getString(R.string.themer_integration_5c0a9)).append(if (TaskerIntegrationManager.isEnabled(this@ThemerActivity)) getString(R.string.themer_on_387d7) else getString(R.string.themer_off_ad504))
                    append(getString(R.string.themer_tasker_551d9)).append(if (TaskerIntegrationManager.isTaskerInstalled(this@ThemerActivity)) getString(R.string.themer_installed_32554) else getString(R.string.themer_not_installed_b81b3))
                    append(getString(R.string.themer_permission_60b6d)).append(if (TaskerIntegrationManager.hasRunTasksPermission(this@ThemerActivity)) getString(R.string.themer_granted_5ae8e) else getString(R.string.themer_not_granted_fe998))
                    append(getString(R.string.themer_task_status_6002c)).append(if (TaskerIntegrationManager.showTaskStatuses(this@ThemerActivity)) getString(R.string.themer_shown_830ab) else getString(R.string.themer_hidden_1ba34))
                    append(getString(R.string.themer_presets_theme_modules_terminal_output_e4fc7))
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
                text = getString(R.string.themer_enable_integration_f2a7e)
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
                text = getString(R.string.themer_show_task_statuses_fee80)
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

            content.addView(actionButton(getString(R.string.themer_test_integration_b2a66)) {
                val result = TaskerIntegrationManager.execute(
                    this,
                    TaskerIntegrationManager.Request(TaskerIntegrationManager.ACTION_TERMINAL_OUTPUT, text = getString(R.string.themer_tasker_integration_test_ok_dbfe7))
                )
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                updateStatus()
            }, inputParams())
            content.addView(actionButton(getString(R.string.themer_setup_examples_7ef67)) { showTaskerSetupDialog() }, inputParams())
            content
        })
    }

    private fun showTaskerSetupDialog() {
        TuixtDialog.showCustom(this, getString(R.string.themer_tasker_setup_e7f1d), ContentFactory { _: Dialog? ->
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val instructions = TextView(this).apply {
                text = getString(R.string.themer_setup_1_enable_tasker_integration_in_retui_2_grant_the_tas_0fc35)
                setTextColor(textColor())
                setTypeface(Tuils.getTypeface(this@ThemerActivity))
                textSize = 13f
                setPadding(dp(this@ThemerActivity, 12f), dp(this@ThemerActivity, 10f), dp(this@ThemerActivity, 12f), dp(this@ThemerActivity, 10f))
                background = rect(this@ThemerActivity, surfaceColor(), borderColor(), 1.25f)
            }
            content.addView(instructions, inputParams())
            val docs = TextView(this).apply {
                text = getString(R.string.themer_open_full_documentation_927ce)
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
        labels.add(getString(R.string.themer_auto_detect_85cd1))
        for (choice in choices) {
            labels.add(choice.label + " (" + choice.packageName + ")")
        }

        TuixtDialog.showOptions(this, getString(R.string.themer_preferred_music_app_1e994), labels, ItemAction { which: Int ->
            if (which == 0) {
                set(this, Behavior.preferred_music_app, Tuils.EMPTYSTRING)
                Toast.makeText(
                    this,
                    getString(R.string.themer_preferred_music_app_reset_to_automatic_detection_9a1d3),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val choice = choices.get(which - 1)
                set(this, Behavior.preferred_music_app, choice.packageName)
                Toast.makeText(
                    this,
                    getString(R.string.themer_preferred_music_app_set_to_73cf9, choice.label),
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
        } else if (requestCode == UI_PACKAGE_ZIP_IMPORT_REQUEST) {
            handleUiPackageZipImportResult(resultCode, data)
        }
    }

    private fun handleUiPackageZipImportResult(resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            Toast.makeText(this, getString(R.string.themer_ui_package_zip_import_cancelled_1451c), Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = getDisplayName(uri) ?: uri.lastPathSegment.orEmpty()
        if (!FrameManager.isUiPackageZipName(fileName)) {
            Toast.makeText(this, getString(R.string.themer_choose_a_file_ending_in_retui_ui_zip_0571c), Toast.LENGTH_LONG).show()
            return
        }
        try {
            val pack = contentResolver.openInputStream(uri).use { input ->
                frameSession().importUiPackageZip(
                    requireNotNull(input) { getString(R.string.themer_unable_to_read_the_selected_ui_package_zip_ffc9a) }
                )
            }
            persistFrameSession(getString(R.string.themer_imported_tap_apply_when_ready_6eb44, pack.name))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.themer_ui_package_zip_import_failed_f51b2, e.displayMessage(this@ThemerActivity)), Toast.LENGTH_LONG).show()
        }
    }

    private fun handleFrameImportResult(resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            pendingFrameTarget = null
            Toast.makeText(this, getString(R.string.themer_frame_import_cancelled_c8e8e), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val target = pendingFrameTarget
            val asset = contentResolver.openInputStream(uri).use { input ->
                frameSession().importFrame(
                    target,
                    getDisplayName(uri) ?: uri.lastPathSegment,
                    requireNotNull(input) { getString(R.string.themer_unable_to_read_the_selected_frame_c6fec) }
                )
            }
            pendingFrameTarget = null
            reloadForFrame(getString(R.string.themer_frame_imported_66bc7, asset.name))
        } catch (e: Exception) {
            pendingFrameTarget = null
            Toast.makeText(this, getString(R.string.themer_frame_import_failed_b43bd, e.displayMessage(this@ThemerActivity)), Toast.LENGTH_LONG).show()
        }
    }

    private fun handleFontImportResult(resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            Toast.makeText(this, getString(R.string.themer_font_import_cancelled_ae79a), Toast.LENGTH_SHORT).show()
            return
        }

        val uri = data.getData()
        var sourceName = getDisplayName(uri!!)
        if (sourceName == null || sourceName.trim { it <= ' ' }.length == 0) {
            sourceName = uri.getLastPathSegment()
        }

        val fileName = sanitizeFontFileName(sourceName)
        if (!isFontFileName(fileName)) {
            Toast.makeText(this, getString(R.string.themer_choose_a_ttf_or_otf_font_file_7d188), Toast.LENGTH_LONG).show()
            return
        }

        val dest = uniqueFontFile(this.fontsDir, fileName)
        try {
            getContentResolver().openInputStream(uri).use { `in` ->
                FileOutputStream(dest).use { out ->
                    checkNotNull(`in`) { getString(R.string.themer_unable_to_read_selected_font_857be) }
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
            Toast.makeText(this, getString(R.string.themer_font_import_failed_2185f, e.displayMessage(this@ThemerActivity)), Toast.LENGTH_LONG).show()
            return
        }

        if (!dest.exists() || dest.length() == 0L) {
            dest.delete()
            Toast.makeText(this, getString(R.string.themer_font_import_failed_empty_file_a88dd), Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, getString(R.string.themer_font_imported_84bdf), Toast.LENGTH_SHORT).show()
        applyFont(dest)
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
            Toast.makeText(this, getString(R.string.themer_backup_cancelled_8ae7e), Toast.LENGTH_SHORT).show()
            return
        }

        val uri = data.getData() ?: return
        if (!backupExportPending) {
            deleteCreatedDocument(uri)
            Toast.makeText(this, getString(R.string.themer_backup_cancelled_before_export_67b17), Toast.LENGTH_LONG).show()
            return
        }
        exportBackup(uri, pendingBackupPassword)
    }

    private fun showBackupPasswordDialog() {
        val content = LinearLayout(this)
        content.setOrientation(LinearLayout.VERTICAL)

        val password = passwordInput(getString(R.string.themer_password_8be3c))
        val confirm = passwordInput(getString(R.string.themer_confirm_password_4a7c5))
        content.addView(password, inputParams())
        content.addView(confirm, inputParams())

        TuixtDialog.showContent(
            this,
            getString(R.string.themer_backup_password_f6bc2),
            content,
            getString(R.string.themer_export_f3e4f),
            getString(R.string.themer_cancel_77dfd),
            ConfirmAction {
                val first = password.getText().toString()
                val second = confirm.getText().toString()
                if (first.length == 0) {
                    Toast.makeText(this, getString(R.string.themer_password_is_required_99234), Toast.LENGTH_SHORT).show()
                    recyclerView!!.postDelayed(Runnable { this.showBackupPasswordDialog() }, 250)
                    return@ConfirmAction
                }
                if (first != second) {
                    Toast.makeText(this, getString(R.string.themer_passwords_do_not_match_f7c3c), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.themer_backup_exported_and_verified_52e08), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            deleteCreatedDocument(uri)
            Toast.makeText(
                this,
                if (e.displayMessage(this@ThemerActivity) == null) getString(R.string.themer_backup_failed_7fd26) else e.displayMessage(this@ThemerActivity),
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
            pendingShareableBehaviorLabels = null
            Toast.makeText(this, getString(R.string.themer_configuration_export_cancelled_92e07), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            BackupManager.exportShareableConfiguration(
                this,
                data.getData() ?: return,
                pendingShareablePresetName,
                pendingShareableBehaviorLabels ?: PresetManager.defaultShareableBehaviorLabels()
            )
            Toast.makeText(this, getString(R.string.themer_shareable_configuration_exported_and_verified_2b7f9), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            data.getData()?.let { deleteCreatedDocument(it) }
            Toast.makeText(
                this,
                if (e.displayMessage(this@ThemerActivity) == null) getString(R.string.themer_configuration_export_failed_36f82) else e.displayMessage(this@ThemerActivity),
                Toast.LENGTH_LONG
            ).show()
        } finally {
            pendingShareablePresetName = null
            pendingShareableBehaviorLabels = null
        }
    }

    private fun handleRestoreResult(resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            Toast.makeText(this, getString(R.string.themer_restore_cancelled_56adf), Toast.LENGTH_SHORT).show()
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
                if (e.displayMessage(this@ThemerActivity) == null) getString(R.string.themer_restore_failed_0f5f0) else e.displayMessage(this@ThemerActivity),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showRestorePasswordDialog() {
        val content = LinearLayout(this)
        content.setOrientation(LinearLayout.VERTICAL)

        val password = passwordInput(getString(R.string.themer_backup_password_d96a6))
        content.addView(password, inputParams())

        TuixtDialog.showContent(
            this,
            getString(R.string.themer_restore_password_3ba9b),
            content,
            getString(R.string.themer_restore_3cbe6),
            getString(R.string.themer_cancel_77dfd),
            ConfirmAction {
                val value = password.getText().toString()
                if (value.length == 0) {
                    Toast.makeText(this, getString(R.string.themer_password_is_required_99234), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, getString(R.string.themer_preset_imported_dc305, importedPreset), Toast.LENGTH_LONG).show()
                return
            }
            Toast.makeText(this, getString(R.string.themer_backup_restored_reloading_49aae), Toast.LENGTH_SHORT).show()
            recyclerView!!.postDelayed(Runnable {
                intent.putExtra(EXTRA_SECTION, SECTION_SYSTEM)
                recreate()
                LauncherActivity.preview(this)
            }, 500)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                if (e.displayMessage(this@ThemerActivity) == null) getString(R.string.themer_restore_failed_0f5f0) else e.displayMessage(this@ThemerActivity),
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
    private class KeyboardShortcutPanelViewHolder(val root: LinearLayout) : RecyclerView.ViewHolder(root)

    private class AppChoice(val label: String, val packageName: String?)
    private data class TypographySetting(
        val label: Int,
        val sample: Int,
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
        const val SECTION_KEYBOARD_SHORTCUTS: String = "keyboard_shortcuts"
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
        private const val VIEW_TYPE_KEYBOARD_SHORTCUT_PANEL = 4
        private const val FONT_SCALE_PANEL = "__font_scale_panel__"
        private const val FRAME_PANEL = "__frame_panel__"
        private const val KEYBOARD_SHORTCUT_PANEL = "__keyboard_shortcut_panel__"
        private const val MIN_TYPOGRAPHY_SP = 8
        private const val MAX_TYPOGRAPHY_SP = 64
        private val FONT_PREVIEW_SIZES = floatArrayOf(10f, 11f, 12f, 14f, 15f, 18f, 64f)
        private val FONT_SECTIONS = setOf(SECTION_FONTS, SECTION_TYPOGRAPHY)
        private val TYPOGRAPHY_SETTINGS = listOf(
            TypographySetting(R.string.typography_ac10e8e95b, R.string.typography_bacd1228fe, Ui.input_output_size),
            TypographySetting(R.string.typography_861edba005, R.string.typography_4d1bb46cf4, Suggestions.suggestions_size),
            TypographySetting(R.string.typography_84157ec9ff, R.string.typography_e733daecea, Ui.module_header_text_size),
            TypographySetting(R.string.typography_e43ff230eb, R.string.typography_d7053f25bb, Ui.module_body_text_size),
            TypographySetting(R.string.typography_38d345e18f, R.string.typography_cede01dc27, Ui.output_header_text_size),
            TypographySetting(R.string.typography_6208d62be1, R.string.typography_6073f5768b, Ui.ram_size, false),
            TypographySetting(R.string.typography_96798b33dd, R.string.typography_d859b29e9b, Ui.battery_size, false),
            TypographySetting(R.string.typography_8d2eaed52b, R.string.typography_d8b4f70ace, Ui.device_size, false),
            TypographySetting(R.string.typography_5902d64dc5, R.string.typography_50a019ba96, Ui.time_size, false),
            TypographySetting(R.string.typography_4f02e9a678, R.string.typography_3ce1e0a76b, Ui.storage_size, false),
            TypographySetting(R.string.typography_c5308e6694, R.string.typography_9d0b571792, Ui.network_size, false),
            TypographySetting(R.string.typography_a7c8ee4433, R.string.typography_5dc9e5dbdd, Ui.notes_size, false),
            TypographySetting(R.string.typography_5b3fc4e80d, R.string.typography_981752f693, Ui.weather_size, false),
            TypographySetting(R.string.typography_9d18578bbb, R.string.typography_8cff46ef5b, Ui.unlock_size, false),
            TypographySetting(R.string.typography_1e7f242177, R.string.typography_4e59487d0e, Ui.ascii_size, false)
        )
        private const val BACKUP_EXPORT_REQUEST = 201
        private const val BACKUP_RESTORE_REQUEST = 202
        private const val SHAREABLE_CONFIG_EXPORT_REQUEST = 203
        private const val FONT_IMPORT_REQUEST = 204
        private const val TASKER_PERMISSION_REQUEST = 205
        private const val FRAME_IMPORT_REQUEST = 206
        private const val UI_PACKAGE_ZIP_IMPORT_REQUEST = 207
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
