package ohi.andre.consolelauncher.managers

import android.content.Context
import android.provider.Settings

/**
 * Re:TUI Keys ships as com.dvil.retui.keys from 0.2.1; GitHub builds up to 0.2.0 used
 * com.dvil.retui.keyboard. The bridge serves whichever is installed, preferring the active keyboard.
 */
object RetuiKeysPackage {
    const val CURRENT = "com.dvil.retui.keys"
    const val LEGACY = "com.dvil.retui.keyboard"
    private val ALL = listOf(CURRENT, LEGACY)

    fun resolve(context: Context): String? {
        val active = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        }.getOrNull()?.substringBefore('/')
        if (active in ALL) return active
        return ALL.firstOrNull { name ->
            runCatching { context.packageManager.getPackageInfo(name, 0) }.isSuccess
        }
    }
}
