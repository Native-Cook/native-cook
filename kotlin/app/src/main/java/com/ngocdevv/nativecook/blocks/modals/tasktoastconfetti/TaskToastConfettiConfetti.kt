package com.ngocdevv.nativecook.blocks.modals.tasktoastconfetti

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import com.ngocdevv.nativecook.shared.TAU
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Pieces thrown out of the toast, and pieces that rain from the top edge a moment later. */
private const val BURST = 40
private const val RAIN = 16
private val COLORS = listOf(0xFF2B6BFF, 0xFF8FB3FF, 0xFFF39A10, 0xFFFFD23F, 0xFFFF5B36, 0xFF2B6BFF, 0xFF5AD19A).map { Color(it) }

/** Width, height and corner radius of the three shapes: a card, a ribbon and a dot. */
private class ConfettiShape(val w: Float, val h: Float, val r: Float)

private val SHAPES = listOf(ConfettiShape(10f, 14f, 2f), ConfettiShape(6f, 20f, 3f), ConfettiShape(10f, 10f, 5f))

/** Burst speeds in points per second, the air drag that slows them, and the speed they settle to. */
private const val SPEED_MIN = 420.0
private const val SPEED_MAX = 980.0
private const val DRAG = 2.4f
private const val FALL_SPEED = 170f
private const val RAIN_DELAY = 0.25
private const val LIFE_MIN = 2.7
private const val LIFE_MAX = 3.3
private const val SWAY = 16f

private class Piece(
    val color: Color,
    val shape: ConfettiShape,
    /** Launch velocity, or for the rain the start position across the screen (a fraction). */
    val vx: Float,
    val vy: Float,
    val x: Float,
    val rain: Boolean,
    val delay: Float,
    val life: Float,
    val swayRate: Float,
    val swayPhase: Float,
    val spin: Float,
    val flipRate: Float,
)

/** One fixed table, so every loop throws the same confetti. */
private val TABLE = List(BURST + RAIN) { i ->
    val rain = i >= BURST
    // A fan from a little above the horizon on the left, down, to a little above it on the right.
    val angle = (-14 + hash(i, 1) * 208) * Math.PI / 180
    val speed = SPEED_MIN + hash(i, 2) * (SPEED_MAX - SPEED_MIN)
    Piece(
        color = COLORS[i % COLORS.size],
        shape = SHAPES[(hash(i, 3) * SHAPES.size).toInt()],
        vx = if (rain) 0f else (cos(angle) * speed).toFloat(),
        vy = if (rain) FALL_SPEED else (sin(angle) * speed * 0.85).toFloat(),
        x = (0.04 + hash(i, 4) * 0.92).toFloat(),
        rain = rain,
        delay = (if (rain) RAIN_DELAY + hash(i, 5) * 0.6 else hash(i, 5) * 0.08).toFloat(),
        life = (LIFE_MIN + hash(i, 6) * (LIFE_MAX - LIFE_MIN)).toFloat(),
        swayRate = (1.2 + hash(i, 7) * 1.4).toFloat(),
        swayPhase = (hash(i, 8) * Math.PI * 2).toFloat(),
        spin = ((if (hash(i, 9) < 0.5) -1 else 1) * (240 + hash(i, 10) * 480)).toFloat(),
        flipRate = (3 + hash(i, 11) * 5).toFloat(),
    )
}

/** A burst of confetti out of the toast and a short rain from the top; none of it takes touches. */
@Composable
internal fun Confetti(clock: LoopClock, at: Float, origin: Offset, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val t = clock.seconds.value
        val width = size.width / density
        scale(density, density, pivot = Offset.Zero) {
            for (piece in TABLE) {
                val age = t - at - piece.delay
                if (age < 0f || age > piece.life) continue
                val shape = piece.shape
                // Thrown out with drag, then settling to a gentle fall: the closed form of v' = -k (v - v_end).
                val slow = (1f - exp(-DRAG * age)) / DRAG
                val x = if (piece.rain) piece.x * width else origin.x + piece.vx * slow
                val y = if (piece.rain) -24f + FALL_SPEED * 1.4f * age else origin.y + FALL_SPEED * age + (piece.vy - FALL_SPEED) * slow
                // The flutter grows in as the throw dies down.
                val flutter = min(1f, age / 0.7f)
                val pop = easeOut(seg(age, 0f, 0.12f))
                withTransform({
                    translate(x + SWAY * flutter * sin(age * piece.swayRate * TAU + piece.swayPhase), y)
                    rotate(piece.spin * age, Offset.Zero)
                    scale(pop, cos(age * piece.flipRate + piece.swayPhase) * pop, Offset.Zero)
                }) {
                    drawRoundRect(
                        color = piece.color,
                        topLeft = Offset(-shape.w / 2, -shape.h / 2),
                        size = Size(shape.w, shape.h),
                        cornerRadius = CornerRadius(shape.r),
                        alpha = 1f - seg(age / piece.life, 0.75f, 1f),
                    )
                }
            }
        }
    }
}
