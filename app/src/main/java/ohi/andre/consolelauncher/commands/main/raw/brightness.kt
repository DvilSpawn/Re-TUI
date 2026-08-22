package ohi.andre.consolelauncher.commands.main.raw

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import android.provider.Settings.System.SCREEN_BRIGHTNESS
import android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE
import android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
import android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL

class brightness : CommandAbstraction {
    @Throws(Exception::class)
    override fun exec(pack: ExecutePack): String? {
        if (!Settings.System.canWrite(pack.context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:" + pack.context.packageName)
            pack.context.startActivity(intent)
            return pack.context.getString(R.string.output_waitingpermission)
        }

        val brightness = pack.getInt()

        (pack.context as Activity).runOnUiThread {
            var b = brightness

            if (b < 0) {
                b = 0
            } else if (b > 100) {
                b = 100
            }

            b = b * 255 / 100

            val autobrightnessState = try {
                Settings.System.getInt(pack.context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            } catch (e: Exception) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            }

            if (autobrightnessState == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                Settings.System.putInt(
                    pack.context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
            }

            val contentResolver: ContentResolver = pack.context.applicationContext.contentResolver
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, b)

            refreshBrightness((pack.context as Activity).window, b.toFloat())
        }

        return null
    }

    private fun refreshBrightness(window: Window, brightness: Float) {
        val lp: WindowManager.LayoutParams = window.attributes
        if (brightness < 0) {
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            lp.screenBrightness = brightness
        }
        window.attributes = lp
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.INT)

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_brightness

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String =
        pack.context.getString(R.string.invalid_integer)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String =
        pack.context.getString(helpRes())
}
