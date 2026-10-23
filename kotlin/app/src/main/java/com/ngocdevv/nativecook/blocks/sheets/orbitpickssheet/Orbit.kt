package com.ngocdevv.nativecook.blocks.sheets.orbitpickssheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class DiscSpec(
    val icon: ImageVector,
    val color: Color,
    val size: Float,
    /** Distance from the badge, in dp. */
    val radius: Float,
    /** Turns per [PERIOD]. */
    val speed: Float,
    /** Where on its orbit it starts, in radians. */
    val phase: Float,
)

// Twelve discs, small and large, on orbits between 90 and 170 dp.
internal val DISCS = listOf(
    DiscSpec(Icons.Filled.SportsTennis, Color(0xFF1FC760), 40f, 150f, 1f, 0.4f),
    DiscSpec(Icons.Filled.Memory, Color(0xFFF2994A), 32f, 170f, 0.8f, 1.5f),
    DiscSpec(Icons.Filled.DirectionsCar, Color(0xFFE5384A), 44f, 110f, 1.2f, 2.6f),
    DiscSpec(Icons.Filled.Pets, Color(0xFF1FC760), 26f, 100f, 1.1f, 3.7f),
    DiscSpec(Icons.Filled.SportsEsports, Color(0xFF3B82F6), 22f, 135f, 0.9f, 4.8f),
    DiscSpec(Icons.Filled.MusicNote, Color(0xFFE5384A), 24f, 160f, 1.3f, 5.6f),
    DiscSpec(Icons.Filled.AutoAwesome, Color(0xFFF5C518), 38f, 120f, 1f, 1f),
    DiscSpec(Icons.Filled.PedalBike, Color(0xFF26C6DA), 24f, 170f, 0.85f, 2.2f),
    DiscSpec(Icons.Filled.LocalPizza, Color(0xFFF2994A), 30f, 95f, 1.25f, 3.1f),
    DiscSpec(Icons.Filled.Favorite, Color(0xFFF2547D), 26f, 145f, 1.15f, 4.2f),
    DiscSpec(Icons.Filled.Public, Color(0xFF26C6DA), 34f, 165f, 0.95f, 5.2f),
    DiscSpec(Icons.Filled.SportsSoccer, Color(0xFFF5C518), 28f, 125f, 1.05f, 0.1f),
)

// The orbits are ellipses, wider than tall.
private const val SQUASH = 0.85f
private const val TURN = (PI * 2).toFloat()
private const val PULSE = 0.08f
private const val SPIRAL_IN = 1.4f
private const val FAR = 2.4f

/**
 * One icon disc: it flies in from outside along a spiral, circles the badge, and is pulled into
 * it. [time] is read while laying out and drawing; [cx] and [cy] are the badge's centre in dp.
 */
@Composable
internal fun Disc(spec: DiscSpec, index: Int, time: () -> Float, cx: Float, cy: Float) {
    fun fly(t: Float): Float {
        val inAt = DISC_IN + index * DISC_STAGGER
        return easeOut(ramp(t, inAt, inAt + DISC_FLY))
    }
    fun suck(t: Float): Float {
        val at = SUCK_AT + index * SUCK_STAGGER
        return easeIn(ramp(t, at, at + SUCK_FOR))
    }
    fun angle(t: Float): Float {
        val inAt = DISC_IN + index * DISC_STAGGER
        return spec.phase + spec.speed * TURN * max(0f, t - inAt) / PERIOD + (1f - fly(t)) * SPIRAL_IN + suck(t) * PI.toFloat()
    }
    fun radius(t: Float) = spec.radius * (1f + FAR * (1f - fly(t))) * (1f - suck(t))

    Box(
        Modifier
            .offset {
                val t = time()
                val x = cx + cos(angle(t)) * radius(t) - spec.size / 2f
                val y = cy + sin(angle(t)) * radius(t) * SQUASH - spec.size / 2f
                IntOffset((x * density).roundToInt(), (y * density).roundToInt())
            }
            .size(spec.size.dp)
            .graphicsLayer {
                val t = time()
                val inAt = DISC_IN + index * DISC_STAGGER
                val orbit = max(0f, t - inAt)
                val scale = (0.3f + 0.7f * fly(t)) * (1f + PULSE * sin(orbit * 2.4f + spec.phase)) * (1f - suck(t))
                scaleX = scale
                scaleY = scale
                alpha = ramp(t, inAt, inAt + 0.12f) * (if (suck(t) < 1f) 1f else 0f)
            }
            .dropShadow(CircleShape, Shadow(radius = 6.dp, color = spec.color, offset = DpOffset(0.dp, 3.dp), alpha = 0.35f))
            .background(spec.color, CircleShape)
            .border(2.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(spec.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size((spec.size * 0.55f).dp))
    }
}

private const val RING = 104f
private const val RING_STROKE = 5f
// Degrees a second.
private const val RING_SPIN = 360f / PERIOD

/** The arc around the badge: it draws itself, keeps turning, and fades when the discs are pulled in. */
@Composable
internal fun Ring(time: () -> Float, cx: Float, cy: Float) {
    Canvas(
        Modifier
            .offset { IntOffset(((cx - RING / 2f) * density).roundToInt(), ((cy - RING / 2f) * density).roundToInt()) }
            .size(RING.dp)
            .graphicsLayer {
                val t = time()
                alpha = ramp(t, RING_IN, RING_IN + 0.2f) * (1f - ramp(t, RING_OUT, RING_OUT + 0.5f))
            },
    ) {
        val t = time()
        val d = density
        val inset = RING_STROKE / 2f * d
        // Three quarters of the circle are drawn, growing as the ring draws itself.
        val sweep = 270f * easeOut(ramp(t, RING_IN, RING_IN + RING_DRAW))
        rotate(max(0f, t - RING_IN) * RING_SPIN) {
            drawArc(
                INDIGO,
                startAngle = 0f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                style = Stroke(RING_STROKE * d, cap = StrokeCap.Round),
            )
        }
    }
}
