package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.media.MediaFolderPickerActivity
import ohi.andre.consolelauncher.managers.media.MediaLibrary
import ohi.andre.consolelauncher.managers.media.MediaScan
import ohi.andre.consolelauncher.managers.mediaplayer.MediaPlayerActivity
import ohi.andre.consolelauncher.tuils.Tuils

/**
 * `media` shows the media library's folders and what they hold; `media -add` picks a folder, `media -rm <n>`
 * drops one, `media -scan` looks through them again, `media -v` opens the video library.
 */
class media : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val context = pack.context
        val args = pack.getString().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when (args.firstOrNull()?.lowercase()) {
            null, "-ls", "status" -> status(pack)
            "-add" -> {
                context.startActivity(Intent(context, MediaFolderPickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Tuils.EMPTYSTRING
            }
            "-rm" -> {
                val index = args.getOrNull(1)?.toIntOrNull()?.minus(1) ?: return context.getString(R.string.help_media)
                val removed = MediaLibrary.removeFolder(context, index) ?: return context.getString(R.string.media_no_such_folder)
                MediaScan.start(context.applicationContext, context.getString(R.string.media_removed, removed))
                Tuils.EMPTYSTRING
            }
            "-scan" -> {
                if (MediaLibrary.folders(context).isEmpty()) return context.getString(R.string.media_no_folders)
                MediaScan.start(context.applicationContext, context.getString(R.string.media_scanning))
                Tuils.EMPTYSTRING
            }
            "-v" -> {
                if (MediaLibrary.folders(context).isEmpty()) return context.getString(R.string.media_no_folders)
                if (MediaLibrary.items(context).none { it.video }) return context.getString(R.string.media_no_videos)
                context.startActivity(Intent(context, MediaPlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MediaPlayerActivity.EXTRA_TAB, MediaPlayerActivity.Tab.VIDEO.name))
                Tuils.EMPTYSTRING
            }
            else -> context.getString(R.string.help_media)
        }
    }

    private fun status(pack: ExecutePack): String {
        val context = pack.context
        val folders = MediaLibrary.folders(context)
        if (folders.isEmpty()) return context.getString(R.string.media_no_folders)
        val items = MediaLibrary.items(context)
        return buildString {
            folders.forEachIndexed { i, uri ->
                val label = MediaLibrary.label(uri)
                val inside = items.filter { it.folder == label || it.folder.startsWith("$label/") }
                append(i + 1).append("  ").append(label).append("  ")
                append(context.resources.getQuantityString(R.plurals.media_tracks, inside.count { !it.video }, inside.count { !it.video }))
                append(" · ")
                append(context.resources.getQuantityString(R.plurals.media_videos, inside.count { it.video }, inside.count { it.video }))
                if (i < folders.lastIndex) append('\n')
            }
        }
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)
    override fun priority(): Int = 3
    override fun helpRes(): Int = R.string.help_media
    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = status(pack)
    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = status(pack)
}
