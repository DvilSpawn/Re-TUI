package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.reader.Books
import ohi.andre.consolelauncher.managers.reader.ReaderActivity
import ohi.andre.consolelauncher.managers.reader.ReaderLibraryActivity
import ohi.andre.consolelauncher.tuils.Tuils

/** `books` opens the book library; `books -last` carries on with the book read last, `books -last pdf` with the last of a kind. */
class books : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val context = pack.context
        val words = pack.getString().trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when (words.firstOrNull()) {
            null, "-ls", "-open" -> library(pack)
            "-last", "-continue" -> {
                val kind = words.getOrNull(1)?.let { Books.kindNamed(it) ?: return context.getString(R.string.help_books) }
                val last = (if (kind == null) Books.last(context) else Books.lastByKind(context).firstOrNull { Books.kindOf(it.first.format) == kind })
                    ?: return library(pack)
                ReaderActivity.open(context, last.first.uri)
                Tuils.EMPTYSTRING
            }
            else -> context.getString(R.string.help_books)
        }
    }

    private fun library(pack: ExecutePack): String {
        pack.context.startActivity(Intent(pack.context, ReaderLibraryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return Tuils.EMPTYSTRING
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)
    override fun priority(): Int = 3
    override fun helpRes(): Int = R.string.help_books
    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = library(pack)
    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = library(pack)
}
