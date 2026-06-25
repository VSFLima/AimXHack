package com.aimx.hack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager

class HackService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var predictionView: PredictionView
    private lateinit var floatingMenu: FloatingMenu

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var processHandler: Handler? = null

    private var isRunning = false
    private var showLines = true
    private var showShotState = true

    // Vision engine for ball detection
    private val visionEngine = SimpleVisionEngine()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val thread = HandlerThread("HackProcess").also { it.start() }
        processHandler = Handler(thread.looper)

        startForeground(2, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true

            val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
            val data = intent?.getParcelableExtra<Intent>("data")

            setupOverlay()

            if (resultCode == Activity.RESULT_OK && data != null) {
                startScreenCapture(resultCode, data)
            }
        }
        return START_STICKY
    }

    private fun setupOverlay() {
        predictionView = PredictionView(this)
        windowManager.addView(predictionView, predictionView.layoutParams)

        floatingMenu = FloatingMenu(this, windowManager, object : FloatingMenu.Callbacks {
            override fun onToggleLines(enabled: Boolean) { showLines = enabled }
            override fun onToggleShotState(enabled: Boolean) { showShotState = enabled }
            override fun onToggleAutoAim(enabled: Boolean) { /* TODO */ }
        })
        floatingMenu.attach()
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        if (mediaProjection == null) return

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val screenDensity = metrics.densityDpi

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            processHandler?.post {
                val image = reader.acquireLatestImage() ?: return@post
                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * screenWidth

                    val bitmap = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                    bitmap.recycle()

                    processFrame(cropped)
                    cropped.recycle()
                } catch (e: Exception) {
                    android.util.Log.e("AimXHack", "Frame error: ${e.message}")
                } finally {
                    image.close()
                }
            }
        }, processHandler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AimXHack",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            processHandler
        )
    }

    private fun processFrame(bitmap: Bitmap) {
        try {
            // Detect table
            val table = visionEngine.detectTable(bitmap)
            // Detect balls
            val balls = visionEngine.detectBalls(bitmap, table)
            // Update overlay
            predictionView.update(balls, table)
        } catch (e: Exception) {
            android.util.Log.e("AimXHack", "Process error: ${e.message}")
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
            .setContentText("Analisando tela do jogo...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        floatingMenu.detach()
        try { windowManager.removeView(predictionView) } catch (_: Exception) {}
        super.onDestroy()
    }
}
