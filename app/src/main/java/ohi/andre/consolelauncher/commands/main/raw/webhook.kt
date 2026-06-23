package ohi.andre.consolelauncher.commands.main.raw

import android.os.Handler
import android.os.Looper
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.WebhookManager.Webhook
import ohi.andre.consolelauncher.tuils.Tuils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.AbstractMap.SimpleEntry
import java.util.Locale
import java.util.ArrayList
import ohi.andre.consolelauncher.managers.WebhookManager

class webhook : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        add {
            override fun exec(pack: ExecutePack): String? {
                val info = pack as MainPack
                val split = Tuils.splitArgs(info.lastCommand)
                if (split.size < 5) return "Usage: webhook -add [name] [url] [body_template]"

                val name: String? = split.get(2)
                val url: String? = split.get(3)

                val bodyParts: MutableList<String?> = split.subList(4, split.size)
                val body = Tuils.toPlanString(bodyParts, Tuils.SPACE)

                val saved = info.webhookManager.add(name, url, body)
                return if (saved) "Webhook " + name + " saved." else "Unable to save webhook " + name + "."
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.TEXTLIST)
            }
        },
        rm {
            override fun exec(pack: ExecutePack): String? {
                val args = pack.getList<String?>()
                if (args.isEmpty()) return "Usage: webhook -rm [name]"
                val name = args.get(0)
                val removed = (pack as MainPack).webhookManager.remove(name)
                return if (removed) "Webhook " + name + " removed." else "Webhook " + name + " not found."
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.TEXTLIST)
            }
        },
        ls {
            override fun exec(pack: ExecutePack): String? {
                val hooks = (pack as MainPack).webhookManager.getWebhooks()
                if (hooks == null || hooks.isEmpty()) return "No webhooks configured."
                val sb = StringBuilder()
                for (w in hooks) {
                    sb.append(w.name).append(" -> ").append(w.url).append(Tuils.NEWLINE)
                }
                return sb.toString().trim { it <= ' ' }
            }

            override fun args(): IntArray? {
                return IntArray(0)
            }
        };

        override fun label(): String? {
            return Tuils.MINUS + name
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
            return pack.context.getString(R.string.help_webhook)
        }

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
            return null
        }

        companion object {
            fun get(p: String?): Param? {
                var p = p
                if (p == null) return null
                p = p.lowercase(Locale.getDefault())
                for (p1 in entries) {
                    if (p == p1.label()) return p1
                }
                return null
            }

            fun labels(): Array<String?> {
                val ps: Array<Param?> = entries.toTypedArray()
                val ss = arrayOfNulls<String>(ps.size)
                for (count in ps.indices) {
                    ss[count] = ps[count]!!.label()
                }
                return ss
            }
        }
    }

    private class WebhookParam(private val w: Webhook) :
        ohi.andre.consolelauncher.commands.main.Param {
        override fun args(): IntArray {
            return intArrayOf(CommandAbstraction.TEXTLIST)
        }

        override fun exec(pack: ExecutePack): String {
            val list = pack.getList<String?>()
            val args = if (list != null) list.toTypedArray<String?>() else arrayOfNulls<String>(0)
            return triggerWebhook(pack as MainPack, w, args)
        }

        override fun label(): String {
            return w.name
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
            return null
        }

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
            return null
        }
    }

    public override fun getParam(
        pack: MainPack,
        param: String
    ): SimpleEntry<Boolean, ohi.andre.consolelauncher.commands.main.Param?> {
        packRef = WeakReference<MainPack>(pack)
        val p: ohi.andre.consolelauncher.commands.main.Param? = Param.Companion.get(param)
        if (p != null) return SimpleEntry<Boolean, ohi.andre.consolelauncher.commands.main.Param?>(
            false,
            p
        )

        val firstWord = param.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
        val webhookName =
            if (firstWord.startsWith(Tuils.MINUS)) firstWord.substring(1) else firstWord
        val w = pack.webhookManager.getWebhook(webhookName)
        if (w != null) {
            return SimpleEntry<Boolean, ohi.andre.consolelauncher.commands.main.Param?>(
                false,
                WebhookParam(w)
            )
        }

        return super.getParam(pack, param)
    }

    override fun paramForString(
        pack: MainPack,
        param: String
    ): ohi.andre.consolelauncher.commands.main.Param? {
        packRef = WeakReference<MainPack>(pack)
        return Param.Companion.get(param)
    }

    public override fun params(): Array<String?> {
        val labels: Array<String?> = Param.Companion.labels()
        val pack: MainPack? = if (packRef != null) packRef!!.get() else null
        if (pack == null || pack.webhookManager == null) return labels

        val hooks = pack.webhookManager.getWebhooks()
        if (hooks == null || hooks.isEmpty()) return labels

        val all = arrayOfNulls<String>(labels.size + hooks.size)
        System.arraycopy(labels, 0, all, 0, labels.size)
        for (i in hooks.indices) {
            all[labels.size + i] = hooks.get(i).name
        }
        return all
    }

    override fun doThings(pack: ExecutePack): String? {
        val info = pack as MainPack
        packRef = WeakReference<MainPack>(info)
        val input = info.lastCommand
        if (input == null) return null

        val split = Tuils.splitArgs(input)

        if (split.size < 2) {
            val hooks = info.webhookManager.getWebhooks()
            val sb = StringBuilder()
            sb.append(info.context.getString(helpRes())).append(Tuils.NEWLINE)
            if (hooks != null && !hooks.isEmpty()) {
                sb.append(Tuils.NEWLINE).append("Configured Webhooks:").append(Tuils.NEWLINE)
                for (w in hooks) sb.append("  • ").append(w.name).append(Tuils.NEWLINE)
            }
            return sb.toString().trim { it <= ' ' }
        }

        val sub = split.get(1) ?: return null
        if (sub.startsWith("-")) return null

        var w = info.webhookManager.getWebhook(sub)
        var webhookArgs = arrayOfNulls<String>(0)

        if (w == null && sub.contains(" ")) {
            val subSplit = Tuils.splitArgs(sub)
            if (!subSplit.isEmpty()) {
                w = info.webhookManager.getWebhook(subSplit.get(0))
                if (w != null) {
                    webhookArgs = subSplit.subList(1, subSplit.size).toTypedArray<String?>()
                }
            }
        } else if (w != null) {
            if (split.size > 2) {
                webhookArgs = split.subList(2, split.size).toTypedArray<String?>()
            }
        }

        if (w != null) {
            return triggerWebhook(info, w, webhookArgs)
        }

        return "Webhook [" + sub + "] not found. Use 'webhook -ls' to see available hooks."
    }

    override fun priority(): Int {
        return 3
    }

    override fun helpRes(): Int {
        return R.string.help_webhook
    }

    companion object {
        private val JSON: MediaType = "application/json; charset=utf-8".toMediaType()
        private val TEXT: MediaType = "text/plain; charset=utf-8".toMediaType()

        // Using WeakReference to allow suggestions while avoiding memory leaks
        private var packRef: WeakReference<MainPack>? = null

        private fun triggerWebhook(
            info: MainPack,
            w: Webhook,
            webhookArgs: Array<String?>
        ): String {
            val template = w.bodyTemplate
            val jsonBody = template != null
                    && (template.trim { it <= ' ' }
                .startsWith("{") || template.trim { it <= ' ' }.startsWith("["))

            val bodyContent: String
            try {
                bodyContent = w.render(webhookArgs, jsonBody) ?: Tuils.EMPTYSTRING
            } catch (e: JSONException) {
                return "Webhook [" + w.name + "] template error: " + e.message
            }

            if (webhookArgs.size > 0) {
                info.historyManager.add(w.name, formatArgsForHistory(webhookArgs))
            }

            val mediaType: MediaType = if (jsonBody) JSON else TEXT
            val body: RequestBody = bodyContent.toRequestBody(mediaType)
            val request = Request.Builder().url(w.url ?: Tuils.EMPTYSTRING).post(body).build()
            val handler = Handler(Looper.getMainLooper())

            info.client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val error = e.toString()
                    handler.post(Runnable {
                        Tuils.sendOutput(
                            info.context,
                            "Webhook [" + w.name + "] Error: " + error
                        )
                    })
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        val resBody = if (r.body != null) r.body!!.string() else "Empty Response"
                        val code = r.code
                        handler.post(Runnable {
                            Tuils.sendOutput(
                                info.context,
                                "Webhook [" + w.name + "] Response [" + code + "]: " + resBody
                            )
                        })
                    }
                }
            })
            return "Triggering webhook: " + w.name
        }

        private fun formatArgsForHistory(webhookArgs: Array<String?>): String {
            val formatted: MutableList<String?> = ArrayList<String?>(webhookArgs.size)
            for (arg in webhookArgs) {
                formatted.add(quoteArg(arg))
            }
            return Tuils.toPlanString(formatted, Tuils.SPACE)
        }

        private fun quoteArg(arg: String?): String {
            if (arg == null) {
                return "\"\""
            }

            var needsQuotes = arg.length == 0
            var i = 0
            while (i < arg.length && !needsQuotes) {
                val c = arg.get(i)
                if (Character.isWhitespace(c) || c == '"' || c == '\\') {
                    needsQuotes = true
                }
                i++
            }

            if (!needsQuotes) {
                return arg
            }

            val escaped = arg.replace("\\", "\\\\").replace("\"", "\\\"")
            return "\"" + escaped + "\""
        }
    }
}
