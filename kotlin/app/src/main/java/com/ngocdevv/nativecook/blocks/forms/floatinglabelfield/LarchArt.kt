package com.ngocdevv.nativecook.blocks.forms.floatinglabelfield

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 The header of the Larch sign-up: a valley in autumn drawn in code. Four ridges fade back into
 haze, a low sun sits behind them, and the near slopes carry conifers, a few of them larches
 that have turned gold. It is drawn once and never animates; the fields are the subject.

 The Skia original blurs the halo and the haze with a mask filter. Those two are gradients here,
 with stops taken from the same Gaussian, so they look alike on every API level.
*/

internal val PAGE = Color(0xFFF3EFE6)
private val RIDGES = listOf(Color(0xFFD3D9C6), Color(0xFFB4C3AA), Color(0xFF7E9C83), Color(0xFF3B6B54))
private val PINE = listOf(Color(0xFF2C5A46), Color(0xFF356650))
private val GOLD = listOf(Color(0xFFD9A441), Color(0xFFC98A1B), Color(0xFFE2B65A))

private fun hash(i: Int): Double {
    val s = sin(i * 12.9898) * 43758.5453
    return s - floor(s)
}

/** Height of ridge `layer` at x: two slow sines and a little texture. */
private fun ridgeY(layer: Int, x: Float, w: Float, base: Float, amp: Float): Float {
    val u = (x / w).toDouble()
    return (
        base + amp * (
            0.62 * sin(u * (2.1 + layer * 0.9) + layer * 1.7) +
                0.3 * sin(u * (5.3 + layer * 1.3) + layer * 4.1) +
                0.08 * sin(u * 23 + layer * 2.3)
            )
        ).toFloat()
}

private fun ridgePath(layer: Int, w: Float, h: Float, base: Float, amp: Float) = Path().apply {
    moveTo(0f, h)
    var x = 0f
    while (x <= w + 4f) {
        lineTo(x, ridgeY(layer, x, w, base, amp))
        x += 4f
    }
    lineTo(w, h)
    close()
}

/** A conifer: three tiers narrowing to a point, standing on (x, y). */
private fun Path.addTree(x: Float, y: Float, tall: Float) {
    val wide = tall * 0.3f
    moveTo(x, y - tall)
    lineTo(x + wide * 0.42f, y - tall * 0.62f)
    lineTo(x + wide * 0.24f, y - tall * 0.62f)
    lineTo(x + wide * 0.74f, y - tall * 0.3f)
    lineTo(x + wide * 0.5f, y - tall * 0.3f)
    lineTo(x + wide, y + 2f)
    lineTo(x - wide, y + 2f)
    lineTo(x - wide * 0.5f, y - tall * 0.3f)
    lineTo(x - wide * 0.74f, y - tall * 0.3f)
    lineTo(x - wide * 0.24f, y - tall * 0.62f)
    lineTo(x - wide * 0.42f, y - tall * 0.62f)
    close()
}

private class Art(
    val ridges: List<Path>,
    val pines: List<Path>,
    val golds: List<Path>,
    val farTrees: Path,
    val sun: Offset,
    val mark: Path,
)

private fun makeArt(w: Float, h: Float, markX: Float, markY: Float): Art {
    val bases = floatArrayOf(h * 0.46f, h * 0.58f, h * 0.7f, h * 0.84f)
    val amps = floatArrayOf(h * 0.07f, h * 0.075f, h * 0.07f, h * 0.06f)
    val ridges = RIDGES.indices.map { ridgePath(it, w, h, bases[it], amps[it]) }

    // Small, pale trees on the third ridge; full-size ones on the nearest.
    val far = Path()
    val farCount = (w / 15f).roundToInt()
    for (i in 0 until farCount) {
        val x = ((i + hash(i + 40) * 0.8) / farCount * w).toFloat()
        if (hash(i + 71) < 0.3) continue
        far.addTree(x, ridgeY(2, x, w, bases[2], amps[2]) + 1f, (7 + hash(i + 9) * 7).toFloat())
    }

    val pines = PINE.map { Path() }
    val golds = GOLD.map { Path() }
    val count = (w / 9f).roundToInt()
    for (i in 0 until count) {
        val x = ((i + hash(i + 3) * 0.9) / count * w).toFloat()
        if (hash(i + 17) < 0.18) continue
        val y = ridgeY(3, x, w, bases[3], amps[3]) + 1f + (hash(i + 5) * 5).toFloat()
        val tall = (15 + hash(i + 11) * 15).toFloat()
        // Larches gather in two stands rather than being sprinkled evenly.
        val stand = sin(x / w * 7.4 + 0.6) * 0.5 + 0.5
        if (hash(i + 29) < 0.14 + stand * 0.42) {
            golds[floor(hash(i + 31) * GOLD.size).toInt()].addTree(x, y, tall * 0.94f)
        } else {
            pines[floor(hash(i + 37) * PINE.size).toInt()].addTree(x, y, tall)
        }
    }

    val mark = Path().apply { addTree(markX + 13f, markY + 20f, 15f) }
    return Art(ridges, pines, golds, far, Offset(w * 0.7f, h * 0.4f), mark)
}

/** Error function (Abramowitz and Stegun 7.1.26), for the edge of a Gaussian blur. */
private fun erf(x: Float): Float {
    val sign = if (x < 0f) -1f else 1f
    val a = if (x < 0f) -x else x
    val t = 1f / (1f + 0.3275911f * a)
    val y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f) *
        t * exp(-a * a)
    return sign * y
}

/** Coverage at `y` of the band y0..y1 blurred with this sigma. */
private fun band(y: Float, y0: Float, y1: Float, sigma: Float): Float {
    val k = sigma * sqrt(2f)
    return 0.5f * (erf((y - y0) / k) - erf((y - y1) / k))
}

/** A full-width band of `color` whose top and bottom edges are blurred. */
private fun DrawScope.hazeBand(y0: Float, y1: Float, sigma: Float, color: Color, width: Float) {
    val top = y0 - sigma * 3f
    val bottom = y1 + sigma * 3f
    val span = bottom - top
    val stops = Array(25) { k ->
        val f = k / 24f
        f to color.copy(alpha = color.alpha * band(top + span * f, y0, y1, sigma))
    }
    drawRect(
        brush = Brush.verticalGradient(colorStops = stops, startY = top, endY = bottom),
        topLeft = Offset(0f, top),
        size = Size(width, span),
    )
}

@Composable
internal fun LarchArt(width: Dp, height: Dp, markX: Dp, markY: Dp, modifier: Modifier = Modifier) {
    val w = width.value
    val h = height.value
    val mx = markX.value
    val my = markY.value
    val art = remember(w, h, mx, my) { makeArt(w, h, mx, my) }

    Canvas(modifier.size(width, height)) {
        scale(density, Offset.Zero) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color(0xFFDCE3D6),
                    0.34f to Color(0xFFECE6CF),
                    0.6f to Color(0xFFF4E5C6),
                    1f to PAGE,
                    startY = 0f,
                    endY = h,
                ),
                size = Size(w, h),
            )

            // The halo: a disc of radius 0.3 h blurred with sigma 0.14 h.
            val haloR = h * 0.3f
            val haloSigma = h * 0.14f
            val haloOuter = haloR + haloSigma * 3f
            val halo = Color(255, 236, 190).copy(alpha = 0.55f)
            val haloStops = Array(17) { k ->
                val f = k / 16f
                f to halo.copy(alpha = 0.55f * band(haloOuter * f, -haloR, haloR, haloSigma))
            }
            drawCircle(
                brush = Brush.radialGradient(colorStops = haloStops, center = art.sun, radius = haloOuter),
                radius = haloOuter,
                center = art.sun,
            )
            drawCircle(Color(0xFFFFF3D2), radius = h * 0.085f, center = art.sun)

            drawPath(art.ridges[0], RIDGES[0])
            drawPath(art.ridges[1], RIDGES[1])
            hazeBand(h * 0.5f, h * 0.7f, 16f, Color(244, 239, 226).copy(alpha = 0.5f), w)
            drawPath(art.farTrees, Color(0xFF6C8D74))
            drawPath(art.ridges[2], RIDGES[2])
            hazeBand(h * 0.7f, h * 0.82f, 14f, Color(244, 239, 226).copy(alpha = 0.42f), w)
            drawPath(art.pines[0], PINE[0])
            drawPath(art.pines[1], PINE[1])
            drawPath(art.golds[0], GOLD[0])
            drawPath(art.golds[1], GOLD[1])
            drawPath(art.golds[2], GOLD[2])
            drawPath(art.ridges[3], RIDGES[3])

            // The valley floor melts into the page.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to PAGE.copy(alpha = 0f),
                    0.55f to PAGE.copy(alpha = 0.8f),
                    1f to PAGE,
                    startY = h * 0.8f,
                    endY = h,
                ),
                topLeft = Offset(0f, h * 0.8f),
                size = Size(w, h * 0.2f + 1f),
            )

            drawRoundRect(
                color = Color(0xFF1F5C45),
                topLeft = Offset(mx, my),
                size = Size(26f, 26f),
                cornerRadius = CornerRadius(8f),
            )
            drawPath(art.mark, Color(0xFFF1D48A))
        }
    }
}
