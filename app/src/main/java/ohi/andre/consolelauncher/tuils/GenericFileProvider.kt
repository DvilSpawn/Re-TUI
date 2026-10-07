package ohi.andre.consolelauncher.tuils

import androidx.core.content.FileProvider
import ohi.andre.consolelauncher.BuildConfig

class GenericFileProvider : FileProvider() {
    override fun getType(uri: android.net.Uri): String? {
        // Resolve through FileProvider first so invalid or escaping URIs are rejected.
        val original = super.getType(uri)
        return if (uri.lastPathSegment?.endsWith(".retui", ignoreCase = true) == true)
            ohi.andre.consolelauncher.managers.BackupManager.PRESET_MIME else original
    }

    companion object {
        @JvmField
        val PROVIDER_NAME: String = BuildConfig.APPLICATION_ID + ".FILE_PROVIDER"
    }
}
