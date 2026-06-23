package ohi.andre.consolelauncher.managers

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Handler
import android.text.TextUtils
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.MainManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.IdValue
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsList
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.Tuils
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.parser.Parser
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.SAXParseException
import java.io.BufferedInputStream
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import java.util.ArrayList
import java.util.Locale
import java.util.regex.Matcher
import javax.xml.parsers.DocumentBuilder
import okhttp3.Response
import okhttp3.ResponseBody
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.VALUE_ATTRIBUTE
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.set
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.writeTo

/**
 * Created by francescoandreuzzi on 01/10/2017.
 */
class RssManager(context: Context, client: OkHttpClient) : XMLPrefsElement {
    private val RSS_CHECK_DELAY = 5000

    private val RSS_FOLDER = "rss"

    private val PUBDATE_CHILD = "pubDate"
    private val ENTRY_CHILD = "item"
    private val LINK_CHILD = "link"
    private val HREF_ATTRIBUTE = "href"

    private val defaultRSSDateFormat = SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US)

    override fun delete(): Array<String?>? {
        return null
    }

    override fun getValues(): XMLPrefsList {
        return values
    }

    override fun write(save: XMLPrefsSave, value: String) {
        XMLPrefsManager.set(
            File(Tuils.getFolder(), PATH),
            save.label(),
            arrayOf<String>(XMLPrefsManager.VALUE_ATTRIBUTE),
            arrayOf<String?>(value)
        )
        values.add(save.label()!!, value)
    }

    override fun path(): String {
        return PATH
    }

    private var defaultColor = 0
    private var downloadMessageColor = 0
    private var defaultFormat: String? = null
    private var timeFormat: String? = null
    private var downloadFormat: String? = null

    private var includeRssDefault = false
    private var showDownloadMessage = false
    private var click = false

    private val context: Context
    private val handler: Handler?

    private val root: File
    private val rssIndexFile: File

    private var feeds: MutableList<Rss>? = null
    private var formats: MutableList<IdValue>? = null
    private var cmdRegexes: MutableList<CmdableRegex>? = null

    private val client: OkHttpClient

    //    those will obscure the tag and its content
    private var hideTagPatterns: Array<Pattern?>? = null

    private var urlPattern: Pattern? = null
    private var idPattern: Pattern? = null
    private var bPattern: Pattern? = null
    private var kbPattern: Pattern? = null
    private var mbPattern: Pattern? = null
    private var gbPattern: Pattern? = null

    private val connectivityManager: ConnectivityManager

    fun refresh() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null)
        }

        if (feeds != null) feeds!!.clear()
        else feeds = ArrayList<Rss>()

        if (formats != null) formats!!.clear()
        else formats = ArrayList<IdValue>()

        if (cmdRegexes != null) cmdRegexes!!.clear()
        else cmdRegexes = ArrayList<CmdableRegex>()

        object : StoppableThread() {
            override fun run() {
                super.run()

                val o: Array<Any?>?
                try {
                    o = XMLPrefsManager.buildDocument(rssIndexFile, NAME)
                    if (o == null) {
                        Tuils.sendXMLParseError(context, PATH)
                        return
                    }
                } catch (e: SAXParseException) {
                    Tuils.sendXMLParseError(context, PATH, e)
                    return
                } catch (e: Exception) {
                    Tuils.log(e)
                    return
                }

                try {
                    val document = o[0] as Document
                    val rootElement = o[1] as Element

                    val nodes = rootElement.getElementsByTagName("*")

                    val enums: MutableList<ohi.andre.consolelauncher.managers.xml.options.Rss> =
                        ArrayList<ohi.andre.consolelauncher.managers.xml.options.Rss>(
                            Arrays.asList<ohi.andre.consolelauncher.managers.xml.options.Rss>(*ohi.andre.consolelauncher.managers.xml.options.Rss.values())
                        )

                    val deleted: Array<String?>? = instance!!.delete()?.map { it }?.toTypedArray()
                    var needToWrite = false

                    for (count in 0..<nodes.getLength()) {
                        val node = nodes.item(count)

                        val nn = node.getNodeName()
                        if (Tuils.find(nn, enums as MutableList<*>) != -1) {
//                              is an enum value
                            values.add(
                                nn,
                                node.getAttributes().getNamedItem(XMLPrefsManager.VALUE_ATTRIBUTE)
                                    .getNodeValue()
                            )

                            for (en in enums.indices) {
                                if (enums.get(en).label() == nn) {
                                    enums.removeAt(en)
                                    break
                                }
                            }
                        } else {
//                             deleted
                            val index = if (deleted == null) -1 else Tuils.find(nn, deleted)
                            if (index != -1) {
                                deleted!![index] = null
                                val e = node as Element
                                rootElement.removeChild(e)

                                needToWrite = true
                            } else {
                                val name = node.getNodeName()
                                if (name == RSS_LABEL) {
                                    val t = node as Element
                                    feeds!!.add(Rss.Companion.fromElement(t)!!)
                                } else if (name == FORMAT_LABEL) {
                                    val e = node as Element

                                    var id: Int
                                    try {
                                        id = e.getAttribute(ID_ATTRIBUTE).toInt()
                                    } catch (exc: Exception) {
                                        id = -1
                                    }

                                    if (id == -1) continue

                                    val format = XMLPrefsManager.getStringAttribute(
                                        e,
                                        XMLPrefsManager.VALUE_ATTRIBUTE
                                    )

                                    val i = IdValue(format, id)
                                    formats!!.add(i)
                                } else if (name == REGEX_CMD_LABEL) {
                                    val e = node as Element

                                    val id: Int
                                    try {
                                        id = e.getAttribute(ID_ATTRIBUTE).toInt()
                                    } catch (exc: Exception) {
                                        continue
                                    }

                                    val regex = XMLPrefsManager.getStringAttribute(
                                        e,
                                        XMLPrefsManager.VALUE_ATTRIBUTE
                                    )
                                    if (regex == null || regex.length == 0) continue

                                    val on = XMLPrefsManager.getStringAttribute(e, ON_ATTRIBUTE)
                                    if (on == null || on.length == 0) continue

                                    val cmd = XMLPrefsManager.getStringAttribute(e, CMD_ATTRIBUTE)
                                    if (cmd == null || cmd.length == 0) continue

                                    cmdRegexes!!.add(CmdableRegex(id, on, regex, cmd))
                                }
                            }
                        }
                    }

                    if (enums.size > 0) {
                        for (s in enums) {
                            val value: String? = s.defaultValue()

                            val em = document.createElement(s.label())
                            em.setAttribute(XMLPrefsManager.VALUE_ATTRIBUTE, value)
                            rootElement.appendChild(em)

                            values.add(s.label(), value!!)
                        }

                        XMLPrefsManager.writeTo(document, rssIndexFile)
                    } else if (needToWrite) {
                        XMLPrefsManager.writeTo(document, rssIndexFile)
                    }
                } catch (e: Exception) {
                    Tuils.log(e)
                    Tuils.toFile(e)
                }

                click =
                    XMLPrefsManager.getBoolean(ohi.andre.consolelauncher.managers.xml.options.Rss.click_rss)
                //                longClick = XMLPrefsManager.getBoolean(ohi.andre.consolelauncher.managers.xml.options.Rss.long_click_rss);
                defaultFormat =
                    XMLPrefsManager.get(ohi.andre.consolelauncher.managers.xml.options.Rss.rss_default_format)
                defaultColor =
                    XMLPrefsManager.getColor(ohi.andre.consolelauncher.managers.xml.options.Rss.rss_item_text_color)
                includeRssDefault =
                    XMLPrefsManager.getBoolean(ohi.andre.consolelauncher.managers.xml.options.Rss.include_rss_default)
                timeFormat =
                    XMLPrefsManager.get(ohi.andre.consolelauncher.managers.xml.options.Rss.rss_time_format)
                showDownloadMessage =
                    XMLPrefsManager.getBoolean(ohi.andre.consolelauncher.managers.xml.options.Rss.show_rss_download)
                if (showDownloadMessage) {
                    downloadFormat =
                        XMLPrefsManager.get(ohi.andre.consolelauncher.managers.xml.options.Rss.rss_download_format)

                    val size = "%s"

                    idPattern = Pattern.compile("%id", Pattern.CASE_INSENSITIVE)
                    urlPattern = Pattern.compile("%url", Pattern.CASE_INSENSITIVE)
                    gbPattern = Pattern.compile(size + "gb", Pattern.CASE_INSENSITIVE)
                    mbPattern = Pattern.compile(size + "mb", Pattern.CASE_INSENSITIVE)
                    kbPattern = Pattern.compile(size + "kb", Pattern.CASE_INSENSITIVE)
                    bPattern = Pattern.compile(size + "b", Pattern.CASE_INSENSITIVE)

                    downloadMessageColor =
                        XMLPrefsManager.getColor(ohi.andre.consolelauncher.managers.xml.options.Rss.rss_download_message_text_color)
                }

                val hiddenTags =
                    XMLPrefsManager.get(ohi.andre.consolelauncher.managers.xml.options.Rss.rss_hidden_tags)
                        .replace(Tuils.SPACE.toRegex(), Tuils.EMPTYSTRING)
                var split: Array<String?>? = null
                for (c in 0..<hiddenTags.length) {
                    val ch = hiddenTags.get(c)
                    if (Character.isLetter(ch)) continue
                    split = hiddenTags.split((ch.toString() + Tuils.EMPTYSTRING).toRegex())
                        .dropLastWhile { it.isEmpty() }.toTypedArray()
                }
                if (split == null) {
                    split = arrayOf<String?>(hiddenTags)
                }

                hideTagPatterns = arrayOfNulls<Pattern>(split.size * 2)
                var c = 0
                var j = 0
                while (c < split.size) {
                    hideTagPatterns!![j] =
                        Pattern.compile("<" + split[c] + "[^>]*>[^<]*<\\/" + split[c] + ">")
                    hideTagPatterns!![j + 1] = Pattern.compile("<" + split[c] + "[^>]*\\/>")
                    c++
                    j += 2
                }

                for (rss in feeds!!) {
                    rss.updateFormat(formats!!)
                    rss.updateIncludeIfMatches()
                    rss.updateExcludeIfMatches()

                    if (rss.color == Int.Companion.MAX_VALUE) rss.color = defaultColor
                }

                for (rg in cmdRegexes!!) {
                    try {
                        val id = rg.literalPattern!!.toInt()
                        rg.regex = RegexManager.instance!!.get(id)!!.regex
                    } catch (exc: Exception) {
                        try {
                            rg.regex = Pattern.compile(rg.literalPattern)
                        } catch (e: Exception) {
                            Tuils.sendOutput(
                                Color.RED,
                                context,
                                context.getString(R.string.invalid_regex) + Tuils.SPACE + rg.literalPattern
                            )
                            rg.regex = null
                        }
                    }
                }

                handler!!.post(updateRunnable)
            }
        }.start()
    }

    fun dispose() {
        if (handler != null) handler.removeCallbacksAndMessages(null)
    }

    fun add(id: Int, timeInSeconds: Long, url: String): String? {
        val output = XMLPrefsManager.add(
            rssIndexFile,
            RSS_LABEL,
            arrayOf<String?>(ID_ATTRIBUTE, TIME_ATTRIBUTE, SHOW_ATTRIBUTE, URL_ATTRIBUTE),
            arrayOf<String?>(id.toString(), timeInSeconds.toString(), true.toString(), url)
        )

        if (output == null) {
            try {
                val r = Rss(url, timeInSeconds, id, true)

                r.lastShownItem = System.currentTimeMillis()
                r.format = defaultFormat

                updateRss(r, true)
                feeds!!.add(r)
            } catch (e: Exception) {
                Tuils.log(e)
                return e.toString()
            }

            return null
        } else return output
    }

    fun addFormat(id: Int, value: String?): String? {
        val output = XMLPrefsManager.add(
            rssIndexFile,
            FORMAT_LABEL,
            arrayOf<String?>(ID_ATTRIBUTE, XMLPrefsManager.VALUE_ATTRIBUTE),
            arrayOf<String?>(id.toString(), value)
        )
        if (output == null) {
            formats!!.add(IdValue(value, id))
            return null
        } else return output
    }

    fun removeFormat(id: Int): String? {
        val output = XMLPrefsManager.removeNode(
            rssIndexFile, FORMAT_LABEL, arrayOf<String?>(
                ID_ATTRIBUTE
            ), arrayOf<String>(id.toString())
        )

        if (output == null) {
            return null
        } else {
            if (output.length > 0) return output
            return context.getString(R.string.id_notfound)
        }
    }

    fun rm(id: Int): String? {
        val output = XMLPrefsManager.removeNode(
            rssIndexFile, RSS_LABEL, arrayOf<String?>(
                ID_ATTRIBUTE
            ), arrayOf<String>(id.toString())
        )
        if (output == null) {
            val rss = File(root, RSS_LABEL + id + ".xml")
            if (rss.exists()) rss.delete()

            removeId(id)
            handler!!.sendEmptyMessage(id)

            return null
        } else {
            if (output.length > 0) return output
            else return context.getString(R.string.rss_not_found)
        }
    }

    fun list(): String {
        if (feeds == null) return "[]"
        val builder = StringBuilder()
        for (r in feeds) {
            if (r == null) continue
            builder.append(Rss.Companion.ID_LABEL).append(":").append(Tuils.SPACE).append(r.id)
                .append(Tuils.NEWLINE).append(r.url).append(Tuils.NEWLINE)
        }

        val output = builder.toString().trim { it <= ' ' }
        if (output.length == 0) return "[]"
        return output
    }

    fun buildModuleText(): String {
        val snapshot = ArrayList<Rss>()
        if (feeds != null) {
            for (feed in feeds) {
                if (feed != null) snapshot.add(feed)
            }
        }

        if (snapshot.size == 0) {
            return ("No RSS feeds."
                    + "\nAdd: rss -add 1 900 https://www.reddit.com/r/android/.rss"
                    + "\nThen: rss -frc 1")
        }

        val out = StringBuilder()
        var shown = 0
        for (feed in snapshot) {
            if (shown >= 3) break

            if (out.length > 0) out.append('\n')
            out.append('[').append(feed.id).append("] ").append(feedLabel(feed)).append('\n')
            if (feed.lastCheckedClient > 0) {
                out.append("checked ").append(formatModuleTime(feed.lastCheckedClient))
                if (feed.wifiOnly) out.append(" (wifi only)")
                out.append('\n')
            }

            val items = latestItemTitles(feed, 3)
            if (items.size == 0) {
                out.append("  no cached items; run rss -frc ").append(feed.id).append('\n')
            } else {
                for (item in items) {
                    out.append("  - ").append(item).append('\n')
                }
            }
            shown++
        }

        val remaining = snapshot.size - shown
        if (remaining > 0) {
            out.append("... ").append(remaining).append(" more feed")
            if (remaining != 1) out.append('s')
            out.append('\n')
        }
        out.append("Commands: rss -l [id], rss -frc [id], rss -add")
        return out.toString().trim { it <= ' ' }
    }

    fun l(id: Int): String? {
        if (feeds == null) return context.getString(R.string.rss_not_found)
        for (feed in feeds) {
            if (feed == null) continue
            if (feed.id == id) {
                try {
                    parse(feed, false)
                    return null
                } catch (e: Exception) {
                    Tuils.log(e)
                    Tuils.toFile(e)
                    return e.toString()
                }
            }
        }

        return context.getString(R.string.rss_not_found)
    }

    fun setShow(id: Int, show: Boolean): String? {
        val output = XMLPrefsManager.set(
            rssIndexFile,
            RSS_LABEL,
            arrayOf<String?>(ID_ATTRIBUTE),
            arrayOf<String>(id.toString()),
            arrayOf<String?>(
                SHOW_ATTRIBUTE
            ),
            arrayOf<String>(show.toString()),
            false
        )
        if (output == null) {
            val r = findId(id)
            if (r != null) r.show = show
            return null
        } else {
            if (output.length > 0) return output
            return context.getString(R.string.rss_not_found)
        }
    }

    fun setTime(id: Int, timeSeconds: Long): String? {
        val output = XMLPrefsManager.set(
            rssIndexFile,
            RSS_LABEL,
            arrayOf<String?>(ID_ATTRIBUTE),
            arrayOf<String>(id.toString()),
            arrayOf<String?>(
                TIME_ATTRIBUTE
            ),
            arrayOf<String>(timeSeconds.toString()),
            false
        )
        if (output == null) {
            val r = findId(id)
            if (r != null) r.updateTimeSeconds = timeSeconds
            return null
        } else {
            if (output.length > 0) return output
            return context.getString(R.string.rss_not_found)
        }
    }

    fun setTimeFormat(id: Int, format: String?): String? {
        val output = XMLPrefsManager.set(
            rssIndexFile,
            RSS_LABEL,
            arrayOf<String?>(ID_ATTRIBUTE),
            arrayOf<String>(id.toString()),
            arrayOf<String?>(
                TIME_FORMAT_ATTRIBUTE
            ),
            arrayOf<String?>(format),
            false
        )
        if (output == null) {
            val r = findId(id)
            if (r != null) r.timeFormat = SimpleDateFormat(format, Locale.getDefault())
            return null
        } else {
            if (output.length > 0) return output
            return context.getString(R.string.rss_not_found)
        }
    }

    fun setFormat(id: Int, format: String?): String? {
        val output = XMLPrefsManager.set(
            rssIndexFile,
            RSS_LABEL,
            arrayOf<String?>(ID_ATTRIBUTE),
            arrayOf<String>(id.toString()),
            arrayOf<String?>(
                FORMAT_ATTRIBUTE
            ),
            arrayOf<String?>(format),
            false
        )
        if (output == null) {
            val r = findId(id)
            if (r != null) r.setFormat(formats!!, format)
            return null
        } else {
            if (output.length > 0) return output
            return context.getString(R.string.rss_not_found)
        }
    }

    fun setColor(id: Int, color: String?): String? {
        val output = XMLPrefsManager.set(
            rssIndexFile,
            RSS_LABEL,
            arrayOf<String?>(ID_ATTRIBUTE),
            arrayOf<String>(id.toString()),
            arrayOf<String?>(
                COLOR_ATTRIBUTE
            ),
            arrayOf<String?>(color),
            false
        )
        if (output == null) {
            val r = findId(id)
            if (r != null) r.color = Color.parseColor(color)
            return null
        } else {
            if (output.length > 0) return output
            return context.getString(R.string.rss_not_found)
        }
    }

    fun setDateTag(id: Int, tag: String?): String? {
        val output = XMLPrefsManager.set(
            rssIndexFile,
            RSS_LABEL,
            arrayOf<String?>(ID_ATTRIBUTE),
            arrayOf<String>(id.toString()),
            arrayOf<String?>(
                DATE_TAG_ATTRIBUTE
            ),
            arrayOf<String?>(tag),
            false
        )
        if (output == null) {
            val r = findId(id)
            if (r != null) r.dateTag = tag
            return null
        } else {
            if (output.length > 0) return output
            return context.getString(R.string.rss_not_found)
        }
    }

    fun setEntryTag(id: Int, tag: String?): String? {
        val output = XMLPrefsManager.set(
            rssIndexFile,
            RSS_LABEL,
            arrayOf<String?>(ID_ATTRIBUTE),
            arrayOf<String>(id.toString()),
            arrayOf<String?>(
                ENTRY_TAG_ATTRIBUTE
            ),
            arrayOf<String?>(tag),
            false
        )
        if (output == null) {
            val r = findId(id)
            if (r != null) r.entryTag = tag
            return null
        } else {
            if (output.length > 0) return output
            return context.getString(R.string.rss_not_found)
        }
    }

    fun setIncludeIfMatches(id: Int, regex: String): String? {
        val output = XMLPrefsManager.set(
            rssIndexFile,
            RSS_LABEL,
            arrayOf<String?>(ID_ATTRIBUTE),
            arrayOf<String>(id.toString()),
            arrayOf<String?>(
                INCLUDE_ATTRIBUTE
            ),
            arrayOf<String?>(regex),
            false
        )
        if (output == null) {
            val r = findId(id)
            if (r != null) r.setIncludeIfMatches(regex)
            return null
        } else {
            if (output.length > 0) return output
            return context.getString(R.string.rss_not_found)
        }
    }

    fun setExcludeIfMatches(id: Int, regex: String): String? {
        val output = XMLPrefsManager.set(
            rssIndexFile,
            RSS_LABEL,
            arrayOf<String?>(ID_ATTRIBUTE),
            arrayOf<String>(id.toString()),
            arrayOf<String?>(
                EXCLUDE_ATTRIBUTE
            ),
            arrayOf<String?>(regex),
            false
        )
        if (output == null) {
            val r = findId(id)
            if (r != null) r.setExcludeIfMatches(regex)
            return null
        } else {
            if (output.length > 0) return output
            return context.getString(R.string.rss_not_found)
        }
    }

    fun setWifiOnly(id: Int, wifiOnly: Boolean): String? {
        val output = XMLPrefsManager.set(
            rssIndexFile,
            RSS_LABEL,
            arrayOf<String?>(ID_ATTRIBUTE),
            arrayOf<String>(id.toString()),
            arrayOf<String?>(
                WIFIONLY_ATTRIBUTE
            ),
            arrayOf<String>(wifiOnly.toString()),
            false
        )
        if (output == null) {
            val r = findId(id)
            if (r != null) r.wifiOnly = wifiOnly
            return null
        } else {
            if (output.length > 0) return output
            return context.getString(R.string.rss_not_found)
        }
    }

    fun addRegexCommand(id: Int, on: String, regex: String?, cmd: String): String? {
        val output = XMLPrefsManager.add(
            rssIndexFile, REGEX_CMD_LABEL, arrayOf<String?>(
                ID_ATTRIBUTE, ON_ATTRIBUTE, XMLPrefsManager.VALUE_ATTRIBUTE, CMD_ATTRIBUTE
            ),
            arrayOf<String?>(id.toString(), on, regex, cmd)
        )
        if (output == null) {
            cmdRegexes!!.add(CmdableRegex(id, on, regex, cmd))
            return null
        } else {
            if (output.length > 0) return output
            return context.getString(R.string.output_error)
        }
    }

    fun rmRegexCommand(id: Int): String? {
        val output = XMLPrefsManager.removeNode(
            rssIndexFile, REGEX_CMD_LABEL, arrayOf<String?>(
                ID_ATTRIBUTE
            ), arrayOf<String>(id.toString())
        )
        if (output == null) {
            for (i in cmdRegexes!!.indices) {
                if (cmdRegexes!!.get(i).id == id) cmdRegexes!!.removeAt(i)
            }

            return null
        } else {
            if (output.length > 0) return output
            return context.getString(R.string.id_notfound)
        }
    }

    //    base methods
    private val updateRunnable: Runnable = object : Runnable {
        override fun run() {
            for (feed in feeds!!) {
                if (feed.needUpdate()) updateRss(feed, false)
            }

            handler!!.postDelayed(this, RSS_CHECK_DELAY.toLong())
        }
    }

    fun updateRss(feed: Int, firstTime: Boolean, force: Boolean): Boolean {
        val rss = findId(feed)
        if (rss == null) return false

        updateRss(rss, firstTime, force)
        return true
    }

    private fun updateRss(feed: Rss, firstTime: Boolean, force: Boolean = false) {
        if (!force && feed.wifiOnly && !connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)!!
                .isConnected()
        ) {
            feed.lastCheckedClient = System.currentTimeMillis()
            feed.updateFile(rssIndexFile)

            return
        }

        object : StoppableThread() {
            override fun run() {
                super.run()

                if (!Tuils.hasInternetAccess()) {
                    if (force) Tuils.sendOutput(Color.RED, context, R.string.no_internet)
                    return
                }

                try {
                    val builder = Request.Builder()
                        .url(feed.url!!)
                        .get()

                    client.newCall(builder.build()).execute().use { response ->
                        if (response.isSuccessful && (firstTime || response.code != 304)) {
                            val body = response.body

                            var bytes: Long = 0
                            if (body != null) bytes = Tuils.download(
                                BufferedInputStream(body.byteStream()),
                                File(root, RSS_LABEL + feed.id + ".xml")
                            )

                            if (showDownloadMessage) {
                                var c: CharSequence =
                                    Tuils.span(downloadFormat, downloadMessageColor) ?: Tuils.EMPTYSTRING

                                var kb = bytes.toDouble() / 1024.0
                                var mb = kb / 1024.0
                                var gb = mb / 1024.0

                                kb = Tuils.round(kb, 2)
                                mb = Tuils.round(mb, 2)
                                gb = Tuils.round(gb, 2)

                                c = urlPattern!!.matcher(c).replaceAll(feed.url)
                                c = idPattern!!.matcher(c).replaceAll(feed.id.toString())
                                c = gbPattern!!.matcher(c).replaceAll(gb.toString())
                                c = mbPattern!!.matcher(c).replaceAll(mb.toString())
                                c = kbPattern!!.matcher(c).replaceAll(kb.toString())
                                c = bPattern!!.matcher(c).replaceAll(bytes.toString())

                                c = TimeManager.instance!!.replace(c)

                                Tuils.sendOutput(downloadMessageColor, context, c)
                            }

                            if (bytes == 0L) {
                                Tuils.sendOutput(
                                    Color.RED,
                                    context,
                                    context.getString(R.string.rss_invalid_empty) + Tuils.SPACE + feed.id
                                )
                                return
                            }

                            //                        feed.lMod = response.header(LAST_MODIFIED_FIELD);
//                        feed.etag = response.header(ETAG_FIELD);
//                        if(feed.etag != null) feed.etag = feed.etag.replaceAll("\"", Tuils.EMPTYSTRING);
                            if (feed.show) parse(feed, true)
                        } else {
//                        not modified
                        }
                    }
                    feed.lastCheckedClient = System.currentTimeMillis()
                    feed.updateFile(rssIndexFile)
                    notifyRssModuleUpdated()
                } catch (e: Exception) {
                    Tuils.log(e)
                    Tuils.toFile(e)
                }
            }
        }.start()
    }

    @Throws(Exception::class)
    private fun parse(feed: Rss, time: Boolean): Boolean {
        var updated = false

        val rssFile = File(root, RSS_LABEL + feed.id + ".xml")
        if (!rssFile.exists()) return false

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()

        val doc: Document
        try {
            doc = dBuilder.parse(rssFile)
        } catch (e: SAXParseException) {
            Tuils.sendXMLParseError(context, PATH, e)
            return false
        }

        doc.getDocumentElement().normalize()

        var greatestTime: Long = -1
        var foundOneDateAtLeast = false

        val entryTag = (if (feed.entryTag != null) feed.entryTag else ENTRY_CHILD)!!
        val dateTag = (if (feed.dateTag != null) feed.dateTag else PUBDATE_CHILD)!!

        val list = doc.getElementsByTagName(entryTag)
        if (list.getLength() == 0) {
            Tuils.sendOutput(
                Color.RED,
                context,
                context.getString(R.string.rss_invalid_entry_tag) + Tuils.SPACE + (entryTag)
            )
            return false
        }

        for (c in list.getLength() downTo 0) {
            val element = list.item(c) as Element?
            if (element == null) {
                continue
            }

            if (time) {
                val l = element.getElementsByTagName(dateTag)
                if (l.getLength() == 0) continue

                foundOneDateAtLeast = true

                val date = l.item(0).getTextContent()

                val d: Date?
                try {
                    d =
                        if (feed.timeFormat != null) feed.timeFormat!!.parse(date) else defaultRSSDateFormat.parse(
                            date
                        )
                } catch (e: Exception) {
                    Tuils.sendOutput(
                        Color.RED,
                        context,
                        rssFile.getName() + ": " + context.getString(R.string.rss_invalid_timeformat)
                    )
                    return false
                }

                val timeLong = d.getTime()
                greatestTime = max(greatestTime, timeLong)

                if (feed.lastShownItem < timeLong) {
                    updated = true
                    showItem(feed, element, false)
                }
            } else {
//                user - requested
                updated = true
                showItem(feed, element, true)
            }
        }

        if (time && !foundOneDateAtLeast) {
            Tuils.sendOutput(
                Color.RED,
                context,
                context.getString(R.string.rss_invalid_date) + Tuils.SPACE + (dateTag)
            )
        } else if (greatestTime != -1L) {
            feed.lastShownItem = greatestTime
            feed.updateLastShownItem(rssIndexFile)
        }

        return updated
    }

    private val formatPattern: Pattern =
        Pattern.compile("%(?:\\[(\\d+)\\])?(?:\\[([^]]+)\\])?([a-zA-Z]+)")
    private val removeTags: Pattern = Pattern.compile("<[^>]+>")
    private val THREE_DOTS = "..."

    private val OPEN_URL = "search -u "
    private val PERCENTAGE = "%"

    init {
        instance = this
        this.context = context

        connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        root = File(Tuils.getFolder(), RSS_FOLDER)
        rssIndexFile = File(Tuils.getFolder(), PATH)

        this.client = client

        prepare()

        values = XMLPrefsList()

        handler = Handler()
        refresh()
    }

    //    called when a new element is detected, it could be triggered many times again in some milliseconds
    private fun showItem(feed: Rss, item: Element?, userRequested: Boolean) {
        if (item == null) return

        var cp = (if (feed.format != null) feed.format else defaultFormat)!!
        cp = Tuils.patternNewline.matcher(cp).replaceAll(Tuils.NEWLINE)

        var s: CharSequence = Tuils.span(cp, feed.color) ?: Tuils.EMPTYSTRING

        val dateTag = (if (feed.dateTag == null) PUBDATE_CHILD else feed.dateTag)!!

        val m = formatPattern.matcher(cp)
        while (m.find()) {
            if (m.groupCount() == 3) {
                val length = m.group(1)
                val color = m.group(2)
                val tag = m.group(3)

                var value: String?
                var cl = feed.color

                val ls = item.getElementsByTagName(tag)
                if (ls.getLength() == 0) value = Tuils.EMPTYSTRING
                else {
                    value = ls.item(0).getTextContent()
                    if (value != null) value = value.trim { it <= ' ' }
                    else value = Tuils.EMPTYSTRING

                    if (tag == dateTag) {
                        val d: Date?
                        try {
                            d =
                                if (feed.timeFormat != null) feed.timeFormat!!.parse(value) else defaultRSSDateFormat.parse(
                                    value
                                )
                        } catch (e: ParseException) {
                            Tuils.log(e)
                            continue
                        }

                        val timeLong = d.getTime()
                        value = TimeManager.instance!!.replace(
                            timeFormat ?: Tuils.EMPTYSTRING,
                            timeLong,
                            Int.Companion.MAX_VALUE
                        ).toString()
                    } else {
                        value = unescapeHtml(value)

                        for (p in hideTagPatterns!!) {
                            if (p != null) {
                                value = p.matcher(value).replaceAll(Tuils.EMPTYSTRING)
                            }
                        }

                        value = removeTags.matcher(value).replaceAll(Tuils.EMPTYSTRING)

                        try {
                            val l = length!!.toInt()
                            value = value.substring(0, l)
                            value = value + THREE_DOTS
                        } catch (e: Exception) {
                        }
                    }

                    try {
                        cl = Color.parseColor(color)
                    } catch (e: Exception) {
                        cl = feed.color
                    }
                }

                val replace: CharSequence?
                if (cl != feed.color && value.length > 0) {
                    replace = Tuils.span(value, cl)
                } else {
                    replace = value
                }

                s = TextUtils.replace(
                    s,
                    arrayOf<String?>(m.group(0)),
                    arrayOf<CharSequence?>(replace)
                )
            }
        }

        if (includeRssDefault) {
            if (feed.excludeIfMatches != null && feed.excludeIfMatches!!.matcher(s.toString())
                    .find()
            ) return
        } else {
            if (feed.includeIfMatches != null && feed.includeIfMatches!!.matcher(s.toString())
                    .find()
            ) return
        }

        if (!userRequested) {
            for (r in cmdRegexes!!) {
                if (r.regex == null || !Tuils.arrayContains(r.on, feed.id)) continue

                var cmd = r.cmd

                val rssMatcher = r.regex!!.matcher(s.toString())
                if (rssMatcher.matches() || rssMatcher.find()) {
                    for (c in 1..<rssMatcher.groupCount() + 1) {
                        cmd = cmd.replace((PERCENTAGE + c).toRegex(), rssMatcher.group(c))
                    }

                    val intent = Intent(MainManager.ACTION_EXEC)
                    intent.putExtra(MainManager.NEED_WRITE_INPUT, false)
                    intent.putExtra(MainManager.CMD, cmd)
                    intent.putExtra(MainManager.CMD_COUNT, MainManager.commandCount)
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                }
            }
        }

        var url: String? = null
        val list = item.getElementsByTagName(LINK_CHILD)
        if (list.getLength() != 0) {
            val n = list.item(0)
            url = n.getTextContent()

            if (n.getNodeType() == Node.ELEMENT_NODE && (url == null || url.length == 0)) {
                url = (n as Element).getAttribute(HREF_ATTRIBUTE)
            }
        }

        val action: String?
        if (url == null || url.length == 0) action = null
        else action = OPEN_URL + url

        Tuils.sendOutput(context, s, TerminalManager.CATEGORY_NO_COLOR, if (click) action else null)
    }

    private fun notifyRssModuleUpdated() {
        val intent = Intent(UIManager.ACTION_MODULE_COMMAND)
        intent.putExtra(UIManager.EXTRA_MODULE_COMMAND, "update")
        intent.putExtra(UIManager.EXTRA_MODULE_NAME, ModuleManager.RSS)
        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(intent)
    }

    private fun feedLabel(feed: Rss): String? {
        try {
            val uri = Uri.parse(feed.url)
            var host = uri.getHost()
            if (!TextUtils.isEmpty(host)) {
                if (host!!.lowercase().startsWith("www.")) {
                    host = host.substring(4)
                }
                return host
            }
        } catch (ignored: Exception) {
        }
        return feed.url
    }

    private fun formatModuleTime(time: Long): String {
        try {
            return TimeManager.instance!!.replace(timeFormat ?: Tuils.EMPTYSTRING, time, Int.Companion.MAX_VALUE)
                .toString()
        } catch (e: Exception) {
            return time.toString()
        }
    }

    private fun latestItemTitles(feed: Rss, max: Int): MutableList<String?> {
        val out = ArrayList<String?>()
        val rssFile = File(root, RSS_LABEL + feed.id + ".xml")
        if (!rssFile.exists()) return out

        try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(rssFile)
            doc.getDocumentElement().normalize()

            val nodes = moduleEntryNodes(doc, feed)
            var count = 0
            while (count < nodes.getLength() && out.size < max) {
                val node = nodes.item(count)
                if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
                    count++
                    continue
                }

                var title = firstText(node as Element, "title")
                if (TextUtils.isEmpty(title)) title = firstText(node, "summary")
                title = cleanModuleText(title)
                if (TextUtils.isEmpty(title)) {
                    count++
                    continue
                }

                var date = firstText(
                    node,
                    if (feed.dateTag != null) feed.dateTag else PUBDATE_CHILD,
                    PUBDATE_CHILD,
                    "updated",
                    "published"
                )
                date = cleanModuleText(date)
                if (!TextUtils.isEmpty(date)) {
                    out.add(shorten(title, 72) + " (" + shorten(date, 24) + ")")
                } else {
                    out.add(shorten(title, 96))
                }
                count++
            }
        } catch (e: Exception) {
            Tuils.log(e)
        }
        return out
    }

    private fun moduleEntryNodes(doc: Document, feed: Rss): NodeList {
        val entryTag = (if (feed.entryTag != null) feed.entryTag else ENTRY_CHILD)!!
        var nodes = doc.getElementsByTagName(entryTag)
        if (nodes.getLength() > 0) return nodes

        nodes = doc.getElementsByTagName(ENTRY_CHILD)
        if (nodes.getLength() > 0) return nodes
        return doc.getElementsByTagName("entry")
    }

    private fun firstText(item: Element, vararg tags: String?): String {
        for (tag in tags) {
            if (TextUtils.isEmpty(tag)) continue
            val nodes = item.getElementsByTagName(tag)
            if (nodes.getLength() == 0) continue

            val node = nodes.item(0)
            if (node == null) continue
            val value = node.getTextContent()
            if (!TextUtils.isEmpty(value)) return value
        }
        return Tuils.EMPTYSTRING
    }

    private fun cleanModuleText(value: String?): String {
        var value = value
        if (value == null) return Tuils.EMPTYSTRING
        value = unescapeHtml(value)
        if (hideTagPatterns != null) {
            for (p in hideTagPatterns) {
                if (p != null) value = p.matcher(value).replaceAll(Tuils.EMPTYSTRING)
            }
        }
        value = removeTags.matcher(value).replaceAll(Tuils.EMPTYSTRING)
        return value.replace("\\s+".toRegex(), Tuils.SPACE).trim { it <= ' ' }
    }

    private fun unescapeHtml(value: String?): String {
        return if (value == null) Tuils.EMPTYSTRING else Parser.unescapeEntities(value, false)
    }

    private fun shorten(value: String?, max: Int): String? {
        if (value == null || value.length <= max) return value
        if (max <= THREE_DOTS.length) return value.substring(0, max)
        return value.substring(0, max - THREE_DOTS.length).trim { it <= ' ' } + THREE_DOTS
    }

    class Rss(
        url: String,
        updateTimeSeconds: Long,
        lastCheckedClient: Long,
        lastShownItem: Long,
        id: Int,
        show: Boolean,
        format: String?,
        includeIfMatches: String?,
        excludeIfMatches: String?,
        color: Int,
        wifiOnly: Boolean,
        timeFormat: String?,
        rootNode: String?,
        timeNode: String?
    ) {
        var url: String? = null
        var updateTimeSeconds: Long = 0
        var lastCheckedClient: Long = 0
        var lastShownItem: Long = 0
        var id: Int = 0
        var show: Boolean = false

        //        public String lMod, etag;
        var entryTag: String? = null
        var dateTag: String? = null

        var format: String? = null

        var includeIfMatches: Pattern? = null
        var excludeIfMatches: Pattern? = null
        var tempInclude: String? = null
        var tempExclude: String? = null

        var color: Int = 0

        var wifiOnly: Boolean = false

        var timeFormat: SimpleDateFormat? = null

        constructor(url: String, updateTimeSeconds: Long, id: Int, show: Boolean) : this(
            url,
            updateTimeSeconds,
            -1,
            -1,
            id,
            show,
            null,
            null,
            null,
            Int.Companion.MAX_VALUE,
            false,
            null,
            null,
            null
        )

        init {
            setAll(
                url,
                updateTimeSeconds,
                lastCheckedClient,
                lastShownItem,
                id,
                show,
                format,
                includeIfMatches,
                excludeIfMatches,
                color,
                wifiOnly,
                timeFormat,
                rootNode,
                timeNode
            )
        }

        private fun setAll(
            url: String,
            updateTimeSeconds: Long,
            lastCheckedClient: Long,
            lastShownItem: Long,
            id: Int,
            show: Boolean,
            format: String?,
            includeIfMatches: String?,
            excludeIfMatches: String?,
            color: Int,
            wifiOnly: Boolean,
            timeFormat: String?,
            rootNode: String?,
            timeNode: String?
        ) {
            this.url = url
            this.updateTimeSeconds = updateTimeSeconds
            this.lastShownItem = lastShownItem
            this.lastCheckedClient = lastCheckedClient
            this.id = id
            this.show = show

            //            this.lMod = lMod;
//            this.etag = etag;
            this.format = format

            this.tempInclude = includeIfMatches
            this.tempExclude = excludeIfMatches

            this.color = color

            this.wifiOnly = wifiOnly

            if (timeFormat == null) this.timeFormat = null
            else try {
                this.timeFormat = SimpleDateFormat(timeFormat, Locale.getDefault())
            } catch (e: Exception) {
                this.timeFormat = null
            }

            this.entryTag = rootNode
            this.dateTag = timeNode
        }

        fun needUpdate(): Boolean {
//            Tuils.log("lc", lastCheckedClient);
//            Tuils.log(System.currentTimeMillis() - lastCheckedClient);
//            Tuils.log("up", updateTimeSeconds * 1000);
            return System.currentTimeMillis() - lastCheckedClient >= (updateTimeSeconds * 1000)
        }

        fun updateLastShownItem(rssFile: File?) {
            XMLPrefsManager.set(
                rssFile,
                RSS_LABEL,
                arrayOf<String?>(ID_ATTRIBUTE),
                arrayOf<String>(id.toString()),
                arrayOf<String?>(
                    LAST_SHOWN_ITEM_ATTRIBUTE
                ),
                arrayOf<String>(lastShownItem.toString()),
                false
            )
        }

        fun updateIncludeIfMatches() {
            if (includeIfMatches != null) {
                try {
                    val id = tempInclude!!.toInt()
                    includeIfMatches = RegexManager.instance!!.get(id)!!.regex
                } catch (exc: Exception) {
                    includeIfMatches = Pattern.compile(tempInclude)
                }
            }
        }

        fun updateExcludeIfMatches() {
            if (excludeIfMatches != null) {
                try {
                    val id = tempExclude!!.toInt()
                    excludeIfMatches = RegexManager.instance!!.get(id)!!.regex
                } catch (exc: Exception) {
                    includeIfMatches = Pattern.compile(tempExclude)
                }
            }
        }

        fun updateFile(rssFile: File?) {
            XMLPrefsManager.set(
                rssFile,
                RSS_LABEL,
                arrayOf<String?>(ID_ATTRIBUTE),
                arrayOf<String>(id.toString()),
                arrayOf<String?>(LASTCHECKED_ATTRIBUTE /*, LASTMODIFIED_ATTRIBUTE, ETAG_ATTRIBUTE*/),
                arrayOf<String>(lastCheckedClient.toString() /*, lMod, etag*/),
                false
            )
        }

        fun updateFormat(formats: MutableList<IdValue>) {
            if (format != null) {
                try {
                    val id = format!!.toInt()
                    for (i in formats) {
                        if (id == i.id) format = i.value
                    }
                } catch (exc: Exception) {
//                  the format is personalized --> it can't be casted to int
                }
            }
        }

        fun setFormat(formats: MutableList<IdValue>, format: String?) {
            this.format = format
            updateFormat(formats)
        }

        fun setIncludeIfMatches(includeIfMatches: String) {
            tempInclude = includeIfMatches
            updateIncludeIfMatches()
        }

        fun setExcludeIfMatches(excludeIfMatches: String) {
            tempExclude = excludeIfMatches
            updateExcludeIfMatches()
        }

        override fun equals(obj: Any?): Boolean {
            if (obj is Rss) return id == obj.id
            if (obj is Int) return id == obj
            return false
        }

        override fun toString(): String {
            val dots = ":"
            return StringBuilder().append(ID_LABEL).append(dots).append(Tuils.SPACE).append(id)
                .append(Tuils.SPACE)
                .append(URL_LABEL).append(dots).append(Tuils.SPACE).append(url).append(Tuils.SPACE)
                .append(UPDATE_TIME_LABEL).append(dots).append(Tuils.SPACE)
                .append(updateTimeSeconds).append(Tuils.SPACE)
                .append(SHOW_LABEL).append(dots).append(Tuils.SPACE).append(show)
                .toString()
        }

        companion object {
            const val ID_LABEL = "ID"
            private const val URL_LABEL = "URL"
            private const val UPDATE_TIME_LABEL = "update time"
            private const val SHOW_LABEL = "show"

            fun fromElement(t: Element): Rss? {
                val id: Int
                try {
                    id = t.getAttribute(ID_ATTRIBUTE).toInt()
                } catch (exc: Exception) {
                    return null
                }

                val url = t.getAttribute(URL_ATTRIBUTE)
                if (url == null) return null

                var updateTime: Long
                try {
                    updateTime = t.getAttribute(TIME_ATTRIBUTE).toLong()
                } catch (e: Exception) {
//                default: 1/2 h
                    updateTime = (60 * 30).toLong()
                }

                var show = true
                try {
                    show = t.getAttribute(SHOW_ATTRIBUTE).toBoolean()
                } catch (e: Exception) {
                }

                val lastChecked = XMLPrefsManager.getLongAttribute(t, LASTCHECKED_ATTRIBUTE)
                val lastShown = XMLPrefsManager.getLongAttribute(t, LAST_SHOWN_ITEM_ATTRIBUTE)

                val format = XMLPrefsManager.getStringAttribute(t, FORMAT_ATTRIBUTE)
                val includeIfMatches = XMLPrefsManager.getStringAttribute(t, INCLUDE_ATTRIBUTE)
                val excludeIfMatches = XMLPrefsManager.getStringAttribute(t, EXCLUDE_ATTRIBUTE)
                var color: Int
                try {
                    color = Color.parseColor(t.getAttribute(COLOR_ATTRIBUTE))
                } catch (exc: Exception) {
                    color = Int.Companion.MAX_VALUE
                }

                var wifiOnly = false
                try {
                    wifiOnly = t.getAttribute(WIFIONLY_ATTRIBUTE).toBoolean()
                } catch (e: Exception) {
                }

                val timeFormat = XMLPrefsManager.getStringAttribute(t, TIME_FORMAT_ATTRIBUTE)

                val rootNode = XMLPrefsManager.getStringAttribute(t, ENTRY_TAG_ATTRIBUTE)
                val timeNode = XMLPrefsManager.getStringAttribute(t, DATE_TAG_ATTRIBUTE)

                return RssManager.Rss(
                    url,
                    updateTime,
                    lastChecked,
                    lastShown,
                    id,
                    show,
                    format,
                    includeIfMatches!!,
                    excludeIfMatches!!,
                    color,
                    wifiOnly,
                    timeFormat,
                    rootNode,
                    timeNode
                )
            }
        }
    }

    //    utils
    private fun removeId(id: Int) {
        for (c in feeds!!.indices) {
            if (feeds!!.get(c).id == id) {
                feeds!!.removeAt(c)
                return
            }
        }
    }

    fun findId(id: Int): Rss? {
        for (c in feeds!!.indices) {
            val r = feeds!!.get(c)
            if (r.id == id) {
                return r
            }
        }

        return null
    }

    private fun prepare(): Boolean {
        var check = true
        if (!root.isDirectory()) {
            check = false
            root.mkdir()
        }
        if (!rssIndexFile.exists()) {
            try {
                rssIndexFile.createNewFile()
                XMLPrefsManager.resetFile(rssIndexFile, NAME)
                check = false

                return check && root.list().size > 1
            } catch (e: Exception) {
                return false
            }
        }

        return check
    }

    private inner class CmdableRegex(id: Int, on: String, regex: String?, cmd: String) :
        RegexManager.Regex() {
        var on: IntArray? = null
        var cmd: String

        init {
            var on = on
            this.id = id

            this.literalPattern = regex
            this.cmd = cmd

            var separator = Tuils.firstNonDigit(on)
            if (separator.code == 0) {
                try {
                    this.on = intArrayOf(on.toInt())
                } catch (e: Exception) {
                    Tuils.log(e)
                }
            } else {
                if (separator == ' ') {
                    val s2 = Tuils.firstNonDigit(Tuils.removeSpaces(on))
                    if (s2.code != 0) {
                        on = Tuils.removeSpaces(on)
                        separator = s2
                    }
                }

                val split: Array<String?> =
                    on.split(Pattern.quote(separator.toString() + Tuils.EMPTYSTRING).toRegex())
                        .dropLastWhile { it.isEmpty() }.toTypedArray()
                this.on = IntArray(split.size)

                for (c in split.indices) {
                    try {
                        this.on!![c] = split[c]!!.toInt()
                    } catch (e: Exception) {
                        Tuils.log(e)
                        this.on!![c] = Int.Companion.MAX_VALUE
                    }
                }
            }
        }
    }

    companion object {
        var TIME_ATTRIBUTE: String = "updateTimeSec"
        var SHOW_ATTRIBUTE: String = "show"
        var URL_ATTRIBUTE: String = "url"
        var LASTCHECKED_ATTRIBUTE: String = "lastChecked"
        var LAST_SHOWN_ITEM_ATTRIBUTE: String = "lastShownItem"
        var ID_ATTRIBUTE: String = "id"
        var FORMAT_ATTRIBUTE: String = "format"
        var INCLUDE_ATTRIBUTE: String = "includeIfMatches"
        var EXCLUDE_ATTRIBUTE: String = "excludeIfMatches"
        var COLOR_ATTRIBUTE: String = "color"
        var WIFIONLY_ATTRIBUTE: String = "wifiOnly"
        var TIME_FORMAT_ATTRIBUTE: String = "timeFormat"
        var DATE_TAG_ATTRIBUTE: String = "pubDateTag"
        var ENTRY_TAG_ATTRIBUTE: String = "entryTag"
        var ON_ATTRIBUTE: String = "on"
        var CMD_ATTRIBUTE: String = "cmd"

        const val RSS_LABEL: String = "rss"
        const val FORMAT_LABEL: String = "format"
        const val REGEX_CMD_LABEL: String = "regex"

        private var values: XMLPrefsList = XMLPrefsList()

        const val PATH: String = "rss.xml"
        const val NAME: String = "RSS"

        var instance: XMLPrefsElement? = null

        fun firstConfiguredFeedId(context: Context?): Int {
            val file = File(Tuils.getFolder(), PATH)
            if (!file.exists()) {
                return -1
            }

            try {
                val o = XMLPrefsManager.buildDocument(file, NAME)
                if (o == null) return -1

                val root = o[1] as Element
                val nodes = root.getElementsByTagName(RSS_LABEL)
                for (count in 0..<nodes.getLength()) {
                    val node = nodes.item(count)
                    if (node.getNodeType() != Node.ELEMENT_NODE) continue

                    val id = (node as Element).getAttribute(ID_ATTRIBUTE)
                    if (TextUtils.isEmpty(id)) continue
                    try {
                        return id!!.toInt()
                    } catch (ignored: Exception) {
                    }
                }
            } catch (e: Exception) {
                Tuils.log(e)
            }
            return -1
        }
    }
}
