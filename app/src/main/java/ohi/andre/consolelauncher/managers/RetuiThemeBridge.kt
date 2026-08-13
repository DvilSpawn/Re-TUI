package ohi.andre.consolelauncher.managers

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import com.dvil.retui.contract.RetuiVisualContract
import java.io.File
import java.io.FileOutputStream
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.GenericFileProvider
import ohi.andre.consolelauncher.tuils.Tuils

object RetuiThemeBridge {
    private const val KEYBOARD_PRIVATE_OPTIONS_PREFIX = "com.dvil.retui.keyboard"
    private const val KEYBOARD_APPLY_CONTEXT_ACTION = "com.dvil.retui.keyboard.APPLY_CONTEXT"
    private const val KEYBOARD_PACKAGE = "com.dvil.retui.keyboard"

    fun putLauncherThemeExtras(intent: Intent, context: Context) {
        RetuiVisualContract.putInto(intent, buildLauncherThemeBundle(context))
    }

    fun putLauncherThemeExtras(
        intent: Intent,
        context: Context,
        frameTarget: FrameTarget,
        recipientPackage: String
    ) {
        val bundle = buildLauncherThemeBundle(context)
        addFrame(context, bundle, frameTarget, recipientPackage, "retui-${frameTarget.id}-frame")
        RetuiVisualContract.putInto(intent, bundle)
    }

    fun applyToKeyboardInput(
        input: EditText?,
        contextLabel: String? = null,
        mode: String? = null
    ) {
        if (input == null) {
            return
        }
        input.privateImeOptions = buildKeyboardPrivateOptions(contextLabel, mode)
    }

    fun sendToKeyboard(
        context: Context?,
        input: EditText?,
        contextLabel: String? = null,
        mode: String? = null
    ) {
        if (context == null || input == null) {
            return
        }
        val bundle = buildLauncherThemeBundle(context, contextLabel, mode)
        addFrame(context, bundle, FrameTarget.KEYBOARD, KEYBOARD_PACKAGE, "retui-keyboard-frame")
        applyToKeyboardInput(input, contextLabel, mode)
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.sendAppPrivateCommand(input, KEYBOARD_APPLY_CONTEXT_ACTION, bundle)
    }

    private fun addFrame(
        context: Context,
        bundle: Bundle,
        target: FrameTarget,
        recipientPackage: String,
        cacheFolder: String
    ) {
        bundle.putBoolean(RetuiVisualContract.FRAME_AVAILABLE, false)
        val source = FrameManager.sharedSource(context, target) ?: return
        try {
            val directory = File(context.cacheDir, cacheFolder)
            check(directory.exists() || directory.mkdirs()) { "Unable to create frame share folder." }
            val image = File(directory, "active.png")
            val temporary = File(directory, "active.png.tmp")
            temporary.delete()
            FileOutputStream(temporary).use { it.write(source.png) }
            check(!image.exists() || image.delete()) { "Unable to replace shared frame image." }
            check(temporary.renameTo(image)) { "Unable to publish shared frame image." }

            val uri = FileProvider.getUriForFile(context, GenericFileProvider.PROVIDER_NAME, image)
            context.grantUriPermission(recipientPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            bundle.putBoolean(RetuiVisualContract.FRAME_AVAILABLE, true)
            bundle.putString(RetuiVisualContract.FRAME_ASSET_ID, source.assetId)
            bundle.putString(RetuiVisualContract.FRAME_IMAGE_URI, uri.toString())
            bundle.putInt(RetuiVisualContract.FRAME_SLICE_LEFT_PX, source.leftPx)
            bundle.putInt(RetuiVisualContract.FRAME_SLICE_TOP_PX, source.topPx)
            bundle.putInt(RetuiVisualContract.FRAME_SLICE_RIGHT_PX, source.rightPx)
            bundle.putInt(RetuiVisualContract.FRAME_SLICE_BOTTOM_PX, source.bottomPx)
            bundle.putFloat(RetuiVisualContract.FRAME_BORDER_LEFT_DP, source.leftDp)
            bundle.putFloat(RetuiVisualContract.FRAME_BORDER_TOP_DP, source.topDp)
            bundle.putFloat(RetuiVisualContract.FRAME_BORDER_RIGHT_DP, source.rightDp)
            bundle.putFloat(RetuiVisualContract.FRAME_BORDER_BOTTOM_DP, source.bottomDp)
            bundle.putString(RetuiVisualContract.FRAME_MODE_TOP, source.topMode)
            bundle.putString(RetuiVisualContract.FRAME_MODE_RIGHT, source.rightMode)
            bundle.putString(RetuiVisualContract.FRAME_MODE_BOTTOM, source.bottomMode)
            bundle.putString(RetuiVisualContract.FRAME_MODE_LEFT, source.leftMode)
            bundle.putString(RetuiVisualContract.FRAME_MODE_CENTER, source.centerMode)
            bundle.putString(RetuiVisualContract.FRAME_FILTERING, source.filtering)
        } catch (error: Exception) {
            Log.e("TUI-FRAME", "Unable to share ${target.id} frame with $recipientPackage", error)
        }
    }

    private fun buildKeyboardPrivateOptions(contextLabel: String?, mode: String?): String {
        val bundle = buildLauncherThemeBundle(null, contextLabel, mode)
        val options = buildList {
            KEYBOARD_COLOR_KEYS.forEach { key ->
                if (bundle.containsKey(key)) {
                    add(key + "=" + RetuiVisualContract.colorToOption(bundle.getInt(key)))
                }
            }
            KEYBOARD_INT_KEYS.forEach { key ->
                if (bundle.containsKey(key)) {
                    add(key + "=" + bundle.getInt(key))
                }
            }
            KEYBOARD_FLOAT_KEYS.forEach { key ->
                if (bundle.containsKey(key)) {
                    add(key + "=" + bundle.getFloat(key))
                }
            }
            KEYBOARD_BOOLEAN_KEYS.forEach { key ->
                if (bundle.containsKey(key)) {
                    add(key + "=" + bundle.getBoolean(key))
                }
            }
            KEYBOARD_STRING_KEYS.forEach { key ->
                bundle.getString(key)?.takeIf { it.isNotBlank() }?.let { value ->
                    add(key + "=" + value)
                }
            }
        }
        return KEYBOARD_PRIVATE_OPTIONS_PREFIX + ":" + options.joinToString(";")
    }

    private fun buildLauncherThemeBundle(
        context: Context?,
        contextLabel: String? = null,
        mode: String? = null
    ): Bundle {
        val bundle = Bundle()
        val terminalSurfaceColor = terminalSurfaceColor()
        val terminalHeaderColor = AppearanceSettings.terminalHeaderTabBackground()
        val terminalBorderColor = AppearanceSettings.terminalBorderColor()
        val outputSurfaceColor = ColorUtils.blendARGB(terminalSurfaceColor, Color.BLACK, 0.10f)
        val inputSurfaceColor = ColorUtils.blendARGB(terminalSurfaceColor, Color.BLACK, 0.16f)
        val fileSelectionColor = LauncherSettings.getColor(Suggestions.file_background_color)
        val topDisplayMargin = XMLPrefsManager.get(Ui.display_margin_top_section)
        val panelText = AppearanceSettings.moduleNameTextColor()
        val buttonBg = AppearanceSettings.moduleButtonBackgroundColor()

        bundle.putInt(RetuiVisualContract.BG, XMLPrefsManager.getColor(Theme.background_color))
        bundle.putInt(RetuiVisualContract.TEXT, XMLPrefsManager.getColor(Theme.output_text_color))
        bundle.putInt(RetuiVisualContract.BORDER, terminalBorderColor)
        bundle.putInt(RetuiVisualContract.TERMINAL_BG, terminalSurfaceColor)
        bundle.putInt(RetuiVisualContract.PANEL_BG, terminalSurfaceColor)
        bundle.putInt(RetuiVisualContract.PANEL_TEXT, panelText)
        bundle.putInt(RetuiVisualContract.PANEL_BORDER, terminalBorderColor)
        bundle.putInt(RetuiVisualContract.HEADER_BG, terminalHeaderColor)
        bundle.putInt(RetuiVisualContract.HEADER_TEXT, panelText)
        bundle.putInt(RetuiVisualContract.BUTTON_BG, buttonBg)
        bundle.putInt(RetuiVisualContract.BUTTON_TEXT, panelText)
        bundle.putInt(RetuiVisualContract.BUTTON_BORDER, terminalBorderColor)
        bundle.putInt(RetuiVisualContract.INPUT_BG, inputSurfaceColor)
        bundle.putInt(RetuiVisualContract.INPUT_TEXT, XMLPrefsManager.getColor(Theme.input_text_color))
        bundle.putInt(RetuiVisualContract.OUTPUT_BG, outputSurfaceColor)
        bundle.putInt(RetuiVisualContract.OUTPUT_TEXT, XMLPrefsManager.getColor(Theme.output_text_color))
        bundle.putInt(RetuiVisualContract.OUTPUT_BORDER, terminalBorderColor)
        bundle.putInt(RetuiVisualContract.DIRECTORY_TEXT, panelText)
        bundle.putInt(RetuiVisualContract.SELECTION_BG, fileSelectionColor)
        bundle.putInt(RetuiVisualContract.SELECTION_TEXT, readableTextFor(fileSelectionColor))
        bundle.putInt(RetuiVisualContract.TOP_MARGIN, 18)
        bundle.putInt(RetuiVisualContract.INPUT_FONT_SIZE, XMLPrefsManager.getInt(Ui.input_output_size))
        bundle.putString(RetuiVisualContract.DISPLAY_MARGIN_TOP, topDisplayMargin)
        bundle.putString(
            RetuiVisualContract.DISPLAY_MARGIN_BOTTOM,
            XMLPrefsManager.get(Ui.display_margin_bottom_section)
        )
        bundle.putBoolean(RetuiVisualContract.DASHED_BORDERS, AppearanceSettings.dashedBorders())
        bundle.putInt(RetuiVisualContract.DASHED_BORDER_DASH_LENGTH, AppearanceSettings.dashLength())
        bundle.putInt(RetuiVisualContract.DASHED_BORDER_GAP_LENGTH, AppearanceSettings.dashGap())
        bundle.putFloat(
            RetuiVisualContract.DASHED_BORDER_STROKE_WIDTH_DP,
            AppearanceSettings.dashedBorderStrokeWidthDp()
        )
        bundle.putInt(RetuiVisualContract.MODULE_CORNER_RADIUS, AppearanceSettings.moduleCornerRadius())
        bundle.putInt(RetuiVisualContract.HEADER_CORNER_RADIUS, AppearanceSettings.headerCornerRadius())
        bundle.putInt(RetuiVisualContract.OUTPUT_CORNER_RADIUS, AppearanceSettings.outputCornerRadius())
        bundle.putInt(RetuiVisualContract.HEADER_TEXT_SIZE, AppearanceSettings.moduleHeaderTextSize())
        bundle.putInt(RetuiVisualContract.BODY_TEXT_SIZE, AppearanceSettings.moduleBodyTextSize())
        bundle.putInt(RetuiVisualContract.OUTPUT_HEADER_TEXT_SIZE, AppearanceSettings.outputHeaderTextSize())
        bundle.putBoolean(RetuiVisualContract.CYBERDECK_MODE, AppearanceSettings.cyberdeckMode())
        bundle.putBoolean(RetuiVisualContract.CRT_FILTER, AppearanceSettings.crtFilter())
        bundle.putBoolean(RetuiVisualContract.CRT_VIGNETTE, AppearanceSettings.crtVignette())

        contextLabel?.takeIf { it.isNotBlank() }?.let {
            bundle.putString(RetuiVisualContract.CONTEXT, it)
        }
        mode?.takeIf { it.isNotBlank() }?.let {
            bundle.putString(RetuiVisualContract.MODE, it)
        }

        if (context != null) {
            Tuils.getTypeface(context)
            val font = resolveLauncherFontExtras(
                AppearanceSettings.useSystemFont(),
                AppearanceSettings.fontFile(),
                Tuils.getFolder(),
                Tuils.fontPath
            )
            font.path?.let { bundle.putString(RetuiVisualContract.FONT_PATH, it) }
            font.file?.let { bundle.putString(RetuiVisualContract.FONT_FILE, it) }
            font.name?.let { bundle.putString(RetuiVisualContract.FONT_NAME, it) }
        }

        return bundle
    }

    internal fun resolveLauncherFontExtras(
        useSystemFont: Boolean,
        configuredFont: String?,
        launcherRoot: File,
        cachedFontPath: String?
    ): LauncherFontExtras {
        if (useSystemFont) return LauncherFontExtras(name = "system")

        val configured = configuredFont?.trim()?.takeIf { it.isNotEmpty() }
        val resolved = Tuils.resolveConfiguredFontFile(launcherRoot, configured)
        if (resolved != null) {
            return LauncherFontExtras(path = resolved.absolutePath, file = resolved.name)
        }

        val cached = cachedFontPath
            ?.takeIf { it.startsWith("/") }
            ?.let(::File)
            ?.takeIf { it.exists() && it.isFile }
        if (cached != null && configured != null && cached.name == File(configured).name) {
            return LauncherFontExtras(path = cached.absolutePath, file = cached.name)
        }

        return if (cachedFontPath == "asset://lucida_console.ttf") {
            LauncherFontExtras(name = "lucida_console")
        } else {
            LauncherFontExtras()
        }
    }

    internal data class LauncherFontExtras(
        val path: String? = null,
        val file: String? = null,
        val name: String? = null
    )

    private fun terminalSurfaceColor(): Int {
        val terminalBg = AppearanceSettings.terminalWindowBackground()
        if (Color.alpha(terminalBg) > 0) {
            return terminalBg
        }
        val outputBg = XMLPrefsManager.getColor(Theme.output_background_color)
        return if (Color.alpha(outputBg) > 0) outputBg else terminalBg
    }

    private fun readableTextFor(background: Int): Int {
        return if (ColorUtils.calculateLuminance(background) > 0.45) Color.BLACK else Color.WHITE
    }

    private val KEYBOARD_COLOR_KEYS = arrayOf(
        RetuiVisualContract.BG,
        RetuiVisualContract.TEXT,
        RetuiVisualContract.BORDER,
        RetuiVisualContract.TERMINAL_BG,
        RetuiVisualContract.PANEL_BG,
        RetuiVisualContract.PANEL_TEXT,
        RetuiVisualContract.PANEL_BORDER,
        RetuiVisualContract.HEADER_BG,
        RetuiVisualContract.HEADER_TEXT,
        RetuiVisualContract.BUTTON_BG,
        RetuiVisualContract.BUTTON_TEXT,
        RetuiVisualContract.BUTTON_BORDER,
        RetuiVisualContract.INPUT_BG,
        RetuiVisualContract.INPUT_TEXT,
        RetuiVisualContract.OUTPUT_BG,
        RetuiVisualContract.OUTPUT_TEXT,
        RetuiVisualContract.OUTPUT_BORDER
    )

    private val KEYBOARD_INT_KEYS = arrayOf(
        RetuiVisualContract.INPUT_FONT_SIZE,
        RetuiVisualContract.DASHED_BORDER_DASH_LENGTH,
        RetuiVisualContract.DASHED_BORDER_GAP_LENGTH,
        RetuiVisualContract.MODULE_CORNER_RADIUS,
        RetuiVisualContract.HEADER_CORNER_RADIUS,
        RetuiVisualContract.OUTPUT_CORNER_RADIUS,
        RetuiVisualContract.HEADER_TEXT_SIZE,
        RetuiVisualContract.BODY_TEXT_SIZE,
        RetuiVisualContract.OUTPUT_HEADER_TEXT_SIZE
    )

    private val KEYBOARD_FLOAT_KEYS = arrayOf(
        RetuiVisualContract.DASHED_BORDER_STROKE_WIDTH_DP
    )

    private val KEYBOARD_BOOLEAN_KEYS = arrayOf(
        RetuiVisualContract.DASHED_BORDERS,
        RetuiVisualContract.CYBERDECK_MODE,
        RetuiVisualContract.CRT_FILTER,
        RetuiVisualContract.CRT_VIGNETTE
    )

    private val KEYBOARD_STRING_KEYS = arrayOf(
        RetuiVisualContract.CONTEXT,
        RetuiVisualContract.MODE
    )
}
