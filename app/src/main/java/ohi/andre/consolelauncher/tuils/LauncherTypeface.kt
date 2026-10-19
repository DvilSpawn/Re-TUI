package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Typeface
import java.io.File
import java.util.Locale

/** Reads a font selection without loading/writing preferences or changing the launcher font cache. */
internal object LauncherTypeface {
    data class Result(val typeface:Typeface,val path:String?,val legacyFileName:String?=null,val customFontError:Exception?=null)

    fun load(context:Context,folder:File?,systemFont:Boolean,configuredFont:String?):Result {
        if(systemFont) return Result(Typeface.DEFAULT,null)
        val configured=resolveConfiguredFile(folder,configuredFont)
        val font=configured?:resolveLegacyFile(folder)
        val legacy=font?.name?.takeIf { configured==null && (configuredFont==null || configuredFont.trim { it<=' ' }.isEmpty()) }
        var error:Exception?=null
        if(font!=null && font.exists() && font.length()>0) {
            try {
                val face=if(android.os.Build.VERSION.SDK_INT>=26) Typeface.Builder(font).build()
                    ?:throw IllegalArgumentException("Selected file is not a valid font")
                else Typeface.createFromFile(font)
                return Result(face,font.absolutePath,legacy)
            }
            catch(failure:Exception) { error=failure }
        }
        return try {
            Result(Typeface.createFromAsset(context.assets,Tuils.BUNDLED_FONT_ASSET),"asset://${Tuils.BUNDLED_FONT_ASSET}",legacy,error)
        } catch(_:Exception) {
            Result(Typeface.MONOSPACE,null,legacy,error)
        }
    }

    fun resolveConfiguredFile(folder:File?,configuredFont:String?):File? {
        if(folder==null || configuredFont==null) return null
        val name=configuredFont.trim { it<=' ' }
        if(name.isEmpty()) return null
        val direct=File(folder,name)
        if(direct.exists() && direct.isFile) return direct
        val nested=File(File(folder,"fonts"),name)
        return nested.takeIf { it.exists() && it.isFile }
    }

    private fun resolveLegacyFile(folder:File?):File? = folder?.listFiles()?.firstOrNull { file ->
        !file.isDirectory && file.name.lowercase(Locale.getDefault()).let { it.endsWith(".ttf") || it.endsWith(".otf") }
    }
}
