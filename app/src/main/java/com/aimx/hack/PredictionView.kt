package com.aimx.hack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager

class PredictionView(context: Context) : View(context) {

    private var trajectories = listOf<NativeBridge.BallTrajectory>()
    private var pockets = listOf<NativeBridge.PocketState>()

    private val ballColors = intArrayOf(
        Color.WHITE,
        Color.rgb(220, 30, 30),
        Color.rgb(200, 100, 20),
        Color.rgb(230, 200, 20),
        Color.rgb(30, 140, 30),
        Color.rgb(30, 30, 180),
        Color.rgb(120, 20, 120),
        Color.rgb(20, 20, 20),
        Color.rgb(200, 30, 200),
        Color.rgb(200, 130, 20),
        Color.rgb(100, 200, 30),
        Color.rgb(20, 180, 100),
        Color.rgb(60, 80, 180),
        Color.rgb(100, 60, 160),
        Color.rgb(140, 30, 60),
        Color.rgb(80, 80, 80)
    )

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        OverlayUtils.overlayWindowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT
    )

    fun update(result: NativeBridge.PredictionResult) {
        trajectories = result.trajectories
        pockets = result.pockets
        postInvalidate()
    }

    fun clear() {
        trajectories = emptyList()
        pockets = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (traj in trajectories) {
            if (traj.positions.size < 2) continue
            val color = ballColors.getOrElse(traj.index) { Color.GRAY }

            linePaint.color = color
            linePaint.style = Paint.Style.STROKE
            linePaint.strokeWidth = 3f

            path.reset()
            val start = traj.positions[0]
            path.moveTo(start.first, start.second)
            for (i in 1 until traj.positions.size) {
                val p = traj.positions[i]
                path.lineTo(p.first, p.second)
            }
            canvas.drawPath(path, linePaint)

            val end = traj.positions.last()
            circlePaint.color = color
            circlePaint.style = Paint.Style.FILL
            canvas.drawCircle(end.first, end.second, 8f, circlePaint)

            circlePaint.color = Color.WHITE
            circlePaint.style = Paint.Style.STROKE
            circlePaint.strokeWidth = 2f
            canvas.drawCircle(end.first, end.second, 8f, circlePaint)
        }

        for (pocket in pockets) {
            val color = if (pocket.active) Color.argb(180, 0, 255, 0) else Color.argb(80, 255, 0, 0)
            circlePaint.color = color
            circlePaint.style = Paint.Style.STROKE
            circlePaint.strokeWidth = 4f
            canvas.drawCircle(pocket.x, pocket.y, 20f, circlePaint)
        }
    }
}
