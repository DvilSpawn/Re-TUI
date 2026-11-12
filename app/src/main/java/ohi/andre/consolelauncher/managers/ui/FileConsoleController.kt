package ohi.andre.consolelauncher.managers.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorders
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.outputCornerRadius
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.outputHeaderTextSize
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache.dirs
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache.files
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.frame.FrameTarget
import ohi.andre.consolelauncher.tuils.frame.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.util.Arrays
import kotlin.math.max
import kotlin.math.min

/** The file console overlay: its views, command handling and listings. */
class FileConsoleController(
    private val context: Context,
    private val rootView: ViewGroup,
    private val mainPackProvider: () -> MainPack?,
    private val executeCommand: (String) -> Unit,
    private val applyKeyboardTheme: (EditText?, String) -> Unit,
    private val sendKeyboardTheme: (EditText?, String) -> Unit,
    private val styleTermuxToolButton: (TextView?, Int) -> Unit,
    private val runOnMainThread: (() -> Unit) -> Unit,
    private val closeOtherSurfaces: () -> Unit,
    private val hideHomeSuggestionsForTermux: () -> Unit,
    private val restoreHomeSuggestionsAfterTermux: () -> Unit,
    private val focusInputEnd: () -> Unit
) {
    private var fileOverlay: View? = null
    private var fileOverlayBasePaddingLeft = 0
    private var fileOverlayBasePaddingTop = 0
    private var fileOverlayBasePaddingRight = 0
    private var fileOverlayBasePaddingBottom = 0
    private var fileWindowBorder: View? = null
    private var fileWindowLabel: TextView? = null
    private var fileClose: TextView? = null
    private var filePath: TextView? = null
    private var fileOutput: TextView? = null
    private var filePrefix: TextView? = null
    private var fileInput: EditText? = null
    private var fileScroll: ScrollView? = null
    private var fileInputGroup: View? = null
    private var fileTools: View? = null
    private var fileRefresh: TextView? = null
    private var fileUp: TextView? = null
    private var fileOpen: TextView? = null
    private var filePaste: TextView? = null

    fun setupFileConsole() {
        fileOverlay = rootView.findViewById<View?>(R.id.file_overlay)
        if (fileOverlay == null) {
            return
        }
        fileOverlayBasePaddingLeft = fileOverlay!!.getPaddingLeft()
        fileOverlayBasePaddingTop = fileOverlay!!.getPaddingTop()
        fileOverlayBasePaddingRight = fileOverlay!!.getPaddingRight()
        fileOverlayBasePaddingBottom = fileOverlay!!.getPaddingBottom()

        fileWindowBorder = rootView.findViewById<View?>(R.id.file_window_border)
        fileWindowLabel = rootView.findViewById<TextView?>(R.id.file_window_label)
        fileClose = rootView.findViewById<TextView?>(R.id.file_close)
        filePath = rootView.findViewById<TextView?>(R.id.file_path)
        fileOutput = rootView.findViewById<TextView?>(R.id.file_output)
        filePrefix = rootView.findViewById<TextView?>(R.id.file_prefix)
        fileInput = rootView.findViewById<EditText?>(R.id.file_input)
        fileScroll = rootView.findViewById<ScrollView?>(R.id.file_scroll)
        fileInputGroup = rootView.findViewById<View?>(R.id.file_input_group)
        fileTools = rootView.findViewById<View?>(R.id.file_tools)
        fileRefresh = rootView.findViewById<TextView?>(R.id.file_refresh)
        fileUp = rootView.findViewById<TextView?>(R.id.file_up)
        fileOpen = rootView.findViewById<TextView?>(R.id.file_open)
        filePaste = rootView.findViewById<TextView?>(R.id.file_paste)

        styleFileConsole()

        if (fileClose != null) {
            fileClose!!.setOnClickListener(View.OnClickListener { v: View? -> closeFileConsole() })
        }

        if (fileInput != null) {
            applyKeyboardTheme(fileInput, "files")
            fileInput!!.setOnEditorActionListener(OnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
                val enter =
                    event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP
                if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                    val command = fileInput!!.getText().toString()
                    fileInput!!.setText(Tuils.EMPTYSTRING)
                    executeFileConsoleCommand(command)
                    true
                } else {
                    false
                }
            })
        }

        if (fileRefresh != null) {
            fileRefresh!!.setOnClickListener(View.OnClickListener { v: View? ->
                refreshFileConsole(
                    true
                )
            })
        }
        if (fileUp != null) {
            fileUp!!.setOnClickListener(View.OnClickListener { v: View? ->
                executeFileConsoleCommand(
                    "cd .."
                )
            })
        }
        if (fileOpen != null) {
            fileOpen!!.setOnClickListener(View.OnClickListener { v: View? ->
                if (fileInput != null) {
                    fileInput!!.setText("open ")
                    fileInput!!.setSelection(fileInput!!.getText().length)
                    fileInput!!.requestFocus()
                }
            })
        }
        if (filePaste != null) {
            filePaste!!.setOnClickListener(View.OnClickListener { v: View? ->
                val text = Tuils.getTextFromClipboard(context)
                if (text != null && text.length > 0 && fileInput != null) {
                    val start = max(fileInput!!.getSelectionStart(), 0)
                    val end = max(fileInput!!.getSelectionEnd(), 0)
                    fileInput!!.getText().replace(min(start, end), max(start, end), text)
                }
            })
        }
    }

    private fun styleFileConsole() {
        if (fileOverlay == null) {
            return
        }

        val borderColor = ThemeColorResolver.color(Theme.workspace_panel_border_color)
        val textColor = ThemeColorResolver.color(Theme.workspace_panel_text_color)
        val bgColor = ThemeColorResolver.color(Theme.workspace_panel_background_color)
        val labelBg = ThemeColorResolver.color(Theme.workspace_header_background_color)

        if (fileWindowBorder != null) {
            fileWindowBorder!!.setBackground(
                TerminalBorderRuntime.panelDrawable(
                    context,
                    bgColor,
                    borderColor,
                    1.5f,
                    outputCornerRadius(),
                    dashedBorders(),
                    target = FrameTarget.OVERLAYS
                )
            )
        }

        if (fileWindowLabel != null) {
            fileWindowLabel!!.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            fileWindowLabel!!.setTextSize(outputHeaderTextSize().toFloat())
            fileWindowLabel!!.setTextColor(textColor)
            fileWindowLabel!!.setBackground(TerminalBorderRuntime.tabDrawable(context, labelBg, FrameTarget.OVERLAYS))
        }
        if (fileClose != null) {
            fileClose!!.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            fileClose!!.setTextSize(outputHeaderTextSize().toFloat())
            fileClose!!.setTextColor(textColor)
            fileClose!!.setBackground(TerminalBorderRuntime.tabDrawable(context, labelBg, FrameTarget.OVERLAYS))
        }
        TerminalBorderRuntime.bind(fileWindowBorder, fileWindowLabel, fileClose)
        if (filePath != null) {
            filePath!!.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            filePath!!.setTextColor(textColor)
        }
        if (fileOutput != null) {
            fileOutput!!.setTypeface(Tuils.getTypeface(context))
            fileOutput!!.setTextColor(textColor)
            fileOutput!!.setTextIsSelectable(true)
        }
        if (filePrefix != null) {
            filePrefix!!.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            filePrefix!!.setTextColor(textColor)
        }
        if (fileInput != null) {
            fileInput!!.setTypeface(Tuils.getTypeface(context))
            fileInput!!.setTextColor(ThemeColorResolver.color(Theme.workspace_input_text_color))
            fileInput!!.setHintTextColor(ThemeColorResolver.color(Theme.field_hint_color))
            Tuils.setCursorDrawableColor(fileInput!!, ThemeColorResolver.color(Theme.cursor_color))
        }
        if (fileInputGroup != null) {
            fileInputGroup!!.setBackground(
                TerminalBorderRuntime.panelDrawable(
                    context,
                    ThemeColorResolver.color(Theme.workspace_input_background_color),
                    ThemeColorResolver.withMaxAlpha(borderColor, 180),
                    1.2f,
                    outputCornerRadius(),
                    dashedBorders(),
                    target = FrameTarget.OVERLAYS
                )
            )
        }
        if (fileTools != null) {
            fileTools!!.setBackgroundColor(Color.TRANSPARENT)
        }
        styleTermuxToolButton(fileRefresh, textColor)
        styleTermuxToolButton(fileUp, textColor)
        styleTermuxToolButton(fileOpen, textColor)
        styleTermuxToolButton(filePaste, textColor)
    }

    fun openFileConsole(command: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnMainThread { openFileConsole(command) }
            return
        }

        if (fileOverlay == null) {
            return
        }

        closeOtherSurfaces()
        styleFileConsole()
        fileOverlay!!.setVisibility(View.VISIBLE)
        fileOverlay!!.bringToFront()
        hideHomeSuggestionsForTermux()
        refreshFileConsole(true)

        val normalized = if (command == null) Tuils.EMPTYSTRING else command.trim { it <= ' ' }
        if (normalized.length > 0) {
            executeFileConsoleCommand(normalized)
        }

        if (fileInput != null) {
            sendKeyboardTheme(fileInput, "files")
            fileInput!!.requestFocus()
            fileInput!!.postDelayed(Runnable {
                val manager =
                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                if (manager != null) {
                    sendKeyboardTheme(fileInput, "files")
                    manager.showSoftInput(fileInput, InputMethodManager.SHOW_IMPLICIT)
                }
            }, 120)
        }
    }

    fun closeFileConsole(restoreSuggestions: Boolean = true) {
        if (fileOverlay != null) {
            fileOverlay!!.setVisibility(View.GONE)
        }
        if (restoreSuggestions) {
            restoreHomeSuggestionsAfterTermux()
        }
        if (fileInput != null) {
            val manager =
                context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            if (manager != null) {
                manager.hideSoftInputFromWindow(fileInput!!.getWindowToken(), 0)
            }
            fileInput!!.clearFocus()
        }
        if (restoreSuggestions) {
            focusInputEnd()
        }
    }

    private fun executeFileConsoleCommand(rawCommand: String?) {
        val command = if (rawCommand == null) Tuils.EMPTYSTRING else rawCommand.trim { it <= ' ' }
        if (command.length == 0) {
            return
        }
        val lower = command.lowercase()

        if ("exit" == lower || "close" == lower) {
            closeFileConsole()
            return
        }
        if ("help" == lower) {
            renderFileConsole(context.getString(R.string.surface_commands_cd_folder_cd_ls_pwd_open_file_termux_open_file_sh_20502))
            return
        }
        if ("refresh" == lower || "reload" == lower || "ls" == lower) {
            refreshFileConsole(true)
            return
        }
        if ("pwd" == lower) {
            renderFileConsole(mainPackProvider()!!.currentDirectory.getAbsolutePath())
            return
        }

        executeCommand(command)

        if (lower == "cd" || lower.startsWith("cd ")) {
            refreshFileConsole(true)
        } else if (lower.startsWith("open ") || lower.startsWith("termux-open ") || lower.startsWith(
                "share "
            )
        ) {
            renderFileConsole(context.getString(R.string.surface_dispatched_4daa9, command))
        } else {
            refreshFileConsole(true)
        }
    }

    fun refreshFileConsole(forceTermuxRequest: Boolean) {
        if (fileOverlay == null || fileOverlay!!.getVisibility() != View.VISIBLE) {
            return
        }

        val path = mainPackProvider()!!.currentDirectory.getAbsolutePath()
        if (filePath != null) {
            filePath!!.setText(path)
        }

        renderFileConsole(buildNativeFileListing(mainPackProvider()!!.currentDirectory))
    }

    private fun buildNativeFileListing(directory: File?): String {
        if (directory == null || !directory.exists()) {
            return context.getString(R.string.surface_path_not_found_7e7ec)
        }
        if (!directory.isDirectory()) {
            return directory.getName()
        }

        val children = directory.listFiles()
        if (children == null || children.size == 0) {
            return "[]"
        }
        Arrays.sort<File?>(
            children,
            Comparator { a: File?, b: File? ->
                a!!.getName().compareTo(b!!.getName(), ignoreCase = true)
            })
        val dirs: MutableList<String?> = ArrayList<String?>()
        val files: MutableList<String?> = ArrayList<String?>()
        for (child in children) {
            if (child.isDirectory()) {
                dirs.add(child.getName())
            } else {
                files.add(child.getName())
            }
        }
        return buildFileListing(dirs, files, null)
    }

    private fun buildFileListing(
        dirs: List<String?>,
        files: List<String?>,
        error: String?
    ): String {
        val out = StringBuilder()
        out.append("[..]")
        if (error != null && error.trim { it <= ' ' }.length > 0) {
            out.append('\n').append(context.getString(R.string.surface_error_36baa)).append(error.trim { it <= ' ' })
        }
        for (dir in dirs) {
            out.append('\n').append(context.getString(R.string.surface_d_a2c8d)).append(dir)
        }
        for (file in files) {
            out.append('\n').append("    ").append(file)
        }
        return out.toString()
    }

    private fun renderFileConsole(text: String?) {
        if (fileOutput != null) {
            fileOutput!!.setText(if (text == null) Tuils.EMPTYSTRING else text)
        }
        if (fileScroll != null) {
            fileScroll!!.post(Runnable { fileScroll!!.scrollTo(0, 0) })
        }
    }

    fun updateFileConsoleFromTermux(path: String?, error: String?) {
        if (fileOverlay == null || fileOverlay!!.getVisibility() != View.VISIBLE) {
            return
        }
        val current = mainPackProvider()!!.currentDirectory.getAbsolutePath()
        if (current != path) {
            return
        }
        if (filePath != null) {
            filePath!!.setText(current)
        }
        renderFileConsole(buildFileListing(dirs(path), files(path), error))
    }

    fun applyDisplayMargins(left: Int, top: Int, right: Int, bottom: Int) {
        OverlayLayoutManager.applyPaddingWithBase(
            fileOverlay,
            fileOverlayBasePaddingLeft,
            fileOverlayBasePaddingTop,
            fileOverlayBasePaddingRight,
            fileOverlayBasePaddingBottom,
            left,
            top,
            right,
            bottom
        )
    }
}
