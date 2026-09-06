package com.snaploop.app.face

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal data class Point2(val x: Double, val y: Double)

internal data class SimilarityTransform(
    val c: Double,
    val s: Double,
    val tx: Double,
    val ty: Double,
) {
    fun map(point: Point2): Point2 = Point2(
        x = c * point.x - s * point.y + tx,
        y = s * point.x + c * point.y + ty,
    )
}

/** Same five ArcFace canonical points and least-squares similarity fit used by the iOS v5 pipeline. */
internal object CanonicalFaceGeometry {
    val canonical112 = listOf(
        Point2(38.2946, 51.6963),
        Point2(73.5318, 51.5014),
        Point2(56.0252, 71.7366),
        Point2(41.5493, 92.3655),
        Point2(70.7299, 92.2041),
    )

    fun targets(outputSize: Int): List<Point2> {
        require(outputSize > 0)
        val scale = outputSize / 112.0
        return canonical112.map { Point2(it.x * scale, it.y * scale) }
    }

    fun estimateSimilarity(source: List<Point2>, target: List<Point2>): SimilarityTransform? {
        if (source.size != target.size || source.size < 2) return null
        val n = source.size.toDouble()
        val sx = source.sumOf { it.x } / n
        val sy = source.sumOf { it.y } / n
        val txMean = target.sumOf { it.x } / n
        val tyMean = target.sumOf { it.y } / n

        var a = 0.0
        var b = 0.0
        var denominator = 0.0
        for (i in source.indices) {
            val px = source[i].x - sx
            val py = source[i].y - sy
            val qx = target[i].x - txMean
            val qy = target[i].y - tyMean
            a += px * qx + py * qy
            b += px * qy - py * qx
            denominator += px * px + py * py
        }
        if (denominator <= 0.0) return null
        val magnitude = hypot(a, b)
        if (magnitude <= 0.0) return null
        val scale = magnitude / denominator
        val angle = atan2(b, a)
        val c = cos(angle) * scale
        val s = sin(angle) * scale
        val outTx = txMean - (c * sx - s * sy)
        val outTy = tyMean - (s * sx + c * sy)
        return SimilarityTransform(c, s, outTx, outTy)
    }
}
