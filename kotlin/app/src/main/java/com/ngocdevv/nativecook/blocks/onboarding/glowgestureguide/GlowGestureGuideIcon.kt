package com.ngocdevv.nativecook.blocks.onboarding.glowgestureguide

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

enum class GlowGestureGuideKind { Tap, Hold, Edge, Swipe }

// Drawn in a 32 × 32 box: a pointing hand, and what each gesture adds around it.
private const val HAND =
    "M11 18V10.5C11 9.4 11.9 8.5 13 8.5C14.1 8.5 15 9.4 15 10.5V16.5" +
        "M15 15.5C15 14.6 15.7 13.9 16.6 13.9C17.5 13.9 18.2 14.6 18.2 15.5V17" +
        "M18.2 16.3C18.2 15.4 18.9 14.7 19.8 14.7C20.7 14.7 21.4 15.4 21.4 16.3V17.5" +
        "M21.4 17.2C21.4 16.3 22.1 15.6 23 15.6C23.9 15.6 24.6 16.3 24.6 17.2V22C24.6 26.4 21.6 29 17.6 29H16.4" +
        "C14 29 12.4 28 11.1 26.3L7.6 21.7C6.9 20.8 7.1 19.6 7.9 19C8.7 18.4 9.8 18.5 10.4 19.3L11 20.1V18"
private const val LEFT = "M7.5 4L4.5 7L7.5 10"
private const val RIGHT = "M18.5 4L21.5 7L18.5 10"
private const val BOX = 32f
private const val LINE = 1.5f
// The fingertip, the centre of the dots, arcs and ripples.
private val Tip = Offset(13f, 10.5f)
private val Dots = listOf(-160f, -125f, -90f, -55f, -20f)
private const val DOT_R = 5.5f
private val Arcs = listOf(4.8f, 7.6f)
private const val ARC_FROM = -160f
private const val ARC_TO = -20f
private const val EDGE_X = 3.5f
// Each gesture's little performance, in seconds after its row is chosen.
internal const val PLAY_FOR = 1.3f
private const val PLAY_AT = 0.2f

private val HandPath = PathParser().parsePathString(HAND).toPath()
private val LeftPath = PathParser().parsePathString(LEFT).toPath()
private val RightPath = PathParser().parsePathString(RIGHT).toPath()
private val Line = Stroke(LINE, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** An arc round the fingertip at radius [r], the same 16 steps the other versions draw. */
private fun arc(r: Float) = Path().apply {
    for (k in 0..16) {
        val a = (ARC_FROM + (ARC_TO - ARC_FROM) * k / 16f) * PI.toFloat() / 180f
        val x = Tip.x + r * cos(a)
        val y = Tip.y + r * sin(a)
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
}

private val InnerArc = arc(Arcs[0])
private val OuterArc = arc(Arcs[1])

private fun bump(s: Float, length: Float) = if (s in 0f..length) sin(PI.toFloat() * s / length) else 0f

/** A wave starting at [at]: 0 → 1 over [length], or -1 outside it. */
private fun wave(t: Float, at: Float, length: Float): Float {
    val u = (t - at) / length
    return if (u in 0f..1f) u else -1f
}

/** Draws a gesture's icon filling the scope; [since] is the seconds since its row was chosen. */
internal fun DrawScope.drawGestureIcon(kind: GlowGestureGuideKind, since: Float, color: Color) {
    val k = size.width / BOX
    scale(k, k, pivot = Offset.Zero) {
        // Edge: the bar flashes twice and the hand nudges towards it.
        val edgeWave = wave(since, PLAY_AT, 0.8f)
        val flash = if (edgeWave < 0f) 0f else 0.5f - 0.5f * cos(4f * PI.toFloat() * edgeWave)

        translate(left = if (kind == GlowGestureGuideKind.Edge) 3f - 2f * flash else 0f) {
            drawPath(HandPath, color, style = Line)
            if (kind == GlowGestureGuideKind.Tap || kind == GlowGestureGuideKind.Edge) {
                for (deg in if (kind == GlowGestureGuideKind.Tap) Dots else Dots.take(3)) {
                    val a = deg * PI.toFloat() / 180f
                    drawCircle(color, 0.8f, Offset(Tip.x + DOT_R * cos(a), Tip.y + DOT_R * sin(a)))
                }
            }
        }

        when (kind) {
            GlowGestureGuideKind.Tap -> for (n in 0..1) {
                // Two ripples from the fingertip.
                val u = wave(since, PLAY_AT + 0.45f * n, 0.6f)
                if (u < 0f) continue
                drawCircle(color, 2f + 8f * u, Offset(Tip.x, Tip.y - 1f), alpha = 0.9f * (1f - u).pow(2), style = Stroke(1.2f))
            }
            GlowGestureGuideKind.Hold -> {
                // Two arcs leave the outer one and fade.
                drawPath(InnerArc, color, style = Line)
                drawPath(OuterArc, color, style = Line)
                for (n in 0..1) {
                    val u = wave(since, PLAY_AT + 0.5f * n, 0.7f)
                    if (u < 0f) continue
                    drawPath(arc(Arcs[1] + 5f * u), color, alpha = 0.9f * (1f - u), style = Line)
                }
            }
            GlowGestureGuideKind.Edge ->
                drawLine(color, Offset(EDGE_X, 5f), Offset(EDGE_X, 29f), LINE, StrokeCap.Round, alpha = 1f - 0.7f * flash)
            GlowGestureGuideKind.Swipe -> {
                // The left chevron nudges left, then the right one right.
                translate(left = -3f * bump(since - PLAY_AT, 0.45f)) { drawPath(LeftPath, color, style = Line) }
                translate(left = 3f * bump(since - PLAY_AT - 0.45f, 0.45f)) { drawPath(RightPath, color, style = Line) }
            }
        }
    }
}
