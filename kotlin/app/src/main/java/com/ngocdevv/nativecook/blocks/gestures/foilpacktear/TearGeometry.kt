package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

import android.graphics.Matrix
import android.graphics.Path
import kotlin.math.*

internal fun tearYAt(line: DoubleArray, localX: Double): Double {
    val x = localX.coerceIn(0.0, 216.0)
    val column = min(71, floor(x / 3).toInt())
    val fraction = (x - column * 3) / 3
    val offset = line[column] * (1 - fraction) + line[column + 1] * fraction
    val tooth = 1 - abs(fraction * 2 - 1)
    val depth = 1.25 + 0.65 * sin(column * 7.13 + 0.8)
    return 34 + offset + tooth * depth
}

internal class TearGeometry {
    val cut = Path()
    val lip = Path()
    val backLip = Path()
    val opening = Path()
    val stage = Matrix()
    val pack = Matrix()
    val curl = Matrix()
    val freeCurl = Matrix()

    private fun trace(path: Path, p: TearPose, back: Boolean = false) {
        val end = p.tearFront.coerceIn(0.0, 216.0)
        path.moveTo(0f, tearYAt(p.tearLine, 0.0).toFloat())
        for (i in 1..ceil(end / 1.5).toInt()) {
            val x = min(i * 1.5, end)
            val gap = max(0.0, sin(PI * x / 216)).pow(0.32) * p.mouth * if (back) -5 else 2
            path.lineTo(x.toFloat(), (tearYAt(p.tearLine, x) + gap).toFloat())
        }
    }

    fun update(p: TearPose) {
        cut.rewind()
        lip.rewind()
        backLip.rewind()
        opening.rewind()
        trace(lip, p)
        trace(backLip, p, true)
        trace(opening, p, true)
        for (i in ceil(p.tearFront / 1.5).toInt() downTo 0) {
            val x = min(p.tearFront, i * 1.5)
            val y = tearYAt(p.tearLine, x) + 2 * p.mouth * max(0.0, sin(PI * x / 216)).pow(0.32)
            opening.lineTo(x.toFloat(), y.toFloat())
        }
        opening.close()
        if (p.tearFront > 0.01) {
            trace(cut, p)
            if (p.tearFront < 216) {
                cut.lineTo(p.tearFront.toFloat(), -1f)
                cut.lineTo(217f, -1f)
            }
        } else {
            cut.moveTo(-1f, -1f)
            cut.lineTo(217f, -1f)
        }
        cut.lineTo(217f, 377f)
        cut.lineTo(-1f, 377f)
        if (p.tearFront <= 0.01) {
            cut.lineTo(-1f, 37f)
            cut.lineTo(5f, 34f)
            cut.lineTo(-1f, 31f)
        }
        cut.close()
        val px = sin(p.swayY) / 900
        val py = -sin(p.swayX) / 900
        val sx = cos(p.swayY)
        val sy = cos(p.swayX)
        set(stage, sx + 201 * px, 201 * py, 201 * (1 - sx) - 201 * (201 * px + 374 * py),
            374 * px, sy + 374 * py, 374 * (1 - sy) - 374 * (201 * px + 374 * py), px, py, 1 - 201 * px - 374 * py)
        val c = cos(p.packRot) * p.packScale
        val s = sin(p.packRot) * p.packScale
        set(pack, c, -s, 93 + p.packX + 108 - c * 108 + s * 188,
            s, c, 176 + p.packY + 188 - s * 108 - c * 188, 0.0, 0.0, 1.0)
        val angle = -0.12 + p.stripRot * p.stripFree
        val depth = 1 - p.stripFree + p.stripFree * (0.55 + 0.45 * abs(cos(p.stripRot * 0.7)))
        val values = doubleArrayOf(cos(angle), -sin(angle) * depth, p.tearFront + p.stripX * p.stripFree,
            sin(angle), cos(angle) * depth, p.stripY * p.stripFree, 0.0, 0.0, 1.0)
        set(curl, *values)
        values[2] += 93
        values[5] += 176
        set(freeCurl, *values)
    }

    private fun set(matrix: Matrix, vararg values: Double) {
        matrix.setValues(FloatArray(9) { values[it].toFloat() })
    }
}
