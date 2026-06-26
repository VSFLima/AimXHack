package com.aimx.hack

import android.util.Log
import kotlin.math.*

class PhysicsEngine {

    companion object {
        private const val TAG = "AimXHack"
        private const val BALL_RADIUS = 3.800475f
        private const val TABLE_W = 254f
        private const val TABLE_H = 127f
        private const val POCKET_RADIUS = 8f
        private const val TIME_STEP = 0.005f
        private const val CUSHION_RESTITUTION = 0.804f
        private const val FRICTION = 0.985f
    }

    data class Point(val x: Float, val y: Float)
    data class BallData(val gameX: Float, val gameY: Float, val type: String)

    private val pockets = arrayOf(
        Point(-130.8f, -67.3f), Point(0f, -72f), Point(130.8f, -67.3f),
        Point(130.8f, 67.3f), Point(0f, 72f), Point(-130.8f, 67.3f)
    )

    fun findBestShot(
        cueBall: SimpleVisionEngine.BallInfo,
        balls: List<BallData>,
        table: SimpleVisionEngine.TableInfo
    ): List<Point>? {
        val cueGameX = (cueBall.x - table.left) / table.scale - 127f
        val cueGameY = (cueBall.y - table.top) / table.scale - 63.5f

        var bestScore = -1f
        var bestTrajectory: List<Point>? = null

        // Test angles in 5 degree steps
        for (angleDeg in 0 until 360 step 5) {
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
            val trajectory = simulateShot(cueGameX, cueGameY, angleRad, 500f, balls)

            // Score: more points = better
            val score = trajectory.size.toFloat()
            if (score > bestScore) {
                bestScore = score
                bestTrajectory = trajectory
            }
        }

        return bestTrajectory
    }

    private fun simulateShot(
        startX: Float, startY: Float,
        angle: Float, power: Float,
        balls: List<BallData>
    ): List<Point> {
        var x = startX
        var y = startY
        var vx = power * cos(angle)
        var vy = power * sin(angle)

        val trajectory = mutableListOf(Point(x, y))

        for (tick in 0 until 2000) {
            // Move
            x += vx * TIME_STEP
            y += vy * TIME_STEP

            // Wall collisions
            if (x <= -TABLE_W / 2 + BALL_RADIUS) {
                x = -TABLE_W / 2 + BALL_RADIUS
                vx = -vx * CUSHION_RESTITUTION
            } else if (x >= TABLE_W / 2 - BALL_RADIUS) {
                x = TABLE_W / 2 - BALL_RADIUS
                vx = -vx * CUSHION_RESTITUTION
            }

            if (y <= -TABLE_H / 2 + BALL_RADIUS) {
                y = -TABLE_H / 2 + BALL_RADIUS
                vy = -vy * CUSHION_RESTITUTION
            } else if (y >= TABLE_H / 2 - BALL_RADIUS) {
                y = TABLE_H / 2 - BALL_RADIUS
                vy = -vy * CUSHION_RESTITUTION
            }

            // Ball collisions
            for (ball in balls) {
                if (ball.type == "WHITE") continue
                val dx = x - ball.gameX
                val dy = y - ball.gameY
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < BALL_RADIUS * 2 && dist > 0) {
                    val nx = dx / dist
                    val ny = dy / dist
                    val dot = vx * nx + vy * ny
                    vx -= 2 * dot * nx
                    vy -= 2 * dot * ny
                    vx *= 0.95f
                    vy *= 0.95f
                }
            }

            // Pocket check
            for (pocket in pockets) {
                val dx = x - pocket.x
                val dy = y - pocket.y
                if (dx * dx + dy * dy < POCKET_RADIUS * POCKET_RADIUS) {
                    trajectory.add(Point(x, y))
                    return trajectory
                }
            }

            // Friction
            val speed = sqrt(vx * vx + vy * vy)
            if (speed < 1f) break
            vx *= FRICTION
            vy *= FRICTION

            // Add point if moved enough
            val last = trajectory.last()
            if (abs(x - last.x) > 2f || abs(y - last.y) > 2f) {
                trajectory.add(Point(x, y))
            }
        }

        return trajectory
    }
}
