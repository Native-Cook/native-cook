package com.ngocdevv.nativecook.blocks.onboarding.skyglasssteps

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.ceil
import kotlin.math.min

internal const val CARD_WIDTH = 298f
internal const val CARD_HEIGHT = 452f
private const val CARD_RADIUS = 34f

/** The clouds are placed for a screen this tall and keep their distance from its bottom edge. */
private const val SKY_HEIGHT = 852f
private val SkyStops = floatArrayOf(0f, 0.55f, 1f)
private val CloudLight = rgba(241, 244, 249, 1f)
private val CloudShade = rgba(167, 181, 204, 1f)
private val Veil = Color.Black.copy(alpha = 0.08f)
private val Edge = Color.White.copy(alpha = 0.14f)

// How a puff fades from its middle to its rim: full for a third of the way, then a soft edge.
private val PuffStops = floatArrayOf(0f, 0.3f, 0.55f, 0.78f, 1f)
private val PuffEdge = floatArrayOf(1f, 0.96f, 0.7f, 0.28f, 0f)

// Seen through glass a puff is GLASS_SPREAD larger all round, fades from its very middle, and is
// thinner the smaller it was: what a blur of that reach makes of it, without blurring anything.
private val GlassStops = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
private val GlassEdge = floatArrayOf(1f, 0.8f, 0.42f, 0.13f, 0f)
private const val GLASS_SPREAD = 34f
private const val GLASS_GAIN = 2.4f

private class Layer(val period: Float, val puffs: FloatArray)

/**
 * The sky in three depths. A layer is one strip of clouds, `period` dp long, that repeats across
 * the screen and moves left by its own length in a loop: the far strip is the shortest, so it is
 * the slowest. A puff is x, y, rx, ry, whether it is shaded (the belly of a cloud), alpha.
 */
private val Layers = listOf(
    Layer(
        131f,
        floatArrayOf(
            40f, 34f, 92f, 30f, 0f, 0.22f,
            66f, 690f, 50f, 18f, 1f, 0.64f,
            37f, 666f, 47f, 39f, 0f, 0.8f,
            62f, 660f, 61f, 51f, 0f, 0.8f,
            96f, 666f, 48f, 40f, 0f, 0.8f,
            79f, 663f, 38f, 33f, 0f, 0.8f,
            30f, 851f, 38f, 9f, 1f, 0.8f,
            8f, 840f, 21f, 18f, 0f, 1f,
            23f, 839f, 23f, 19f, 0f, 1f,
            49f, 839f, 24f, 20f, 0f, 1f,
            30f, 837f, 21f, 18f, 0f, 1f,
        ),
    ),
    Layer(
        196.5f,
        floatArrayOf(
            110f, 78f, 130f, 26f, 0f, 0.18f,
            30f, 196f, 120f, 46f, 0f, 0.12f,
            70f, 416f, 55f, 26f, 1f, 0.74f,
            42f, 391f, 47f, 39f, 0f, 0.92f,
            65f, 380f, 73f, 61f, 0f, 0.92f,
            106f, 383f, 66f, 55f, 0f, 0.92f,
            59f, 377f, 51f, 45f, 0f, 0.92f,
            150f, 844f, 50f, 12f, 1f, 0.8f,
            124f, 830f, 27f, 22f, 0f, 1f,
            145f, 829f, 28f, 24f, 0f, 1f,
            179f, 832f, 21f, 18f, 0f, 1f,
            145f, 825f, 27f, 23f, 0f, 1f,
            36f, 356f, 28f, 23f, 0f, 0.92f,
            70f, 330f, 36f, 30f, 0f, 0.92f,
            108f, 346f, 30f, 25f, 0f, 0.92f,
            84f, 364f, 26f, 20f, 1f, 0.45f,
        ),
    ),
    Layer(
        262f,
        floatArrayOf(
            150f, 490f, 75f, 57f, 1f, 0.8f,
            105f, 426f, 122f, 102f, 0f, 1f,
            139f, 423f, 129f, 108f, 0f, 1f,
            159f, 412f, 152f, 127f, 0f, 1f,
            193f, 423f, 128f, 107f, 0f, 1f,
            119f, 402f, 97f, 85f, 0f, 1f,
            157f, 391f, 124f, 108f, 0f, 1f,
            30f, 776f, 75f, 27f, 1f, 0.72f,
            -16f, 752f, 44f, 37f, 0f, 0.9f,
            20f, 740f, 70f, 59f, 0f, 0.9f,
            51f, 731f, 90f, 75f, 0f, 0.9f,
            76f, 751f, 46f, 38f, 0f, 0.9f,
            12f, 734f, 50f, 44f, 0f, 0.9f,
            42f, 729f, 59f, 51f, 0f, 0.9f,
            62f, 356f, 38f, 30f, 0f, 1f,
            104f, 322f, 44f, 36f, 0f, 1f,
            150f, 298f, 50f, 42f, 0f, 1f,
            198f, 316f, 44f, 36f, 0f, 1f,
            238f, 348f, 36f, 30f, 0f, 1f,
            128f, 340f, 34f, 28f, 1f, 0.5f,
            182f, 352f, 30f, 24f, 1f, 0.45f,
        ),
    ),
)

/** One colour that fades outwards over a circle of radius 1; a puff is this circle stretched. */
private fun puffBrush(color: Color, edge: FloatArray, stops: FloatArray) = Brush.radialGradient(
    *Array(stops.size) { stops[it] to color.copy(alpha = edge[it]) },
    center = Offset.Zero,
    radius = 1f,
)

private val SharpLight = puffBrush(CloudLight, PuffEdge, PuffStops)
private val SharpShade = puffBrush(CloudShade, PuffEdge, PuffStops)
private val GlassLight = puffBrush(CloudLight, GlassEdge, GlassStops)
private val GlassShade = puffBrush(CloudShade, GlassEdge, GlassStops)

/**
 * Draws the clouds that reach into [box]. [turn] is how far through the loop the sky is, 0 to 1;
 * [top] is where the clouds' own zero is.
 */
private fun DrawScope.drawClouds(glass: Boolean, turn: Float, top: Float, box: Rect) {
    val spread = if (glass) GLASS_SPREAD else 0f
    for (layer in Layers) {
        val puffs = layer.puffs
        for (i in puffs.indices step 6) {
            val rx = puffs[i + 2] + spread
            val ry = puffs[i + 3] + spread
            val y = puffs[i + 1] + top
            if (y + ry < box.top || y - ry > box.bottom) continue
            val thin = if (glass) min(1f, GLASS_GAIN * puffs[i + 2] * puffs[i + 3] / (rx * ry)) else 1f
            val shaded = puffs[i + 4] == 1f
            val brush = if (glass) (if (shaded) GlassShade else GlassLight) else (if (shaded) SharpShade else SharpLight)
            // The first copy that reaches the box, then one every period until they are past it.
            var x = puffs[i] - turn * layer.period
            x += ceil((box.left - rx - x) / layer.period) * layer.period
            while (x - rx < box.right) {
                withTransform({
                    translate(x, y)
                    scale(rx, ry, pivot = Offset.Zero)
                }) { drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = puffs[i + 5] * thin) }
                x += layer.period
            }
        }
    }
}

/**
 * A blue sky with clouds that drift, and the three panes of glass in front of it, in the block's
 * own dp. Inside a pane the same sky is drawn again as a blur would leave it, then tinted, so the
 * clouds pass behind the cards as slow shifts of light.
 *
 * @param turn How far through the loop the sky is, 0 to 1.
 * @param position Where the carousel is, in cards.
 * @param cardTop The top edge of the cards.
 */
internal fun DrawScope.drawSky(width: Float, height: Float, turn: Float, position: Double, cardTop: Float, colors: List<Color>, glass: Color) {
    val screen = Rect(0f, 0f, width, height)
    val top = height - SKY_HEIGHT
    val sky = if (colors.size == SkyStops.size) {
        Brush.verticalGradient(*Array(SkyStops.size) { SkyStops[it] to colors[it] }, startY = 0f, endY = height)
    } else {
        Brush.verticalGradient(colors, startY = 0f, endY = height)
    }

    drawRect(sky, size = screen.size)
    drawClouds(glass = false, turn, top, screen)
    // A thin veil keeps white text readable over the clouds.
    drawRect(Veil, size = screen.size)

    for (step in 0 until STEPS) {
        val x = (width - CARD_WIDTH) / 2f + (offsetOf(step, position) * PITCH).toFloat()
        if (x + CARD_WIDTH <= 0f || x >= width) continue
        val card = Rect(x, cardTop, x + CARD_WIDTH, cardTop + CARD_HEIGHT)
        val pane = Path().apply { addRoundRect(RoundRect(card, CornerRadius(CARD_RADIUS))) }
        clipPath(pane) {
            drawRect(sky, size = screen.size)
            drawClouds(glass = true, turn, top, card)
            drawRect(Veil, size = screen.size)
            drawRect(glass, size = screen.size)
        }
        // A hairline of light just inside the pane's edge.
        drawRoundRect(
            Edge,
            topLeft = Offset(x + 0.5f, cardTop + 0.5f),
            size = Size(CARD_WIDTH - 1f, CARD_HEIGHT - 1f),
            cornerRadius = CornerRadius(CARD_RADIUS - 0.5f),
            style = Stroke(1f),
        )
    }
}
