package com.aimx.hack

import android.app.Activity
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
import android.util.Log
import android.view.Gravity
import android.view.WindowManager

class HackService : Service() {

    companion object {
        private const val TAG = "AimXHack"
        var isRunning = false
        var pendingResultCode: Int = Activity.RESULT_CANCELED
        var pendingData: Intent? = null
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: OverlayView? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var processHandler: Handler? = null
    private val visionEngine = SimpleVisionEngine()
    private val physicsEngine = PhysicsEngine()
    private var isCapturing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val thread = HandlerThread("AimXProcess").also { it.start() }
        processHandler = Handler(thread.looper)
        startForeground(2, createNotification())
        isRunning = true
        setupOverlay()
        startCapture()
    }

    private fun setupOverlay() {
        overlayView = OverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.MANUFACTURER.equals("samsung", ignoreCase = true))
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else
                OverlayUtils.overlayWindowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 0.79f
        }
        try {
            windowManager.addView(overlayView, params)
            Log.d(TAG, "Overlay added")
        } catch (e: Exception) {
            Log.e(TAG, "Overlay error: ${e.message}")
        }
    }

    private fun startCapture() {
        if (pendingResultCode != Activity.RESULT_OK || pendingData == null) {
            Log.e(TAG, "No capture permission")
            return
        }

        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = pm.getMediaProjection(pendingResultCode, pendingData!!)
        pendingData = null

        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection null")
            return
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val captureW = metrics.widthPixels / 2
        val captureH = metrics.heightPixels / 2

        imageReader = ImageReader.newInstance(captureW, captureH, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            processHandler?.post {
                val image = reader.acquireLatestImage() ?: return@post
                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * captureW

                    val bitmap = Bitmap.createBitmap(
                        captureW + rowPadding / pixelStride, captureH,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, captureW, captureH)
                    bitmap.recycle()

                    processFrame(cropped)
                    cropped.recycle()
                } catch (e: Exception) {
                    Log.e(TAG, "Frame error: ${e.message}")
                } finally {
                    image.close()
                }
            }
        }, processHandler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AimXHack", captureW, captureH, metrics.densityDpi / 2,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, processHandler
        )

        isCapturing = true
        Log.d(TAG, "Capture started: ${captureW}x${captureH}")
    }

    private fun processFrame(bitmap: Bitmap) {
        try {
            val table = visionEngine.detectTable(bitmap) ?: return
            val balls = visionEngine.detectBalls(bitmap, table)
            val limitedBalls = if (balls.size > 16) balls.sortedByDescending { it.radius }.take(16) else balls

            // Find cue ball and calculate trajectory
            val cueBall = limitedBalls.find { it.type == SimpleVisionEngine.BallType.WHITE }
            var trajectory: List<PhysicsEngine.Point>? = null

            if (cueBall != null) {
                val gameBalls = limitedBalls.map { b ->
                    PhysicsEngine.BallData(
                        gameX = (b.x - table.left) / table.scale - 127f,
                        gameY = (b.y - table.top) / table.scale - 63.5f,
                        type = b.type.name
                    )
                }
                trajectory = physicsEngine.findBestShot(cueBall, gameBalls, table)
            }

            overlayView?.update(limitedBalls, table, trajectory)
        } catch (e: Exception) {
            Log.e(TAG, "Process error: ${e.message}")
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
            .setContentText("Detectando bolas...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "PARAR", stopIntent).build())
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        isCapturing = false
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        try { overlayView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        overlayView = null
        super.onDestroy()
    }
}
