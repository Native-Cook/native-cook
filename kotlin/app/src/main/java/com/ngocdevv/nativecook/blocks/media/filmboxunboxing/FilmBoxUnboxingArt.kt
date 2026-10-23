package com.ngocdevv.nativecook.blocks.media.filmboxunboxing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The drawn objects: the film box (a cuboid projected in perspective, with two flaps on hinges),
 * the canister (a cylinder drawn in thin strips so its label can turn), and the spinner. The
 * lettering on both is cut from the glyph outlines below, the same in the three stacks. Every
 * length is in dp: the caller scales the canvas by the density first.
 */

// The box, in dp: long side, height, depth.
private const val BOX_W = 150f
private const val BOX_H = 96f
private const val BOX_D = 84f
/** Distance from the eye to the screen, in dp: the strength of the perspective. */
private const val FOCAL = 700f
/** The box's resting turn: from the left, from above, and tipped up to the right (degrees). */
private const val BOX_YAW = -30f
private const val BOX_PITCH = 22f
private const val BOX_ROLL = -20f
/** The gap between the two closed flaps, showing the inside. */
private const val SEAM = 0.8f

// The canister, in dp: length, radius of the body and of the end caps, length of a cap.
private const val CAN_LEN = 156f
private const val CAN_R = 44f
private const val CAP_R = 48f
private const val CAP_LEN = 13f
/** How much of an end the eye sees: the width of an end's ellipse over its height. */
private const val CAN_VIEW = 0.4f
/** The canister lies on a diagonal, its open end up and to the left (degrees). */
private const val CAN_TILT = 30f
private const val STRIPS = 36
// Where things sit round the canister, in radians from the middle of the label.
private const val LABEL_FROM = -1.2f
private const val STRIPE_FROM = 0.8f
private const val STRIPE_TO = 1.15f
private const val FILM_STEP = 0.21f
/** "ALIEN" is this many dp per glyph unit (a glyph is 10 units tall), centred at TEXT_AT. */
private const val TEXT_SCALE = 2.6f
private const val TEXT_AT = -0.2f

private const val PI_F = PI.toFloat()
private const val TAU_F = PI_F * 2

// Colours as 0–255 channels, mixed and lit per face.
private val Kraft = floatArrayOf(217f, 164f, 65f)
private val KraftInside = floatArrayOf(104f, 72f, 30f)
private val Amber = floatArrayOf(255f, 184f, 0f)
private val InkRgb = floatArrayOf(22f, 18f, 12f)
private val RedRgb = floatArrayOf(214f, 58f, 46f)
private val PaperRgb = floatArrayOf(246f, 241f, 228f)
private val MintRgb = floatArrayOf(168f, 230f, 192f)
private val MintRim = floatArrayOf(207f, 244f, 222f)
private val GlassRgb = floatArrayOf(10f, 32f, 22f)
private val Silver = floatArrayOf(233f, 236f, 234f)
private val LabelStripe = floatArrayOf(20f, 20f, 20f)
private val FilmDark = floatArrayOf(14f, 52f, 35f)
private val FilmLight = floatArrayOf(34f, 104f, 68f)
private val FilmLime = floatArrayOf(124f, 214f, 155f)
private val SpinnerGrey = floatArrayOf(138f, 138f, 138f)
private val WhiteRgb = floatArrayOf(255f, 255f, 255f)
/** Light falls from the upper left, in front: the direction towards it. */
private val Light = floatArrayOf(-0.3f, -0.8f, -0.5f)
private val LightLength = sqrt(0.09f + 0.64f + 0.25f)

private class Glyph(val w: Float, val c: List<FloatArray>)

// Block capitals, 10 units tall, as outlines (x, y pairs); a second outline is a hole.
private val Glyphs = mapOf(
    'A' to Glyph(8.5f, listOf(floatArrayOf(2.6f, 0f, 5.9f, 0f, 8.5f, 10f, 5.6f, 10f, 5.2f, 8.2f, 3.3f, 8.2f, 2.9f, 10f, 0f, 10f), floatArrayOf(3.75f, 5.8f, 4.75f, 5.8f, 4.25f, 3.4f))),
    'C' to Glyph(7.5f, listOf(floatArrayOf(2.4f, 0f, 7.5f, 0f, 7.5f, 2.6f, 3.6f, 2.6f, 3f, 3.2f, 3f, 6.8f, 3.6f, 7.4f, 7.5f, 7.4f, 7.5f, 10f, 2.4f, 10f, 0f, 7.6f, 0f, 2.4f))),
    'E' to Glyph(6.5f, listOf(floatArrayOf(0f, 0f, 6.5f, 0f, 6.5f, 2.5f, 3f, 2.5f, 3f, 3.8f, 6f, 3.8f, 6f, 6.2f, 3f, 6.2f, 3f, 7.5f, 6.5f, 7.5f, 6.5f, 10f, 0f, 10f))),
    'F' to Glyph(7f, listOf(floatArrayOf(0f, 0f, 7f, 0f, 7f, 2.6f, 3f, 2.6f, 3f, 4f, 6.2f, 4f, 6.2f, 6.4f, 3f, 6.4f, 3f, 10f, 0f, 10f))),
    'G' to Glyph(7.5f, listOf(floatArrayOf(2.4f, 0f, 7.5f, 0f, 7.5f, 2.6f, 3.6f, 2.6f, 3f, 3.2f, 3f, 6.8f, 3.6f, 7.4f, 4.8f, 7.4f, 4.8f, 6.4f, 4f, 6.4f, 4f, 4.4f, 7.5f, 4.4f, 7.5f, 10f, 2.4f, 10f, 0f, 7.6f, 0f, 2.4f))),
    'I' to Glyph(3f, listOf(floatArrayOf(0f, 0f, 3f, 0f, 3f, 10f, 0f, 10f))),
    'L' to Glyph(6.5f, listOf(floatArrayOf(0f, 0f, 3f, 0f, 3f, 7.4f, 6.5f, 7.4f, 6.5f, 10f, 0f, 10f))),
    'M' to Glyph(10f, listOf(floatArrayOf(0f, 0f, 3f, 0f, 5f, 3.6f, 7f, 0f, 10f, 0f, 10f, 10f, 7.2f, 10f, 7.2f, 5f, 5f, 8.4f, 2.8f, 5f, 2.8f, 10f, 0f, 10f))),
    'N' to Glyph(8f, listOf(floatArrayOf(0f, 0f, 3f, 0f, 5.2f, 4.6f, 5.2f, 0f, 8f, 0f, 8f, 10f, 5f, 10f, 2.8f, 5.4f, 2.8f, 10f, 0f, 10f))),
    'O' to Glyph(
        8f,
        listOf(
            floatArrayOf(2.4f, 0f, 5.6f, 0f, 8f, 2.4f, 8f, 7.6f, 5.6f, 10f, 2.4f, 10f, 0f, 7.6f, 0f, 2.4f),
            floatArrayOf(3.4f, 2.6f, 4.6f, 2.6f, 5.2f, 3.2f, 5.2f, 6.8f, 4.6f, 7.4f, 3.4f, 7.4f, 2.8f, 6.8f, 2.8f, 3.2f),
        ),
    ),
    'U' to Glyph(7.5f, listOf(floatArrayOf(0f, 0f, 3f, 0f, 3f, 7.2f, 4.5f, 7.2f, 4.5f, 0f, 7.5f, 0f, 7.5f, 7.6f, 5.1f, 10f, 2.4f, 10f, 0f, 7.6f))),
    '.' to Glyph(3f, listOf(floatArrayOf(0f, 7.2f, 3f, 7.2f, 3f, 10f, 0f, 10f))),
)
private const val TRACK = 0.8f
private const val SPACE = 3f
/** Units the top of a letter leans right over its height. */
private const val ITALIC = 0.2f

private class Lettering(val contours: List<FloatArray>, val width: Float)

/** A word's outlines at (x, y), [scale] dp per unit, leaning. */
private fun lettering(text: String, scale: Float, x: Float, y: Float): Lettering {
    val out = mutableListOf<FloatArray>()
    var pen = 0f
    for (ch in text) {
        if (ch == ' ') {
            pen += SPACE
            continue
        }
        val glyph = Glyphs.getValue(ch)
        for (contour in glyph.c) {
            out += FloatArray(contour.size) { i ->
                if (i % 2 == 0) x + (pen + contour[i] + (10 - contour[i + 1]) * ITALIC) * scale else y + contour[i] * scale
            }
        }
        pen += glyph.w + TRACK
    }
    return Lettering(out, (pen - TRACK) * scale)
}

private fun rect(x: Float, y: Float, w: Float, h: Float) = floatArrayOf(x, y, x + w, y, x + w, y + h, x, y + h)

private fun disc(x: Float, y: Float, r: Float) = FloatArray(40) { i ->
    val a = (i / 2) / 20f * TAU_F
    if (i % 2 == 0) x + r * cos(a) else y + r * sin(a)
}

private class Face(val idx: IntArray, val n: FloatArray, val o: FloatArray, val u: FloatArray, val v: FloatArray)

private const val FRONT = 0
private const val LEFT = 2

// The six faces as corner indices (clockwise seen from outside), the outward normal, and a
// frame for the print on it: origin, then unit vectors along and down the face.
private const val A = BOX_W / 2
private const val B = BOX_H / 2
private const val C = BOX_D / 2
private val Corners = listOf(
    floatArrayOf(-A, -B, -C), floatArrayOf(A, -B, -C), floatArrayOf(A, B, -C), floatArrayOf(-A, B, -C),
    floatArrayOf(-A, -B, C), floatArrayOf(A, -B, C), floatArrayOf(A, B, C), floatArrayOf(-A, B, C),
)
private val Faces = listOf(
    Face(intArrayOf(0, 1, 2, 3), floatArrayOf(0f, 0f, -1f), floatArrayOf(-A, -B, -C), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f)),
    Face(intArrayOf(5, 4, 7, 6), floatArrayOf(0f, 0f, 1f), floatArrayOf(A, -B, C), floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 1f, 0f)),
    Face(intArrayOf(4, 0, 3, 7), floatArrayOf(-1f, 0f, 0f), floatArrayOf(-A, -B, C), floatArrayOf(0f, 0f, -1f), floatArrayOf(0f, 1f, 0f)),
    Face(intArrayOf(1, 5, 6, 2), floatArrayOf(1f, 0f, 0f), floatArrayOf(A, -B, -C), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 1f, 0f)),
    Face(intArrayOf(3, 2, 6, 7), floatArrayOf(0f, 1f, 0f), floatArrayOf(-A, B, -C), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 1f)),
)

private class Print(val face: Int, val color: FloatArray, val contours: List<FloatArray>)

// The print: on the front a tab, the wordmark, a line of small capitals, the eye badge and a
// barcode sticker; on the left end three lines, a small wordmark and a sticker.
private val Prints = listOf(
    Print(FRONT, InkRgb, listOf(rect(10f, 12f, 9f, 38f))),
    Print(FRONT, InkRgb, lettering("FILM CO.", 1.9f, 28f, 36f).contours),
    Print(FRONT, InkRgb, lettering("ANALOGUE FILM", 0.5f, 30f, 61f).contours),
    Print(FRONT, RedRgb, listOf(disc(131f, 18f, 9.5f))),
    Print(FRONT, PaperRgb, listOf(disc(130.4f, 18f, 5f))),
    Print(FRONT, InkRgb, listOf(disc(131f, 18.4f, 2.4f))),
    Print(FRONT, PaperRgb, listOf(rect(10f, 74f, 24f, 14f))),
    Print(FRONT, InkRgb, listOf(12.5f, 15f, 18f, 20f, 23.5f, 26f, 29.5f).map { rect(it, 76f, 1.2f, 10f) }),
    Print(LEFT, InkRgb, listOf(rect(18f, 30f, 46f, 2.4f), rect(18f, 36f, 46f, 2.4f), rect(18f, 42f, 30f, 2.4f))),
    Print(LEFT, InkRgb, lettering("FILM CO.", 0.9f, 18f, 54f).contours),
    Print(LEFT, PaperRgb, listOf(rect(18f, 72f, 22f, 14f))),
)

private val Alien = lettering("ALIEN", 1f, 0f, 0f)

private fun color(c: FloatArray, light: Float, alpha: Float = 1f) =
    Color(min(255f, c[0] * light) / 255f, min(255f, c[1] * light) / 255f, min(255f, c[2] * light) / 255f, alpha)

private fun mix(a: FloatArray, b: FloatArray, t: Float) =
    floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)

private fun polygon(points: FloatArray) = Path().apply {
    moveTo(points[0], points[1])
    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
    close()
}

/** The rotation the box is drawn with: roll · pitch · yaw, as nine numbers row by row. */
private fun rotation(yaw: Float, pitch: Float, roll: Float): FloatArray {
    val cy = cos(yaw)
    val sy = sin(yaw)
    val cp = cos(pitch)
    val sp = sin(pitch)
    val cr = cos(roll)
    val sr = sin(roll)
    return floatArrayOf(
        cr * cy - sr * sp * sy, -sr * cp, cr * sy + sr * sp * cy,
        sr * cy + cr * sp * sy, cr * cp, sr * sy - cr * sp * cy,
        -cp * sy, sp, cp * cy,
    )
}

private fun turn(m: FloatArray, x: Float, y: Float, z: Float) = floatArrayOf(
    m[0] * x + m[1] * y + m[2] * z,
    m[3] * x + m[4] * y + m[5] * z,
    m[6] * x + m[7] * y + m[8] * z,
)

/** A turned point on the screen, the box centred at [center]. */
private fun onScreen(p: FloatArray, center: Offset): Offset {
    val k = FOCAL / (FOCAL + p[2])
    return Offset(center.x + p[0] * k, center.y + p[1] * k)
}

/** True when the outside of a surface through [p] with normal [n] (both turned) faces the eye. */
private fun facing(n: FloatArray, p: FloatArray) = n[0] * p[0] + n[1] * p[1] + n[2] * (p[2] + FOCAL) < 0

private fun lit(n: FloatArray) = max(0f, (n[0] * Light[0] + n[1] * Light[1] + n[2] * Light[2]) / LightLength)

private class Surface(val depth: Float, val points: FloatArray, val color: FloatArray, val light: Float, val face: Int)

internal class BoxPose(
    /** Degrees added to the resting turn. */
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    /** Degrees the flaps stand open. */
    val flap: Float,
    val glow: Float,
    val sweep: Float,
    val dive: Float,
)

private fun amber(alpha: Float) = Color(1f, 0.72f, 0f, alpha)

/** The box centred at [center]; `dive` zooms into its opening and towards [focus]. */
internal fun DrawScope.drawFilmBox(center: Offset, focus: Offset, pose: BoxPose) {
    val rad = PI_F / 180
    val m = rotation((BOX_YAW + pose.yaw) * rad, (BOX_PITCH + pose.pitch) * rad, (BOX_ROLL + pose.roll) * rad)
    val turned = Corners.map { turn(m, it[0], it[1], it[2]) }
    val screen = turned.map { onScreen(it, center) }
    // The opening: its middle is where the dive goes.
    val top = intArrayOf(0, 1, 5, 4)
    val ox = top.sumOf { screen[it].x.toDouble() }.toFloat() / 4
    val oy = top.sumOf { screen[it].y.toDouble() }.toFloat() / 4
    val scale = 1 + pose.dive * (DIVE_SCALE - 1)

    withTransform({
        translate(ox + (focus.x - ox) * pose.dive, oy + (focus.y - oy) * pose.dive)
        scale(scale, scale, pivot = Offset.Zero)
        translate(-ox, -oy)
    }) {
        val inside = mutableListOf<Surface>()
        val outside = mutableListOf<Surface>()
        Faces.forEachIndexed { f, face ->
            val n = turn(m, face.n[0], face.n[1], face.n[2])
            val mid = turn(m, face.n[0] * A, face.n[1] * B, face.n[2] * C)
            val points = FloatArray(8)
            var depth = 0f
            face.idx.forEachIndexed { k, c ->
                points[k * 2] = screen[c].x
                points[k * 2 + 1] = screen[c].y
                depth += turned[c][2] / 4
            }
            if (facing(n, mid)) {
                outside += Surface(depth, points, Kraft, 0.58f + 0.5f * lit(n), f)
            } else {
                // Inside, the floor takes the most of the light that comes out of the box.
                val glow = pose.glow * (if (f == 4) 0.85f else 0.6f)
                inside += Surface(depth, points, mix(KraftInside, Amber, glow), 0.35f + 0.4f * lit(floatArrayOf(-n[0], -n[1], -n[2])) + glow, -1)
            }
        }
        // Far to near, so that nearer surfaces paint over farther ones.
        inside.sortByDescending { it.depth }
        outside.sortByDescending { it.depth }

        for (s in inside) drawPath(polygon(s.points), color(s.color, s.light))

        val opening = polygon(floatArrayOf(screen[0].x, screen[0].y, screen[1].x, screen[1].y, screen[5].x, screen[5].y, screen[4].x, screen[4].y))
        if (pose.glow > 0) {
            clipPath(opening) {
                drawPath(opening, Brush.radialGradient(listOf(amber(0.85f * pose.glow), amber(0f)), Offset(ox, oy), BOX_W * 0.6f))
            }
        }

        val walls = Path()
        for (s in outside) {
            val path = polygon(s.points)
            drawPath(path, color(s.color, s.light))
            walls.addPath(path)
            // The print on this face, lit like the face.
            val face = Faces[s.face]
            for (print in Prints) {
                if (print.face != s.face) continue
                val printed = Path().apply { fillType = PathFillType.EvenOdd }
                for (contour in print.contours) {
                    for (k in contour.indices step 2) {
                        val u = contour[k]
                        val v = contour[k + 1]
                        val q = onScreen(
                            turn(
                                m,
                                face.o[0] + u * face.u[0] + v * face.v[0],
                                face.o[1] + u * face.u[1] + v * face.v[1],
                                face.o[2] + u * face.u[2] + v * face.v[2],
                            ),
                            center,
                        )
                        if (k == 0) printed.moveTo(q.x, q.y) else printed.lineTo(q.x, q.y)
                    }
                    printed.close()
                }
                drawPath(printed, color(print.color, min(1.05f, s.light)))
            }
        }

        // A streak of light crosses the walls while the box opens.
        if (pose.sweep > 0 && pose.sweep < 1) {
            val left = screen.minOf { it.x }
            val right = screen.maxOf { it.x }
            val t = pose.sweep
            val x = left - 40 + (right - left + 80) * (t * t * (3 - 2 * t))
            val alpha = 0.6f * sin(PI_F * t)
            clipPath(walls) {
                withTransform({ rotate(20f, pivot = Offset(x, center.y)) }) {
                    drawRect(
                        Brush.linearGradient(listOf(amber(0f), amber(alpha), amber(0f)), Offset(x - 26, 0f), Offset(x + 26, 0f)),
                        topLeft = Offset(x - 26, center.y - 220),
                        size = Size(52f, 440f),
                    )
                }
            }
        }

        // The flaps, hinged on the long top edges. Closed, they are the lid.
        val angle = pose.flap * rad
        val d = C - SEAM
        val lift = d * sin(angle)
        val reach = d * cos(angle)
        val shapes = listOf(
            listOf(floatArrayOf(-A, -B, -C), floatArrayOf(A, -B, -C), floatArrayOf(A, -B - lift, -C + reach), floatArrayOf(-A, -B - lift, -C + reach)),
            listOf(floatArrayOf(A, -B, C), floatArrayOf(-A, -B, C), floatArrayOf(-A, -B - lift, C - reach), floatArrayOf(A, -B - lift, C - reach)),
        )
        val normals = listOf(floatArrayOf(0f, -cos(angle), -sin(angle)), floatArrayOf(0f, -cos(angle), sin(angle)))
        val flaps = mutableListOf<Surface>()
        for (f in 0 until 2) {
            val points = FloatArray(8)
            var depth = 0f
            val mid = FloatArray(3)
            shapes[f].forEachIndexed { k, corner ->
                val p = turn(m, corner[0], corner[1], corner[2])
                val q = onScreen(p, center)
                points[k * 2] = q.x
                points[k * 2 + 1] = q.y
                depth += p[2] / 4
                for (j in 0 until 3) mid[j] += p[j] / 4
            }
            val n = turn(m, normals[f][0], normals[f][1], normals[f][2])
            flaps += if (facing(n, mid)) {
                Surface(depth, points, Kraft, 0.58f + 0.5f * lit(n), -1)
            } else {
                Surface(depth, points, mix(KraftInside, Amber, pose.glow * 0.4f), 0.5f + 0.4f * lit(floatArrayOf(-n[0], -n[1], -n[2])), -1)
            }
        }
        flaps.sortByDescending { it.depth }
        for (s in flaps) drawPath(polygon(s.points), color(s.color, s.light))

        // Light spills out of the opening.
        if (pose.glow > 0) {
            val r = BOX_W * 0.9f
            drawCircle(Brush.radialGradient(listOf(amber(0.35f * pose.glow), amber(0f)), Offset(ox, oy), r), r, Offset(ox, oy))
        }
    }
}

/** Wraps an angle into -π…π. */
private fun wrap(a: Float) = a - TAU_F * round(a / TAU_F)

/** What is printed round the canister [s] radians from the middle of the label. */
private fun surface(s: Float): FloatArray {
    val a = wrap(s)
    if (a >= LABEL_FROM && a < STRIPE_FROM) return Silver
    if (a >= STRIPE_FROM && a < STRIPE_TO) return LabelStripe
    // The film behind the window: bands along the axis, every third a bright one.
    var past = a - STRIPE_TO
    if (past < 0) past += TAU_F
    val band = floor(past / FILM_STEP).toInt()
    if (band % 6 == 3) return FilmLime
    return if (band % 2 == 0) FilmDark else FilmLight
}

/** Light on the round side, ψ radians from the line facing the eye, with a highlight above. */
private fun roundLight(psi: Float) = 0.42f + 0.58f * max(0f, cos(psi + 0.45f))

private fun shine(psi: Float): Float {
    val k = (psi + 0.75f) / 0.2f
    return exp(-k * k)
}

/**
 * A drum from [x0] to [x1] of radius [r], seen a little from its left end, in strips round its
 * front half. [spin] turns the print; [printed] is false for a plain mint cap.
 */
private fun DrawScope.drum(x0: Float, x1: Float, r: Float, spin: Float, printed: Boolean) {
    val e = r * CAN_VIEW
    val step = PI_F / STRIPS
    for (i in 0 until STRIPS) {
        // Each strip reaches a little into the next, so no seam shows between them.
        val a = -PI_F / 2 + i * step - 0.01f
        val b = a + step + 0.02f
        val mid = a + step / 2 + 0.01f
        val surfaceColor = if (printed) surface(mid + spin) else MintRgb
        val light = roundLight(mid)
        val gloss = shine(mid) * (if (printed) 0.55f else 0.4f)
        val base = floatArrayOf(min(255f, surfaceColor[0] * light), min(255f, surfaceColor[1] * light), min(255f, surfaceColor[2] * light))
        val ca = e * cos(a)
        val cb = e * cos(b)
        drawPath(
            polygon(floatArrayOf(x0 + ca, r * sin(a), x1 + ca, r * sin(a), x1 + cb, r * sin(b), x0 + cb, r * sin(b))),
            color(mix(base, WhiteRgb, gloss), 1f),
        )
    }
}

/** The canister centred at [center], [spin] radians round its axis. */
internal fun DrawScope.drawFilmCanister(center: Offset, scale: Float, spin: Float) {
    val half = CAN_LEN / 2
    withTransform({
        translate(center.x, center.y)
        rotate(CAN_TILT, pivot = Offset.Zero)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        // A faint mint glow behind it.
        val glowR = half + 40
        drawCircle(
            Brush.radialGradient(listOf(Color(0.66f, 0.9f, 0.75f, 0.12f), Color(0.66f, 0.9f, 0.75f, 0f)), Offset.Zero, glowR),
            glowR,
            Offset.Zero,
        )

        drum(half - CAP_LEN, half, CAP_R, spin, false)
        drum(-half + CAP_LEN, half - CAP_LEN, CAN_R, spin, true)

        // "ALIEN" wraps round the side: each point goes to its angle on the round surface, folded
        // flat at the edge it turns behind.
        if (cos(wrap(TEXT_AT - spin)) > 0.15f) {
            val e = CAN_R * CAN_VIEW
            val path = Path().apply { fillType = PathFillType.EvenOdd }
            for (contour in Alien.contours) {
                val count = contour.size / 2
                for (k in 0 until count) {
                    val x0 = contour[k * 2]
                    val y0 = contour[k * 2 + 1]
                    val x1 = contour[(k + 1) % count * 2]
                    val y1 = contour[(k + 1) % count * 2 + 1]
                    // Three steps along each edge keep the long strokes bent round the drum.
                    for (j in 0 until 3) {
                        val t = j / 3f
                        val gx = x0 + (x1 - x0) * t
                        val gy = y0 + (y1 - y0) * t
                        val psi = wrap(TEXT_AT + (gy - 5) * TEXT_SCALE / CAN_R - spin).coerceIn(-PI_F / 2, PI_F / 2)
                        val x = (gx - Alien.width / 2) * TEXT_SCALE + e * cos(psi)
                        val y = CAN_R * sin(psi)
                        if (k == 0 && j == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                }
                path.close()
            }
            drawPath(path, color(InkRgb, 1f))
        }

        drum(-half, -half + CAP_LEN, CAP_R, spin, false)

        // The open end: a mint rim round dark glass, and the spool's hub inside.
        val er = CAP_R * CAN_VIEW
        fun oval(k: Float, c: Color) = drawOval(c, Offset(-half - er * k, -CAP_R * k), Size(er * 2 * k, CAP_R * 2 * k))
        oval(1f, color(MintRim, 1f))
        oval(0.74f, color(GlassRgb, 1f))
        oval(0.26f, color(MintRgb, 1f, 0.85f))
        oval(0.1f, color(GlassRgb, 1f))
    }
}

/** Eight ticks round a point, the brightest one stepping round ten times a second. */
internal fun DrawScope.drawFilmSpinner(point: Offset, time: Float, alpha: Float) {
    val head = floor(time * 10).toInt() % 8
    for (i in 0 until 8) {
        val behind = (head - i + 8) % 8
        val a = i / 8f * TAU_F - PI_F / 2
        drawLine(
            color(SpinnerGrey, 1f, alpha * max(0.2f, 1 - behind * 0.12f)),
            Offset(point.x + 3.6f * cos(a), point.y + 3.6f * sin(a)),
            Offset(point.x + 7.2f * cos(a), point.y + 7.2f * sin(a)),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round,
        )
    }
}
