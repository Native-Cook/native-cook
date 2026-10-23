package com.ngocdevv.nativecook.blocks.modals.paymentcheckburst

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import com.ngocdevv.nativecook.shared.TAU
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

// Green confetti falls from the top of the screen.
private const val CONFETTI = 42
private val CONFETTI_COLORS = listOf(0xFF2FBF71, 0xFF7BE0A8, 0xFF16A05A, 0xFFB9F2D2).map { Color(it) }
private const val CONFETTI_SPREAD = 1.2
private const val FALL_MIN = 2.0
private const val FALL_MAX = 2.8
private const val SWAY = 18f
private const val SPIN = 540.0

// Silver coins spill out of the reward bar and tumble down.
private const val COINS = 12
private const val COIN_MIN = 16.0
private const val COIN_MAX = 30.0
private const val COIN_SPEED_MIN = 220.0
private const val COIN_SPEED_MAX = 520.0
private const val COIN_FAN = 62.0
private const val COIN_GRAVITY = 1100f
private const val COIN_LIFE = 2.2f
private const val COIN_FADE_AT = 1.6f
private const val COIN_SPREAD = 0.45

private class Piece(
    val color: Color,
    /** Every third piece is a thin strip. */
    val strip: Boolean,
    val x: Float,
    val drift: Float,
    val delay: Float,
    val fall: Float,
    val sway: Float,
    val phase: Float,
    val spin: Float,
)

private class Coin(val size: Float, val vx: Float, val vy: Float, val delay: Float, val spin: Float, val tilt: Float)

private val ConfettiTable = List(CONFETTI) { i ->
    Piece(
        color = CONFETTI_COLORS[i % CONFETTI_COLORS.size],
        strip = i % 3 == 0,
        x = (0.04 + hash(i, 1) * 0.92).toFloat(),
        drift = ((hash(i, 2) - 0.5) * 70).toFloat(),
        delay = (hash(i, 3) * CONFETTI_SPREAD).toFloat(),
        fall = (FALL_MIN + hash(i, 4) * (FALL_MAX - FALL_MIN)).toFloat(),
        sway = (0.6 + hash(i, 5) * 1.2).toFloat(),
        phase = (hash(i, 6) * TAU).toFloat(),
        spin = ((if (hash(i, 7) < 0.5) -1 else 1) * (0.4 + hash(i, 8) * 0.6) * SPIN).toFloat(),
    )
}

private val CoinTable = List(COINS) { i ->
    val angle = (hash(i, 11) - 0.5) * 2 * COIN_FAN * PI / 180
    val speed = COIN_SPEED_MIN + hash(i, 12) * (COIN_SPEED_MAX - COIN_SPEED_MIN)
    Coin(
        size = (COIN_MIN + hash(i, 13) * (COIN_MAX - COIN_MIN)).toFloat(),
        vx = (sin(angle) * speed).toFloat(),
        vy = (-cos(angle) * speed).toFloat(),
        delay = (hash(i, 14) * COIN_SPREAD).toFloat(),
        spin = (3 + hash(i, 15) * 5).toFloat(),
        tilt = ((hash(i, 16) - 0.5) * 70).toFloat(),
    )
}

private val CoinHigh = Color(0xFFF4F4F6)
private val CoinLow = Color(0xFFA9AAAF)
private val CoinRing = Color(120 / 255f, 120 / 255f, 126 / 255f, 0.6f)

/** A five-pointed star centred on the origin, the size of the icon font's star at 0.45 of the coin. */
private fun star(radius: Float) = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else radius * 0.48f
        val a = i * PI.toFloat() / 5f - PI.toFloat() / 2f
        val x = cos(a) * r
        val y = sin(a) * r + radius * 0.06f
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Green squares and strips falling from the top of the screen. */
@Composable
internal fun Confetti(clock: LoopClock, at: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val t = clock.seconds.value
        val width = size.width / density
        val height = size.height / density
        scale(density, density, pivot = Offset.Zero) {
            for (piece in ConfettiTable) {
                val age = t - at - piece.delay
                if (age < 0f || age > piece.fall) continue
                val p = age / piece.fall
                val w = if (piece.strip) 4f else 9f
                val h = if (piece.strip) 16f else 9f
                val x = piece.x * width + piece.drift * p + SWAY * sin(p * piece.sway * TAU + piece.phase)
                val y = -10f + height * 0.9f * p.pow(1.25f)
                val pop = easeOut(seg(p, 0f, 0.06f))
                withTransform({
                    translate(x + w / 2, y + h / 2)
                    rotate(piece.spin * p, Offset.Zero)
                    scale(pop, pop, Offset.Zero)
                }) {
                    drawRoundRect(piece.color, Offset(-w / 2, -h / 2), Size(w, h), CornerRadius(2f), alpha = 1f - seg(p, 0.6f, 1f))
                }
            }
        }
    }
}

/** Silver coins thrown out of one point, in dp. */
@Composable
internal fun Coins(clock: LoopClock, at: Float, x: Float, y: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val t = clock.seconds.value
        scale(density, density, pivot = Offset.Zero) {
            for (coin in CoinTable) {
                val age = t - at - coin.delay
                if (age < 0f || age > COIN_LIFE) continue
                val s = coin.size
                val pop = easeOut(seg(age, 0f, 0.1f))
                val alpha = 1f - seg(age, COIN_FADE_AT, COIN_LIFE)
                withTransform({
                    translate(x + coin.vx * age + s / 2, y + coin.vy * age + 0.5f * COIN_GRAVITY * age * age + s / 2)
                    rotate(coin.tilt, Offset.Zero)
                    // Turning about its vertical axis: the coin's width follows the cosine.
                    scale(pop * max(0.12f, abs(cos(age * coin.spin))), pop, Offset.Zero)
                }) {
                    drawCircle(
                        Brush.linearGradient(
                            listOf(CoinHigh, CoinLow),
                            start = Offset(-s / 2 + 0.2f * s, -s / 2),
                            end = Offset(-s / 2 + 0.8f * s, s / 2),
                        ),
                        s / 2, alpha = alpha,
                    )
                    drawCircle(CoinRing, s / 2 - 2.5f, alpha = alpha, style = Stroke(1f))
                    drawPath(star(s * 0.45f * 0.46f), Color.White.copy(alpha = 0.95f), alpha = alpha)
                }
            }
        }
    }
}
