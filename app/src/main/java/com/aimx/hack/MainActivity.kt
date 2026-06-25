package com.aimx.hack

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQ_OVERLAY = 1001
        private const val REQ_CAPTURE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQ_OVERLAY)
            return
        }

        startCapture()
    }

    private fun startCapture() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_OVERLAY -> {
                if (Settings.canDrawOverlays(this)) startCapture()
                else {
                    Toast.makeText(this, "Permissão de overlay necessária", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            REQ_CAPTURE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val serviceIntent = Intent(this, HackService::class.java)
                    serviceIntent.putExtra("resultCode", resultCode)
                    serviceIntent.putExtra("data", data)
                    startForegroundService(serviceIntent)
                    Toast.makeText(this, "AimX Hack iniciado!", Toast.LENGTH_SHORT).show()
                    moveTaskToBack(true)
                } else {
                    Toast.makeText(this, "Permissão de captura negada", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
