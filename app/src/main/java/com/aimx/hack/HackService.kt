package com.aimx.hack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.WindowManager

class HackService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var nativeBridge: NativeBridge
    private lateinit var predictionView: PredictionView
    private lateinit var floatingMenu: FloatingMenu
    private lateinit var processHandler: Handler

    private var showLines = true
    private var showShotState = true
    private var autoAimEnabled = false
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        nativeBridge = NativeBridge()

        val thread = HandlerThread("HackProcess").also { it.start() }
        processHandler = Handler(thread.looper)

        startForeground(2, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            setupOverlay()
            startPredictionLoop()
        }
        return START_STICKY
    }

    private fun setupOverlay() {
        predictionView = PredictionView(this)
        windowManager.addView(predictionView, predictionView.layoutParams)

        floatingMenu = FloatingMenu(this, windowManager, object : FloatingMenu.Callbacks {
            override fun onToggleLines(enabled: Boolean) { showLines = enabled }
            override fun onToggleShotState(enabled: Boolean) { showShotState = enabled }
            override fun onToggleAutoAim(enabled: Boolean) { autoAimEnabled = enabled }
        })
        floatingMenu.attach()
    }

    private fun startPredictionLoop() {
        processHandler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return
                try {
                    if (!nativeBridge.nativeInit()) {
                        processHandler.postDelayed(this, 1000)
                        return
                    }
                    processFrame()
                } catch (e: Exception) {
                    android.util.Log.e("AimXHack", "Error: ${e.message}")
                }
                processHandler.postDelayed(this, 50)
            }
        })
    }

    private fun processFrame() {
        if (!nativeBridge.nativeIsInGame()) {
            predictionView.clear()
            return
        }
        val result = nativeBridge.getPrediction()
        if (result != null) {
            predictionView.update(result)
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("aimxhack", "AimX Hack", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val stopIntent = PendingIntent.getBroadcast(
            this, 0, Intent(this, StopReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "aimxhack")
            .setContentTitle("AimX Hack Ativo")
            .setContentText("Toque para sair")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        floatingMenu.detach()
        try { windowManager.removeView(predictionView) } catch (_: Exception) {}
        super.onDestroy()
    }
}
