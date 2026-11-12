package ohi.andre.consolelauncher.managers.file

import android.content.Context
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FilenameFilter
import java.io.IOException
import java.util.Locale
import android.content.Intent

object FileManager {
    const val FILE_NOTFOUND: Int = 10
    const val ISDIRECTORY: Int = 11
    const val IOERROR: Int = 12

    private const val ASTERISK = "*"
    private val DOT = Tuils.DOT

    fun writeOn(file: File?, text: String): String? {
        try {
            val stream = FileOutputStream(file)
            stream.write(text.toByteArray())
            stream.flush()
            stream.close()
            return null
        } catch (e: FileNotFoundException) {
            return e.toString()
        } catch (e: IOException) {
            return e.toString()
        }
    }

    fun openFile(c: Context, file: File?): Int {
        if (file == null) {
            return FILE_NOTFOUND
        }
        if (file.isDirectory()) {
            return ISDIRECTORY
        }

        val intent = Tuils.openFile(c, file)

        c.startActivity(intent)
        return 0
    }

    fun cd(currentDirectory: File?, path: String?): DirInfo {
        var path = path ?: Tuils.EMPTYSTRING
        var file: File?
        var notFound: String? = ""

        //        path == "/" (root folder)
        if (path == File.separator) return DirInfo(File(path), null)

        //        remove the useless "/" from the end of path
        if (path.endsWith(File.separator)) path = path.substring(0, path.length - 1)

        //        absolute path
        if (path.startsWith(File.separator)) file = File(path)
        else {
//            create a file from the path
            file = File(currentDirectory, path)
            //            assign path
            path = file.getAbsolutePath()
        }

        //        cycle on path until file exists
        var toAdd: String?
        while (!file!!.exists()) {
//            find the last slash
            var slash = path.lastIndexOf(File.separator)
            if (slash == -1) slash = 0

            toAdd = path.substring(slash, path.length)
            //            add a "/" to be sure
            if (!toAdd.startsWith(File.separator)) toAdd = toAdd + File.separator
            //            toadd is concatenated at the end of not found
            notFound = toAdd + notFound

            //            adjust path
            file = file.getParentFile()
            path = file.getAbsolutePath()
        }

        //        ok, now file exists, path = file absolute path

//        check if path contains ".."
        var cut: String
        var pathSection: String
        var count = path.length
        while (path.contains("..")) {
            pathSection = path.substring(0, count)
            count = pathSection.lastIndexOf(File.separator)

            //            get the part between "/" and end of path or "/"
            cut = pathSection.substring(count + 1, pathSection.length)
            //            if cut is ..
            if (cut == "..") {
//                find the slash before count
                val preSlash = path.substring(0, count).lastIndexOf(File.separator)
                //                find the part after ".."
                val rightPart = path.substring(count + cut.length + 1)

                path = path.substring(0, preSlash + 1) + rightPart
            }
        }
        file = File(path)

        if (notFound!!.length <= 0) notFound = null
        else if (notFound.length > 1) {
            if (notFound.startsWith(File.separator)) notFound = notFound.substring(1)
            if (notFound.endsWith(File.separator)) notFound =
                notFound.substring(0, notFound.length - 1)
        }

        return DirInfo(file, notFound)
    }

    fun wildcard(path: String?): WildcardInfo? {
        if (path == null || !path.contains(ASTERISK) || path.contains(File.separator)) {
            return null
        }

        if (path.trim { it <= ' ' } == ASTERISK) {
            return WildcardInfo(true)
        }

        val dot = path.lastIndexOf(DOT)
        try {
            val beforeDot = path.substring(0, dot)
            val afterDot = path.substring(dot + 1)
            return WildcardInfo(beforeDot, afterDot)
        } catch (e: Exception) {
            return null
        }
    }

    class DirInfo(var file: File, var notFound: String?) {
        val completePath: String
            get() = file.getAbsolutePath() + "/" + notFound
    }

    class WildcardInfo {
        var allNames: Boolean = false
        var allExtensions: Boolean = false
        var name: String? = null
        var extension: String? = null

        constructor(name: String, extension: String) {
            this.name = name
            this.extension = extension

            allNames = name.length == 0 || name == ASTERISK
            allExtensions = extension.length == 0 || extension == ASTERISK
        }

        constructor(all: Boolean) {
            if (all) {
                this.allExtensions = all
                this.allNames = all
            }
        }
    }

    class SpecificNameFileFilter : FilenameFilter {
        private var name: String? = null

        fun setName(name: String?) {
            this.name = name?.lowercase(Locale.getDefault())
        }

        override fun accept(dir: File?, filename: String): Boolean {
            var filename = filename
            val dot = filename.lastIndexOf(Tuils.DOT)
            if (dot == -1) {
                return false
            }

            filename = filename.substring(0, dot)
            return filename.lowercase(Locale.getDefault()) == name
        }
    }

    class SpecificExtensionFileFilter : FilenameFilter {
        private var extension: String? = null

        fun setExtension(extension: String?) {
            this.extension = extension?.lowercase(Locale.getDefault())
        }

        override fun accept(dir: File?, filename: String): Boolean {
            val dot = filename.lastIndexOf(Tuils.DOT)
            if (dot == -1) {
                return false
            }

            val fileExtension = filename.substring(dot + 1)
            return fileExtension.lowercase(Locale.getDefault()) == extension
        }
    }
}
