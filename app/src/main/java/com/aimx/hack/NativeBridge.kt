package com.aimx.hack

class NativeBridge {
    init { System.loadLibrary("aimxhack") }

    external fun nativeInit(): Boolean
    external fun nativeIsInGame(): Boolean
    external fun nativeGetShotResult(): FloatArray?
    external fun nativeGetShotAngle(): Double
    external fun nativeGetShotPower(): Double
    external fun nativeGetBallsCount(): Int
    external fun nativeGetBallPositions(): DoubleArray?
    external fun nativeGetBallClassifications(): IntArray?

    data class BallTrajectory(
        val index: Int,
        val positions: List<Pair<Float, Float>>
    )

    data class PocketState(
        val active: Boolean,
        val x: Float,
        val y: Float
    )

    data class PredictionResult(
        val trajectories: List<BallTrajectory>,
        val pockets: List<PocketState>
    )

    fun getPrediction(): PredictionResult? {
        val data = nativeGetShotResult() ?: return null
        if (data.isEmpty()) return null

        val trajectories = mutableListOf<BallTrajectory>()
        val pockets = mutableListOf<PocketState>()
        var idx = 0

        val isTrajectoryEnabled = data[idx++] != 0f
        if (isTrajectoryEnabled) {
            val nBalls = data[idx++].toInt()
            for (i in 0 until nBalls) {
                val ballIdx = data[idx++].toInt()
                val nPositions = data[idx++].toInt()
                val positions = mutableListOf<Pair<Float, Float>>()
                for (j in 0 until nPositions) {
                    val x = data[idx++]
                    val y = data[idx++]
                    positions.add(x to y)
                }
                trajectories.add(BallTrajectory(ballIdx, positions))
            }
        }

        val isShotStateEnabled = data[idx++] != 0f
        if (isShotStateEnabled) {
            for (i in 0 until 6) {
                val active = data[idx++] != 0f
                val x = data[idx++]
                val y = data[idx++]
                pockets.add(PocketState(active, x, y))
            }
        }

        return PredictionResult(trajectories, pockets)
    }
}
