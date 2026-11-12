package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.notes.JournalStore
import ohi.andre.consolelauncher.notes.LauncherNotes
import ohi.andre.consolelauncher.tuils.Tuils

/** `journal` opens today's journal; `journal some text` logs it as an entry, like jrnl. */
class journal : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        val text = pack.getString().trim()
        if (text.isEmpty()) return open(pack)
        JournalStore(pack.context).append(System.currentTimeMillis(), text)
        return pack.context.getString(R.string.journal_logged)
    }

    private fun open(pack: ExecutePack): String {
        LauncherNotes.openJournal(pack.context)
        return Tuils.EMPTYSTRING
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)
    override fun priority(): Int = 4
    override fun helpRes(): Int = R.string.help_journal
    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = open(pack)
    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = open(pack)
}
