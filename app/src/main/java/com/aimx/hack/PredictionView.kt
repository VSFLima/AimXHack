package com.aimx.hack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager

class PredictionView(context: Context) : View(context) {

    private var balls = listOf<SimpleVisionEngine.BallInfo>()
    private var table: SimpleVisionEngine.TableInfo? = null

    private val ballColors = mapOf(
        SimpleVisionEngine.BallType.WHITE to Color.WHITE,
        SimpleVisionEngine.BallType.BLACK to Color.BLACK,
        SimpleVisionEngine.BallType.SOLID to Color.rgb(220, 30, 30),
        SimpleVisionEngine.BallType.STRIPED to Color.rgb(30, 30, 180),
        SimpleVisionEngine.BallType.UNKNOWN to Color.GRAY
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        OverlayUtils.overlayWindowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT
    )

    fun update(balls: List<SimpleVisionEngine.BallInfo>, table: SimpleVisionEngine.TableInfo?) {
        this.balls = balls
        this.table = table
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw table border
        table?.let {
            paint.color = Color.argb(80, 0, 200, 0)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRect(it.left, it.top, it.right, it.bottom, paint)

            // Draw holes
            for (hole in it.holes) {
                paint.color = Color.argb(150, 0, 0, 0)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(hole.first, hole.second, 15f, paint)
            }
        }

        // Draw balls
        for (ball in balls) {
            val color = ballColors[ball.type] ?: Color.GRAY

            // Fill
            paint.color = color
            paint.style = Paint.Style.FILL
            canvas.drawCircle(ball.x, ball.y, ball.radius, paint)

            // Border
            paint.color = Color.argb(180, 255, 255, 255)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawCircle(ball.x, ball.y, ball.radius, paint)

            // Label
            when (ball.type) {
                SimpleVisionEngine.BallType.WHITE -> {
                    paint.color = Color.YELLOW
                    paint.textSize = 14f
                    paint.textAlign = Paint.Align.CENTER
                    paint.style = Paint.Style.FILL
                    canvas.drawText("W", ball.x, ball.y + 5f, paint)
                }
                SimpleVisionEngine.BallType.BLACK -> {
                    paint.color = Color.WHITE
                    paint.textSize = 14f
                    paint.textAlign = Paint.Align.CENTER
                    paint.style = Paint.Style.FILL
                    canvas.drawText("8", ball.x, ball.y + 5f, paint)
                }
                else -> {}
            }
        }

        // Draw info
        paint.color = Color.argb(200, 0, 230, 118)
        paint.textSize = 24f
        paint.textAlign = Paint.Align.LEFT
        paint.style = Paint.Style.FILL
        canvas.drawText("AimX Hack | Bolas: ${balls.size}", 40f, 60f, paint)
    }
}
