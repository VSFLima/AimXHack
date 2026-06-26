package com.aimx.hack

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQ_OVERLAY = 1001
        private const val REQ_CAPTURE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (HackService.isRunning) {
            Toast.makeText(this, "AimX já ativo!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                REQ_OVERLAY
            )
            return
        }

        requestCapture()
    }

    private fun requestCapture() {
        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(pm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQ_OVERLAY -> {
                if (Settings.canDrawOverlays(this)) {
                    requestCapture()
                } else {
                    Toast.makeText(this, "Permissão necessária", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            REQ_CAPTURE -> {
                if (resultCode == RESULT_OK && data != null) {
                    HackService.pendingResultCode = resultCode
                    HackService.pendingData = data
                    startForegroundService(Intent(this, HackService::class.java))
                    Toast.makeText(this, "AimX ativo!", Toast.LENGTH_SHORT).show()
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 500)
                } else {
                    Toast.makeText(this, "Permissão negada", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
