package com.ngocdevv.nativecook.blocks.onboarding.quietsignalonboarding

import android.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The fixed outlines of the five diagrams (the `svg(...)` constants of `QuietSignalDiagrams.tsx`,
 * on a 300-unit square) and the builders of the moving lines. Each builder appends its own
 * contours to a path, as `p.addPath(builder(...))` does in the React Native file.
 */
internal object QuietSignalOnboardingShapes {
    const val TAU = PI * 2

    private fun parse(d: String): Path = PathParser().parsePathString(d).toPath().asAndroidPath()

    /** A face in profile looking right: crown, brow, nose, lips, chin, then the neck. */
    val HEAD: Path = parse(
        "M146 30C204 26 244 60 245 108L246 122C247 130 251 137 257 146L267 160C269 164 266 167 260 167" +
            "L254 168C256 172 256 176 252 179C255 183 254 187 249 189C251 195 252 201 247 207" +
            "C239 215 222 214 211 214L207 290",
    )
    val HEAD_FILL: Path = parse(
        "M146 30C204 26 244 60 245 108L246 122C247 130 251 137 257 146L267 160C269 164 266 167 260 167" +
            "L254 168C256 172 256 176 252 179C255 183 254 187 249 189C251 195 252 201 247 207" +
            "C239 215 222 214 211 214L207 290L120 290C125 250 118 230 104 214C70 176 80 40 146 30Z",
    )
    val PILL: Path = parse("M44 54H130A10 10 0 0 1 130 74H44A10 10 0 0 1 44 54Z")
    val DOC: Path = parse("M40 156H150V272H40ZM40 168H150")
    private const val PHONE_BODY =
        "M104 26H196A20 20 0 0 1 216 46V254A20 20 0 0 1 196 274H104A20 20 0 0 1 84 254V46A20 20 0 0 1 104 26Z"
    val PHONE: Path = parse("${PHONE_BODY}M132 26V30Q132 36 138 36H162Q168 36 168 30V26")
    val PHONE_CLIP: Path = parse(PHONE_BODY)
    val CROSS: Path = parse("M150 10V290M10 150H290")

    /** Two long faint curves that sweep through the phone behind its waves. */
    val FAINT: Path = parse("M70 250C110 230 130 120 160 80S200 40 230 30M80 260C150 240 170 120 200 90S240 60 260 50")
    val EYE: Path = parse("M228 138Q238 130 248 138Q238 146 228 138ZM238 135.5A2.5 2.5 0 1 1 237.9 135.5")
    val KEY: Path = parse("M3 3L10 10M8 12L12 8")
    val EMBLEM: Path = parse(
        "M66 150H110C122 150 126 146 130 136L142 104C148 90 166 88 172 100C178 112 170 122 164 132" +
            "L150 154C144 166 150 178 162 180C176 182 182 170 186 160L196 134C200 124 208 118 222 118",
    )
    val RING: Path = parse("M150 66A84 84 0 1 1 149.99 66Z")

    /** `wave`: a sine across [x0] to [x1] sampled every 4 units, optionally tapered to nothing at both ends. */
    fun addWave(
        path: Path,
        x0: Double,
        x1: Double,
        y: Double,
        amp: Double,
        length: Double,
        phase: Double,
        tapered: Boolean = true,
    ) {
        var x = x0
        while (x <= x1) {
            val taper = if (tapered) sin((x - x0) / (x1 - x0) * PI) else 1.0
            val v = y + sin(x / length * TAU + phase) * amp * taper
            if (x == x0) path.moveTo(x.toFloat(), v.toFloat()) else path.lineTo(x.toFloat(), v.toFloat())
            x += 4.0
        }
    }

    /** `bars`: [count] vertical strokes [gap] apart that breathe, tallest in the middle. */
    fun addBars(
        path: Path,
        t: Double,
        x0: Double,
        count: Int,
        gap: Double,
        y: Double,
        max: Double,
        seed: Double,
    ) {
        for (i in 0 until count) {
            val centre = 1.0 - abs(i - (count - 1) / 2.0) / ((count - 1) / 2.0 + 1.0)
            val wobble = 0.5 + 0.25 * sin(t * 5.1 + i * 1.7 + seed) + 0.25 * sin(t * 3.3 + i * 0.9 + seed * 2.0)
            val h = 3.0 + max * centre * (0.45 + 0.55 * wobble)
            val x = (x0 + i * gap).toFloat()
            path.moveTo(x, (y - h / 2.0).toFloat())
            path.lineTo(x, (y + h / 2.0).toFloat())
        }
    }

    /** `crescent`: the outer half circle facing [angle], closed by an inner arc pulled back toward the centre: a moon. */
    fun addCrescent(path: Path, cx: Double, cy: Double, r: Double, angle: Double, thickness: Double) {
        val a0 = angle - PI / 2
        val dx = cos(angle)
        val dy = sin(angle)
        for (i in 0..28) {
            val a = a0 + PI * (i / 28.0)
            val x = (cx + cos(a) * r).toFloat()
            val y = (cy + sin(a) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (i in 28 downTo 0) {
            val s = i / 28.0
            val a = a0 + PI * s
            val pull = sin(s * PI) * thickness
            path.lineTo((cx + cos(a) * r - dx * pull).toFloat(), (cy + sin(a) * r - dy * pull).toFloat())
        }
    }

    /** `arc`: a polyline round the circle at ([cx], [cy]) from angle [from] to [to], in radians. */
    fun addArc(path: Path, cx: Double, cy: Double, r: Double, from: Double, to: Double) {
        val steps = max(2, ceil(abs(to - from) / 0.08).toInt())
        for (i in 0..steps) {
            val a = from + (to - from) * (i.toDouble() / steps)
            val x = (cx + cos(a) * r).toFloat()
            val y = (cy + sin(a) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }
}
