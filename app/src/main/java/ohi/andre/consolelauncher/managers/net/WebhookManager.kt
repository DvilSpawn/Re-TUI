package ohi.andre.consolelauncher.managers.net

import android.content.Context
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import org.w3c.dom.NodeList
import java.util.ArrayList
import java.util.Iterator

class WebhookManager(private val context: Context?) {
    private var webhooks: MutableList<Webhook>? = null
    private val file: File

    init {
        this.file = File(Tuils.getFolder(), FILE_NAME)
        reload()
    }

    fun reload() {
        webhooks = ArrayList<Webhook>()
        try {
            val o = XMLPrefsManager.buildDocument(file, ROOT_NAME)
            if (o == null) return

            val d = o[0] as Document?
            val root = o[1] as Element
            val nodes = root.getElementsByTagName(WEBHOOK_TAG)

            for (i in 0..<nodes.getLength()) {
                val node = nodes.item(i)
                if (node is Element) {
                    val e = node
                    val name = e.getAttribute(NAME_ATTRIBUTE)
                    val url = e.getAttribute(URL_ATTRIBUTE)
                    val body = e.getAttribute(BODY_ATTRIBUTE)
                    if (name != null && !name.isEmpty()) {
                        webhooks!!.add(Webhook(name, url, body))
                    }
                }
            }
        } catch (e: Exception) {
            Tuils.log(e)
        }
    }

    fun getWebhook(name: String?): Webhook? {
        for (w in webhooks!!) {
            if (w.name.equals(name, ignoreCase = true)) return w
        }
        return null
    }

    fun getWebhooks(): MutableList<Webhook> {
        return webhooks!!
    }

    fun add(name: String?, url: String?, body: String?): Boolean {
        if (name == null || name.trim { it <= ' ' }.length == 0 || url == null || url.trim { it <= ' ' }.length == 0) {
            return false
        }

        try {
            val o = XMLPrefsManager.buildDocument(file, ROOT_NAME)
            if (o == null) {
                return false
            }

            val d = o[0] as Document
            val root = o[1] as Element

            removeMatchingNodes(root, name)

            val element = d.createElement(WEBHOOK_TAG)
            element.setAttribute(NAME_ATTRIBUTE, name.trim { it <= ' ' })
            element.setAttribute(URL_ATTRIBUTE, url.trim { it <= ' ' })
            element.setAttribute(BODY_ATTRIBUTE, if (body == null) Tuils.EMPTYSTRING else body)
            root.appendChild(element)

            XMLPrefsManager.writeTo(d, file)
            reload()
            return true
        } catch (e: Exception) {
            Tuils.log(e)
            return false
        }
    }

    fun remove(name: String?): Boolean {
        if (name == null || name.trim { it <= ' ' }.length == 0) {
            return false
        }

        try {
            val o = XMLPrefsManager.buildDocument(file, ROOT_NAME)
            if (o == null) {
                return false
            }

            val d = o[0] as Document?
            val root = o[1] as Element
            val removed = removeMatchingNodes(root, name)

            if (removed) {
                XMLPrefsManager.writeTo(d, file)
                reload()
            }

            return removed
        } catch (e: Exception) {
            Tuils.log(e)
            return false
        }
    }

    private fun removeMatchingNodes(root: Element, name: String): Boolean {
        val nodes = root.getElementsByTagName(WEBHOOK_TAG)
        val toRemove: MutableList<Node?> = ArrayList<Node?>()

        for (i in 0..<nodes.getLength()) {
            val node = nodes.item(i)
            if (node !is Element) {
                continue
            }

            val element = node
            val existingName = element.getAttribute(NAME_ATTRIBUTE)
            if (existingName != null && existingName.equals(
                    name.trim { it <= ' ' },
                    ignoreCase = true
                )
            ) {
                toRemove.add(node)
            }
        }

        for (node in toRemove) {
            root.removeChild(node)
        }

        return !toRemove.isEmpty()
    }

    class Webhook(var name: String, var url: String?, var bodyTemplate: String?) {
        fun substitute(args: Array<String?>): String {
            return substitutePlain(bodyTemplate, args)
        }

        @Throws(JSONException::class)
        fun render(args: Array<String?>, jsonBody: Boolean): String? {
            if (!jsonBody) {
                return substitute(args)
            }

            val template =
                if (bodyTemplate == null) Tuils.EMPTYSTRING else bodyTemplate!!.trim { it <= ' ' }
            if (template.startsWith("{")) {
                val `object` = JSONObject(template)
                return replaceJsonObject(`object`, args).toString()
            } else if (template.startsWith("[")) {
                val array = JSONArray(template)
                return replaceJsonArray(array, args).toString()
            }

            return substitute(args)
        }

        companion object {
            private fun substitutePlain(template: String?, args: Array<String?>): String {
                var result = if (template == null) Tuils.EMPTYSTRING else template
                for (i in args.indices) {
                    result = result.replace("%" + (i + 1), args[i]!!)
                }
                return result
            }

            @Throws(JSONException::class)
            private fun replaceJsonObject(`object`: JSONObject, args: Array<String?>): JSONObject {
                val keys = `object`.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    `object`.put(key, replaceJsonValue(`object`.get(key), args))
                }
                return `object`
            }

            @Throws(JSONException::class)
            private fun replaceJsonArray(array: JSONArray, args: Array<String?>): JSONArray {
                for (i in 0..<array.length()) {
                    array.put(i, replaceJsonValue(array.get(i), args))
                }
                return array
            }

            @Throws(JSONException::class)
            private fun replaceJsonValue(value: Any?, args: Array<String?>): Any? {
                if (value is JSONObject) {
                    return replaceJsonObject(value, args)
                } else if (value is JSONArray) {
                    return replaceJsonArray(value, args)
                } else if (value is String) {
                    return substitutePlain(value, args)
                }

                return value
            }
        }
    }

    companion object {
        private const val FILE_NAME = "webhooks.xml"
        private const val ROOT_NAME = "webhooks"
        private const val WEBHOOK_TAG = "webhook"
        private const val NAME_ATTRIBUTE = "name"
        private const val URL_ATTRIBUTE = "url"
        private const val BODY_ATTRIBUTE = "body"
    }
}
