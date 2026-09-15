package ohi.andre.consolelauncher.managers

import android.content.Context
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import java.util.regex.Pattern
import okhttp3.OkHttpClient
import okhttp3.Request
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.Tuils
import android.content.SharedPreferences
import okhttp3.Response

object ChangelogManager {
    private const val PREFS_NAME = "changelogPrefs"

    @JvmStatic
    fun printLog(context: Context, client: OkHttpClient) {
        printLog(context, client, false)
    }

    @JvmStatic
    fun printLog(context: Context, client: OkHttpClient, force: Boolean) {
        val originalUrl = "https://pastebin.com/n5AYHd26"
        val changelogUrl = if (!originalUrl.contains("raw")) {
            originalUrl.replace(".com/", ".com/raw/")
        } else {
            originalUrl
        }

        val preferences = context.getSharedPreferences(PREFS_NAME, 0)
        val maxLength = 200

        if (force || !preferences.getBoolean(changelogUrl, false)) {
            Thread {
                if (!Tuils.hasInternetAccess()) {
                    if (force) {
                        Tuils.sendOutput(context, R.string.no_internet)
                    }
                    return@Thread
                }

                val newlinePattern = Pattern.compile("^-", Pattern.MULTILINE)

                try {
                    val builder = Request.Builder()
                        .url(changelogUrl)
                        .get()

                    client.newCall(builder.build()).execute().use { response ->
                        if (!response.isSuccessful || response.code == 304) {
                            if (force) {
                                Tuils.sendOutput(context, "${R.string.internet_error}${Tuils.SPACE}${response.code}")
                            }
                            return@Thread
                        }

                        val header = context.getString(R.string.manager_changelogmanager_changelog_2b4a2, BuildConfig.VERSION_NAME)
                        var log = response.body?.string() ?: Tuils.EMPTYSTRING
                        log = newlinePattern.matcher(log).replaceAll(Tuils.DOUBLE_SPACE + "-")

                        val cut = !force && log.length >= maxLength
                        if (cut) {
                            log = log.substring(0, maxLength) + "..."
                        }

                        Tuils.sendOutput(context, header + Tuils.NEWLINE + log)

                        if (cut) {
                            val sp = SpannableString(context.getString(R.string.manager_changelogmanager_click_here_to_see_the_full_changelog_897bf))
                            sp.setSpan(
                                ForegroundColorSpan(XMLPrefsManager.getColor(Theme.output_text_color)),
                                0,
                                sp.length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            sp.setSpan(
                                ForegroundColorSpan(XMLPrefsManager.getColor(Theme.link_text_color)),
                                6,
                                10,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            Tuils.sendOutput(context, sp, TerminalManager.CATEGORY_NO_COLOR, Uri.parse(changelogUrl))
                        }
                    }

                    preferences.edit().putBoolean(changelogUrl, true).apply()
                } catch (e: Exception) {
                    Tuils.sendOutput(context, e.toString())
                    Tuils.log(e)
                }
            }.start()
        }
    }
}
