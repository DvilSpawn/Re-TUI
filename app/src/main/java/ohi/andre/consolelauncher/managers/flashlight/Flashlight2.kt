package ohi.andre.consolelauncher.managers.flashlight

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver

class Flashlight2(context: Context) : Flashlight(context) {
    private var mCameraIDList: Array<String>? = null
    private var flashSupported = false

    override fun turnOn() {
        if (!getStatus()) {
            val cameraManager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            try {
                mCameraIDList = cameraManager.cameraIdList
            } catch (e: CameraAccessException) {
                sendError(e)
                return
            }

            try {
                val cameraParameters = cameraManager.getCameraCharacteristics(mCameraIDList!![0])
                flashSupported = cameraParameters.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)!!
            } catch (e: Exception) {
                sendError(e)
                return
            }

            if (flashSupported) {
                try {
                    cameraManager.setTorchMode(mCameraIDList!![0], true)
                    updateStatus(true)
                } catch (e: CameraAccessException) {
                    sendError(e)
                }
            }
        }
    }

    override fun turnOff() {
        if (getStatus()) {
            if (mCameraIDList != null && flashSupported) {
                val cameraManager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                try {
                    cameraManager.setTorchMode(mCameraIDList!![0], false)
                } catch (e: CameraAccessException) {
                    sendError(e)
                    return
                }
                updateStatus(false)
            }
        }
    }

    private fun sendError(e: Exception) {
        val intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
        intent.putExtra(PrivateIOReceiver.TEXT, e.toString())
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent)
    }

    companion object {
        const val TYPE: String = Constants.ID_DEVICE_OUTPUT_TORCH_FLASH_NEW
    }
}
