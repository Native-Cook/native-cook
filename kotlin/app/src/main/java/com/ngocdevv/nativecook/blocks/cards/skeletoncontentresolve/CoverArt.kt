package com.ngocdevv.nativecook.blocks.cards.skeletoncontentresolve

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/** A seeded 0..1 value, the same on every run and in every stack. */
private fun hash(i: Int): Float {
    val x = sin(i * 12.9898 + 4.1414) * 43758.5453
    return (x - floor(x)).toFloat()
}

/** One cover in an s by s box: a three-stop ground, a motif, a soft light from the top left. */
internal fun DrawScope.drawCover(spec: CoverSpec, s: Float) {
    val g = spec.ground
    drawRect(
        Brush.linearGradient(
            0f to g[0], 0.55f to g[1], 1f to g[2],
            start = Offset(s * 0.1f, 0f), end = Offset(s * 0.25f, s),
        ),
        size = Size(s, s),
    )
    drawMotif(spec, s)
    drawRect(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.2f), 0.45f to Color.White.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.16f),
            start = Offset.Zero, end = Offset(s, s),
        ),
        size = Size(s, s),
    )
}

private fun DrawScope.drawMotif(spec: CoverSpec, s: Float) {
    val ink = spec.inkColor
    val accent = spec.accentColor
    when (spec.motif) {
        CoverMotif.Sun -> {
            drawCircle(accent, s * 0.21f, Offset(s * 0.6f, s * 0.44f))
            drawRect(ink, Offset(0f, s * 0.62f), Size(s, s * 0.4f))
            drawRect(accent, Offset(s * 0.35f, s * 0.7f), Size(s * 0.5f, s * 0.02f), alpha = 0.5f)
            drawRect(accent, Offset(s * 0.43f, s * 0.78f), Size(s * 0.34f, s * 0.02f), alpha = 0.32f)
            drawRect(accent, Offset(s * 0.5f, s * 0.86f), Size(s * 0.2f, s * 0.02f), alpha = 0.18f)
        }
        CoverMotif.Rings -> {
            val c = Offset(s * 0.3f, s * 0.74f)
            listOf(0.2f, 0.36f, 0.52f, 0.68f, 0.84f).forEachIndexed { i, r ->
                drawCircle(ink, s * r, c, alpha = 0.95f - i * 0.16f, style = Stroke(s * 0.028f))
            }
            drawCircle(accent, s * 0.085f, c)
        }
        CoverMotif.Bars -> for (i in 0..5) {
            val h = s * (0.22f + 0.5f * hash(i + 3))
            val bw = s * 0.085f
            drawRoundRect(
                if (i == 2) accent else ink,
                Offset(s * 0.16f + i * s * 0.122f, s * 0.5f - h / 2f),
                Size(bw, h),
                CornerRadius(bw / 2f),
            )
        }
        CoverMotif.Stripes -> rotateRad(-0.6f, Offset(s / 2f, s / 2f)) {
            for (i in 0..6) {
                drawRect(
                    if (i == 3) accent else ink,
                    Offset(-s * 0.3f, s * 0.08f + i * s * 0.135f),
                    Size(s * 1.6f, s * 0.07f),
                    alpha = if (i == 3) 1f else 0.85f - 0.08f * abs(i - 3),
                )
            }
        }
        CoverMotif.Dots -> for (i in 0..15) {
            val col = i % 4
            val row = i / 4
            val big = hash(i + 11)
            drawCircle(
                if (i == 6) accent else ink,
                s * (0.03f + 0.045f * big),
                Offset(s * (0.2f + col * 0.2f), s * (0.2f + row * 0.2f)),
                alpha = if (i == 6) 1f else 0.55f + 0.4f * big,
            )
        }
        CoverMotif.Arc -> {
            val c = Offset(s * 0.08f, s)
            drawCircle(ink, s * 0.72f, c)
            drawCircle(spec.ground[1], s * 0.48f, c)
            drawCircle(accent, s * 0.26f, c)
            drawCircle(accent, s * 0.055f, Offset(s * 0.76f, s * 0.24f), alpha = 0.9f)
        }
        CoverMotif.Steps -> for (i in 0..4) {
            drawRect(
                if (i == 4) accent else ink,
                Offset(0f, s * (0.24f + i * 0.14f)),
                Size(s * (0.3f + i * 0.14f), s * 0.11f),
                alpha = if (i == 4) 1f else 0.5f + i * 0.12f,
            )
        }
    }
}
