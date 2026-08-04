package ohi.andre.consolelauncher.managers.flashlight

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver

class TorchManager private constructor() {
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var context: Context? = null

    fun isOn(): Boolean = cameraId != null

    fun turnOn(context: Context) {
        if (isOn()) return

        try {
            val manager = context.getSystemService(CameraManager::class.java)
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            manager.setTorchMode(id, true)
            cameraManager = manager
            cameraId = id
            this.context = context.applicationContext
        } catch (e: Exception) {
            sendError(context, e)
        }
    }

    fun turnOff() {
        val manager = cameraManager ?: return
        val id = cameraId ?: return
        try {
            manager.setTorchMode(id, false)
            cameraManager = null
            cameraId = null
            context = null
        } catch (e: Exception) {
            context?.let { sendError(it, e) }
        }
    }

    fun toggle(context: Context) {
        if (isOn()) turnOff() else turnOn(context)
    }

    private fun sendError(context: Context, error: Exception) {
        val intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
            .putExtra(PrivateIOReceiver.TEXT, error.toString())
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    companion object {
        private var mInstance: TorchManager? = null

        @JvmStatic
        fun getInstance(): TorchManager {
            if (mInstance == null) {
                mInstance = TorchManager()
            }
            return mInstance!!
        }
    }
}
