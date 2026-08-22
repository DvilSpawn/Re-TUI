package ohi.andre.consolelauncher.managers.flashlight

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver

object TorchManager {
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null

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
        } catch (e: Exception) {
            sendError(context, e)
        }
    }

    fun turnOff(context: Context) {
        val manager = cameraManager ?: return
        val id = cameraId ?: return
        try {
            manager.setTorchMode(id, false)
        } catch (e: Exception) {
            sendError(context, e)
        } finally {
            cameraManager = null
            cameraId = null
        }
    }

    fun toggle(context: Context) {
        if (isOn()) turnOff(context) else turnOn(context)
    }

    private fun sendError(context: Context, error: Exception) {
        val intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
            .putExtra(PrivateIOReceiver.TEXT, error.toString())
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

}
