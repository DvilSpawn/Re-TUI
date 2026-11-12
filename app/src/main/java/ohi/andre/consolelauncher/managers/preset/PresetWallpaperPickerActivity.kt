package ohi.andre.consolelauncher.managers.preset

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.localization.LocalizedComponentActivity
import ohi.andre.consolelauncher.tuils.Tuils

/** Picks the image a preset keeps as its wallpaper. The photo picker needs no storage permission. */
class PresetWallpaperPickerActivity : LocalizedComponentActivity() {
    private val pick = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val name = intent.getStringExtra(EXTRA_PRESET)
        if (uri != null && name != null) {
            runCatching { PresetWallpaper.setImage(this, name, uri) }
                .onSuccess { Tuils.sendOutput(this, getString(R.string.preset_wallpaper_saved, name)) }
                .onFailure { Tuils.sendOutput(this, getString(R.string.preset_wallpaper_failed)) }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    companion object {
        private const val EXTRA_PRESET = "preset"

        /** After a save that could not tell the wallpaper: offer to pick the image, or keep the preset without one. */
        fun ask(context: Context, name: String) = TuixtDialog.showConfirm(
            context,
            context.getString(R.string.preset_wallpaper_ask_title),
            context.getString(R.string.preset_wallpaper_ask_message, name),
            context.getString(R.string.preset_wallpaper_choose),
            context.getString(R.string.preset_wallpaper_skip),
            TuixtDialog.ConfirmAction {
                context.startActivity(Intent(context, PresetWallpaperPickerActivity::class.java)
                    .putExtra(EXTRA_PRESET, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        )
    }
}
