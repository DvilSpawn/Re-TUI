package ohi.andre.consolelauncher.commands.tuixt

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.PorterDuff
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ConfirmAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ContentFactory
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.InputAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ItemAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout.addFoldAwareHost
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.accentColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.borderColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.overlayColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.rect
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.surfaceColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleInput
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleListItem
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.stylePanel
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
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.requestNoTitleIfFullscreen
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
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.LauncherSystemUi

class ThemerActivity : AppCompatActivity() {
    private var recyclerView: RecyclerView? = null
    private var header: TextView? = null
    private var supportFooter: LinearLayout? = null
    private var sectionsAdapter: RecyclerView.Adapter<ViewHolder?>? = null
    private val sectionItems: MutableList<String> = ArrayList<String>()
    private var section: String? = null
    private var pendingBackupUri: Uri? = null
    private var pendingRestoreUri: Uri? = null
    private var pendingShareablePresetName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        requestNoTitleIfFullscreen(this)
        super.onCreate(savedInstanceState)
        applyFullscreen(this)

        section = if (getIntent() != null) getIntent().getStringExtra(EXTRA_SECTION) else null
        if (section == null || section!!.length == 0) {
            section = SECTION_HOME
        }

        val screen = FrameLayout(this)
        screen.setBackgroundColor(overlayColor())
        screen.setFitsSystemWindows(true)
        val contentHost = addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)

        val root = LinearLayout(this)
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

        sectionsAdapter = object : RecyclerView.Adapter<ViewHolder?>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val tv = TextView(parent.getContext())
                tv.setLayoutParams(
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                return ViewHolder(tv)
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                val fileName = sectionItems.get(position)
                val itemView = holder.itemView as TextView
                itemView.setText(fileName.uppercase(Locale.getDefault()))
                styleListItem(this@ThemerActivity, itemView, false)
                val params = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, dp(this@ThemerActivity, 8f))
                itemView.setLayoutParams(params)
                holder.itemView.setOnClickListener(View.OnClickListener { v: View? ->
                    if (fileName == "Appearance") {
                        openSection(SECTION_APPEARANCE)
                    } else if (fileName == "Behavior") {
                        openSection(SECTION_BEHAVIOR)
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
                    } else if (fileName.startsWith("Preferred Music App")) {
                        showPreferredMusicAppPicker()
                    } else if (fileName == "Fonts") {
                        showFontsDialog()
                    } else if (fileName == "Presets") {
                        showPresetsDialog()
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
                            startActivity(intent)
                        }
                    } else if (fileName == "Backup") {
                        launchBackupPicker()
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

    private fun showPresetsDialog() {
        val options = mutableListOf<String?>("Save Current as Preset", "Apply Preset")
        TuixtDialog.showOptions(this@ThemerActivity, "Presets", options, ItemAction { which: Int ->
            if (which == 0) {
                TuixtDialog.showInput(
                    this@ThemerActivity,
                    "Save Preset",
                    "Preset name",
                    "Save",
                    "Cancel",
                    InputAction { value: String? ->
                        val name = value!!.trim { it <= ' ' }
                        if (name.length > 0) {
                            savePreset(name)
                        }
                    })
            } else {
                val presetNames = PresetManager.listAllPresetNames()
                if (presetNames.isEmpty()) {
                    Toast.makeText(this@ThemerActivity, "No presets found.", Toast.LENGTH_SHORT)
                        .show()
                    return@ItemAction
                }

                TuixtDialog.showOptions(
                    this@ThemerActivity,
                    "Select Preset",
                    presetNames,
                    ItemAction { w: Int -> applyPreset(presetNames.get(w)) })
            }
        })
    }

    private fun savePreset(name: String?) {
        try {
            PresetManager.save(name ?: return)
            Toast.makeText(this@ThemerActivity, "Preset saved! Reloading...", Toast.LENGTH_SHORT)
                .show()
            recyclerView!!.postDelayed(Runnable {
                if (LauncherActivity.instance != null) {
                    LauncherActivity.instance!!.reload()
                }
                finish()
            }, 500)
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
                if (LauncherActivity.instance != null) {
                    LauncherActivity.instance!!.reload()
                }
                finish()
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
                "Open Wallpaper Picker",
                "Open Live Wallpaper Picker"
            )
        } else if (SECTION_BEHAVIOR == section) {
            return mutableListOf(
                "behavior.xml",
                "apps.xml",
                "notifications.xml",
                "cmd.xml"
            )
        } else if (SECTION_PERSONALIZATION == section) {
            return mutableListOf(
                dystopiaRowLabel(),
                "alias.txt",
                "Toolbar Buttons",
                "ASCII Settings",
                "rss.xml"
            )
        } else if (SECTION_INTEGRATIONS == section) {
            return mutableListOf("Preferred Music App: " + this.preferredMusicAppSummary)
        } else if (SECTION_SYSTEM == section) {
            return mutableListOf(
                "Backup",
                "Create Shareable Configuration",
                "Restore",
                "Rate the App",
                "Send Feedback",
                "View Crash Log"
            )
        }

        return mutableListOf(
            "Appearance",
            "Behavior",
            "Personalization",
            "Integrations",
            "System & Support"
        )
    }

    private fun openSection(targetSection: String?) {
        section = targetSection
        header!!.setText(getHeaderText(section))
        sectionItems.clear()
        sectionItems.addAll(getItemsForSection(section))
        sectionsAdapter!!.notifyDataSetChanged()
        recyclerView!!.scrollToPosition(0)
        updateSupportFooter()
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
            fingerprint.setBackground(rect(this, surfaceColor(), borderColor(), 1.25f))
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
        button.setBackground(rect(this, surfaceColor(), borderColor(), 1.25f))
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

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (SECTION_HOME != section) {
            openSection(SECTION_HOME)
            return
        }
        super.onBackPressed()
    }

    private fun openConfigFile(fileName: String) {
        val intent = Intent(this@ThemerActivity, TuixtActivity::class.java)
        intent.putExtra(TuixtActivity.PATH, File(Tuils.getFolder(), fileName).getAbsolutePath())
        startActivityForResult(intent, LauncherActivity.TUIXT_REQUEST)
    }

    private fun openAsciiSettings() {
        val intent = Intent(this@ThemerActivity, TuixtActivity::class.java)
        intent.putExtra(TuixtActivity.MODE, TuixtActivity.MODE_ASCII_SETTINGS)
        startActivityForResult(intent, LauncherActivity.TUIXT_REQUEST)
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
        if (LauncherActivity.instance != null) {
            LauncherActivity.instance!!.reload()
        }
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

    private fun showFontsDialog() {
        val fontsDir = this.fontsDir
        val fonts = listFontFiles(fontsDir)

        TuixtDialog.showCustom(this, "Select Font", ContentFactory { dialog: Dialog? ->
            val content = LinearLayout(this)
            content.setOrientation(LinearLayout.VERTICAL)

            addFontActionRow(
                content,
                dialog!!,
                "Default (System Font)",
                Runnable { this.applySystemFont() })
            addFontActionRow(
                content,
                dialog,
                "Import Font...",
                Runnable { this.launchFontImportPicker() })
            for (font in fonts) {
                addFontFileRow(content, dialog, font)
            }
            content
        })
    }

    private fun addFontActionRow(
        content: LinearLayout,
        dialog: Dialog,
        label: String,
        action: Runnable
    ) {
        val row = TextView(this)
        row.setText(label.uppercase())
        styleListItem(this, row, false)
        row.setOnClickListener(View.OnClickListener { v: View? ->
            dialog.dismiss()
            action.run()
        })

        val rowParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        rowParams.bottomMargin = dp(this, 8f)
        content.addView(row, rowParams)
    }

    private fun addFontFileRow(content: LinearLayout, dialog: Dialog, font: File) {
        val row = LinearLayout(this)
        row.setOrientation(LinearLayout.HORIZONTAL)
        row.setGravity(Gravity.CENTER_VERTICAL)

        val label = TextView(this)
        label.setText(font.getName().uppercase())
        styleListItem(this, label, false)
        label.setOnClickListener(View.OnClickListener { v: View? ->
            dialog.dismiss()
            applyFont(font)
        })

        val delete = TextView(this)
        delete.setText("X")
        styleButton(this, delete, false)
        delete.setMinWidth(dp(this, 52f))
        delete.setMinHeight(dp(this, 48f))
        delete.setOnClickListener(View.OnClickListener { v: View? ->
            confirmDeleteFont(
                dialog,
                font
            )
        })

        row.addView(
            label, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val spacer = View(this)
        row.addView(spacer, LinearLayout.LayoutParams(dp(this, 8f), 1))

        row.addView(
            delete, LinearLayout.LayoutParams(
                dp(this, 58f),
                dp(this, 48f)
            )
        )

        val rowParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        rowParams.bottomMargin = dp(this, 8f)
        content.addView(row, rowParams)
    }

    private fun confirmDeleteFont(fontsDialog: Dialog, font: File) {
        TuixtDialog.showConfirm(
            this,
            "Delete Font",
            "Delete " + font.getName() + "?",
            "Delete",
            "Cancel",
            ConfirmAction {
                fontsDialog.dismiss()
                deleteFont(font)
            })
    }

    private fun deleteFont(font: File) {
        val deletedName = font.getName()
        val deleted = !font.exists() || font.delete()

        val rootCopy = File(Tuils.getFolder(), deletedName)
        if (rootCopy.exists() && rootCopy.isFile()) {
            Tuils.insertOld(rootCopy)
        }

        if (!deleted) {
            Toast.makeText(this, "Could not delete font.", Toast.LENGTH_LONG).show()
            return
        }

        val selected = get(Ui.font_file)
        if (selected != null && selected == deletedName) {
            set(this, Ui.system_font, "true")
            set(this, Ui.font_file, "")
            finalizeFontChange("Font deleted. System font applied!")
            return
        }

        Toast.makeText(this, "Font deleted.", Toast.LENGTH_SHORT).show()
        showFontsDialog()
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

    private fun applySystemFont() {
        set(this, Ui.system_font, "true")
        set(this, Ui.font_file, "")

        sweepCurrentFonts()
        finalizeFontChange("System font applied!")
    }

    private fun applyFont(source: File) {
        set(this, Ui.system_font, "false")
        set(this, Ui.font_file, source.getName())

        sweepCurrentFonts()

        val tuiFolder = Tuils.getFolder()
        val dest = File(tuiFolder, source.getName())
        try {
            Log.e(
                "TUI-THEMER",
                "Copying font from " + source.getAbsolutePath() + " to " + dest.getAbsolutePath()
            )
            Tuils.copy(source, dest)

            if (dest.exists() && dest.length() > 0) {
                Log.e("TUI-THEMER", "Copy successful! Size: " + dest.length())
            } else {
                Log.e("TUI-THEMER", "Copy failed or file is empty!")
            }

            finalizeFontChange("Font applied!")
        } catch (e: Exception) {
            Toast.makeText(this, "Error applying font: " + e.message, Toast.LENGTH_LONG).show()
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

    private fun finalizeFontChange(message: String?) {
        Tuils.cancelFont()

        Toast.makeText(this, message + " Reloading...", Toast.LENGTH_SHORT).show()

        recyclerView!!.postDelayed(Runnable {
            if (LauncherActivity.instance != null) {
                LauncherActivity.instance!!.reload()
            }
            finish()
        }, 500)
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
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Live wallpaper picker is unavailable on this device.",
                Toast.LENGTH_SHORT
            ).show()
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
            Toast.makeText(this, "Backup picker is unavailable on this device.", Toast.LENGTH_SHORT)
                .show()
        }
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
                if (label != null && label.length > 0) {
                    return label.toString() + " (" + packageName + ")"
                }
            } catch (ignored: Exception) {
            }

            return packageName
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
                if (info.activityInfo == null || info.activityInfo.packageName == null) {
                    continue
                }

                val packageName = info.activityInfo.packageName
                if (seenPackages.contains(packageName)) {
                    continue
                }

                val loadedLabel = info.loadLabel(packageManager)
                val label =
                    if (loadedLabel != null) loadedLabel.toString() else packageName
                choices.add(AppChoice(label, packageName))
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
            if (LauncherActivity.instance != null) {
                LauncherActivity.instance!!.reload()
            }
            finish()
        } else if (requestCode == BACKUP_EXPORT_REQUEST) {
            handleBackupResult(resultCode, data)
        } else if (requestCode == SHAREABLE_CONFIG_EXPORT_REQUEST) {
            handleShareableConfigurationResult(resultCode, data)
        } else if (requestCode == BACKUP_RESTORE_REQUEST) {
            handleRestoreResult(resultCode, data)
        } else if (requestCode == FONT_IMPORT_REQUEST) {
            handleFontImportResult(resultCode, data)
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
            Toast.makeText(this, "Backup cancelled.", Toast.LENGTH_SHORT).show()
            return
        }

        pendingBackupUri = data.getData()
        TuixtDialog.showOptions(
            this,
            "Backup Protection",
            mutableListOf<String?>("Encrypt with Password", "Export Without Password"),
            ItemAction { which: Int ->
                if (which == 0) {
                    showBackupPasswordDialog()
                } else {
                    exportBackup(null)
                }
            })
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
                exportBackup(first)
            })
    }

    private fun exportBackup(password: String?) {
        try {
            BackupManager.exportBackup(this, pendingBackupUri ?: return, password)
            Toast.makeText(this, "Backup exported.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                if (e.message == null) "Backup failed." else e.message,
                Toast.LENGTH_LONG
            ).show()
        } finally {
            pendingBackupUri = null
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
            Toast.makeText(this, "Shareable configuration exported.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
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
            BackupManager.importBackup(this, pendingRestoreUri ?: return, password)
            Toast.makeText(this, "Backup restored. Reloading...", Toast.LENGTH_SHORT).show()
            pendingRestoreUri = null
            recyclerView!!.postDelayed(Runnable {
                if (LauncherActivity.instance != null) {
                    LauncherActivity.instance!!.reload()
                }
                finish()
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

    private class AppChoice(val label: String, val packageName: String?)
    companion object {
        const val EXTRA_SECTION: String = "section"
        const val SECTION_HOME: String = "home"
        const val SECTION_APPEARANCE: String = "appearance"
        const val SECTION_BEHAVIOR: String = "behavior"
        const val SECTION_PERSONALIZATION: String = "personalization"
        const val SECTION_INTEGRATIONS: String = "integrations"
        const val SECTION_SYSTEM: String = "system"
        private const val BACKUP_EXPORT_REQUEST = 201
        private const val BACKUP_RESTORE_REQUEST = 202
        private const val SHAREABLE_CONFIG_EXPORT_REQUEST = 203
        private const val FONT_IMPORT_REQUEST = 204
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
    }
}
