package ohi.andre.consolelauncher.managers.media

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.localization.LocalizedComponentActivity
import ohi.andre.consolelauncher.tuils.Tuils

/** Opens the system folder picker for `media -add`, keeps the chosen folder, and scans the library again. */
class MediaFolderPickerActivity : LocalizedComponentActivity() {
    private val pick = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching { MediaLibrary.addFolder(this, uri) }
                .onSuccess { MediaScan.start(applicationContext, getString(R.string.media_added, MediaLibrary.label(uri))) }
                .onFailure { Tuils.sendOutput(this, getString(R.string.media_add_failed)) }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pick.launch(null)
    }
}
