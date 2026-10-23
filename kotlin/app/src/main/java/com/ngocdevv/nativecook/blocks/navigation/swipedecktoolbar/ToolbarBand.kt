package com.ngocdevv.nativecook.blocks.navigation.swipedecktoolbar

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import com.ngocdevv.nativecook.shared.SoftRoundRect
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

internal val Band = Color(0xFF070909)

// The band above the bottom inset, and the least the inset counts for.
internal const val BAND_HEIGHT = 62f
internal const val BAND_INSET = 34f

// The icons' centres: under the band's top edge, and the first and last from the screen's sides.
internal const val ICON_Y = 40f
internal const val ICON_INSET = 52f
private const val TOP_RADIUS = 40f

// The valley starts this far inside the end icons, takes SLOPE to reach its floor and is DEPTH deep.
private const val SHOULDER = 40f
private const val SLOPE = 52f
private const val DEPTH = 74f
private const val RING = 58f

// A ring in flight is this much wider, and its outline this much thinner.
private const val RING_STRETCH = 0.5f
private const val RING_THIN = 0.4f
internal const val CAPSULE_WIDTH = 136f
internal const val CAPSULE_HEIGHT = 42f

// The cross and the heart from the capsule's ends.
internal const val MARK_INSET = 29f

// The side of the capsule a verdict lights.
private const val PILL_WIDTH = 54f
private const val PILL_HEIGHT = 38f
private const val MARKER_WIDTH = 30f
private const val MARKER_HEIGHT = 20f
private const val MARKER_SLIDE = 6f
private const val GLOW = 16f
private const val STROKE = 1.6f
private const val CROSS_STROKE = 2f
private const val CLOCK = 8.5f
private const val CROSS = 5f

// The heart of the capsule next to the heart of the tab, and how much a verdict's mark grows.
private const val HEART_SMALL = 0.83f
private const val GROW = 0.15f
private const val FLASH = 0.06f

// A heart 16.8 dp wide around its own centre.
private val Heart = PathParser()
    .parsePathString(
        "M0 8.2 C0 8.2 -8.4 3.4 -8.4 -2.6 C-8.4 -5.4 -6.2 -7.4 -3.7 -7.4 C-2.1 -7.4 -0.7 -6.5 0 -5.2 " +
            "C0.7 -6.5 2.1 -7.4 3.7 -7.4 C6.2 -7.4 8.4 -5.4 8.4 -2.6 C8.4 3.4 0 8.2 0 8.2 Z",
    )
    .toPath()

internal fun iconX(index: Float, width: Float) = ICON_INSET + index * (width - ICON_INSET * 2f) / 3f

internal fun smooth(x: Float, from: Float, to: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** What the band keeps between frames: its outline, and the soft shapes of the verdicts' glow. */
internal class BandPaints(rejectColor: Color, likeColor: Color) {
    val outline = Path()
    val hands = Path()
    val rejectGlow = SoftRoundRect(rejectColor.copy(alpha = 0.6f), GLOW)
    val likeGlow = SoftRoundRect(likeColor.copy(alpha = 0.6f), GLOW)
    val halo = SoftRoundRect(Color.White, 8f)
}

/** The band: a sheet with round top corners whose top edge sinks [depth] between its shoulders. */
private fun Path.band(width: Float, height: Float, depth: Float) {
    val from = ICON_INSET + SHOULDER
    val to = width - from
    val half = SLOPE / 2f
    rewind()
    moveTo(0f, height)
    lineTo(0f, TOP_RADIUS)
    quadraticTo(0f, 0f, TOP_RADIUS, 0f)
    lineTo(from, 0f)
    cubicTo(from + half, 0f, from + half, depth, from + SLOPE, depth)
    lineTo(to - SLOPE, depth)
    cubicTo(to - half, depth, to - half, 0f, to, 0f)
    lineTo(width - TOP_RADIUS, 0f)
    quadraticTo(width, 0f, width, TOP_RADIUS)
    lineTo(width, height)
    close()
}

/**
 * The bottom band of the swipe deck: four line icons and a ring on the current one. On the deck's
 * tab its top edge sinks into a valley, and the two middle icons meet in a glass capsule as the
 * cross and the heart that judge a card.
 *
 * @param valley 0 flat, 1 with the valley open; a spring may carry it a little past either.
 * @param ring Where the ring is, in tabs; [ringFrom] and [ringTo] are the tabs it left and is going to.
 * @param cardX How far the top card is from its place.
 * @param fly Whether the card is flying off: 0 to 1, toward [side].
 * @param full The pull that lights a side fully.
 * @param still Reduced motion: the two states of the band cross-fade, and verdicts only change colour.
 */
internal fun DrawScope.drawToolbarBand(
    valley: Float,
    ring: Float,
    ringFrom: Float,
    ringTo: Float,
    cardX: Float,
    fly: Float,
    side: Float,
    full: Float,
    accent: Color,
    likeColor: Color,
    rejectColor: Color,
    paints: BandPaints,
    still: Boolean,
) {
    val width = size.width / density
    val height = size.height / density
    val open = valley.coerceIn(0f, 1f)
    val capsuleX = width / 2f - CAPSULE_WIDTH / 2f
    val capsuleY = ICON_Y - CAPSULE_HEIGHT / 2f

    // Everything below is in dp.
    scale(density, density, pivot = Offset.Zero) {
        paints.outline.band(width, height, if (still) DEPTH else DEPTH * valley)
        drawPath(paints.outline, Band)
        if (still) {
            paints.outline.band(width, height, 0f)
            drawPath(paints.outline, Band, alpha = 1f - open)
        }

        // In flight the ring is longer along its way and its outline thinner.
        val span = ringTo - ringFrom
        val flight = if (still || span == 0f) 0f else sin(PI.toFloat() * ((ring - ringFrom) / span).coerceIn(0f, 1f))
        val ringX = iconX(ring, width)
        val ringWidth = RING * (1f + RING_STRETCH * flight)
        val ringAt = Offset(ringX - ringWidth / 2f, ICON_Y - RING / 2f)
        drawRoundRect(Color.White, ringAt, Size(ringWidth, RING), CornerRadius(RING / 2f), alpha = 0.04f)
        drawRoundRect(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.35f), accent.copy(alpha = 0.6f)),
                start = Offset(ringX - RING / 2f, ICON_Y - RING / 2f),
                end = Offset(ringX + RING / 2f, ICON_Y + RING / 2f),
            ),
            ringAt,
            Size(ringWidth, RING),
            CornerRadius(RING / 2f),
            style = Stroke(1f - RING_THIN * flight),
        )

        // A person: a head over a rounded body.
        translate(iconX(0f, width), ICON_Y) {
            drawCircle(Color.White, 3.6f, Offset(0f, -4.2f), style = Stroke(STROKE))
            drawRoundRect(Color.White, Offset(-6.5f, 2.3f), Size(13f, 6.4f), CornerRadius(3.2f), style = Stroke(STROKE))
        }

        // Two tilted cards, the upper one in the accent colour.
        val cardAt = Offset(-4.5f, -6f)
        val cardSize = Size(9f, 12f)
        translate(iconX(3f, width) - 1.6f, ICON_Y + 1.2f) {
            rotate(Math.toDegrees(-0.17).toFloat(), pivot = Offset.Zero) {
                drawRoundRect(Color.White, cardAt, cardSize, CornerRadius(2.5f), style = Stroke(STROKE))
            }
        }
        translate(iconX(3f, width) + 2.4f, ICON_Y - 1.2f) {
            rotate(Math.toDegrees(0.25).toFloat(), pivot = Offset.Zero) {
                drawRoundRect(Band, cardAt, cardSize, CornerRadius(2.5f))
                drawRoundRect(accent, cardAt, cardSize, CornerRadius(2.5f), style = Stroke(STROKE))
            }
        }

        // A verdict grows with the card's pull; a card that flies off lights its side fully, then fades.
        fun strength(to: Float): Float {
            val pulled = (to * cardX / full).coerceIn(0f, 1f)
            val flown = if (fly > 0f && side == to) smooth(fly, 0f, 0.25f) else 0f
            return max(pulled, flown) * (1f - smooth(fly, 0.5f, 1f)) * open
        }
        val reject = strength(-1f)
        val like = strength(1f)

        val glass = if (still) open else smooth(open, 0.3f, 1f)
        val capsuleAt = Offset(capsuleX, capsuleY)
        val capsuleSize = Size(CAPSULE_WIDTH, CAPSULE_HEIGHT)
        drawRoundRect(Color(0xFF0E2233), capsuleAt, capsuleSize, CornerRadius(CAPSULE_HEIGHT / 2f), alpha = 0.7f * glass)
        drawRoundRect(Color.White, capsuleAt, capsuleSize, CornerRadius(CAPSULE_HEIGHT / 2f), alpha = 0.12f * glass, style = Stroke(1f))
        drawPill(capsuleX + 2f, rejectColor, paints.rejectGlow, glass * reject, fly, inward = 1f, still)
        drawPill(capsuleX + CAPSULE_WIDTH - 2f - PILL_WIDTH, likeColor, paints.likeGlow, glass * like, fly, inward = -1f, still)
        drawRoundRect(
            Color.White,
            Offset(width / 2f - MARKER_WIDTH / 2f + if (still) 0f else MARKER_SLIDE * (like - reject), ICON_Y - MARKER_HEIGHT / 2f),
            Size(MARKER_WIDTH, MARKER_HEIGHT),
            CornerRadius(MARKER_HEIGHT / 2f),
            alpha = 0.25f * glass * (if (still) 1f else smooth(open, 0.7f, 1f)) * (1f - max(reject, like)),
            style = Stroke(1f),
        )

        if (still) {
            drawMiddle(0f, 1f - open, width, reject, like, paints, still)
            drawMiddle(1f, open, width, reject, like, paints, still)
        } else {
            drawMiddle(open, 1f, width, reject, like, paints, still)
        }
    }
}

/** One side of the capsule lit by a verdict: its colour from the end toward the middle, and a glow. */
private fun DrawScope.drawPill(x: Float, color: Color, glow: SoftRoundRect, strength: Float, fly: Float, inward: Float, still: Boolean) {
    if (strength <= 0f) return
    val y = ICON_Y - PILL_HEIGHT / 2f
    val grown = if (still) 1f else 1f + FLASH * sin(PI.toFloat() * fly)
    scale(grown, grown, pivot = Offset(x + PILL_WIDTH / 2f, ICON_Y)) {
        if (!still) drawIntoCanvas { glow.draw(it.nativeCanvas, x, y, PILL_WIDTH, PILL_HEIGHT, PILL_HEIGHT / 2f, strength) }
        val outer = if (inward > 0f) x else x + PILL_WIDTH
        drawRoundRect(
            Brush.linearGradient(listOf(color, color.copy(alpha = 0.45f)), start = Offset(outer, 0f), end = Offset(outer + inward * PILL_WIDTH, 0f)),
            Offset(x, y),
            Size(PILL_WIDTH, PILL_HEIGHT),
            CornerRadius(PILL_HEIGHT / 2f),
            alpha = strength,
        )
        drawRoundRect(Color.White, Offset(x, y), Size(PILL_WIDTH, PILL_HEIGHT), CornerRadius(PILL_HEIGHT / 2f), alpha = 0.2f * strength, style = Stroke(1f))
    }
}

/**
 * The two middle tabs on their way to the capsule: at 0 a clock and an outlined heart, at 1 the
 * cross its hands have opened into and the same heart, smaller and solid.
 */
private fun DrawScope.drawMiddle(m: Float, opacity: Float, width: Float, reject: Float, like: Float, paints: BandPaints, still: Boolean) {
    if (opacity <= 0f) return
    val crossX = width / 2f - CAPSULE_WIDTH / 2f + MARK_INSET
    val heartX = width / 2f + CAPSULE_WIDTH / 2f - MARK_INSET
    val clockX = iconX(1f, width)
    val tabHeartX = iconX(2f, width)

    translate(clockX + (crossX - clockX) * m, ICON_Y) {
        val grown = if (still) 1f else 1f + GROW * reject
        scale(grown, grown, pivot = Offset.Zero) {
            drawCircle(Color.White, CLOCK * (1f - 0.6f * m), Offset.Zero, alpha = opacity * (1f - smooth(m, 0f, 0.6f)), style = Stroke(STROKE))
            // The hands leave the centre and become the two strokes of the cross.
            paints.hands.rewind()
            paints.hands.moveTo(-CROSS * m, CROSS * m)
            paints.hands.lineTo(CROSS * m, -4.5f - (CROSS - 4.5f) * m)
            paints.hands.moveTo(-CROSS * m, -CROSS * m)
            paints.hands.lineTo(3f + (CROSS - 3f) * m, 1.8f + (CROSS - 1.8f) * m)
            drawPath(paints.hands, Color.White, alpha = opacity, style = Stroke(STROKE + (CROSS_STROKE - STROKE) * m, cap = StrokeCap.Round))
        }
    }

    translate(tabHeartX + (heartX - tabHeartX) * m, ICON_Y) {
        val factor = (1f - (1f - HEART_SMALL) * m) * if (still) 1f else 1f + GROW * like
        scale(factor, factor, pivot = Offset.Zero) {
            if (!still && like > 0f) drawIntoCanvas { paints.halo.draw(it.nativeCanvas, -13f, -13f, 26f, 26f, 13f, opacity * 0.4f * like) }
            drawPath(Heart, Color.White, alpha = opacity * m)
            drawPath(Heart, Color.White, alpha = opacity, style = Stroke(STROKE, join = StrokeJoin.Round))
        }
    }
}
