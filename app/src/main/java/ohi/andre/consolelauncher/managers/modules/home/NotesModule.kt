package ohi.andre.consolelauncher.managers.modules.home

import android.content.Context
import android.text.TextUtils
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import kotlin.math.max
import kotlin.math.min

internal class NotesModule : HomeModule {
    override val id = ModuleManager.NOTES
    override val usesThemeTypeface = true

    override fun show(host: HomeModuleHost) = host.renderText(id, buildText(host.context))

    private fun buildText(context: Context): String {
        val records = ohi.andre.consolelauncher.managers.NotesManager.loadRecords(context)
        if (records.size == 0) {
            return (context.getString(R.string.surface_no_notes_add_notes_add_todo_follow_up_open_editor_notes_3d1ec))
        }

        val out = StringBuilder()
        out.append(context.resources.getQuantityString(R.plurals.surface_notes_count, records.size, records.size)).append('\n')
        val limit = min(records.size, 6)
        for (count in 0..<limit) {
            val record = records.get(count)
            if (record == null || TextUtils.isEmpty(record.text)) continue
            out.append(count + 1).append(". ")
            if (record.lock) out.append(context.getString(R.string.surface_locked_f6608))
            out.append(shortenModuleLine(record.text, 96)).append('\n')
        }
        val remaining = records.size - limit
        if (remaining > 0) {
            out.append(context.resources.getQuantityString(R.plurals.surface_more_count, remaining, remaining)).append('\n')
        }
        out.append(context.getString(R.string.surface_commands_notes_notes_add_notes_ls_86691))
        return out.toString().trim { it <= ' ' }
    }

    private fun shortenModuleLine(value: String?, max: Int): String {
        if (value == null) return ""
        val cleaned = value.replace("\\s+".toRegex(), " ").trim { it <= ' ' }
        if (cleaned.length <= max) return cleaned
        return cleaned.substring(0, max(0, max - 3)).trim { it <= ' ' } + "..."
    }
}
