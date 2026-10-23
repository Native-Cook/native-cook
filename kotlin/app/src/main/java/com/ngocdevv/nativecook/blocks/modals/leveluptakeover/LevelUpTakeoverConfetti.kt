package com.ngocdevv.nativecook.blocks.modals.leveluptakeover

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private const val PIECES = 48
private val COLORS = listOf(0xFFFF4D5E, 0xFF2F6BFF, 0xFF2FC96B, 0xFFFFD21A, 0xFFFF8A2A, 0xFFB65CFF).map { Color(it) }

/** Width, height and corner of the three shapes: a card, a ribbon and a dot. */
private class PieceShape(val w: Float, val h: Float, val r: Float)

private val SHAPES = listOf(PieceShape(9f, 14f, 2f), PieceShape(6f, 20f, 3f), PieceShape(10f, 10f, 5f))

// Launch speed in points a second, the fan's half-angle around straight up, gravity and air drag.
private const val SPEED_MIN = 520.0
private const val SPEED_MAX = 1050.0
private const val FAN = 78.0
private const val GRAVITY = 900f
private const val DRAG = 1.8f
private const val LIFE = 2.4f
private const val FADE_AT = 2.0f
private const val SPIN = 900.0

private class Piece(val color: Color, val shape: PieceShape, val vx: Float, val vy: Float, val spin: Float, val flutter: Float)

/** One fixed table, so every burst, in every loop, is the same. */
private val TABLE = List(PIECES) { i ->
    val angle = (hash(i, 1) - 0.5) * 2 * FAN * Math.PI / 180
    val speed = SPEED_MIN + hash(i, 2) * (SPEED_MAX - SPEED_MIN)
    Piece(
        color = COLORS[i % COLORS.size],
        shape = SHAPES[(hash(i, 6) * SHAPES.size).toInt()],
        vx = (sin(angle) * speed).toFloat(),
        vy = (-cos(angle) * speed).toFloat(),
        spin = ((if (hash(i, 3) < 0.5) -1 else 1) * (0.4 + hash(i, 4) * 0.6) * SPIN).toFloat(),
        flutter = (0.5 + hash(i, 5)).toFloat(),
    )
}

/** Paper cards, ribbons and dots thrown up and out from [origin]; [count] of the table's pieces fire at [at]. */
@Composable
internal fun Confetti(clock: LoopClock, at: Float, origin: Offset, modifier: Modifier = Modifier, count: Int = PIECES) {
    Canvas(modifier) {
        val age = clock.seconds.value - at
        if (age < 0f || age > LIFE) return@Canvas
        // Speed dies away to a steady fall: the usual drag-and-gravity path in closed form.
        val settle = (1f - exp(-DRAG * age)) / DRAG
        val fall = GRAVITY / DRAG
        val pop = easeOut(seg(age, 0f, 0.08f))
        val alpha = 1f - seg(age, FADE_AT, LIFE)
        scale(density, density, pivot = Offset.Zero) {
            for (piece in TABLE.subList(0, count)) {
                val shape = piece.shape
                withTransform({
                    translate(origin.x + piece.vx * settle, origin.y + (piece.vy - fall) * settle + fall * age)
                    rotate(piece.spin * age, Offset.Zero)
                    // Turning edge-on as it tumbles, like paper.
                    scale(pop, (0.35f + 0.65f * abs(cos(age * piece.flutter * 6f))) * pop, Offset.Zero)
                }) {
                    drawRoundRect(piece.color, Offset(-shape.w / 2, -shape.h / 2), Size(shape.w, shape.h), CornerRadius(shape.r), alpha = alpha)
                }
            }
        }
    }
}
