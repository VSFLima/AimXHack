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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        OverlayUtils.overlayWindowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        alpha = 0.79f  // Samsung bloqueia toques se alpha > 0.80
    }

    fun update(balls: List<SimpleVisionEngine.BallInfo>, table: SimpleVisionEngine.TableInfo?) {
        this.balls = balls
        this.table = table
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // === DEBUG: Mostrar tudo que está sendo detectado ===

        table?.let { t ->
            // Borda da mesa (verde)
            paint.color = Color.argb(120, 0, 255, 0)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRect(t.left, t.top, t.right, t.bottom, paint)

            // Dimensões da mesa
            paint.color = Color.argb(200, 0, 255, 0)
            paint.textSize = 18f
            paint.textAlign = Paint.Align.LEFT
            paint.style = Paint.Style.FILL
            val tableW = t.right - t.left
            val tableH = t.bottom - t.top
            canvas.drawText("Mesa: ${tableW.toInt()}x${tableH.toInt()}px", t.left, t.top - 10f, paint)
            canvas.drawText("Escala: ${String.format("%.2f", t.scale)}px/un", t.left, t.top - 30f, paint)

            // Buracos (círculos pretos)
            for ((i, hole) in t.holes.withIndex()) {
                paint.color = Color.argb(200, 255, 0, 0)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                val pocketRadius = (SimpleVisionEngine.POCKET_RADIUS_GAME * t.scale)
                canvas.drawCircle(hole.first, hole.second, pocketRadius, paint)

                // Label do buraco
                paint.color = Color.argb(255, 255, 255, 0)
                paint.textSize = 14f
                paint.textAlign = Paint.Align.CENTER
                paint.style = Paint.Style.FILL
                canvas.drawText("B$i", hole.first, hole.second + 5f, paint)
            }
        }

        // Bolas detectadas
        for (ball in balls) {
            val color = when (ball.type) {
                SimpleVisionEngine.BallType.WHITE -> Color.WHITE
                SimpleVisionEngine.BallType.BLACK -> Color.BLACK
                SimpleVisionEngine.BallType.SOLID -> Color.rgb(255, 50, 50)
                SimpleVisionEngine.BallType.STRIPED -> Color.rgb(50, 50, 255)
                SimpleVisionEngine.BallType.UNKNOWN -> Color.GRAY
            }

            // Círculo da bola (preenchimento)
            paint.color = Color.argb(150, Color.red(color), Color.green(color), Color.blue(color))
            paint.style = Paint.Style.FILL
            canvas.drawCircle(ball.x, ball.y, ball.radius, paint)

            // Círculo da bola (borda)
            paint.color = color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawCircle(ball.x, ball.y, ball.radius, paint)

            // Label da bola
            val label = when (ball.type) {
                SimpleVisionEngine.BallType.WHITE -> "W"
                SimpleVisionEngine.BallType.BLACK -> "8"
                SimpleVisionEngine.BallType.SOLID -> "S"
                SimpleVisionEngine.BallType.STRIPED -> "L"
                SimpleVisionEngine.BallType.UNKNOWN -> "?"
            }
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.CENTER
            paint.style = Paint.Style.FILL
            canvas.drawText(label, ball.x, ball.y + 6f, paint)

            // Coordenadas
            paint.color = Color.argb(180, 200, 200, 200)
            paint.textSize = 10f
            canvas.drawText("${ball.x.toInt()},${ball.y.toInt()}", ball.x, ball.y + ball.radius + 15f, paint)
        }

        // === Painel de status (topo) ===
        paint.color = Color.argb(200, 0, 0, 0)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), 80f, paint)

        paint.color = Color.argb(255, 0, 230, 118)
        paint.textSize = 22f
        paint.textAlign = Paint.Align.LEFT
        paint.style = Paint.Style.FILL
        canvas.drawText("AimX Hack", 20f, 30f, paint)

        paint.color = Color.WHITE
        paint.textSize = 16f
        canvas.drawText("Bolas: ${balls.size} | Mesa: ${if (table != null) "OK" else "N/A"}", 20f, 55f, paint)

        // Tipo das bolas
        val solids = balls.count { it.type == SimpleVisionEngine.BallType.SOLID }
        val striped = balls.count { it.type == SimpleVisionEngine.BallType.STRIPED }
        val white = balls.count { it.type == SimpleVisionEngine.BallType.WHITE }
        val black = balls.count { it.type == SimpleVisionEngine.BallType.BLACK }
        canvas.drawText("S:$solids L:$striped W:$white 8:$black", 20f, 75f, paint)
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false
}
