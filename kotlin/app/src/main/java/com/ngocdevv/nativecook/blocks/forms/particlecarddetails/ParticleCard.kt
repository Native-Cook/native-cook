package com.ngocdevv.nativecook.blocks.forms.particlecarddetails

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val PARTICLE_LOOP = 7.8f

private object CardSpec {
    const val CARD_IN = .09f
    const val FLASH_FOR = .3f
    const val LINE_AT = .08f
    const val LINE_FOR = .62f
    const val TRAIL = .5f
    const val LINE_PAD = 14f
    const val RISE = 34f
    const val STILL_HOLD = .35f
    const val STILL_OUT = .35f
    const val SIDE = 36f
    const val TOP = 52f
    const val BOTTOM = 36f
    const val RADIUS = 13f
    val MARK_RADII = floatArrayOf(4f, 8.5f, 13f, 17.5f)
    const val MARK_SWEEP = 76f
    const val MARK_RIGHT = 40f
    const val MARK_BOTTOM = 38f
    const val MARK_STROKE = 2f
    val MARK_INK = Color(0xFFDCDCE1)
    const val TAU = 6.2831853f
    val CLOUD_CELLS = floatArrayOf(4.2f, 6.6f)
    val CLOUD_FILLS = floatArrayOf(.62f, .3f)
    val CLOUD_SEEDS = floatArrayOf(0f, 91f)
    val CLOUD_OFFSETS = floatArrayOf(0f, 2.6f)
    val GRAIN_CELLS = floatArrayOf(1.7f, 2.3f, 3.1f)
    val GRAIN_FILLS = floatArrayOf(.92f, .4f, .12f)
    val GRAIN_SEEDS = floatArrayOf(7f, 19f, 43f)
    val GRAIN_DECAY = floatArrayOf(6f, 18f, 80f)
    val GRAIN_RISE = floatArrayOf(0f, 1f, 2.2f)
    const val BODY_CELL = 1.4f
    // Coverage rings and colour buckets replace per-pixel shader work with batched paths.
    const val COVERAGE_LAYERS = 4
    const val CLOUD_LEVELS = 64
    const val DUST_LEVELS = 16
    const val LIGHT_LEVELS = 32
    val CLOUD_COLORS = listOf(Color(.74f, .64f, 1f), Color(.6f, .96f, .76f), Color.White)
}

private fun cardSmooth(value: Float, low: Float, high: Float): Float {
    val t = ((value - low) / (high - low)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
private fun cardFract(value: Float) = value - floor(value)
private fun cardHash(x: Float, y: Float): Float {
    var a = cardFract(x * .1031f)
    var b = cardFract(y * .1031f)
    var c = a
    val d = a * (b + 33.33f) + b * (c + 33.33f) + c * (a + 33.33f)
    a += d; b += d; c += d
    return cardFract((a + b) * c)
}
private fun cardNoise(x: Float): Float {
    val i = floor(x); val f = x - i; val u = f * f * (3f - 2f * f)
    val a = cardHash(i, 3.7f); val b = cardHash(i + 1f, 3.7f)
    return a + (b - a) * u
}
private fun cardRagged(x: Float) = (cardNoise(x * .07f) - .5f) * 12f + (cardNoise(x * .31f + 40f) - .5f) * 6f
private fun cardInverseSmooth(value: Float) = .5f - sin(asin(1f - 2f * value.coerceIn(0f, 1f)) / 3f)
private fun cardBox(x: Float, y: Float, width: Float, height: Float): Float {
    val qx = abs(x) - width / 2f + CardSpec.RADIUS
    val qy = abs(y) - height / 2f + CardSpec.RADIUS
    return hypot(max(0f, qx), max(0f, qy)) + min(max(qx, qy), 0f) - CardSpec.RADIUS
}
private data class CardDot(val x: Float, val y: Float, val radius: Float, val beats: Float, val phase: Float, val bright: Float, val tint: Int)
private data class CardGrain(val x: Float, val y: Float, val radius: Float, val reach: Float, val ragged: Float)
private data class CardGrainLayer(val rows: List<List<CardGrain>>, val cell: Float, val rise: Float)
private data class CardColumn(val x: Float, val ragged: Float, val thresholds: FloatArray)

private class CardCache(val width: Float, val height: Float) {
    val dots = ArrayList<CardDot>()
    val grains = ArrayList<CardGrainLayer>()
    val columns = ArrayList<CardColumn>()
    val rounded = Path()
    val mark = Path()
    val coverage = FloatArray(CardSpec.COVERAGE_LAYERS) { cardInverseSmooth(1f - (it + .5f) / CardSpec.COVERAGE_LAYERS) }
    val canvasWidth = width + CardSpec.SIDE * 2f
    val canvasHeight = height + CardSpec.TOP + CardSpec.BOTTOM
    init {
        val s = CardSpec
        rounded.addRoundRect(RoundRect(Rect(s.SIDE, s.TOP, s.SIDE + width, s.TOP + height), CornerRadius(s.RADIUS)))
        val markX = s.SIDE + width - s.MARK_RIGHT - s.MARK_RADII.last()
        val markY = s.TOP + height - s.MARK_BOTTOM
        for (radius in s.MARK_RADII) mark.arcTo(Rect(markX - radius, markY - radius, markX + radius, markY + radius), -s.MARK_SWEEP / 2f, s.MARK_SWEEP, true)
        for (layer in s.CLOUD_CELLS.indices) {
            val cell = s.CLOUD_CELLS[layer]; val seed = s.CLOUD_SEEDS[layer]; val offset = s.CLOUD_OFFSETS[layer]
            for (gy in 0..ceil((canvasHeight + offset) / cell).toInt()) {
                for (gx in 0..ceil((canvasWidth + offset) / cell).toInt()) {
                    val sx = gx + seed; val sy = gy + seed
                    val x = (gx + .5f + (cardHash(sx, sy) - .5f) * .7f) * cell
                    val y = (gy + .5f + (cardHash(sx + 11.3f, sy + 11.3f) - .5f) * .7f) * cell
                    val edge = cardBox(x - s.SIDE - width / 2f, y - s.TOP - height / 2f, width, height)
                    if (cardHash(sx + 23.1f, sy + 23.1f) > s.CLOUD_FILLS[layer] * (1f - cardSmooth(edge, -10f, 14f))) continue
                    val glint = cardHash(sx + 67.1f, sy + 67.1f); val tint = cardHash(sx + 79.9f, sy + 79.9f)
                    dots += CardDot(x - offset, y - offset, .4f + .5f * cardHash(sx + 37.9f, sy + 37.9f), floor(5f + 11f * cardHash(sx + 41.7f, sy + 41.7f)), cardHash(sx + 53.3f, sy + 53.3f), .22f + .78f * glint * glint, if (tint < .1f) 0 else if (tint < .2f) 1 else 2)
                }
            }
        }
        for (layer in s.GRAIN_CELLS.indices) {
            val cell = s.GRAIN_CELLS[layer]; val seed = s.GRAIN_SEEDS[layer]; val drift = s.GRAIN_RISE[layer]
            val maxRise = s.RISE * (s.LINE_FOR + s.TRAIL) * drift
            val rows = ArrayList<List<CardGrain>>()
            for (gy in 0..ceil((canvasHeight + maxRise) / cell).toInt()) {
                val row = ArrayList<CardGrain>()
                for (gx in floor(-24f / cell).toInt()..ceil((canvasWidth + 24f) / cell).toInt()) {
                    val sx = gx + seed; val sy = gy + seed
                    val keep = cardHash(sx + 23.1f, sy + 23.1f)
                    if (keep > s.GRAIN_FILLS[layer]) continue
                    val x = (gx + .5f + (cardHash(sx, sy) - .5f) * .6f) * cell
                    val y = (gy + .5f + (cardHash(sx + 11.3f, sy + 11.3f) - .5f) * .6f) * cell
                    row += CardGrain(x, y, cell * (.28f + .22f * cardHash(sx + 37.9f, sy + 37.9f)), if (keep == 0f) Float.POSITIVE_INFINITY else -s.GRAIN_DECAY[layer] * ln(keep / s.GRAIN_FILLS[layer]), cardRagged(x - s.SIDE))
                }
                rows += row
            }
            grains += CardGrainLayer(rows, cell, drift)
        }
        val cell = s.BODY_CELL
        for (gx in floor(s.SIDE / cell).toInt()..ceil((s.SIDE + width) / cell).toInt()) {
            val x = gx * cell
            val thresholds = FloatArray(ceil(canvasHeight / cell).toInt() + 1) { gy -> -1.5f + 8.5f * cardInverseSmooth(cardHash(gx + 5f, gy + 5f)) }
            columns += CardColumn(x, cardRagged(x + cell / 2f - s.SIDE), thresholds)
        }
    }
}

/** Cached hashed points keep the shader's grids and clock without per-pixel CPU shading. */
@Composable
fun ParticleCard(width: Float, height: Float, time: Float, period: Float, elapsed: Float, still: Boolean, modifier: Modifier = Modifier) {
    if (width <= 0f || height <= 0f || !width.isFinite() || !height.isFinite()) return
    val cache = remember(width, height) { CardCache(width, height) }
    Canvas(modifier.layout { measurable, _ ->
        val canvas = measurable.measure(Constraints.fixed(cache.canvasWidth.dp.roundToPx(), cache.canvasHeight.dp.roundToPx()))
        layout(width.dp.roundToPx(), height.dp.roundToPx()) { canvas.place(-CardSpec.SIDE.dp.roundToPx(), -CardSpec.TOP.dp.roundToPx()) }
    }.clearAndSetSemantics {}) {
        surface(0.dp) { _, _, _, _ ->
            val s = CardSpec
            val line = if (still) -1000f else -s.LINE_PAD + ((height + s.LINE_PAD * 2f) * max(0f, elapsed - s.LINE_AT)) / s.LINE_FOR
            val solid = when {
                elapsed < 0f -> 0f
                still -> cardSmooth(elapsed, 0f, s.CARD_IN * 2f) * (1f - cardSmooth(elapsed, s.CARD_IN * 2f + s.STILL_HOLD, s.CARD_IN * 2f + s.STILL_HOLD + s.STILL_OUT))
                elapsed < s.LINE_AT + s.LINE_FOR + s.TRAIL -> cardSmooth(elapsed, 0f, s.CARD_IN)
                else -> 0f
            }
            drawCardCloud(cache, time, period)
            if (solid > 0f) {
                val flash = if (still) 0f else .45f * (1f - cardSmooth(elapsed, s.CARD_IN, s.FLASH_FOR))
                drawCardBody(cache, line, solid, flash)
                if (!still) drawCardDust(cache, line, solid, s.RISE * max(0f, elapsed - s.LINE_AT))
                val top = s.TOP + max(0f, line)
                if (top < s.TOP + height) clipRect(0f, top, cache.canvasWidth, s.TOP + height) {
                    drawPath(cache.mark, s.MARK_INK, alpha = solid, style = Stroke(s.MARK_STROKE, cap = StrokeCap.Round))
                }
            }
        }
    }
}

private fun DrawScope.drawCardCloud(cache: CardCache, time: Float, period: Float) {
    val s = CardSpec
    val paths = arrayOfNulls<Path>(s.CLOUD_LEVELS * 3 * s.COVERAGE_LAYERS)
    val phaseTime = if (period.isFinite() && period > 0f) time / period else 0f
    for (dot in cache.dots) {
        val twinkle = .5f + .5f * sin(s.TAU * (dot.beats * phaseTime + dot.phase))
        val bright = dot.bright * (.12f + .88f * twinkle * twinkle)
        val level = (bright * (s.CLOUD_LEVELS - 1)).roundToInt().coerceIn(0, s.CLOUD_LEVELS - 1)
        if (level == 0) continue
        for (ring in cache.coverage.indices) {
            val index = (dot.tint * s.CLOUD_LEVELS + level) * s.COVERAGE_LAYERS + ring
            val path = paths[index] ?: Path().also { paths[index] = it }
            val r = max(0f, dot.radius - .45f + .75f * cache.coverage[ring])
            path.addOval(Rect(dot.x - r, dot.y - r, dot.x + r, dot.y + r))
        }
    }
    for (index in paths.indices) {
        val path = paths[index] ?: continue
        val bucket = index / s.COVERAGE_LAYERS
        val alpha = (bucket % s.CLOUD_LEVELS).toFloat() / (s.CLOUD_LEVELS - 1) / s.COVERAGE_LAYERS
        drawPath(path, s.CLOUD_COLORS[bucket / s.CLOUD_LEVELS], alpha = alpha, blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.drawCardBody(cache: CardCache, line: Float, solid: Float, flash: Float) {
    val s = CardSpec; val cell = s.BODY_CELL
    val mask = Path(); val highlights = arrayOfNulls<Path>(s.LIGHT_LEVELS)
    val bottom = s.TOP + cache.height
    for (column in cache.columns) {
        val edge = s.TOP + line + column.ragged
        val fullTop = max(s.TOP, edge + 7f)
        if (fullTop < bottom) mask.addRect(Rect(column.x, fullTop, column.x + cell, bottom))
        val first = max(0, floor((edge - 1.5f) / cell).toInt())
        val last = min(column.thresholds.lastIndex, floor((edge + 7f) / cell).toInt())
        for (row in first..last) {
            val top = max(row * cell, edge + column.thresholds[row]); val end = (row + 1) * cell
            if (top < end) mask.addRect(Rect(column.x, top, column.x + cell, end))
        }
        for (strip in 0 until ceil(11.5f / cell).toInt()) {
            val y = edge - 1.5f + strip * cell
            val alpha = 1f - cardSmooth(y + cell / 2f - edge, 0f, 10f)
            val level = (alpha * (s.LIGHT_LEVELS - 1)).roundToInt().coerceIn(0, s.LIGHT_LEVELS - 1)
            if (level > 0) (highlights[level] ?: Path().also { highlights[level] = it }).addRect(Rect(column.x, y, column.x + cell, y + cell))
        }
    }
    drawContext.canvas.saveLayer(Rect(0f, 0f, cache.canvasWidth, cache.canvasHeight), Paint().apply { alpha = solid })
    clipPath(cache.rounded) {
        clipPath(mask) {
            val factor = 1f + flash
            drawPath(cache.rounded, Brush.verticalGradient(listOf(Color(.2f * factor, .2f * factor, .215f * factor), Color(.135f * factor, .135f * factor, .15f * factor)), s.TOP, bottom))
            for (level in 1 until s.LIGHT_LEVELS) {
                val path = highlights[level] ?: continue
                drawPath(path, Color(.66f * factor, .66f * factor, .7f * factor), alpha = level.toFloat() / (s.LIGHT_LEVELS - 1))
            }
        }
    }
    drawContext.canvas.restore()
}

private fun DrawScope.drawCardDust(cache: CardCache, line: Float, solid: Float, rise: Float) {
    val s = CardSpec
    val paths = HashMap<Int, Path>()
    for ((layerIndex, layer) in cache.grains.withIndex()) {
        val shift = rise * layer.rise
        val first = max(0, floor(shift / layer.cell).toInt() - 1)
        val last = min(layer.rows.lastIndex, ceil((min(cache.canvasHeight, s.TOP + line + 9f) + shift) / layer.cell).toInt())
        for (row in first..last) {
            for (grain in layer.rows[row]) {
                val y = grain.y - shift; val localY = y - s.TOP; val approximateAbove = line - localY
                if (localY > cache.height || localY <= -46f || approximateAbove + 9f < 0f || approximateAbove - 9f > grain.reach) continue
                var x = grain.x; var edge = line + grain.ragged
                if (layerIndex == 1) {
                    val sway = sin(localY * .09f + rise * .08f) * .06f
                    repeat(3) {
                        x = grain.x - sway * (edge - localY)
                        edge = line + cardRagged(x - s.SIDE)
                    }
                }
                val above = edge - localY; val localX = x - s.SIDE
                if (above < 0f || above > grain.reach) continue
                val beside = max(-localX, localX - cache.width)
                val within = solid * cardSmooth(localY, -46f, -6f) * (1f - cardSmooth(beside, -2f, 8f)) * cardSmooth(edge, 0f, 14f)
                val opacity = (within * (s.DUST_LEVELS - 1)).roundToInt().coerceIn(0, s.DUST_LEVELS - 1)
                if (opacity == 0) continue
                val tint = (cardSmooth(above, 0f, 60f) * (s.DUST_LEVELS - 1)).roundToInt().coerceIn(0, s.DUST_LEVELS - 1)
                for (ring in 0 until s.COVERAGE_LAYERS) {
                    val r = max(0f, grain.radius - .4f + .65f * cache.coverage[ring])
                    val key = (tint * s.DUST_LEVELS + opacity) * s.COVERAGE_LAYERS + ring
                    paths.getOrPut(key) { Path() }.addOval(Rect(x - r, y - r, x + r, y + r))
                }
            }
        }
    }
    val keys = paths.keys.sorted()
    for (ring in 0 until s.COVERAGE_LAYERS) {
        for (key in keys) {
            if (key % s.COVERAGE_LAYERS != ring) continue
            val bucket = key / s.COVERAGE_LAYERS
            val t = (bucket / s.DUST_LEVELS).toFloat() / (s.DUST_LEVELS - 1)
            val within = (bucket % s.DUST_LEVELS).toFloat() / (s.DUST_LEVELS - 1)
            val before = within * ring / s.COVERAGE_LAYERS
            val next = within * (ring + 1) / s.COVERAGE_LAYERS
            val opacity = (next - before) / max(.0001f, 1f - before)
            drawPath(paths.getValue(key), Color(.72f - .22f * t, .72f - .22f * t, .76f - .21f * t), alpha = opacity)
        }
    }
}

