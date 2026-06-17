package ohi.andre.consolelauncher.commands.tuixt

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.PinItemRequest
import android.content.pm.ShortcutInfo
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout.addFoldAwareHost
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.overlayColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleHeader
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.stylePanel
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.textColor
import ohi.andre.consolelauncher.managers.AliasManager
import ohi.andre.consolelauncher.managers.PinnedShortcutManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.requestNoTitleIfFullscreen
import ohi.andre.consolelauncher.tuils.Tuils
import android.content.Context
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.tuils.LauncherSystemUi

@RequiresApi(Build.VERSION_CODES.O)
class PinShortcutConfirmActivity : Activity() {
    private var request: PinItemRequest? = null
    private var shortcutInfo: ShortcutInfo? = null
    private var aliasInput: EditText? = null
    private var aliasToggle: TextView? = null
    private var createAlias = true

    override fun onCreate(savedInstanceState: Bundle?) {
        requestNoTitleIfFullscreen(this)
        super.onCreate(savedInstanceState)
        applyFullscreen(this)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !loadRequest()) {
            finishWithToast("Shortcut pin request unavailable.")
            return
        }

        setContentView(buildContent())
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

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        finish()
    }

    private fun loadRequest(): Boolean {
        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps?
        if (launcherApps == null) return false

        request = launcherApps.getPinItemRequest(getIntent())
        if (request == null || !request!!.isValid()) return false
        if (request!!.getRequestType() != PinItemRequest.REQUEST_TYPE_SHORTCUT) return false

        shortcutInfo = request!!.getShortcutInfo()
        return shortcutInfo != null
    }

    private fun buildContent(): View {
        val screen = FrameLayout(this)
        screen.setBackgroundColor(overlayColor())
        screen.setFitsSystemWindows(true)
        val contentHost = addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)

        val panelShell = FrameLayout(this)
        panelShell.setClipChildren(false)
        panelShell.setClipToPadding(false)

        val root = LinearLayout(this)
        root.setOrientation(LinearLayout.VERTICAL)
        root.setPadding(dp(this, 14f), dp(this, 46f), dp(this, 14f), dp(this, 14f))
        stylePanel(this, root)

        val panelParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        panelParams.gravity = Gravity.CENTER
        panelParams.setMargins(dp(this, 28f), dp(this, 28f), dp(this, 28f), dp(this, 28f))
        contentHost.addView(panelShell, panelParams)

        val rootParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        rootParams.topMargin = dp(this, 11f)
        panelShell.addView(root, rootParams)

        val header = TextView(this)
        header.setText("Pin Shortcut")
        styleHeader(this, header)
        val headerParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        headerParams.gravity = Gravity.TOP or Gravity.START
        headerParams.leftMargin = dp(this, 38f)
        panelShell.addView(header, headerParams)

        val title = terminalText(labelFor(shortcutInfo!!))
        title.setTextSize(16f)
        root.addView(title, blockParams())

        val source = terminalText("From " + shortcutInfo!!.getPackage())
        source.setTextColor(ColorUtils.setAlphaComponent(textColor(), 190))
        root.addView(source, blockParams())

        aliasInput = EditText(this)
        TuixtTheme.styleInput(this, aliasInput!!)
        aliasInput!!.setSingleLine(true)
        aliasInput!!.setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        aliasInput!!.setText(PinnedShortcutManager.defaultHandle(shortcutInfo!!.getShortLabel()))
        aliasInput!!.setSelectAllOnFocus(true)
        root.addView(aliasInput, blockParams())

        val toggleRow = LinearLayout(this)
        toggleRow.setOrientation(LinearLayout.HORIZONTAL)
        toggleRow.setGravity(Gravity.CENTER_VERTICAL)

        val label = terminalText("Create alias")
        toggleRow.addView(label)

        val spacer = View(this)
        toggleRow.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))

        aliasToggle = TextView(this)
        updateAliasToggle()
        aliasToggle!!.setOnClickListener(View.OnClickListener { v: View? ->
            createAlias = !createAlias
            updateAliasToggle()
        })
        toggleRow.addView(aliasToggle)
        root.addView(toggleRow, blockParams())

        val bottomBar = LinearLayout(this)
        bottomBar.setOrientation(LinearLayout.HORIZONTAL)
        bottomBar.setGravity(Gravity.CENTER_VERTICAL)
        bottomBar.setPadding(0, dp(this, 8f), 0, 0)

        val cancel = button("CANCEL", false)
        cancel.setOnClickListener(View.OnClickListener { v: View? -> finish() })
        bottomBar.addView(cancel)

        val bottomSpacer = View(this)
        bottomBar.addView(bottomSpacer, LinearLayout.LayoutParams(0, 1, 1f))

        val pin = button("PIN", true)
        pin.setOnClickListener(View.OnClickListener { v: View? -> acceptAndClose() })
        bottomBar.addView(pin)
        root.addView(bottomBar)

        return screen
    }

    private fun acceptAndClose() {
        if (request == null || !request!!.isValid()) {
            finishWithToast("Shortcut pin request expired.")
            return
        }

        var handle = PinnedShortcutManager.normalizeHandle(aliasInput!!.getText().toString())
        if (handle.length == 0) {
            handle = PinnedShortcutManager.defaultHandle(shortcutInfo!!.getShortLabel())
        }

        var accepted: Boolean
        try {
            accepted = request!!.accept()
        } catch (e: Exception) {
            Tuils.log(e)
            accepted = false
        }

        if (!accepted) {
            finishWithToast("Shortcut pin request was cancelled.")
            return
        }

        try {
            PinnedShortcutManager.save(this, handle, shortcutInfo, labelFor(shortcutInfo!!))
            if (createAlias) {
                addAlias(handle, "shortcut -use @" + handle)
            }
            Toast.makeText(this, "Pinned @" + handle, Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Tuils.log(e)
            finishWithToast("Pinned, but mapping save failed.")
        }
    }

    private fun addAlias(name: String?, value: String?) {
        val intent = Intent(AliasManager.ACTION_ADD)
        intent.putExtra(AliasManager.NAME, name)
        intent.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, value)
        val delivered =
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent)
        if (delivered) return

        val manager = AliasManager(getApplicationContext())
        try {
            manager.add(getApplicationContext(), name ?: return, value ?: "")
        } finally {
            manager.dispose()
        }
    }

    private fun updateAliasToggle() {
        TuixtTheme.styleToggle(this, aliasToggle!!, createAlias)
    }

    private fun terminalText(text: String?): TextView {
        val view = TextView(this)
        view.setText(text)
        view.setTextColor(textColor())
        view.setTypeface(Tuils.getTypeface(this), Typeface.BOLD)
        view.setTextSize(13f)
        view.setGravity(Gravity.CENTER_VERTICAL)
        return view
    }

    private fun button(label: String?, primary: Boolean): TextView {
        val view = TextView(this)
        view.setText(label)
        styleButton(this, view, primary)
        return view
    }

    private fun blockParams(): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, dp(this, 10f))
        return params
    }

    private fun labelFor(info: ShortcutInfo): String {
        val shortLabel = info.getShortLabel()
        if (shortLabel != null && shortLabel.length > 0) return shortLabel.toString()
        val longLabel = info.getLongLabel()
        if (longLabel != null && longLabel.length > 0) return longLabel.toString()
        return info.getId()
    }

    private fun finishWithToast(message: String?) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
