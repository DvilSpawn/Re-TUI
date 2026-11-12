package ohi.andre.consolelauncher.tuils

import android.webkit.MimeTypeMap
import java.util.Locale
import java.util.HashMap

object MimeTypes {
    const val ALL_MIME_TYPES = "*/*"

    private val MIME_TYPES = HashMap<String, String>(1 + (66 / 0.75).toInt()).apply {
        put("asm", "text/x-asm")
        put("json", "application/json")
        put("js", "application/javascript")

        put("def", "text/plain")
        put("in", "text/plain")
        put("rc", "text/plain")
        put("list", "text/plain")
        put("log", "text/plain")
        put("pl", "text/plain")
        put("prop", "text/plain")
        put("properties", "text/plain")
        put("rc", "text/plain")
        put("ini", "text/plain")
        put("md", "text/markdown")

        put("epub", "application/epub+zip")
        put("ibooks", "application/x-ibooks+zip")

        put("ifb", "text/calendar")
        put("eml", "message/rfc822")
        put("msg", "application/vnd.ms-outlook")

        put("ace", "application/x-ace-compressed")
        put("bz", "application/x-bzip")
        put("bz2", "application/x-bzip2")
        put("cab", "application/vnd.ms-cab-compressed")
        put("gz", "application/x-gzip")
        put("lrf", "application/octet-stream")
        put("jar", "application/java-archive")
        put("xz", "application/x-xz")
        put("Z", "application/x-compress")

        put("bat", "application/x-msdownload")
        put("ksh", "text/plain")
        put("sh", "application/x-sh")

        put("db", "application/octet-stream")
        put("db3", "application/octet-stream")

        put("otf", "application/x-font-otf")
        put("ttf", "application/x-font-ttf")
        put("psf", "application/x-font-linux-psf")

        put("cgm", "image/cgm")
        put("btif", "image/prs.btif")
        put("dwg", "image/vnd.dwg")
        put("dxf", "image/vnd.dxf")
        put("fbs", "image/vnd.fastbidsheet")
        put("fpx", "image/vnd.fpx")
        put("fst", "image/vnd.fst")
        put("mdi", "image/vnd.ms-mdi")
        put("npx", "image/vnd.net-fpx")
        put("xif", "image/vnd.xiff")
        put("pct", "image/x-pict")
        put("pic", "image/x-pict")

        put("adp", "audio/adpcm")
        put("au", "audio/basic")
        put("snd", "audio/basic")
        put("m2a", "audio/mpeg")
        put("m3a", "audio/mpeg")
        put("oga", "audio/ogg")
        put("spx", "audio/ogg")
        put("aac", "audio/x-aac")
        put("mka", "audio/x-matroska")

        put("jpgv", "video/jpeg")
        put("jpgm", "video/jpm")
        put("jpm", "video/jpm")
        put("mj2", "video/mj2")
        put("mjp2", "video/mj2")
        put("mpa", "video/mpeg")
        put("ogv", "video/ogg")
        put("flv", "video/x-flv")
        put("mkv", "video/x-matroska")
    }

    @JvmStatic
    fun getMimeType(path: String, isDirectory: Boolean): String? {
        if (isDirectory) {
            return null
        }

        var type: String? = ALL_MIME_TYPES
        val extension = getExtension(path)

        if (extension.isNotEmpty()) {
            val extensionLowerCase = extension.lowercase(Locale.getDefault())
            val mime = MimeTypeMap.getSingleton()
            type = mime.getMimeTypeFromExtension(extensionLowerCase)
            if (type == null) {
                type = MIME_TYPES[extensionLowerCase]
            }
        }
        if (type == null) {
            type = ALL_MIME_TYPES
        }
        return type
    }

    @JvmStatic
    fun getExtension(path: String): String =
        if (path.contains(".")) path.substring(path.lastIndexOf(".") + 1).lowercase(Locale.getDefault()) else ""
}
