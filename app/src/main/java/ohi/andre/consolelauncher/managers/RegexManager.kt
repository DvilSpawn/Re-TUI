package ohi.andre.consolelauncher.managers

import android.content.Context
import android.graphics.Color
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.Tuils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXParseException
import java.io.File
import java.util.regex.Pattern
import android.text.SpannableString
import org.w3c.dom.NodeList
import java.util.ArrayList
import java.util.Iterator
import java.util.regex.Matcher

/**
 * Created by francescoandreuzzi on 04/10/2017.
 */
class RegexManager(context: Context) {
    private val PATH = "regex.xml"
    private val ROOT = "REGEX"
    private val REGEX_LABEL = "regex"
    private val ID_ATTRIBUTE = "id"

    private var regexes: MutableList<Regex>? = null

    init {
        if (regexes != null) regexes!!.clear()
        else regexes = ArrayList<Regex>()

        object : StoppableThread() {
            override fun run() {
                super.run()

                try {
                    val root = Tuils.getFolder()
                    val file = File(root, PATH)
                    if (!file.exists()) {
                        file.createNewFile()
                        XMLPrefsManager.resetFile(file, ROOT)
                    }

                    val o: Array<Any?>?
                    try {
                        o = XMLPrefsManager.buildDocument(file, ROOT)
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

                    val el = o[1] as Element

                    val busyIds: MutableList<Int?> = ArrayList<Int?>()

                    val nodeList = el.getElementsByTagName(REGEX_LABEL)

                    Out@ for (c in 0..<nodeList.getLength()) {
                        val e = nodeList.item(c) as Element

                        if (!e.hasAttribute(XMLPrefsManager.VALUE_ATTRIBUTE)) continue
                        val value = e.getAttribute(XMLPrefsManager.VALUE_ATTRIBUTE)

                        val id: Int
                        try {
                            id = e.getAttribute(ID_ATTRIBUTE).toInt()
                        } catch (exc: Exception) {
                            continue
                        }

                        for (j in busyIds.indices) {
                            if (busyIds.get(j) as Int == id) continue@Out
                        }

                        busyIds.add(id)

                        if (value != null && value.length > 0) {
                            regexes!!.add(Regex(value, id))
                        }
                    }
                } catch (e: Exception) {
                    Tuils.log(e)
                    Tuils.toFile(e)
                    return
                }
            }
        }.start()

        instance = this
    }

    fun get(id: Int): Regex? {
        for (r in regexes!!) {
            if (r.id == id) return r
        }

        return null
    }

    private fun rmFromList(id: Int) {
        val iterator = regexes!!.iterator()
        while (iterator.hasNext()) {
            val r = iterator.next()
            if (r.id == id) {
                iterator.remove()
            }
        }
    }

    //    null: all good
    //    "": used id
    fun add(id: Int, value: String): String? {
        for (c in regexes!!.indices) {
            if (regexes!!.get(c).id == id) return Tuils.EMPTYSTRING
        }

        regexes!!.add(Regex(value, id))

        val file = File(Tuils.getFolder(), PATH)

        return XMLPrefsManager.add(
            file,
            REGEX_LABEL,
            arrayOf<String>(ID_ATTRIBUTE, XMLPrefsManager.VALUE_ATTRIBUTE),
            arrayOf<String?>(id.toString(), value)
        )
    }

    //    null: all good
    //    "": not found
    fun rm(id: Int): String? {
        var id = id
        try {
            val file = File(Tuils.getFolder(), PATH)

            val o = XMLPrefsManager.buildDocument(file, null)
            if (o == null) {
                return null
            }

            val d = o[0] as Document?
            val el = o[1] as Element

            var needToWrite = false

            val nodeList = el.getElementsByTagName(REGEX_LABEL)
            for (c in 0..<nodeList.getLength()) {
                val e = nodeList.item(c) as Element

                val cId = Int.Companion.MAX_VALUE
                try {
                    id = e.getAttribute(ID_ATTRIBUTE).toInt()
                } catch (exc: Exception) {
                    continue
                }

                if (cId == id) {
                    needToWrite = true
                    el.removeChild(e)
                }
            }

            if (needToWrite) {
                XMLPrefsManager.writeTo(d, file)
                rmFromList(id)
                return null
            } else return Tuils.EMPTYSTRING
        } catch (e: Exception) {
            return e.toString()
        }
    }

    fun test(id: Int, test: String): CharSequence? {
        val regex = get(id)
        if (regex == null) return Tuils.EMPTYSTRING

        if (regex.regex == null) return "null"
        val m = regex.regex!!.matcher(test)

        val color = XMLPrefsManager.getColor(Theme.regex_match_background_color)
        val outputColor = XMLPrefsManager.getColor(Theme.output_text_color)

        if (m.matches()) {
            return Tuils.span(color, outputColor, test) ?: SpannableString(test)
        }

        var last = 0
        val s = Tuils.span(test, outputColor) ?: SpannableString(test)
        while (m.find()) {
            val g0 = m.group(0)
            last = Tuils.span(color, s, g0, last)
        }

        return s
    }

    fun dispose() {
        if (regexes != null) {
            regexes!!.clear()
            regexes = null
        }

        instance = null
    }

    open class Regex {
        var regex: Pattern? = null
        var literalPattern: String? = null
        var id: Int = 0

        constructor()

        constructor(value: String, id: Int) {
            this.regex = Pattern.compile(value)
            this.id = id
        }
    }

    companion object {
        var instance: RegexManager? = null
    }
}
