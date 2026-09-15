package ohi.andre.consolelauncher.commands.main.raw

import android.os.Handler
import android.os.Looper
import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.tuils.Tuils
import okhttp3.RequestBody

class post : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val info = pack as MainPack
        val args = pack.args!!

        if (args.size < 2) {
            return onNotArgEnough(pack, args.size)!!
        }

        val url = args[0] as String
        val bodyContent = args[1] as String

        val mediaType = if (bodyContent.trim().startsWith("{") || bodyContent.trim().startsWith("[")) JSON else TEXT
        val body = bodyContent.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val handler = Handler(Looper.getMainLooper())

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val error = e.toString()
                handler.post { Tuils.sendOutput(info.context, pack.context.getString(R.string.command_post_post_error_91b4e, error)) }
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    val resBody = r.body?.string() ?: pack.context.getString(R.string.command_post_empty_response_a5c48)
                    val code = r.code
                    handler.post { Tuils.sendOutput(info.context, pack.context.getString(R.string.command_post_post_591a3, code, resBody)) }
                }
            }
        })

        return pack.context.getString(R.string.command_post_sending_post_request_2dba0)
    }

    override fun helpRes(): Int = R.string.help_post

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.NO_SPACE_STRING, CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 2

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String {
        val info = pack as MainPack
        return info.res.getString(helpRes())
    }

    override fun onArgNotFound(pack: ExecutePack, index: Int): String? = null

    companion object {
        private val client = OkHttpClient()
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val TEXT = "text/plain; charset=utf-8".toMediaType()
    }
}
