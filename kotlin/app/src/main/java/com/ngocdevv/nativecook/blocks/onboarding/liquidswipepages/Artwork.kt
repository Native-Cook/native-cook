package com.ngocdevv.nativecook.blocks.onboarding.liquidswipepages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.PI
import kotlin.math.sin

enum class ArtKind { Tiles, Flag, Coins }

/** The box every picture is drawn in. */
internal const val ART_WIDTH = 300f
internal const val ART_HEIGHT = 340f

// Tiles: five app tiles juggled on an arc over a disc. Each bobs, a fifth of a turn after its neighbour.
private const val TILE = 60f
private const val TILE_RADIUS = 16f
private const val TILE_ICON = 28f
private const val BOB = 6f
private const val DISC_X = 150f
private const val DISC_Y = 232f
private const val DISC = 60f
private const val DISC_CORE = 36f

private class Tile(val icon: ImageVector, val x: Float, val y: Float, val tilt: Float)

private val Tiles = listOf(
    Tile(Icons.Rounded.Image, 26f, 175f, -12f),
    Tile(Icons.Rounded.PlayArrow, 74f, 112f, -6f),
    Tile(Icons.Rounded.WbCloudy, 150f, 88f, 0f),
    Tile(Icons.Rounded.ChatBubble, 226f, 112f, 6f),
    Tile(Icons.Rounded.Tune, 274f, 175f, 12f),
)

// Flag: a pole planted on a hill, with a second hill behind. The flag's free end waves.
private val Hill = Path().apply { moveTo(30f, 322f); lineTo(150f, 202f); lineTo(270f, 322f); close() }
private val BackHill = Path().apply { moveTo(130f, 322f); lineTo(214f, 248f); lineTo(298f, 322f); close() }
private const val POLE_X = 148f
private const val POLE_Y = 18f
private const val POLE_WIDTH = 4f
private const val POLE_HEIGHT = 196f
private const val FLAG_X = 152f
private const val FLAG_Y = 24f
private const val FLAG_WIDTH = 70f
private const val FLAG_HEIGHT = 44f
private const val FLAG_WAVE = 5f
private const val FLAG_TURNS = 0.9f
private const val FLAG_STEPS = 12

// Coins: three discs that rise like a chart, and an arrow through them.
private val Coins = listOf(Triple(70f, 243f, 25f), Triple(150f, 180f, 35f), Triple(238f, 93f, 45f))
private const val COIN_RIM = 7f
private val Trend = Path().apply {
    moveTo(24f, 291f); lineTo(70f, 243f); lineTo(150f, 180f); lineTo(238f, 93f); lineTo(270f, 61f)
    moveTo(252f, 61f); lineTo(270f, 61f); lineTo(270f, 79f)
}

/**
 * A page's picture: flat shapes in white and in shades of the page's colour.
 *
 * @param color The page's colour: the tiles' icons are cut out of it.
 * @param clock Seconds since the screen appeared; read while drawing only.
 */
@Composable
internal fun Artwork(kind: ArtKind, color: Color, clock: DoubleState, still: Boolean, modifier: Modifier = Modifier) {
    val box = modifier.size(ART_WIDTH.dp, ART_HEIGHT.dp).clearAndSetSemantics {}
    when (kind) {
        ArtKind.Tiles -> Box(box) {
            for (r in listOf(DISC, DISC_CORE)) {
                Box(Modifier.offset((DISC_X - r).dp, (DISC_Y - r).dp).size((r * 2).dp).background(Color.White.copy(alpha = 0.16f), CircleShape))
            }
            Tiles.forEachIndexed { index, tile ->
                Box(
                    Modifier
                        .offset((tile.x - TILE / 2).dp, (tile.y - TILE / 2).dp)
                        .size(TILE.dp)
                        .graphicsLayer {
                            val turn = clock.doubleValue / AMBIENT + index.toDouble() / Tiles.size
                            translationY = if (still) 0f else -BOB.dp.toPx() * sin(2 * PI * turn).toFloat()
                            rotationZ = tile.tilt
                        }
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(TILE_RADIUS.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tile.icon, contentDescription = null, tint = color, modifier = Modifier.size(TILE_ICON.dp))
                }
            }
        }

        ArtKind.Flag -> {
            val flag = remember { Path() }
            Canvas(box) {
                surface(0.dp) { _, _, _, _ ->
                    drawPath(BackHill, Color.Black.copy(alpha = 0.07f))
                    drawPath(Hill, Color.Black.copy(alpha = 0.13f))
                    drawRoundRect(Color.White, Offset(POLE_X, POLE_Y), Size(POLE_WIDTH, POLE_HEIGHT), CornerRadius(POLE_WIDTH / 2f))

                    val phase = if (still) 0f else (2 * PI * clock.doubleValue / AMBIENT).toFloat()
                    // Held at the pole, free at the far end.
                    fun lift(f: Float) = FLAG_WAVE * f * sin(phase - f * FLAG_TURNS * 2f * PI.toFloat())
                    flag.rewind()
                    for (i in 0..FLAG_STEPS) {
                        val f = i.toFloat() / FLAG_STEPS
                        if (i == 0) flag.moveTo(FLAG_X, FLAG_Y + lift(f)) else flag.lineTo(FLAG_X + FLAG_WIDTH * f, FLAG_Y + lift(f))
                    }
                    for (i in FLAG_STEPS downTo 0) {
                        val f = i.toFloat() / FLAG_STEPS
                        flag.lineTo(FLAG_X + FLAG_WIDTH * f, FLAG_Y + lift(f) + FLAG_HEIGHT)
                    }
                    flag.close()
                    drawPath(flag, Color.White)
                }
            }
        }

        ArtKind.Coins -> Canvas(box) {
            surface(0.dp) { _, _, _, _ ->
                for ((x, y, r) in Coins) drawCircle(Color.White.copy(alpha = 0.26f), r, Offset(x, y))
                for ((x, y, r) in Coins) drawCircle(Color.White.copy(alpha = 0.6f), r - COIN_RIM, Offset(x, y), style = Stroke(2f))
                drawPath(Trend, Color.White, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
