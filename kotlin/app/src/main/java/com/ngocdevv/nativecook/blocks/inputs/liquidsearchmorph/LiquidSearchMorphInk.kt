package com.ngocdevv.nativecook.blocks.inputs.liquidsearchmorph

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// The pill is one drop of ink. Opening it, each edge travels on its own share of the morph, so the
// edge in front leaves first and the one behind catches up: the top rises before the bottom, the
// right edge runs out before the left, and the pill stretches like a drop pulled across glass.
// Each pair is (start, length) as shares of the morph (0…1). Played backwards for closing, the same
// shares make the bottom and the left lead the way down.
private val TOP = 0f to 0.8f
private val BOTTOM = 0.14f to 0.86f
private val LEFT = 0.1f to 0.8f
private val RIGHT = 0f to 0.75f
private val REST = 0.07f to 0.83f
// A drop keeps its volume: for every dp it is drawn out taller than it would be, it gets
// NARROWING dp narrower on each side.
private const val NARROWING = 0.4f
// Two smaller drops ride inside it, the magnifier's drop and a little one behind it, and lag
// further: they part from the pill on a thread of ink and merge into it again.
private val DROP = 0.3f to 0.7f
private val TAIL = 0.44f to 0.56f
private const val DROP_RADIUS = 17f
private const val TAIL_RADIUS = 8f
/** Where the magnifier sits, from the pill's left edge. */
internal const val ICON_INSET = 25f
// The goo: everything is blurred by GOO_BLUR, then its alpha is pushed through a steep ramp
// (alpha × GOO_GAIN − GOO_CUT), so shapes that come close melt into one with a crisp edge.
private const val GOO_BLUR = 9f
private const val GOO_GAIN = 22f
private const val GOO_CUT = 10f
// A press pinches the pill in by PRESS_INSET dp all round.
private const val PRESS_INSET = 3f

/** The pill's edges and its two drops at one point of the morph, in dp. */
internal class InkShape(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val dropX: Float,
    val dropY: Float,
    val tailX: Float,
    val tailY: Float,
)

private fun easeInOut(t: Float) = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

/** How far an edge with this share has moved; under reduced motion it jumps at the middle. */
private fun edge(t: Float, share: Pair<Float, Float>, still: Boolean): Float =
    if (still) (if (t < 0.5f) 0f else 1f) else easeInOut(((t - share.first) / share.second).coerceIn(0f, 1f))

private fun mix(a: Float, b: Float, k: Float) = a + (b - a) * k

/** The shape at `t` of the morph from [closed] (0) to [open] (1). */
internal fun inkAt(t: Float, closed: Rect, open: Rect, still: Boolean): InkShape {
    val top = mix(closed.top, open.top, edge(t, TOP, still))
    val bottom = mix(closed.bottom, open.bottom, edge(t, BOTTOM, still))
    val rest = mix(closed.height, open.height, edge(t, REST, still))
    val stretch = max(0f, bottom - top - rest) * NARROWING
    val drop = edge(t, DROP, still)
    val tail = edge(t, TAIL, still)
    return InkShape(
        left = mix(closed.left, open.left, edge(t, LEFT, still)) + stretch,
        top = top,
        right = mix(closed.right, open.right, edge(t, RIGHT, still)) - stretch,
        bottom = bottom,
        dropX = mix(closed.left + ICON_INSET, open.left + ICON_INSET, drop),
        dropY = mix(closed.center.y, open.center.y, drop),
        tailX = mix(closed.left + ICON_INSET + 14, open.left + ICON_INSET + 14, tail),
        tailY = mix(closed.center.y, open.center.y, tail),
    )
}

/**
 * The ink pill, drawn through the goo filter (API 31+). Below that the pieces are drawn as they
 * are, without melting into each other.
 */
@Composable
internal fun InkPill(
    t: () -> Float,
    press: () -> Float,
    closed: Rect,
    open: Rect,
    still: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val goo: RenderEffect? = remember(density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = sigmaToRadius(GOO_BLUR * density.density)
            val blur = android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
            // Android's colour matrix offsets are in 0…255.
            val ramp = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, GOO_GAIN, -GOO_CUT * 255f,
                ),
            )
            android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(ramp), blur).asComposeRenderEffect()
        } else {
            null
        }
    }
    Canvas(
        modifier.graphicsLayer {
            renderEffect = goo
            // Under reduced motion the pill fades out at the jump and back in after it.
            alpha = if (still) abs(1 - 2 * t()) else 1f
        },
    ) {
        val p = inkAt(t(), closed, open, still)
        val inset = PRESS_INSET * press()
        val w = p.right - p.left - inset * 2
        val h = p.bottom - p.top - inset * 2
        if (w <= 0f || h <= 0f) return@Canvas
        // Always fully round, so a drawn-out pill reads as a drop, never as a box.
        val r = min(w, h) / 2
        scale(density.density, density.density, pivot = Offset.Zero) {
            drawRoundRect(color, Offset(p.left + inset, p.top + inset), Size(w, h), CornerRadius(r, r))
            drawCircle(color, DROP_RADIUS, Offset(p.dropX, p.dropY))
            drawCircle(color, TAIL_RADIUS, Offset(p.tailX, p.tailY))
        }
    }
}
