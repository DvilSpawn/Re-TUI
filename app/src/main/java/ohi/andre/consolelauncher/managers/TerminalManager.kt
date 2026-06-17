package ohi.andre.consolelauncher.managers

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.IBinder
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.AppsManager.LaunchInfo
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.LongClickMovementMethod.Companion.getInstance
import ohi.andre.consolelauncher.tuils.LongClickableSpan
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter
import java.util.Locale
import android.content.res.ColorStateList
import android.text.Layout
import java.lang.reflect.Field
import java.util.ArrayList
import ohi.andre.consolelauncher.tuils.LongClickMovementMethod

/*Copyright Francesco Andreuzzi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/
class TerminalManager(
    terminalView: TextView,
    inputView: EditText,
    prefixView: TextView,
    submitView: ImageView?,
    backView: ImageView?,
    nextView: ImageButton?,
    deleteView: ImageButton?,
    pasteView: ImageButton?,
    context: Context,
    mainPack: MainPack,
    executer: CommandExecuter
) {
    private val SCROLL_DELAY = 200
    private val CMD_LIST_SIZE = 40

    private var lastEnter: Long = 0

    private val prefix: String
    private val suPrefix: String

    private val mScrollView: ScrollView
    private var mTerminalView: TextView?
    private var mInputView: EditText?

    private val mPrefix: TextView?
    private var suMode = false

    private val cmdList: MutableList<String> = ArrayList<String>(CMD_LIST_SIZE)
    private var howBack = -1

    private val mScrollRunnable: Runnable = object : Runnable {
        override fun run() {
            val restoreInputFocus = mInputView != null && mInputView!!.hasFocus()
            val child = if (mScrollView.childCount > 0) mScrollView.getChildAt(0) else null
            val scrollY = if (child == null) 0 else child.bottom - mScrollView.height
            mScrollView.scrollTo(0, if (scrollY > 0) scrollY else 0)
            if (restoreInputFocus && mInputView != null) {
                mInputView!!.requestFocus()
            }
        }
    }

    val mainPack: MainPack

    private var defaultHint = true

    private var clearCmdsCount = 0

    private val clearAfterCmds: Int
    private var clearAfterMs: Int
    private var maxLines: Int
    private val clearRunnable: Runnable = object : Runnable {
        override fun run() {
            clear()
            mTerminalView!!.postDelayed(this, clearAfterMs.toLong())
        }
    }

    private val inputFormat: String?
    private val outputFormat: String?
    private val inputColor: Int
    private val outputColor: Int

    private val clickCommands: Boolean
    private val longClickCommands: Boolean

    var mContext: Context

    private val executer: CommandExecuter

    private fun setupNewInput() {
        mInputView!!.setText(Tuils.EMPTYSTRING)

        if (defaultHint) {
            mInputView!!.setHint(Tuils.getHint(mainPack.currentDirectory.getAbsolutePath()))
        }

        requestInputFocus()
    }

    private fun onNewInput(): Boolean {
        if (mInputView == null) {
            return false
        }

        val input: CharSequence = mInputView!!.getText()

        val cmd = input.toString().trim { it <= ' ' }

        var obj: Any? = null
        try {
            obj = (input as Spannable).getSpans<LaunchInfo?>(
                0,
                input.length,
                LaunchInfo::class.java
            )[0]
        } catch (e: Exception) {
//            an error will probably be thrown everytime, but we don't need to track it
        }

        if (input.length > 0) {
            clearCmdsCount++
            if (clearCmdsCount != 0 && clearAfterCmds > 0 && clearCmdsCount % clearAfterCmds == 0) clear()

            writeToView(input, CATEGORY_INPUT)

            if (cmdList.size == CMD_LIST_SIZE) {
                cmdList.removeAt(0)
            }
            cmdList.add(cmdList.size, cmd)
            howBack = -1
        }

        //        DO NOT USE THE INTENT APPROACH
//        apps are not launching properly, when one has been launched, an other attempt will show always the same
//
//        Intent intent = new Intent(MainManager.ACTION_EXEC);
//        intent.putExtra(MainManager.CMD, cmd);
//        intent.putExtra(MainManager.CMD_COUNT, MainManager.commandCount);
//
//        Parcelable p = null;
//        if(obj instanceof Parcelable) p = (Parcelable) obj;
//        intent.putExtra(MainManager.PARCELABLE, p);
//
//        LocalBroadcastManager.getInstance(mContext.getApplicationContext()).sendBroadcast(intent);
        executer.execute(cmd, obj)

        setupNewInput()

        return true
    }

    fun executeInput(input: String?): Boolean {
        if (input == null || input.trim { it <= ' ' }.length == 0 || mInputView == null) {
            return false
        }

        this.input = input
        return onNewInput()
    }

    fun setOutput(output: CharSequence?, type: Int) {
        if (output == null || output.length == 0) return

        writeToView(output, type)
    }

    fun setOutput(color: Int, output: CharSequence?) {
        var color = color
        if (output == null || output.length == 0) return

        if (color == NO_COLOR) {
            color = XMLPrefsManager.getColor(Theme.output_text_color)
        }

        val si = SpannableString(output)
        si.setSpan(ForegroundColorSpan(color), 0, output.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val s = TextUtils.concat(Tuils.NEWLINE, si)
        writeToView(s)
    }

    val terminalText: String
        get() = mTerminalView!!.getText().toString()

    fun onBackPressed() {
        if (cmdList.size > 0) {
            if (howBack == -1) {
                howBack = cmdList.size
            } else if (howBack == 0) {
                return
            }
            howBack--

            this.input = cmdList.get(howBack)
        }
    }

    fun onNextPressed() {
        if (howBack != -1 && howBack < cmdList.size) {
            howBack++

            val input: String
            if (howBack == cmdList.size) {
                input = Tuils.EMPTYSTRING
            } else {
                input = cmdList.get(howBack)
            }

            this.input = input
        }
    }

    init {
        if (terminalView == null || inputView == null || prefixView == null) throw UnsupportedOperationException()

        this.mContext = context
        this.executer = executer

        this.mainPack = mainPack

        this.clickCommands = XMLPrefsManager.getBoolean(Behavior.click_commands)
        this.longClickCommands = XMLPrefsManager.getBoolean(Behavior.long_click_commands)

        this.clearAfterMs = XMLPrefsManager.getInt(Behavior.clear_after_seconds) * 1000
        this.clearAfterCmds = XMLPrefsManager.getInt(Behavior.clear_after_cmds)
        this.maxLines = XMLPrefsManager.getInt(Behavior.max_lines)

        inputFormat = XMLPrefsManager.get(Behavior.input_format)
        outputFormat = XMLPrefsManager.get(Behavior.output_format)

        inputColor = XMLPrefsManager.getColor(Theme.input_text_color)
        outputColor = XMLPrefsManager.getColor(Theme.output_text_color)

        prefix = XMLPrefsManager.get(Ui.input_prefix)
        suPrefix = XMLPrefsManager.get(Ui.input_root_prefix)

        val ioSize = XMLPrefsManager.getInt(Ui.input_output_size)

        prefixView.setTypeface(Tuils.getTypeface(context))
        prefixView.setTextColor(XMLPrefsManager.getColor(Theme.input_text_color))
        prefixView.setTextSize(ioSize.toFloat())
        prefixView.setText(if (prefix.endsWith(Tuils.SPACE)) prefix else prefix + Tuils.SPACE)
        this.mPrefix = prefixView

        val toolbarColor = XMLPrefsManager.getColor(Theme.toolbar_icon_color)
        val enterColor = XMLPrefsManager.getColor(Theme.enter_icon_color)

        if (submitView != null) {
            submitView.setColorFilter(enterColor, PorterDuff.Mode.SRC_IN)
            submitView.setOnClickListener(View.OnClickListener { v: View? -> onNewInput() })
        }

        if (backView != null) {
            backView.setColorFilter(toolbarColor, PorterDuff.Mode.SRC_IN)
            backView.setBackgroundColor(0)
            backView.setOnClickListener(View.OnClickListener { v: View? -> onBackPressed() })
        }

        if (nextView != null) {
            nextView.setColorFilter(toolbarColor, PorterDuff.Mode.SRC_IN)
            nextView.setBackgroundColor(0)
            nextView.setOnClickListener(View.OnClickListener { v: View? -> onNextPressed() })
        }

        if (pasteView != null) {
            pasteView.setColorFilter(toolbarColor, PorterDuff.Mode.SRC_IN)
            pasteView.setBackgroundColor(0)
            pasteView.setOnClickListener(View.OnClickListener { v: View? ->
                val text = Tuils.getTextFromClipboard(context)
                if (text != null && text.length > 0) {
                    this.input = this.input + text
                }
            })
        }

        if (deleteView != null) {
            deleteView.setColorFilter(toolbarColor, PorterDuff.Mode.SRC_IN)
            deleteView.setBackgroundColor(0)
            deleteView.setOnClickListener(View.OnClickListener { v: View? ->
                this.input = Tuils.EMPTYSTRING
            })
        }

        this.mTerminalView = terminalView
        this.mTerminalView!!.setTypeface(Tuils.getTypeface(context))
        this.mTerminalView!!.setTextSize(ioSize.toFloat())
        this.mTerminalView!!.setMovementMethod(getInstance(XMLPrefsManager.getInt(Behavior.long_click_duration)))
        this.mTerminalView!!.setTextIsSelectable(true)

        val hintColor = XMLPrefsManager.getColor(Theme.session_info_text_color)

        mTerminalView!!.setHintTextColor(hintColor)

        if (clearAfterMs > 0) this.mTerminalView!!.postDelayed(clearRunnable, clearAfterMs.toLong())
        if (maxLines > 0) {
            this.mTerminalView!!.getViewTreeObserver()
                .addOnPreDrawListener(ViewTreeObserver.OnPreDrawListener {
                    if (this@TerminalManager.mTerminalView == null) return@OnPreDrawListener true
                    val l = terminalView.getLayout()
                    if (l == null) return@OnPreDrawListener true

                    val count = l.getLineCount() - 1

                    if (count > maxLines) {
                        var excessive = count - maxLines

                        var text = terminalView.getText()
                        while (excessive >= 0) {
                            val index = TextUtils.indexOf(text, Tuils.NEWLINE)
                            if (index == -1) break
                            text = text.subSequence(index + 1, text.length)
                            excessive--
                        }

                        terminalView.setText(text)
                    }
                    true
                })
        }

        var v: View? = mTerminalView
        do {
            v = v!!.getParent() as View
        } while (v !is ScrollView)
        this.mScrollView = v

        this.mInputView = inputView
        this.mInputView!!.setTextSize(ioSize.toFloat())
        this.mInputView!!.setTextColor(XMLPrefsManager.getColor(Theme.input_text_color))
        this.mInputView!!.setTypeface(Tuils.getTypeface(context))
        this.mInputView!!.setHint(Tuils.getHint(mainPack.currentDirectory.getAbsolutePath()))
        this.mInputView!!.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        Tuils.setCursorDrawableColor(this.mInputView!!, XMLPrefsManager.getColor(Theme.cursor_color))
        this.mInputView!!.setHighlightColor(Color.TRANSPARENT)
        this.mInputView!!.setOnEditorActionListener(OnEditorActionListener { v1: TextView?, actionId: Int, event: KeyEvent? ->
            if (!mInputView!!.hasFocus()) mInputView!!.requestFocus()
            //                physical enter
            if (actionId == KeyEvent.ACTION_DOWN) {
                if (lastEnter == 0L) {
                    lastEnter = System.currentTimeMillis()
                } else {
                    val difference = System.currentTimeMillis() - lastEnter
                    lastEnter = System.currentTimeMillis()
                    if (difference < 350) {
                        return@OnEditorActionListener true
                    }
                }
            }

            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || actionId == KeyEvent.ACTION_DOWN) {
                onNewInput()
            }
            true
        })
        //        if(autoLowerFirstChar) {
//            this.mInputView.setFilters(new InputFilter[] {new InputFilter() {
//                @Override
//                public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
//                    if (dstart == 0 && dend == 0 && start == 0 && end == 1 && source.length() > 0) {
//                        return TextUtils.concat(source.toString().toLowerCase().charAt(0) + Tuils.EMPTYSTRING, source.subSequence(1,source.length()));
//                    }
//
//                    return source;
//                }
//            }});
//        }
    }

    private fun writeToView(text: CharSequence, type: Int) {
        var text = text
        text = getFinalText(text, type)!!
        text = TextUtils.concat(Tuils.NEWLINE, text)
        writeToView(text)
    }

    private fun writeToView(text: CharSequence?) {
        mTerminalView!!.post(Runnable {
            mTerminalView!!.append(text)
            scrollToEnd()
        })
    }

    private fun getFinalText(t: CharSequence, type: Int): CharSequence? {
        var t = t
        var s: CharSequence?
        when (type) {
            CATEGORY_INPUT -> {
                t = t.toString()

                val su = t.toString().startsWith("su ") || suMode

                val si = Tuils.span(inputFormat, inputColor) ?: SpannableString(inputFormat)
                if (clickCommands || longClickCommands) si.setSpan(
                    LongClickableSpan(
                        if (clickCommands) t.toString() else null,
                        if (longClickCommands) t.toString() else null,
                        PrivateIOReceiver.ACTION_INPUT
                    ), 0,
                    si.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                s = TimeManager.instance!!.replace(si)
                s = TextUtils.replace(
                    s,
                    arrayOf<String>(
                        FORMAT_INPUT, FORMAT_PREFIX, FORMAT_NEWLINE, FORMAT_INPUT.uppercase(
                            Locale.getDefault()
                        ), FORMAT_PREFIX.uppercase(Locale.getDefault()), FORMAT_NEWLINE.uppercase(
                            Locale.getDefault()
                        )
                    ),
                    arrayOf<CharSequence?>(
                        t,
                        if (su) suPrefix else prefix,
                        Tuils.NEWLINE,
                        t,
                        if (su) suPrefix else prefix,
                        Tuils.NEWLINE
                    )
                )
            }

            CATEGORY_OUTPUT -> {
                t = t.toString()

                val so = Tuils.span(outputFormat, outputColor)

                s = TextUtils.replace(
                    so,
                    arrayOf<String>(
                        FORMAT_OUTPUT,
                        FORMAT_NEWLINE,
                        FORMAT_OUTPUT.uppercase(Locale.getDefault()),
                        FORMAT_NEWLINE.uppercase(
                            Locale.getDefault()
                        )
                    ),
                    arrayOf<CharSequence>(t, Tuils.NEWLINE, t, Tuils.NEWLINE)
                )
            }

            CATEGORY_NO_COLOR -> s = t
            else -> return null
        }

        return s
    }

    fun simulateEnter() {
        onNewInput()
    }

    fun executeQuietly(cmd: String?, obj: Any?) {
        if (cmd == null || cmd.trim { it <= ' ' }.length == 0) {
            return
        }
        executer.execute(cmd.trim { it <= ' ' }, obj)
        setupNewInput()
    }

    var input: String
        get() = mInputView!!.getText().toString()
        set(input) {
            setInput(input, null)
        }

    fun setInput(input: String?, obj: Any?) {
        if (input == null) {
            return
        }
        val spannable = SpannableString(input)
        if (obj != null) {
            spannable.setSpan(obj, 0, input.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        mInputView!!.setText(spannable)
        focusInputEnd()
    }

    fun setHint(hint: String?) {
        defaultHint = false

        if (mInputView != null) {
            mInputView!!.setHint(hint)
        }
    }

    fun setDefaultHint() {
        defaultHint = true

        if (mInputView != null) {
            mInputView!!.setHint(Tuils.getHint(mainPack.currentDirectory.getAbsolutePath()))
        }
    }

    fun focusInputEnd() {
        mInputView!!.setSelection(this.input.length)
    }

    fun scrollToEnd() {
        mScrollView.postDelayed(mScrollRunnable, SCROLL_DELAY.toLong())
    }

    fun requestInputFocus() {
        mInputView!!.requestFocus()
    }

    val inputWindowToken: IBinder?
        get() = mInputView!!.getWindowToken()

    val inputView: View?
        get() = mInputView

    fun refreshTypeface() {
        val typeface = Tuils.getTypeface(mContext)
        if (typeface == null) {
            return
        }

        if (mPrefix != null) {
            mPrefix.setTypeface(typeface)
        }

        if (mTerminalView != null) {
            mTerminalView!!.setTypeface(typeface)
        }

        if (mInputView != null) {
            mInputView!!.setTypeface(typeface)
        }
    }

    fun clear() {
        mTerminalView!!.post(Runnable { mTerminalView!!.setText(Tuils.EMPTYSTRING) })
        cmdList.clear()
        clearCmdsCount = 0
    }

    fun onRoot() {
        (mContext as Activity).runOnUiThread(Runnable {
            suMode = true
            mPrefix!!.setText(if (suPrefix.endsWith(Tuils.SPACE)) suPrefix else suPrefix + Tuils.SPACE)
        })
    }

    fun onStandard() {
        (mContext as Activity).runOnUiThread(Runnable {
            suMode = false
            mPrefix!!.setText(if (prefix.endsWith(Tuils.SPACE)) prefix else prefix + Tuils.SPACE)
        })
    }

    companion object {
        const val CATEGORY_INPUT: Int = 10
        const val CATEGORY_OUTPUT: Int = 11
        const val CATEGORY_NO_COLOR: Int = 20

        var NO_COLOR: Int = Int.Companion.MAX_VALUE

        const val FORMAT_INPUT: String = "%i"
        const val FORMAT_OUTPUT: String = "%o"
        const val FORMAT_PREFIX: String = "%p"
        const val FORMAT_NEWLINE: String = "%n"
    }
}
