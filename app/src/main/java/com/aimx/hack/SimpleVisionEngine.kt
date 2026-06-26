package com.aimx.hack

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.*

class SimpleVisionEngine {

    companion object {
        private const val TAG = "AimXHack"

        // Constantes do jogo (engenharia reversa)
        const val GAME_TABLE_WIDTH = 254.0f
        const val GAME_TABLE_HEIGHT = 127.0f
        const val GAME_RATIO = 2.0f  // 254/127 = 2:1
        const val BALL_RADIUS_GAME = 3.800475f
        const val POCKET_RADIUS_GAME = 8.0f

        // Cores das bolas do 8 Ball Pool (HSV aproximado)
        // Bola branca (cue): branca, alta luminosidade
        // Bolas sólidas: vermelho, laranja, amarelo, verde, azul, roxo, preta(8)
        // Bolas listradas: mesma cor mas com faixa branca
    }

    data class TableInfo(
        val left: Float, val top: Float,
        val right: Float, val bottom: Float,
        val holes: List<Pair<Float, Float>>,
        val scale: Float  // pixels por unidade do jogo
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

        // 1. Encontrar área verde (feltro da mesa)
        var greenMinX = w; var greenMaxX = 0
        var greenMinY = h; var greenMaxY = 0
        var greenCount = 0

        for (y in 0 until h step 8) {
            for (x in 0 until w step 8) {
                val pixel = pixels[y * w + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Feltro verde: G muito alto, R e B baixos
                // Verde típico do 8 Ball Pool: G > 100, R < 60, B < 60
                if (g > 100 && g > r * 2.0 && g > b * 2.0 && r < 60 && b < 60) {
                    if (x < greenMinX) greenMinX = x
                    if (x > greenMaxX) greenMaxX = x
                    if (y < greenMinY) greenMinY = y
                    if (y > greenMaxY) greenMaxY = y
                    greenCount++
                }
            }
        }

        // Precisa de muitos pixels verdes para confirmar que é a mesa
        if (greenCount < 200) return null

        // 2. Verificar proporção (deve ser ~2:1)
        val tableW = greenMaxX - greenMinX
        val tableH = greenMaxY - greenMinY
        val ratio = tableW.toFloat() / tableH.toFloat()

        if (ratio < 1.3f || ratio > 2.7f) {
            Log.d(TAG, "Bad ratio: $ratio (expected ~2.0)")
            return null
        }

        val left = greenMinX.toFloat()
        val top = greenMinY.toFloat()
        val right = greenMaxX.toFloat()
        val bottom = greenMaxY.toFloat()
        val scale = tableW / GAME_TABLE_WIDTH

        // 3. Encontrar buracos (pontos escuros nas bordas)
        val pockets = mutableListOf<Pair<Float, Float>>()
        val pocketRadius = (POCKET_RADIUS_GAME * scale).toInt()

        // Procurar buracos nos cantos e no meio das bordas
        val searchAreas = listOf(
            // Cantos
            Pair((left + pocketRadius * 2).toInt(), (top + pocketRadius * 2).toInt()),
            Pair((right - pocketRadius * 2).toInt(), (top + pocketRadius * 2).toInt()),
            Pair((left + pocketRadius * 2).toInt(), (bottom - pocketRadius * 2).toInt()),
            Pair((right - pocketRadius * 2).toInt(), (bottom - pocketRadius * 2).toInt()),
            // Meio das bordas
            Pair(((left + right) / 2).toInt(), (top + pocketRadius).toInt()),
            Pair(((left + right) / 2).toInt(), (bottom - pocketRadius).toInt())
        )

        for ((sx, sy) in searchAreas) {
            if (sx < 0 || sx >= w || sy < 0 || sy >= h) continue

            // Verificar se é escuro
            val pixel = pixels[sy * w + sx]
            val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3

            if (brightness < 50) {
                pockets.add(sx.toFloat() to sy.toFloat())
            } else {
                // Procurar ponto escuro próximo
                for (dy in -pocketRadius..pocketRadius step 3) {
                    for (dx in -pocketRadius..pocketRadius step 3) {
                        val nx = sx + dx
                        val ny = sy + dy
                        if (nx in 0 until w && ny in 0 until h) {
                            val p = pixels[ny * w + nx]
                            val b = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3
                            if (b < 30) {
                                pockets.add(nx.toFloat() to ny.toFloat())
                                break
                            }
                        }
                    }
                    if (pockets.size > pockets.size) break
                }
            }
        }

        Log.d(TAG, "Table: ${tableW}x${tableH}, ratio=$ratio, scale=$scale, pockets=${pockets.size}")
        return TableInfo(left, top, right, bottom, pockets.take(6), scale)
    }

    fun detectBalls(bitmap: Bitmap, table: TableInfo?): List<BallInfo> {
        if (table == null) return emptyList()

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Raio esperado em pixels (baseado na escala da mesa)
        val expectedRadius = (BALL_RADIUS_GAME * table.scale).toInt().coerceIn(4, 40)

        val tL = table.left.toInt().coerceAtLeast(0)
        val tT = table.top.toInt().coerceAtLeast(0)
        val tR = table.right.toInt().coerceAtMost(w)
        val tB = table.bottom.toInt().coerceAtMost(h)

        Log.d(TAG, "Scanning for balls: radius=$expectedRadius, area=$tL,$tT-$tR,$tB")

        val candidates = mutableListOf<Triple<Float, Float, Float>>()

        // Escanear em grid espaçado
        val step = expectedRadius

        for (cy in tT + expectedRadius until tB - expectedRadius step step) {
            for (cx in tL + expectedRadius until tR - expectedRadius step step) {
                val centerPixel = pixels[cy * w + cx]
                val cr = Color.red(centerPixel)
                val cg = Color.green(centerPixel)
                val cb = Color.blue(centerPixel)
                val centerBrightness = (cr + cg + cb) / 3

                // Pular se for verde (feltro)
                if (cg > 80 && cg > cr * 1.5 && cg > cb * 1.5) continue

                // Pular se for muito escuro (buraco)
                if (centerBrightness < 40) continue

                // Pular se for muito claro (reflexo/UI)
                if (centerBrightness > 240) continue

                // Verificar se há contraste ao redor (bola tem borda definida)
                var highContrastCount = 0
                var totalChecked = 0

                for (angle in 0 until 360 step 45) {
                    val rad = Math.toRadians(angle.toDouble())
                    val nx = cx + (expectedRadius * cos(rad)).toInt()
                    val ny = cy + (expectedRadius * sin(rad)).toInt()

                    if (ny in 0 until h && nx in 0 until w) {
                        val pixel = pixels[ny * w + nx]
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)

                        // Contraste com centro
                        val diff = abs(cr - r) + abs(cg - g) + abs(cb - b)
                        if (diff > 150) highContrastCount++
                        totalChecked++
                    }
                }

                // Precisa de contraste alto em pelo menos 50% dos pontos
                if (totalChecked > 0 && highContrastCount.toFloat() / totalChecked > 0.5f) {
                    candidates.add(Triple(cx.toFloat(), cy.toFloat(), highContrastCount.toFloat()))
                }
            }
        }

        // Clusterizar candidatos
        val clusters = clusterPoints(candidates.map { it.first to it.second }, expectedRadius * 1.5f)
        val balls = mutableListOf<BallInfo>()

        for (cluster in clusters) {
            if (cluster.size < 2) continue
            val cx = cluster.map { it.first }.average().toFloat()
            val cy = cluster.map { it.second }.average().toFloat()

            // Pular se perto de buraco
            val nearPocket = table.holes.any { (hx, hy) ->
                sqrt((hx - cx).pow(2) + (hy - cy).pow(2)) < expectedRadius * 3
            }
            if (nearPocket) continue

            // Classificar bola
            val type = classifyBall(pixels, w, h, cx.toInt(), cy.toInt(), expectedRadius)
            balls.add(BallInfo(cx, cy, expectedRadius.toFloat(), type))
        }

        Log.d(TAG, "Balls detected: ${balls.size}")
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

                    if (r > 200 && g > 200 && b > 200) whiteCount++
                    if (r < 40 && g < 40 && b < 40) blackCount++
                    total++
                }
            }
        }

        if (total == 0) return BallType.UNKNOWN
        val whiteRatio = whiteCount.toFloat() / total
        val blackRatio = blackCount.toFloat() / total

        return when {
            whiteRatio > 0.65f -> BallType.WHITE      // Bola branca (cue)
            blackRatio > 0.55f -> BallType.BLACK       // Bola 8 (preta)
            whiteRatio > 0.3f -> BallType.STRIPED      // Listrada (tem faixa branca)
            else -> BallType.SOLID                      // Sólida
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
