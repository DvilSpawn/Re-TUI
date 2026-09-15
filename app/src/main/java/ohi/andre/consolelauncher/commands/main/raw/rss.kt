package ohi.andre.consolelauncher.commands.main.raw

import android.text.InputType
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.FormField
import ohi.andre.consolelauncher.managers.RssManager
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Rss
import ohi.andre.consolelauncher.tuils.Tuils
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import java.util.regex.Pattern
import org.w3c.dom.Node

/**
 * Created by francescoandreuzzi on 30/09/2017.
 */
class rss : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        add {
            override fun exec(pack: ExecutePack): String? {
                val id = pack.getInt()
                val tm = pack.get() as Long
                val url = pack.getString()

                return (pack as MainPack).rssManager!!.add(id, tm, url)
            }

            override fun args(): IntArray? {
                return intArrayOf(
                    CommandAbstraction.INT,
                    CommandAbstraction.LONG,
                    CommandAbstraction.PLAIN_TEXT
                )
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
                if (n <= 1) return showAddForm(pack)
                return super.onNotArgEnough(pack, n)
            }
        },
        rm {
            override fun exec(pack: ExecutePack): String? {
                val id = pack.getInt()

                return (pack as MainPack).rssManager!!.rm(id)
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT)
            }
        },
        ls {
            override fun exec(pack: ExecutePack): String? {
                return showFeedList(pack)
            }

            override fun args(): IntArray? {
                return IntArray(0)
            }
        },
        menu {
            override fun exec(pack: ExecutePack): String? {
                return showFeedMenu(pack, pack.getInt())
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT)
            }
        },
        l {
            override fun exec(pack: ExecutePack): String? {
                val id = pack.getInt()
                return (pack as MainPack).rssManager!!.l(id)
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT)
            }
        },
        show {
            override fun exec(pack: ExecutePack): String? {
                val id = pack.getInt()
                val show = pack.getBoolean()

                return (pack as MainPack).rssManager!!.setShow(id, show)
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.BOOLEAN)
            }
        },
        update_time {
            override fun exec(pack: ExecutePack): String? {
                val id = pack.getInt()
                val tm = pack.get() as Long

                return (pack as MainPack).rssManager!!.setTime(id, tm)
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.LONG)
            }
        },
        url {
            override fun exec(pack: ExecutePack): String? {
                val id = pack.getInt()
                val url = pack.getString()
                validateUrl(url)?.let { return pack.context.getString(it) }
                return (pack as MainPack).rssManager!!.setUrl(id, url)
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT)
            }
        },
        alias {
            override fun exec(pack: ExecutePack): String? {
                return (pack as MainPack).rssManager!!.setAlias(pack.getInt(), pack.getString())
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT)
            }
        },
        time_format {
            override fun exec(pack: ExecutePack): String? {
                return (pack as MainPack).rssManager!!.setTimeFormat(
                    pack.getInt(),
                    pack.getString()
                )
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT)
            }
        },
        format {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT)
            }

            override fun exec(pack: ExecutePack): String? {
                val id = pack.getInt()
                val s = pack.getString()

                return (pack as MainPack).rssManager!!.setFormat(id, s)
            }
        },
        color {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.COLOR)
            }

            override fun exec(pack: ExecutePack): String? {
                val id = pack.getInt()
                val c = pack.getString()

                return (pack as MainPack).rssManager!!.setColor(id, c)
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                if (index == 2) return pack.context.getString(R.string.output_invalidcolor)
                return super.onArgNotFound(pack, index)
            }
        },
        entry_tag {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT)
            }

            override fun exec(pack: ExecutePack): String? {
                return (pack as MainPack).rssManager!!.setEntryTag(pack.getInt(), pack.getString())
            }
        },
        date_tag {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT)
            }

            override fun exec(pack: ExecutePack): String? {
                return (pack as MainPack).rssManager!!.setDateTag(pack.getInt(), pack.getString())
            }
        },
        last_check {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT)
            }

            override fun exec(pack: ExecutePack): String? {
                val n = XMLPrefsManager.findNode(
                    File(Tuils.getFolder(), RssManager.PATH),
                    RssManager.RSS_LABEL,
                    arrayOf<String?>(RssManager.ID_ATTRIBUTE),
                    arrayOf<String?>(pack.getInt().toString())
                )
                if (n == null) return pack.context.getString(R.string.id_notfound)

                val el = n as Element

                val value = if (el.hasAttribute(RssManager.LASTCHECKED_ATTRIBUTE)) el.getAttribute(
                    RssManager.LASTCHECKED_ATTRIBUTE
                ) else null
                if (value == null) return pack.context.getString(R.string.rss_never_checked)

                try {
                    return TimeManager.instance!!.replace(
                        XMLPrefsManager.get(Rss.rss_time_format), value.toLong(),
                        Int.Companion.MAX_VALUE
                    ).toString()
                } catch (e: Exception) {
                    Tuils.log(e)
                    return pack.context.getString(R.string.output_error)
                }
            }
        },
        frc {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT)
            }

            override fun exec(pack: ExecutePack): String? {
                if (!(pack as MainPack).rssManager!!.updateRss(
                        pack.getInt(),
                        false,
                        true
                    )
                ) return pack.context.getString(R.string.id_notfound)
                return null
            }
        },
        info {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT)
            }

            override fun exec(pack: ExecutePack): String? {
                val rss = (pack as MainPack).rssManager!!.findId(pack.getInt())
                if (rss == null) return pack.context.getString(R.string.id_notfound)

                return rss.toString()
            }
        },
        include_if_matches {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT)
            }

            override fun exec(pack: ExecutePack): String? {
                val id = pack.getInt()
                val r = pack.getString()

                return (pack as MainPack).rssManager!!.setIncludeIfMatches(id, r)
            }
        },
        exclude_if_matches {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT)
            }

            override fun exec(pack: ExecutePack): String? {
                val id = pack.getInt()
                val r = pack.getString()

                return (pack as MainPack).rssManager!!.setExcludeIfMatches(id, r)
            }
        },
        add_command {
            override fun args(): IntArray? {
                return intArrayOf(
                    CommandAbstraction.INT,
                    CommandAbstraction.NO_SPACE_STRING,
                    CommandAbstraction.NO_SPACE_STRING,
                    CommandAbstraction.PLAIN_TEXT
                )
            }

            override fun exec(pack: ExecutePack): String? {
                val id = pack.getInt()

                val on = pack.getString()
                val regex = pack.getString()
                val cmd = pack.getString()

                try {
                    Pattern.compile(regex)
                } catch (e: Exception) {
                    return e.toString()
                }

                return (pack as MainPack).rssManager!!.addRegexCommand(id, on, regex, cmd)
            }
        },
        rm_command {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT)
            }

            override fun exec(pack: ExecutePack): String? {
                return (pack as MainPack).rssManager!!.rmRegexCommand(pack.getInt())
            }
        },
        wifi_only {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.BOOLEAN)
            }

            override fun exec(pack: ExecutePack): String? {
                val id = pack.getInt()
                val w = pack.getBoolean()

                return (pack as MainPack).rssManager!!.setWifiOnly(id, w)
            }
        },
        add_format {
            override fun exec(pack: ExecutePack): String? {
                return (pack as MainPack).rssManager!!.addFormat(pack.getInt(), pack.getString())
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT)
            }
        },
        rm_format {
            override fun exec(pack: ExecutePack): String? {
                return (pack as MainPack).rssManager!!.removeFormat(pack.getInt())
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT)
            }
        },
        file {
            override fun exec(pack: ExecutePack): String? {
                pack.context.startActivity(
                    Tuils.openFile(
                        pack.context,
                        File(Tuils.getFolder(), RssManager.PATH)
                    )
                )
                return null
            }

            override fun args(): IntArray? {
                return IntArray(0)
            }
        };

        override fun label(): String? {
            return Tuils.MINUS + name
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
            return pack.context.getString(R.string.help_rss)
        }

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
            return pack.context.getString(R.string.invalid_integer)
        }

        companion object {
            private const val FIELD_ID = "id"
            private const val FIELD_SECONDS = "seconds"
            private const val FIELD_URL = "url"
            private const val FIELD_ALIAS = "alias"

            fun get(p: String): Param? {
                var p = p
                p = p.lowercase(Locale.ROOT)
                val ps = entries.toTypedArray()
                for (p1 in ps) if (p.endsWith(p1.label()!!)) return p1
                return null
            }

            fun labels(): Array<String?> {
                val ps = entries.toTypedArray()
                val ss = arrayOfNulls<String>(ps.size)

                for (count in ps.indices) {
                    ss[count] = ps[count].label()
                }

                return ss
            }

            private fun showAddForm(pack: ExecutePack): String {
                val manager = (pack as MainPack).rssManager!!
                TuixtDialog.showValidatedForm(
                    pack.context,
                    pack.context.getString(R.string.command_rss_add_rss_feed_9d80f),
                    listOf(
                        FormField(FIELD_ID, pack.context.getString(R.string.command_rss_id_89f89), "1", InputType.TYPE_CLASS_NUMBER, manager.nextId().toString()),
                        FormField(FIELD_SECONDS, pack.context.getString(R.string.command_rss_refresh_seconds_3114f), "900", InputType.TYPE_CLASS_NUMBER, "900"),
                        FormField(FIELD_URL, pack.context.getString(R.string.command_rss_url_0e2d9), "https://example.com/feed.xml", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI),
                        FormField(FIELD_ALIAS, pack.context.getString(R.string.command_rss_alias_04259), pack.context.getString(R.string.command_rss_optional_display_name_d7b98))
                    ),
                    pack.context.getString(R.string.command_rss_add_f9460),
                    pack.context.getString(R.string.command_rss_cancel_1507c),
                    { values -> validateAddForm(manager, values)?.let(pack.context::getString) }
                ) { values ->
                    val result = manager.add(
                        values[FIELD_ID]!!.toInt(),
                        values[FIELD_SECONDS]!!.toLong(),
                        values[FIELD_URL].orEmpty(),
                        values[FIELD_ALIAS]
                    )
                    Tuils.sendOutput(pack.context, result ?: pack.context.getString(R.string.command_rss_rss_feed_added_a32c7))
                }
                return pack.context.getString(R.string.command_rss_opening_rss_setup_17e4d)
            }

            private fun showFeedList(pack: ExecutePack): String {
                val manager = (pack as MainPack).rssManager!!
                val feeds = manager.snapshot()
                val items = feeds.map { "[" + it.id + "] " + manager.displayLabel(it) }.toMutableList()
                items.add(pack.context.getString(R.string.command_rss_add_feed_dbdaf))

                TuixtDialog.showOptions(
                    pack.context,
                    pack.context.getString(R.string.command_rss_rss_feeds_1bfa2),
                    items
                ) { index ->
                    if (index == feeds.size) {
                        showAddForm(pack)
                    } else {
                        showFeedMenu(pack, feeds[index].id)?.let { Tuils.sendOutput(pack.context, it) }
                    }
                }
                return if (feeds.isEmpty()) pack.context.getString(R.string.command_rss_no_rss_feeds_saved_d7342) else pack.context.getString(R.string.command_rss_opening_rss_feeds_b554e)
            }

            private fun showFeedMenu(pack: ExecutePack, id: Int): String? {
                val manager = (pack as MainPack).rssManager!!
                val feed = manager.findId(id) ?: return pack.context.getString(R.string.id_notfound)
                val showLabel = if (feed.show) pack.context.getString(R.string.command_rss_hide_from_stream_38c80) else pack.context.getString(R.string.command_rss_show_in_stream_ca7b2)
                TuixtDialog.showOptions(
                    pack.context,
                    pack.context.getString(R.string.command_rss_rss_f543e, id),
                    listOf(pack.context.getString(R.string.command_rss_show_latest_b30b3), pack.context.getString(R.string.command_rss_refresh_now_39bf9), pack.context.getString(R.string.command_rss_edit_url_e7962), pack.context.getString(R.string.command_rss_rename_alias_587f3), pack.context.getString(R.string.command_rss_edit_interval_557bc), showLabel, "remove")
                ) { index ->
                    when (index) {
                        0 -> manager.l(id)?.let { Tuils.sendOutput(pack.context, it) }
                        1 -> {
                            if (manager.updateRss(id, false, true)) Tuils.sendOutput(pack.context, pack.context.getString(R.string.command_rss_rss_refresh_requested_b09c3))
                            else Tuils.sendOutput(pack.context, pack.context.getString(R.string.id_notfound))
                        }
                        2 -> showUrlForm(pack, manager, id, feed.url.orEmpty())
                        3 -> showAliasForm(pack, manager, id, feed.alias.orEmpty())
                        4 -> showIntervalForm(pack, manager, id, feed.updateTimeSeconds)
                        5 -> {
                            val nextShow = !feed.show
                            val result = manager.setShow(id, nextShow)
                            Tuils.sendOutput(pack.context, result ?: if (nextShow) pack.context.getString(R.string.command_rss_rss_feed_shown_ee233) else pack.context.getString(R.string.command_rss_rss_feed_hidden_89571))
                        }
                        6 -> confirmRemove(pack, manager, id)
                    }
                }
                return pack.context.getString(R.string.command_rss_opening_rss_feed_options_2c8a2)
            }

            private fun showUrlForm(pack: ExecutePack, manager: RssManager, id: Int, currentUrl: String) {
                TuixtDialog.showValidatedForm(
                    pack.context,
                    pack.context.getString(R.string.command_rss_rss_url_090c6),
                    listOf(FormField(FIELD_URL, pack.context.getString(R.string.command_rss_url_0e2d9), "https://example.com/feed.xml", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, currentUrl)),
                    pack.context.getString(R.string.command_rss_save_50815),
                    pack.context.getString(R.string.command_rss_cancel_1507c),
                    { values -> validateUrl(values[FIELD_URL])?.let(pack.context::getString) }
                ) { values ->
                    val result = manager.setUrl(id, values[FIELD_URL].orEmpty())
                    Tuils.sendOutput(pack.context, result ?: pack.context.getString(R.string.command_rss_rss_url_updated_4de4a))
                }
            }

            private fun showAliasForm(pack: ExecutePack, manager: RssManager, id: Int, currentAlias: String) {
                TuixtDialog.showValidatedForm(
                    pack.context,
                    pack.context.getString(R.string.command_rss_rss_alias_66634),
                    listOf(FormField(FIELD_ALIAS, pack.context.getString(R.string.command_rss_alias_04259), pack.context.getString(R.string.command_rss_optional_display_name_d7b98), InputType.TYPE_CLASS_TEXT, currentAlias)),
                    pack.context.getString(R.string.command_rss_save_50815),
                    pack.context.getString(R.string.command_rss_cancel_1507c),
                    { null }
                ) { values ->
                    val alias = values[FIELD_ALIAS]
                    val result = manager.setAlias(id, alias)
                    Tuils.sendOutput(pack.context, result ?: if (alias.isNullOrBlank()) pack.context.getString(R.string.command_rss_rss_alias_cleared_c57e3) else pack.context.getString(R.string.command_rss_rss_alias_updated_73c49))
                }
            }

            private fun showIntervalForm(pack: ExecutePack, manager: RssManager, id: Int, seconds: Long) {
                TuixtDialog.showValidatedForm(
                    pack.context,
                    pack.context.getString(R.string.command_rss_rss_interval_dca4f),
                    listOf(FormField(FIELD_SECONDS, pack.context.getString(R.string.command_rss_refresh_seconds_3114f), "900", InputType.TYPE_CLASS_NUMBER, seconds.toString())),
                    pack.context.getString(R.string.command_rss_save_50815),
                    pack.context.getString(R.string.command_rss_cancel_1507c),
                    { values -> validateSeconds(values[FIELD_SECONDS])?.let(pack.context::getString) }
                ) { values ->
                    val result = manager.setTime(id, values[FIELD_SECONDS]!!.toLong())
                    Tuils.sendOutput(pack.context, result ?: pack.context.getString(R.string.command_rss_rss_interval_updated_9c833))
                }
            }

            private fun confirmRemove(pack: ExecutePack, manager: RssManager, id: Int) {
                TuixtDialog.showConfirm(
                    pack.context,
                    pack.context.getString(R.string.command_rss_remove_rss_2af36, id),
                    pack.context.getString(R.string.command_rss_remove_this_rss_feed_0c52b),
                    pack.context.getString(R.string.command_rss_remove_f9662),
                    pack.context.getString(R.string.command_rss_cancel_1507c)
                ) {
                    val result = manager.rm(id)
                    Tuils.sendOutput(pack.context, result ?: pack.context.getString(R.string.command_rss_rss_feed_removed_2ac75))
                }
            }

            private fun validateAddForm(manager: RssManager, values: Map<String, String>): Int? {
                val id = values[FIELD_ID]?.toIntOrNull()
                if (id == null || id <= 0) return R.string.validation_id_invalid
                if (manager.findId(id) != null) return R.string.validation_id_exists
                validateSeconds(values[FIELD_SECONDS])?.let { return it }
                validateUrl(values[FIELD_URL])?.let { return it }
                return null
            }

            private fun validateSeconds(value: String?): Int? {
                val seconds = value?.toLongOrNull()
                return if (seconds == null || seconds <= 0) R.string.validation_refresh_invalid else null
            }

            private fun validateUrl(value: String?): Int? {
                val url = value?.trim().orEmpty()
                val lower = url.lowercase(Locale.US)
                if (url.isBlank()) return R.string.validation_url_missing
                return if (lower.startsWith("http://") || lower.startsWith("https://")) null else R.string.validation_url_scheme
            }
        }
    }

    override fun paramForString(
        pack: MainPack,
        param: String
    ): ohi.andre.consolelauncher.commands.main.Param? {
        return Param.Companion.get(param)
    }

    override fun doThings(pack: ExecutePack): String? {
        return null
    }

    override fun priority(): Int {
        return 3
    }

    override fun helpRes(): Int {
        return R.string.help_rss
    }

    public override fun params(): Array<String?> {
        return arrayOf("-add", "-ls")
    }
}
