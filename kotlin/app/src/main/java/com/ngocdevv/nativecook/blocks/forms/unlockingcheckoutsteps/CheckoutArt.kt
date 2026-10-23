package com.ngocdevv.nativecook.blocks.forms.unlockingcheckoutsteps

import android.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.PI

private const val MAP_WIDTH = 310f
const val MAP_RATIO = 166f / MAP_WIDTH
private const val ART = 220f
private val TEAL = Color(0xFF2A9DB3)
private val OUTLINE = Color(0xFF17251E)
private val MAP_FILL = Color(0xFFE9E9E9)
private val BLOCKS_INK = Color(0xFFBDBDBD)
private val STREETS_INK = Color(0xFFF8F8F8)
private val CARD_FILL = Color(0xFFF6B48A)
private val BOX_FILL = Color(0xFFEEC392)
private val TOP_FILL = Color(0xFFDBA972)
private val SIDE_FILL = Color(0xFFC9955C)
private val TAPE_FILL = Color(0xFFFBEED9)
private val BADGE_FILL = Color(0xFF34D58A)
private val ROUTE_START = Offset(62f, 130f)
private val ROUTE_END = Offset(238f, 48f)
private val BADGE = Offset(166f, 66f)
private const val BADGE_RADIUS = 30f
private val AVENUES = PathParser().parsePathString("M-10 122L124 -10M36 176L204 -10M150 176L330 38M-10 62L92 176M-10 18L330 112").toPath()
private val STREETS = PathParser().parsePathString("M96 -10L320 152M204 -10L252 176M-10 96L60 176M250 -10L330 20M-10 150L40 176M130 100L190 176M60 40L110 90").toPath()
private val BLOCKS = PathParser().parsePathString("M132 22L166 46L150 68L116 44ZM222 60L262 72L254 104L214 92ZM40 96L72 132L50 152L18 116ZM176 104L214 122L200 152L162 134ZM258 122L300 134L294 160L252 148ZM20 34L52 12").toPath()
private val ROUTE = PathParser().parsePathString("M62 130C74 84 112 52 162 48L238 48").toPath()
private val CARD_CHIP = PathParser().parsePathString("M58 46L74 46M58 52L74 52").toPath()
private val CARD_WAVES = PathParser().parsePathString("M112 40C116 44 116 52 112 56M119 36C125 43 125 53 119 60").toPath()
private val BOX_TOP = PathParser().parsePathString("M46 108L64 88L168 88L150 108Z").toPath()
private val BOX_SIDE = PathParser().parsePathString("M150 108L168 88L168 170L150 192Z").toPath()
private val BOX_TAPE = PathParser().parsePathString("M90 108L106 88L122 88L106 108L106 134L90 134Z").toPath()
private val BOX_LABEL = PathParser().parsePathString("M60 164L96 164M60 174L84 174").toPath()
private val SPARKS = PathParser().parsePathString("M196 30L204 18M204 50L216 46M182 20L182 8").toPath()
private val BADGE_CHECK = PathParser().parsePathString("M153 67L162 77L180 56").toPath()

/** The street strip stays fixed while only the route's measured prefix is drawn. */
@Composable
fun RouteMap(width: Float, drawn: State<Double>, modifier: Modifier = Modifier) {
    val measure = remember { PathMeasure(ROUTE.asAndroidPath(), false) }
    val partial = remember { Path() }
    Canvas(modifier.size(width.dp, (width * MAP_RATIO).dp).clipToBounds().clearAndSetSemantics {}) {
        surface(0.dp) { _, _, _, _ ->
            scale(width / MAP_WIDTH, pivot = Offset.Zero) {
                drawRect(MAP_FILL, size = Size(MAP_WIDTH, MAP_WIDTH * MAP_RATIO))
                drawPath(BLOCKS, BLOCKS_INK, style = Stroke(1f))
                drawPath(AVENUES, STREETS_INK, style = Stroke(7f))
                drawPath(STREETS, STREETS_INK, style = Stroke(3.5f))
                partial.reset()
                val progress = drawn.value.coerceIn(0.0, 1.0).toFloat()
                if (progress > 0f) {
                    measure.getSegment(0f, measure.length * progress, partial.asAndroidPath(), true)
                    drawPath(partial, TEAL, style = Stroke(2.6f, cap = StrokeCap.Round))
                }
                drawCircle(TEAL, 5f, ROUTE_START)
                val arrived = (drawn.value * 5.0 - 4.0).coerceIn(0.0, 1.0).toFloat()
                drawCircle(TEAL, 5f, ROUTE_END, alpha = arrived)
                drawCircle(Color.White, 2f, ROUTE_END, alpha = arrived)
            }
        }
    }
}

/** The parcel and card are fixed paths; only the badge's check is revealed over time. */
@Composable
fun SuccessArt(size: Float, checked: State<Double>, modifier: Modifier = Modifier) {
    val measure = remember { PathMeasure(BADGE_CHECK.asAndroidPath(), false) }
    val partial = remember { Path() }
    Canvas(modifier.size(size.dp).clipToBounds().clearAndSetSemantics {}) {
        surface(0.dp) { _, _, _, _ ->
            scale(size / ART, pivot = Offset.Zero) {
                rotate(-.12f * 180f / PI.toFloat(), Offset(90f, 58f)) {
                    drawRoundRect(CARD_FILL, Offset(36f, 22f), Size(108f, 72f), CornerRadius(12f))
                    drawRoundRect(OUTLINE, Offset(36f, 22f), Size(108f, 72f), CornerRadius(12f), style = Stroke(3f))
                    drawRoundRect(OUTLINE, Offset(52f, 40f), Size(28f, 18f), CornerRadius(5f), style = Stroke(2.4f))
                    drawPath(CARD_CHIP, OUTLINE, style = Stroke(2f, cap = StrokeCap.Round))
                    drawPath(CARD_WAVES, OUTLINE, style = Stroke(2.4f, cap = StrokeCap.Round))
                }
                drawRect(BOX_FILL, Offset(46f, 108f), Size(104f, 84f))
                drawPath(BOX_TOP, TOP_FILL)
                drawPath(BOX_SIDE, SIDE_FILL)
                drawPath(BOX_TAPE, TAPE_FILL)
                val edge = Stroke(3f, join = StrokeJoin.Round)
                drawRect(OUTLINE, Offset(46f, 108f), Size(104f, 84f), style = edge)
                drawPath(BOX_TOP, OUTLINE, style = edge)
                drawPath(BOX_SIDE, OUTLINE, style = edge)
                drawPath(BOX_LABEL, OUTLINE, style = Stroke(2.6f, cap = StrokeCap.Round))
                drawCircle(BADGE_FILL, BADGE_RADIUS, BADGE)
                drawCircle(OUTLINE, BADGE_RADIUS, BADGE, style = Stroke(3.4f))
                partial.reset()
                val progress = checked.value.coerceIn(0.0, 1.0).toFloat()
                if (progress > 0f) {
                    measure.getSegment(0f, measure.length * progress, partial.asAndroidPath(), true)
                    drawPath(partial, Color.White, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                drawPath(SPARKS, OUTLINE, style = Stroke(3f, cap = StrokeCap.Round))
            }
        }
    }
}
