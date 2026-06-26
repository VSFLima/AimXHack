package com.aimx.hack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class OverlayView(context: Context) : View(context) {

    private var balls = listOf<SimpleVisionEngine.BallInfo>()
    private var table: SimpleVisionEngine.TableInfo? = null
    private var trajectory: List<PhysicsEngine.Point>? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun update(
        balls: List<SimpleVisionEngine.BallInfo>,
        table: SimpleVisionEngine.TableInfo,
        trajectory: List<PhysicsEngine.Point>?
    ) {
        this.balls = balls
        this.table = table
        this.trajectory = trajectory
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw table border
        table?.let {
            paint.color = Color.argb(100, 0, 255, 0)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRect(it.left, it.top, it.right, it.bottom, paint)
        }

        // Draw balls
        for (ball in balls) {
            val color = when (ball.type) {
                SimpleVisionEngine.BallType.WHITE -> Color.WHITE
                SimpleVisionEngine.BallType.BLACK -> Color.BLACK
                SimpleVisionEngine.BallType.SOLID -> Color.rgb(255, 50, 50)
                SimpleVisionEngine.BallType.STRIPED -> Color.rgb(50, 150, 255)
                SimpleVisionEngine.BallType.UNKNOWN -> Color.GRAY
            }

            paint.color = color
            paint.style = Paint.Style.FILL
            canvas.drawCircle(ball.x.toFloat(), ball.y.toFloat(), ball.radius.toFloat(), paint)

            paint.color = Color.argb(200, 255, 255, 255)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawCircle(ball.x.toFloat(), ball.y.toFloat(), ball.radius.toFloat(), paint)
        }

        // Draw trajectory
        trajectory?.let { traj ->
            if (traj.size >= 2) {
                // Convert game coordinates to screen coordinates
                table?.let { t ->
                    path.reset()
                    val first = traj[0]
                    val screenX0 = (first.x + 127f) * t.scale + t.left
                    val screenY0 = (first.y + 63.5f) * t.scale + t.top
                    path.moveTo(screenX0, screenY0)

                    for (i in 1 until traj.size) {
                        val p = traj[i]
                        val sx = (p.x + 127f) * t.scale + t.left
                        val sy = (p.y + 63.5f) * t.scale + t.top
                        path.lineTo(sx, sy)
                    }

                    paint.color = Color.argb(200, 255, 0, 0)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    canvas.drawPath(path, paint)

                    // Draw end point
                    val last = traj.last()
                    val endX = (last.x + 127f) * t.scale + t.left
                    val endY = (last.y + 63.5f) * t.scale + t.top
                    paint.color = Color.argb(200, 0, 255, 0)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(endX, endY, 8f, paint)
                }
            }
        }

        // Status bar
        paint.color = Color.argb(200, 0, 0, 0)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, 500f, 60f, paint)

        paint.color = Color.rgb(0, 230, 118)
        paint.textSize = 22f
        paint.textAlign = Paint.Align.LEFT
        paint.style = Paint.Style.FILL
        canvas.drawText("AimX | Bolas: ${balls.size}", 20f, 25f, paint)

        table?.let {
            paint.color = Color.rgb(200, 200, 200)
            paint.textSize = 16f
            canvas.drawText("Mesa: ${it.right - it.left}x${it.bottom - it.top}", 20f, 48f, paint)
        }
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false
}
