package com.aimx.hack

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class LauncherActivity : Activity() {

    companion object {
        private const val REQ_OVERLAY = 1001
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

        startHack()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startHack()
            } else {
                Toast.makeText(this, "Permissão de overlay necessária", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startHack() {
        startForegroundService(Intent(this, HackService::class.java))
        Toast.makeText(this, "AimX Hack iniciado!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
