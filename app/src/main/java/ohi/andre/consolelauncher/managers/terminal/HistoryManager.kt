package ohi.andre.consolelauncher.managers.terminal

import java.io.File
import java.util.Locale
import org.w3c.dom.Document
import org.w3c.dom.Element
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.Tuils
import android.content.Context
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.util.ArrayList
import java.util.HashMap
import java.util.Map

class HistoryManager {
    private var history: MutableMap<String, MutableList<String>> = HashMap()
    private val file: File = File(Tuils.getFolder(), FILE_NAME)

    init {
        reload()
    }

    fun reload() {
        history = HashMap()
        try {
            val built = XMLPrefsManager.buildDocument(file, ROOT_NAME) ?: return
            val root = built[1] as Element
            val webhookNodes = root.getElementsByTagName(WEBHOOK_TAG)

            for (i in 0 until webhookNodes.length) {
                val node = webhookNodes.item(i)
                if (node is Element) {
                    val name = node.getAttribute(NAME_ATTRIBUTE)
                    val entries: MutableList<String> = ArrayList()

                    val entryNodes = node.getElementsByTagName(ENTRY_TAG)
                    for (j in 0 until entryNodes.length) {
                        val entryNode = entryNodes.item(j)
                        if (entryNode is Element) {
                            entries.add(entryNode.getAttribute(VALUE_ATTRIBUTE))
                        }
                    }
                    history[name.lowercase(Locale.getDefault())] = entries
                }
            }
        } catch (e: Exception) {
            Tuils.log(e)
        }
    }

    fun add(webhookName: String, args: String) {
        val name = webhookName.lowercase(Locale.getDefault())
        var entries = history[name]
        if (entries == null) {
            entries = ArrayList()
            history[name] = entries
        }

        entries.remove(args)
        entries.add(0, args)

        if (entries.size > MAX_HISTORY) {
            entries.removeAt(entries.size - 1)
        }

        save()
    }

    private fun save() {
        try {
            val built = XMLPrefsManager.buildDocument(file, ROOT_NAME) ?: return
            val document = built[0] as Document
            val root = built[1] as Element

            while (root.hasChildNodes()) {
                root.removeChild(root.firstChild)
            }

            for ((key, values) in history) {
                val webhookElement = document.createElement(WEBHOOK_TAG)
                webhookElement.setAttribute(NAME_ATTRIBUTE, key)
                for (value in values) {
                    val entryElement = document.createElement(ENTRY_TAG)
                    entryElement.setAttribute(VALUE_ATTRIBUTE, value)
                    webhookElement.appendChild(entryElement)
                }
                root.appendChild(webhookElement)
            }

            XMLPrefsManager.writeTo(document, file)
        } catch (e: Exception) {
            Tuils.log(e)
        }
    }

    fun getHistory(webhookName: String): List<String>? =
        history[webhookName.lowercase(Locale.getDefault())]

    companion object {
        private const val FILE_NAME = "history.xml"
        private const val ROOT_NAME = "history"
        private const val WEBHOOK_TAG = "webhook"
        private const val ENTRY_TAG = "entry"
        private const val NAME_ATTRIBUTE = "name"
        private const val VALUE_ATTRIBUTE = "value"
        private const val MAX_HISTORY = 5
    }
}
