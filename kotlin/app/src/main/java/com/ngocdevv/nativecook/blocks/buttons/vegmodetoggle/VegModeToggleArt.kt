package com.ngocdevv.nativecook.blocks.buttons.vegmodetoggle

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.max
import kotlin.math.min

/*
 Veg Mode Toggle — the two dishes, drawn in code (a port of VegModeToggleArt.swift).

 Units: the toggle's height H = 1, origin at the track's top-left; the track is 2.42 × 1. Each dish
 pops up about its pivot on the track's bottom line (y = 1), which also cuts it: the caller clips
 everything below y = 1.
 Broccoli: a cloud of seven lobes, a two-prong stem, a darker front floret and a garlic bulb.
 Drumstick: a red leg leaning right, a white bone on top, a halved boiled egg in front.
*/

internal fun vmtHex(hex: Long, alpha: Float = 1f) = Color(
    ((hex shr 16) and 0xFF).toInt() / 255f, ((hex shr 8) and 0xFF).toInt() / 255f, (hex and 0xFF).toInt() / 255f, alpha,
)

/**
 * Fills and strokes either in colour or, for the shadow layer, as a blurred black silhouette.
 *
 * The art is drawn in units of the toggle's height (`unit` dp). A blurred path is rasterised at its
 * local size before the canvas scale applies, so the silhouette is drawn scaled up to dp first:
 * at unit scale a 1 × 1 mask stretched 270 times would smear into a wide halo.
 */
internal class VmtPen(private val scope: DrawScope, private val silhouette: Boolean, blur: Float, private val unit: Float) {
    private val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        if (blur > 0f) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
    }
    private val toDp = android.graphics.Matrix().apply { setScale(unit, unit) }
    val colour get() = !silhouette

    private fun ink(path: Path) = scope.drawIntoCanvas {
        val dp = android.graphics.Path(path.asAndroidPath()).apply { transform(toDp) }
        val c = it.nativeCanvas
        c.save()
        c.scale(1 / unit, 1 / unit)
        c.drawPath(dp, ink)
        c.restore()
    }

    fun fill(path: Path, brush: () -> Brush) {
        if (silhouette) {
            ink.style = android.graphics.Paint.Style.FILL
            ink(path)
        } else {
            scope.drawPath(path, brush())
        }
    }

    fun stroke(path: Path, width: Float, brush: () -> Brush) {
        if (silhouette) {
            ink.style = android.graphics.Paint.Style.STROKE
            ink.strokeWidth = width * unit
            ink.strokeCap = android.graphics.Paint.Cap.ROUND
            ink(path)
        } else {
            scope.drawPath(path, brush(), style = Stroke(width, cap = StrokeCap.Round))
        }
    }
}

internal object VmtArt {
    fun circle(x: Double, y: Double, r: Double) = Path().apply {
        addOval(Rect((x - r).toFloat(), (y - r).toFloat(), (x + r).toFloat(), (y + r).toFloat()))
    }

    fun linear(stops: List<Pair<Long, Float>>, x0: Double, y0: Double, x1: Double, y1: Double) = Brush.linearGradient(
        *stops.map { it.second to vmtHex(it.first) }.toTypedArray(),
        start = Offset(x0.toFloat(), y0.toFloat()), end = Offset(x1.toFloat(), y1.toFloat()),
    )

    fun radial(hex: Long, alpha: Float, x: Double, y: Double, r: Double) = Brush.radialGradient(
        listOf(vmtHex(hex, alpha), vmtHex(hex, 0f)), center = Offset(x.toFloat(), y.toFloat()), radius = r.toFloat(),
    )

    // Geometry (shared by the art and its shadow).

    private val LOBES = listOf(
        Triple(0.27, -0.13, 0.24), Triple(-0.16, 0.25, 0.30), Triple(0.54, 0.06, 0.22), Triple(0.19, 0.40, 0.40),
        Triple(0.22, 0.74, 0.28), Triple(0.58, 0.58, 0.20), Triple(-0.06, 0.58, 0.26),
    )
    private val FLORET_LOBES = listOf(
        Triple(0.64, 0.28, 0.13), Triple(0.80, 0.20, 0.14), Triple(0.97, 0.29, 0.12),
        Triple(0.70, 0.42, 0.11), Triple(0.82, 0.40, 0.15), Triple(0.94, 0.42, 0.10),
    )

    private fun union(list: List<Triple<Double, Double, Double>>) = Path().apply {
        for (l in list) addPath(circle(l.first, l.second, l.third))
    }

    private val cloud = union(LOBES)
    private val floret = union(FLORET_LOBES)

    /** The floret's short stalk, under its crown. */
    private val stalk = Path().apply {
        moveTo(0.74f, 0.46f); lineTo(0.90f, 0.46f); lineTo(0.86f, 0.74f); lineTo(0.78f, 0.74f); close()
    }
    private val bulb = circle(0.93, 0.97, 0.25)

    /** The bulb's papery tip, drawn as its own shape over the bulb. */
    private val bulbTip = Path().apply {
        moveTo(0.88f, 0.76f)
        quadraticTo(0.92f, 0.72f, 0.94f, 0.63f)
        quadraticTo(0.95f, 0.72f, 0.98f, 0.76f)
        close()
    }
    private val cloves = Path().apply {
        moveTo(0.86f, 0.80f); quadraticTo(0.81f, 0.90f, 0.84f, 1.02f)
        moveTo(1.00f, 0.80f); quadraticTo(1.05f, 0.90f, 1.02f, 1.02f)
    }
    private val stem = Path().apply {
        moveTo(0.56f, 1.05f); lineTo(0.47f, 0.78f)
        moveTo(0.58f, 1.05f); lineTo(0.66f, 0.80f)
    }
    private val bone = Path().apply { moveTo(2.03f, 0.10f); lineTo(2.13f, -0.12f) }
    private val boneEnds = Path().apply {
        addPath(circle(2.08, -0.19, 0.075))
        addPath(circle(2.19, -0.14, 0.075))
    }
    private val meat = Path().apply {
        moveTo(1.33f, 1.0f)
        cubicTo(1.30f, 0.62f, 1.64f, 0.26f, 2.03f, 0.05f)
        cubicTo(2.12f, 0.14f, 2.23f, 0.42f, 2.22f, 0.66f)
        lineTo(2.10f, 1.0f)
        close()
    }

    /** The left edge of the leg, where the gloss runs. */
    private val meatGloss = Path().apply {
        moveTo(1.45f, 0.80f)
        cubicTo(1.47f, 0.55f, 1.68f, 0.32f, 1.93f, 0.17f)
    }

    // Pop.

    /**
     * Places a dish for its amount a (0 hidden, 1 at rest) and velocity v: it rises from below the
     * bottom line, grows, leans in and squashes with its speed. Reduced motion only fades it.
     */
    private inline fun DrawScope.pop(a: Double, v: Double, pivot: Double, lean: Double, reduced: Boolean, block: DrawScope.() -> Unit) {
        if (reduced) { block(); return }
        val k = max(-1.0, min(1.0, v / 8))
        val s = max(0.0, 0.6 + 0.4 * a)
        withTransform({
            translate(pivot.toFloat(), (1 + (1 - a) * 1.05).toFloat())
            rotate(((1 - a) * lean).toFloat(), Offset.Zero)
            scale((s * (1 - 0.05 * k)).toFloat(), (s * (1 + 0.07 * k)).toFloat(), Offset.Zero)
            translate(-pivot.toFloat(), -1f)
        }) { block() }
    }

    /** A part that lags its dish: scaled about its own pivot by how far behind it is. */
    private inline fun DrawScope.part(a: Double, a2: Double, x: Double, y: Double, reduced: Boolean, block: DrawScope.() -> Unit) {
        if (reduced) { block(); return }
        val s = max(0.0, 1 + 0.6 * (a2 - a)).toFloat()
        withTransform({ scale(s, s, Offset(x.toFloat(), y.toFloat())) }) { block() }
    }

    // Drawing.

    fun DrawScope.broccoli(a: Double, v: Double, a2: Double, reduced: Boolean, pen: VmtPen) {
        pop(a, v, 0.30, -14.0, reduced) {
            pen.stroke(stem, 0.09f) { Brush.linearGradient(listOf(vmtHex(0x3BB455), vmtHex(0x3BB455))) }
            pen.fill(cloud) { linear(listOf(0x1FA244L to 0f, 0x4CC262L to 0.5f, 0xAEE483L to 1f), -0.3, -0.3, 0.35, 1.0) }
            // One broad gloss on the crown.
            if (pen.colour) drawPath(cloud, radial(0xFFFFFF, 0.38f, 0.05, 0.02, 0.34))

            part(a, a2, 0.82, 0.74, reduced) {
                pen.fill(stalk) { linear(listOf(0x5CC266L to 0f, 0x9EDC86L to 1f), 0.82, 0.46, 0.82, 0.74) }
                pen.fill(floret) { linear(listOf(0x1C9A40L to 0f, 0x5DCB6CL to 1f), 0.6, 0.1, 0.95, 0.55) }
                if (pen.colour) {
                    drawPath(circle(0.80, 0.20, 0.14), radial(0xFFFFFF, 0.24f, 0.76, 0.15, 0.12))
                    drawPath(circle(0.64, 0.28, 0.13), radial(0xFFFFFF, 0.18f, 0.60, 0.23, 0.11))
                }
            }

            part(a, a2, 0.93, 1.0, reduced) {
                pen.fill(bulbTip) { linear(listOf(0xF4F1EAL to 0f, 0xDCD9D2L to 1f), 0.93, 0.63, 0.93, 0.76) }
                pen.fill(bulb) { linear(listOf(0xFFFFFFL to 0f, 0xDCD9D2L to 1f), 0.93, 0.70, 0.93, 1.05) }
                if (pen.colour) {
                    drawPath(cloves, vmtHex(0xCFCBC2), style = Stroke(0.012f))
                    drawPath(circle(0.87, 0.84, 0.07), radial(0xFFFFFF, 0.9f, 0.87, 0.84, 0.07))
                }
            }
        }
    }

    fun DrawScope.drumstick(a: Double, v: Double, a2: Double, reduced: Boolean, pen: VmtPen) {
        pop(a, v, 1.76, 14.0, reduced) {
            val boneShade = { linear(listOf(0xFFFFFFL to 0f, 0xDAD5CBL to 1f), 2.05, -0.28, 2.18, 0.08) }
            pen.stroke(bone, 0.10f, boneShade)
            pen.fill(boneEnds, boneShade)

            pen.fill(meat) { linear(listOf(0xF0603AL to 0f, 0xE2412FL to 0.45f, 0xD2282BL to 1f), 1.95, 0.1, 1.45, 0.95) }
            if (pen.colour) {
                drawPath(meatGloss, vmtHex(0xFFFFFF, 0.28f), style = Stroke(0.05f, cap = StrokeCap.Round))
                drawPath(circle(1.98, 0.16, 0.10), radial(0xFFFFFF, 0.30f, 1.98, 0.16, 0.10))
            }

            part(a, a2, 2.0, 1.0, reduced) {
                withTransform({
                    translate(2.02f, 0.86f)
                    rotate(-30f, Offset.Zero)
                }) {
                    pen.fill(eggWhite) { linear(listOf(0xFFFFFFL to 0f, 0xE3DFD6L to 1f), 0.207, -0.158, -0.197, 0.140) }
                }
                if (pen.colour) {
                    drawPath(circle(1.96, 0.92, 0.17), linear(listOf(0xFFD64FL to 0f, 0xF0961EL to 1f), 1.90, 0.76, 2.04, 1.08))
                    drawPath(circle(1.91, 0.86, 0.04), vmtHex(0xFFFFFF, 0.6f))
                }
            }
        }
    }

    /** The egg white in its own frame (centre at the origin, before its −30° turn). */
    private val eggWhite = Path().apply { addOval(Rect(-0.30f, -0.24f, 0.30f, 0.24f)) }
}
