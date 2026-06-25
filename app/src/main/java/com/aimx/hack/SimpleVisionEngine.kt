package com.aimx.hack

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

class SimpleVisionEngine {

    data class TableInfo(
        val left: Float, val top: Float,
        val right: Float, val bottom: Float,
        val holes: List<Pair<Float, Float>>
    )

    data class BallInfo(
        val x: Float, val y: Float,
        val radius: Float,
        val type: BallType
    )

    enum class BallType { SOLID, STRIPED, BLACK, WHITE, UNKNOWN }

    fun detectTable(bitmap: Bitmap): TableInfo? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Find dark pockets
        val darkPoints = mutableListOf<Pair<Float, Float>>()
        val step = 4

        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val pixel = pixels[y * w + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = (r + g + b) / 3

                if (brightness < 40) {
                    darkPoints.add(x.toFloat() to y.toFloat())
                }
            }
        }

        // Cluster dark points into pockets
        val clusters = clusterPoints(darkPoints, 30f)
        val pockets = mutableListOf<Pair<Float, Float>>()

        for (cluster in clusters) {
            if (cluster.size < 5) continue
            val cx = cluster.map { it.first }.average().toFloat()
            val cy = cluster.map { it.second }.average().toFloat()
            val radius = cluster.maxOfOrNull {
                sqrt((it.first - cx).pow(2) + (it.second - cy).pow(2))
            } ?: 0f

            if (radius in 5f..60f) {
                pockets.add(cx to cy)
            }
        }

        if (pockets.size < 4) {
            // Fallback: estimate table from screen size
            val tableW = w * 0.9f
            val tableH = tableW / 2f
            val left = (w - tableW) / 2f
            val top = (h - tableH) / 2f
            return TableInfo(
                left, top, left + tableW, top + tableH,
                listOf(
                    left to top,
                    (left + left + tableW) / 2f to top,
                    left + tableW to top,
                    left to top + tableH,
                    (left + left + tableW) / 2f to top + tableH,
                    left + tableW to top + tableH
                )
            )
        }

        // Build table from pockets
        val sorted = pockets.sortedBy { it.first + it.second * 10 }
        val left = (sorted.minOfOrNull { it.first } ?: 0f) - 20f
        val right = (sorted.maxOfOrNull { it.first } ?: w.toFloat()) + 20f
        val top = (sorted.minOfOrNull { it.second } ?: 0f) - 20f
        val bottom = (sorted.maxOfOrNull { it.second } ?: h.toFloat()) + 20f

        return TableInfo(left, top, right, bottom, sorted.take(6))
    }

    fun detectBalls(bitmap: Bitmap, table: TableInfo?): List<BallInfo> {
        if (table == null) return emptyList()

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val tL = table.left.toInt().coerceAtLeast(0)
        val tT = table.top.toInt().coerceAtLeast(0)
        val tR = table.right.toInt().coerceAtMost(w)
        val tB = table.bottom.toInt().coerceAtMost(h)

        val avgSize = ((table.right - table.left) + (table.bottom - table.top)) / 2
        val expectedRadius = (avgSize * 0.022f).toInt().coerceIn(6, 25)

        val candidates = mutableListOf<Triple<Float, Float, Float>>()
        val step = 3

        for (cy in tT + expectedRadius until tB - expectedRadius step step) {
            for (cx in tL + expectedRadius until tR - expectedRadius step step) {
                var score = 0f
                var total = 0

                for (angle in 0 until 360 step 15) {
                    val rad = Math.toRadians(angle.toDouble())
                    val nx = cx + (expectedRadius * cos(rad)).toInt()
                    val ny = cy + (expectedRadius * sin(rad)).toInt()

                    if (ny in 0 until h && nx in 0 until w) {
                        val pixel = pixels[ny * w + nx]
                        val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3

                        if (brightness > 20) {
                            score += 1f
                        }
                        total++
                    }
                }

                if (total > 0 && score / total > 0.6f) {
                    candidates.add(Triple(cx.toFloat(), cy.toFloat(), score))
                }
            }
        }

        val clusters = clusterPoints(candidates.map { it.first to it.second }, expectedRadius * 1.5f)
        val balls = mutableListOf<BallInfo>()

        for (cluster in clusters) {
            if (cluster.size < 3) continue
            val cx = cluster.map { it.first }.average().toFloat()
            val cy = cluster.map { it.second }.average().toFloat()

            // Check if near pocket
            val nearPocket = table.holes.any { (hx, hy) ->
                sqrt((hx - cx).pow(2) + (hy - cy).pow(2)) < expectedRadius * 2
            }
            if (nearPocket) continue

            // Classify ball
            val type = classifyBall(pixels, w, h, cx.toInt(), cy.toInt(), expectedRadius)
            balls.add(BallInfo(cx, cy, expectedRadius.toFloat(), type))
        }

        return balls
    }

    private fun classifyBall(pixels: IntArray, w: Int, h: Int, cx: Int, cy: Int, radius: Int): BallType {
        var whiteCount = 0
        var blackCount = 0
        var total = 0

        val minX = (cx - radius).coerceAtLeast(0)
        val minY = (cy - radius).coerceAtLeast(0)
        val maxX = (cx + radius).coerceAtMost(w - 1)
        val maxY = (cy + radius).coerceAtMost(h - 1)

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= radius * radius) {
                    val pixel = pixels[y * w + x]
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    if (r > 192 && g > 192 && b > 192) whiteCount++
                    if (r < 64 && g < 64 && b < 64) blackCount++
                    total++
                }
            }
        }

        if (total == 0) return BallType.UNKNOWN
        val whiteRatio = whiteCount.toFloat() / total
        val blackRatio = blackCount.toFloat() / total

        return when {
            whiteRatio > 0.7f -> BallType.WHITE
            blackRatio > 0.6f -> BallType.BLACK
            whiteRatio > 0.3f -> BallType.STRIPED
            else -> BallType.SOLID
        }
    }

    private fun clusterPoints(points: List<Pair<Float, Float>>, threshold: Float): List<List<Pair<Float, Float>>> {
        val clusters = mutableListOf<MutableList<Pair<Float, Float>>>()
        val used = BooleanArray(points.size)

        for (i in points.indices) {
            if (used[i]) continue
            used[i] = true
            val cluster = mutableListOf(points[i])

            for (j in i + 1 until points.size) {
                if (used[j]) continue
                val dx = points[i].first - points[j].first
                val dy = points[i].second - points[j].second
                if (sqrt(dx * dx + dy * dy) < threshold) {
                    used[j] = true
                    cluster.add(points[j])
                }
            }
            clusters.add(cluster)
        }
        return clusters
    }
}
