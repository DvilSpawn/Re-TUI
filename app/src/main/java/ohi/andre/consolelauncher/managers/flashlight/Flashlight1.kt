package ohi.andre.consolelauncher.managers.flashlight

import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.hardware.Camera
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver

@Suppress("DEPRECATION")
class Flashlight1(context: Context) : Flashlight(context) {
    private var mCamera: Camera? = null
    private var flashSupported = false

    override fun turnOn() {
        if (ready() && !getStatus()) {
            try {
                mCamera!!.setPreviewTexture(SurfaceTexture(0))
                mCamera!!.startPreview()
                updateStatus(true)
            } catch (e: Exception) {
                if (mCamera != null) {
                    try {
                        mCamera!!.release()
                        mCamera = null
                    } catch (ignored: Exception) {
                    }
                }

                val intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
                intent.putExtra(PrivateIOReceiver.TEXT, e.toString())
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent)
            }
        }
    }

    override fun turnOff() {
        if (getStatus() && mCamera != null) {
            mCamera!!.stopPreview()
            mCamera!!.release()
            mCamera = null
            updateStatus(false)
        }
    }

    private fun ready(): Boolean {
        if (mCamera == null) {
            try {
                mCamera = Camera.open()
            } catch (e: Exception) {
                val intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
                intent.putExtra(PrivateIOReceiver.TEXT, e.toString())
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent)
                return false
            }
        }

        val cameraParameters = mCamera!!.parameters
        val supportedFlashModes = cameraParameters.supportedFlashModes
        if (supportedFlashModes != null) {
            if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                flashSupported = true
                cameraParameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
            } else if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_ON)) {
                flashSupported = true
                cameraParameters.flashMode = Camera.Parameters.FLASH_MODE_ON
            }
        }

        if (flashSupported) {
            try {
                mCamera!!.parameters = cameraParameters
            } catch (e: RuntimeException) {
                val intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
                intent.putExtra(PrivateIOReceiver.TEXT, e.toString())
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent)
                return false
            }
        }
        return true
    }
}
