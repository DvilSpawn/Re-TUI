package ohi.andre.consolelauncher.commands.main.raw

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.callback.CallbackAuthManager
import ohi.andre.consolelauncher.tuils.Tuils

class retuitoken : CommandAbstraction {
    override fun exec(info: ExecutePack): String {
        var command = ""
        val args = info.args
        if (args != null && args.isNotEmpty()) {
            val arg = info.get()
            if (arg != null) {
                command = arg.toString().trim().lowercase(Locale.ROOT)
            }
        }

        if (command.isEmpty() || command == "-status" || command == "status") {
            return status(info)
        }
        if (command == "-show" || command == "show") {
            val token = CallbackAuthManager.getOrCreateToken(info.context)
            CallbackAuthManager.setEnabled(info.context, true)
            copyToken(info.context, token)
            return tokenOutput(info.context, info.context.getString(R.string.command_retuitoken_callback_auth_enabled_8ae51), token)
        }
        if (command == "-rotate" || command == "rotate") {
            val token = CallbackAuthManager.rotateToken(info.context)
            copyToken(info.context, token)
            return tokenOutput(info.context, info.context.getString(R.string.command_retuitoken_callback_token_rotated_7ce91), token)
        }
        if (command == "-on" || command == "on") {
            val token = CallbackAuthManager.getOrCreateToken(info.context)
            CallbackAuthManager.setEnabled(info.context, true)
            copyToken(info.context, token)
            return tokenOutput(info.context, info.context.getString(R.string.command_retuitoken_callback_auth_enabled_8ae51), token)
        }
        if (command == "-off" || command == "off") {
            CallbackAuthManager.setEnabled(info.context, false)
            return info.context.getString(R.string.command_retuitoken_callback_auth_disabled_f38f0)
        }

        return info.context.getString(R.string.help_retuitoken)
    }

    private fun status(info: ExecutePack): String =
        info.context.getString(R.string.command_retuitoken_callback_auth_token_present_3703c, (if (CallbackAuthManager.isEnabled(info.context)) "enabled" else "disabled"), Tuils.NEWLINE, CallbackAuthManager.getToken(info.context).isNotEmpty())

    private fun copyToken(context: Context, token: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.command_retuitoken_re_tui_callback_token_1e777), token))
    }

    private fun tokenOutput(context: android.content.Context, message: String, token: String): String =
        context.getString(R.string.command_detail_retuitoken_token_copied_to_clipboard_token_274e3, message +
            Tuils.NEWLINE, Tuils.NEWLINE, token)

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 2

    override fun helpRes(): Int = R.string.help_retuitoken

    override fun onArgNotFound(info: ExecutePack, indexNotFound: Int): String =
        info.context.getString(R.string.help_retuitoken)

    override fun onNotArgEnough(info: ExecutePack, nArgs: Int): String = status(info)
}
