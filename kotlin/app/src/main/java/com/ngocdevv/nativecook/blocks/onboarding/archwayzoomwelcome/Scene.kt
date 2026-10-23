package com.ngocdevv.nativecook.blocks.onboarding.archwayzoomwelcome

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.ngocdevv.nativecook.shared.TAU
import kotlin.math.sin

// The scene is a camera travelling forwards for ever: through an archway to the bar chart behind
// it, on through the bars, to the next archway. Everything in it is drawn from one number, the
// seconds into a round, and each archway with its chart is a "generation" that is drawn twice:
// once on its way out (at `tau`) and once on its way in (at `tau - LOOP`). The one coming in
// stands, at the end of the round, exactly where the one going out stood at its start.

/** One round of the camera, in seconds. */
const val LOOP = 10.7f

// The times of a generation, in seconds from the moment it stands at rest.
// It comes into view small and far away, ARRIVE before, and grows to its place over GROW.
private const val ARRIVE = 3.6f
private const val ARRIVE_FADE = 0.7f
private const val GROW = 2.9f
private const val ARRIVE_SCALE = 0.4f

// The push in: the arch grows to ARCH_ZOOM times its size over PUSH, speeding up, and fades as its
// opening passes the edges of the screen.
private const val PUSH_AT = 0.7f
private const val PUSH = 1.4f
private const val ARCH_ZOOM = 4f
private const val ARCH_OUT_AT = 1.3f
private const val ARCH_OUT = 0.7f

// The chart is further away, so it grows more slowly, to FULL by CREEP_AT; then it creeps on by
// CREEP until LEAVE_AT, and from there the camera pushes through it.
private const val FULL = 7.5f
private const val CREEP_AT = 5f
private const val CREEP = 0.3f
private const val LEAVE_AT = 7.1f
private const val LEAVE_PUSH = 0.476f

// The two key bars turn green from the top down, and a marker drops in over each.
private const val GREEN_IN = 0.3f
private const val MARKER_OUT = 0.7f
private const val MARKER_BOB = 4f
private const val MARKER_BOB_EVERY = 2.1f

// The bars dissolve into mist: their feet first, then all of them; the green ones last.
private val Mist = floatArrayOf(7.8f, 1f, 8.3f, 1.6f)
private val GreenMist = floatArrayOf(8.2f, 1.2f, 8.8f, 1.7f)

// The hills at the horizon sink as the camera sets off.
private const val HILLS_OUT = 1.4f
private const val HILLS_DROP = 40f
private const val HILLS_IN = 1.5f

// Sizes, in dp at rest. Everything scales about the point on the horizon in the middle of the
// screen, which is HORIZON above the sheet.
internal const val HORIZON = 184f

// The arch: its base under the horizon, then its nested outlines, widest first, and its plinth.
private const val ARCH_BASE = 28f
private val ArchWidths = floatArrayOf(112f, 92f, 72f, 62f)
private val ArchHeights = floatArrayOf(150f, 140f, 130f, 125f)
private val Plinth = Size(130f, 8f)

// The chart: the width of a key bar, and the ground it stands on under the horizon.
private const val UNIT = 12.7f
private const val GROUND = 25.5f

private enum class Kind { Light, Pale, Mid, Dark, Key }

/** A bar: its left and right edge and its height, in key-bar widths from the middle, and its colour. */
private class Bar(val from: Float, val to: Float, val height: Float, val kind: Kind)

// From the back to the front.
private val Bars = listOf(
    Bar(0.18f, 0.54f, 1.4f, Kind.Light),
    Bar(-0.52f, 0f, 1.03f, Kind.Pale),
    Bar(-2.07f, -1.65f, 0.4f, Kind.Mid),
    Bar(1.65f, 2.1f, 1.75f, Kind.Dark),
    Bar(-1.53f, -0.53f, 1.6f, Kind.Key),
    Bar(0.56f, 1.56f, 2.9f, Kind.Key),
    Bar(-0.45f, 0.47f, 0.55f, Kind.Mid),
)

// The part of a bar's width that is its side face, which is darker by SIDE_SHADE.
private const val SIDE = 0.1f
private const val SIDE_SHADE = 0.12f

// A marker over a key bar: its width and height in key-bar widths, and how far over the bar it floats.
private const val MARKER_WIDTH = 0.3f
private const val MARKER_HEIGHT = 0.26f
private const val MARKER_LIFT = 0.4f
private const val MARKER_DROP = 0.5f

private val Greys = listOf(Color(0.89f, 0.89f, 0.89f), Color(0.851f, 0.855f, 0.851f), Color(0.788f, 0.788f, 0.788f), Color(0.745f, 0.749f, 0.745f))
private val Green = Color(0.141f, 0.769f, 0.537f)
private val GreenPale = Color(0.698f, 0.918f, 0.839f)
private val MarkerGreen = Color(0.231f, 0.796f, 0.596f)
private val Hill = Color(0.925f, 0.925f, 0.925f)
private val ArchFaces = listOf(Color(0.918f, 0.918f, 0.918f), Color(0.941f, 0.941f, 0.941f), Color(0.769f, 0.769f, 0.769f))
private val PlinthGrey = Color(0.851f, 0.855f, 0.851f)

// The sky, from the top of the screen to its bottom edge.
private val Sky = arrayOf(
    0f to Color(0.996f, 0.996f, 0.996f),
    0.55f to Color(0.996f, 0.996f, 0.996f),
    0.8f to Color(0.745f, 0.745f, 0.745f),
    1f to Color(0.643f, 0.643f, 0.643f),
)

// One path for every shape of a frame; the scene is only ever drawn on the main thread.
private val Scratch = Path()

private fun smooth(t: Float): Float {
    val u = t.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

private fun shaded(color: Color) = Color(color.red * (1f - SIDE_SHADE), color.green * (1f - SIDE_SHADE), color.blue * (1f - SIDE_SHADE))

private fun archScale(tau: Float): Float {
    if (tau < 0f) return ARRIVE_SCALE + (1f - ARRIVE_SCALE) * smooth((tau + GROW) / GROW)
    val u = ((tau - PUSH_AT) / PUSH).coerceIn(0f, 1f)
    return 1f + (ARCH_ZOOM - 1f) * u * u
}

private fun archAlpha(tau: Float) = if (tau < 0f) smooth((tau + ARRIVE) / ARRIVE_FADE) else 1f - smooth((tau - ARCH_OUT_AT) / ARCH_OUT)

private fun chartScale(tau: Float): Float {
    if (tau < 0f) return archScale(tau)
    if (tau < CREEP_AT) return 1f + (FULL - 1f) * smooth((tau - PUSH_AT) / (CREEP_AT - PUSH_AT))
    val span = LEAVE_AT - CREEP_AT
    if (tau < LEAVE_AT) {
        val u = (tau - CREEP_AT) / span
        return FULL + CREEP * u * u
    }
    // It leaves at the speed the creep ended with, and gathers more.
    val on = tau - LEAVE_AT
    return FULL + CREEP + 2f * CREEP / span * on + LEAVE_PUSH * on * on
}

/** An arch's outline: a rectangle with a round top, standing on the arch's base. */
private fun Path.outline(width: Float, height: Float) {
    val half = width / 2f
    val spring = ARCH_BASE - height + half
    moveTo(-half, ARCH_BASE)
    lineTo(-half, spring)
    arcTo(Rect(-half, spring - half, half, spring + half), 180f, 180f, false)
    lineTo(half, ARCH_BASE)
    close()
}

/** One archway with its chart and its hills, [tau] seconds after it stood at rest (before, if negative). */
private fun DrawScope.paintGeneration(tau: Float, middle: Float, horizon: Float) {
    if (tau < -ARRIVE) return
    val arriving = if (tau < 0f) archAlpha(tau) else 1f

    val hills = if (tau < 0f) smooth((tau + ARRIVE) / HILLS_IN) else 1f - smooth((tau - PUSH_AT) / HILLS_OUT)
    if (hills > 0f) {
        val y = horizon + 8f + if (tau < 0f) 0f else HILLS_DROP * smooth((tau - PUSH_AT) / HILLS_OUT)
        Scratch.rewind()
        Scratch.moveTo(middle - 230f, y); Scratch.quadraticTo(middle - 90f, y - 34f, middle + 40f, y); Scratch.close()
        Scratch.moveTo(middle - 60f, y); Scratch.quadraticTo(middle + 100f, y - 42f, middle + 240f, y); Scratch.close()
        drawPath(Scratch, Hill, alpha = hills)
    }

    // The chart, a bar at a time.
    val scale = chartScale(tau)
    val unit = UNIT * scale
    val ground = horizon + GROUND * scale
    val green = if (tau < 0f) 0f else smooth((tau - PUSH_AT) / GREEN_IN)

    // A face of a bar between `top` and `bottom`, shaded from the bar's `head` down to the ground.
    // A side face has a slant: its top runs down and away.
    fun bar(left: Float, right: Float, top: Float, bottom: Float, head: Float, from: Color, to: Color, slant: Float) {
        Scratch.rewind()
        Scratch.moveTo(left, top); Scratch.lineTo(right, top + slant); Scratch.lineTo(right, bottom); Scratch.lineTo(left, bottom); Scratch.close()
        drawPath(Scratch, Brush.verticalGradient(listOf(from, to), startY = head, endY = ground))
    }

    Bars.forEachIndexed { index, item ->
        val key = item.kind == Kind.Key
        val fade = if (key) GreenMist else Mist
        val foot = arriving * (1f - smooth((tau - fade[0]) / fade[1]))
        val head = arriving * (1f - smooth((tau - fade[2]) / fade[3]))
        if (head <= 0f) return@forEachIndexed
        val left = middle + item.from * unit
        val right = middle + item.to * unit
        val top = ground - item.height * unit
        val side = (right - left) * SIDE
        val grey = Greys[if (key) Kind.Mid.ordinal else item.kind.ordinal]
        // A key bar is green down to `turn`, and still grey below it.
        val turn = if (key) top + (ground - top) * green else top
        if (turn > top) {
            bar(left, right - side, top, turn, top, Green.copy(alpha = head), GreenPale.copy(alpha = foot), 0f)
            bar(right - side, right, top, turn, top, shaded(Green).copy(alpha = head), shaded(GreenPale).copy(alpha = foot), side * 0.6f)
        }
        if (turn < ground) {
            bar(left, right - side, turn, ground, top, grey.copy(alpha = head), grey.copy(alpha = foot), 0f)
            bar(right - side, right, turn, ground, top, shaded(grey).copy(alpha = head), shaded(grey).copy(alpha = foot), if (turn > top) 0f else side * 0.6f)
        }
        if (key && tau > PUSH_AT && tau < LEAVE_AT + MARKER_OUT) {
            // The marker drops into place, bobs, and sinks onto its bar at the end.
            val sink = smooth((tau - LEAVE_AT) / MARKER_OUT)
            val lift = MARKER_LIFT + MARKER_DROP * (1f - green) - MARKER_LIFT * sink
            val bob = MARKER_BOB * sin(tau / MARKER_BOB_EVERY * TAU + index) * smooth(tau - PUSH_AT - GREEN_IN) * (1f - sink)
            val x = (left + right - side) / 2f
            val tip = top - lift * unit + bob
            Scratch.rewind()
            Scratch.moveTo(x - MARKER_WIDTH * unit / 2f, tip - MARKER_HEIGHT * unit)
            Scratch.lineTo(x + MARKER_WIDTH * unit / 2f, tip - MARKER_HEIGHT * unit)
            Scratch.lineTo(x, tip)
            Scratch.close()
            drawPath(Scratch, MarkerGreen, alpha = green * (1f - sink))
        }
    }

    // The arch in front of it: three faces, each the gap between two outlines, on a plinth.
    val alpha = archAlpha(tau)
    if (alpha <= 0f) return
    val zoom = archScale(tau)
    withTransform({
        translate(middle, horizon)
        scale(zoom, zoom, pivot = Offset.Zero)
    }) {
        drawRect(PlinthGrey, Offset(-Plinth.width / 2f, ARCH_BASE), Plinth, alpha = alpha)
        for (step in ArchFaces.indices) {
            Scratch.rewind()
            Scratch.fillType = PathFillType.EvenOdd
            Scratch.outline(ArchWidths[step], ArchHeights[step])
            Scratch.outline(ArchWidths[step + 1], ArchHeights[step + 1])
            drawPath(Scratch, ArchFaces[step], alpha = alpha)
        }
        Scratch.rewind()
        Scratch.fillType = PathFillType.NonZero
    }
}

/**
 * Paints the scene, in dp, on a screen of the given size, [tau] seconds into a round (0 up to
 * [LOOP]), with the horizon at [horizon] from the top.
 */
internal fun DrawScope.paintScene(width: Float, height: Float, horizon: Float, tau: Float) {
    drawRect(Brush.verticalGradient(*Sky, startY = 0f, endY = height), size = Size(width, height))
    // The one coming in is further away, so it is behind the one going out.
    paintGeneration(tau - LOOP, width / 2f, horizon)
    paintGeneration(tau, width / 2f, horizon)
}
