package com.ngocdevv.nativecook.blocks.onboarding.morphingbadgetour

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private fun rgb(color: Float) = Color(0xFF000000.toInt() or color.toInt())

/** A rounded rectangle round (x, y), filled with a gradient from [a] to [b]: down at tilt 0, across at 1. */
private fun DrawScope.block(x: Float, y: Float, w: Float, h: Float, radius: Float, alpha: Float, a: Float, b: Float, tilt: Float) {
    if (alpha <= 0.003f || w <= 0.5f || h <= 0.5f) return
    val dx = sin(tilt * PI.toFloat() / 2f) * w / 2f
    val dy = cos(tilt * PI.toFloat() / 2f) * h / 2f
    val r = min(radius, min(w, h) / 2f)
    drawRoundRect(
        Brush.linearGradient(listOf(rgb(a), rgb(b)), Offset(x - dx, y - dy), Offset(x + dx, y + dy)),
        Offset(x - w / 2f, y - h / 2f),
        Size(w, h),
        CornerRadius(r, r),
        alpha.coerceIn(0f, 1f),
    )
}

/** A polygon whose points start at [first] in [points], drawn round (x, y) at [scale]. [path] is reused. */
private fun DrawScope.polygon(path: Path, points: FloatArray, first: Int, x: Float, y: Float, scale: Float, color: Color, alpha: Float) {
    path.rewind()
    var i = first
    while (i + 1 < points.size) {
        if (i == first) path.moveTo(x + points[i] * scale, y + points[i + 1] * scale) else path.lineTo(x + points[i] * scale, y + points[i + 1] * scale)
        i += 2
    }
    path.close()
    drawPath(path, color, alpha.coerceIn(0f, 1f))
}

private fun DrawScope.glyph(path: Path, index: Int, x: Float, y: Float, scale: Float, color: Color, alpha: Float) {
    if (alpha <= 0.003f) return
    val shown = alpha.coerceIn(0f, 1f)
    for (part in Glyphs[index]) {
        when (part[0]) {
            0f -> polygon(path, part, 1, x, y, scale, color, shown)
            2f -> drawCircle(color, part[3] * scale, Offset(x + part[1] * scale, y + part[2] * scale), shown)
            else -> drawRoundRect(
                color,
                Offset(x + part[1] * scale, y + part[2] * scale),
                Size(part[3] * scale, part[4] * scale),
                CornerRadius(part[5] * scale, part[5] * scale),
                alpha = shown,
            )
        }
    }
}

/**
 * Draws the cast round ([cx], [cy]), in dp. [read] gives field `i` of piece `k`; [veil] multiplies
 * every opacity; [clock] is the time for the bobbing, the breathing and the sparkles (null: none).
 */
internal fun DrawScope.paintStage(path: Path, read: (k: Int, i: Int) -> Float, veil: Float, clock: Float?, cx: Float, cy: Float) {
    if (veil <= 0.003f) return
    val bobbing = if (clock == null) 0f else read(IDLE, 0)
    val breathing = if (clock == null) 0f else read(IDLE, 1)
    val time = clock ?: 0f

    // The capsule, which is the beam in the last scene.
    block(
        cx + read(CAPSULE, 0), cy + read(CAPSULE, 1), read(CAPSULE, 2), read(CAPSULE, 3), read(CAPSULE, 4), read(CAPSULE, 5) * veil,
        read(CAPSULE, 6), read(CAPSULE, 7), read(CAPSULE, 8),
    )

    // The diamond is in front of the beam and behind the wallet it drops into.
    val diamond = read(DIAMOND, 5) * veil
    if (diamond > 0.003f) {
        for (facet in DiamondFacets) {
            polygon(path, facet, 1, cx + read(DIAMOND, 0), cy + read(DIAMOND, 1), read(DIAMOND, 2) / 2f, rgb(facet[0]), diamond)
        }
    }

    for (n in 0 until EXTRAS + TOKENS) {
        // The small discs first: the badges lie over them.
        val k = if (n < EXTRAS) EXTRA + n else TOKEN + n - EXTRAS
        val x = read(k, 0)
        val y = read(k, 1) - bob(k, bobbing, time)
        val size = breathe(x, y, breathing, time)
        val w = read(k, 2) * size
        val h = read(k, 3) * size
        val alpha = read(k, 5) * veil
        block(cx + x, cy + y, w, h, read(k, 4) * size, alpha, read(k, 6), read(k, 7), read(k, 8))
        if (k >= TOKEN && k < TOKEN + TOKENS) {
            val index = k - TOKEN
            glyph(path, index, cx + x, cy + y, min(w, h) * GLYPH_SCALE, if (GlyphWhite[index]) Color.White else Color.Black, alpha * read(k, 9))
        }
    }

    // The thin line inside the wallet.
    val outline = read(OUTLINE, 5) * veil
    if (outline > 0.003f) {
        val w = read(OUTLINE, 2)
        val h = read(OUTLINE, 3)
        drawRoundRect(
            rgb(BACKGROUND.toFloat()),
            Offset(cx + read(OUTLINE, 0) - w / 2f, cy + read(OUTLINE, 1) - h / 2f),
            Size(w, h),
            CornerRadius(read(OUTLINE, 4), read(OUTLINE, 4)),
            Stroke(1.5f),
            outline.coerceIn(0f, 1f),
        )
    }

    // The hero badge: a blue ring, a white disc and a black rounded square, in the proportions 78 : 52 : 18.
    val hero = (read(HERO, 5) * veil).coerceIn(0f, 1f)
    if (hero > 0.003f) {
        val x = cx + read(HERO, 0)
        val y = cy + read(HERO, 1)
        val unit = read(HERO, 2) / 78f
        drawCircle(rgb(HERO_RING.toFloat()), 36f * unit, Offset(x, y), hero, Stroke(6f * unit))
        drawCircle(Color.White, 26f * unit, Offset(x, y), hero)
        drawRoundRect(Color.Black, Offset(x - 9f * unit, y - 9f * unit), Size(18f * unit, 18f * unit), CornerRadius(4f * unit, 4f * unit), alpha = hero)
    }

    for (j in 0 until SPARKS) {
        val alpha = read(SPARK + j, 5) * veil
        if (alpha <= 0.003f) continue
        val size = read(SPARK + j, 2) / 2f * if (clock == null) 1f else pulse(j, time)
        polygon(path, SparkShape, 0, cx + read(SPARK + j, 0), cy + read(SPARK + j, 1), size, rgb(SPARKLE.toFloat()), alpha)
    }
}
