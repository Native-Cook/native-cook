package com.ngocdevv.nativecook.blocks.text.particlewordmorph

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val WORDS = listOf("SPARK", "DRIFT", "BLOOM", "ORBIT", "GLOW")
private val PALETTES = listOf(
    0xFFFFB36B.toInt() to 0xFFFF5F7E.toInt(),
    0xFF6BE4FF.toInt() to 0xFF4F7DFF.toInt(),
    0xFFFF8AD8.toInt() to 0xFFA77BFF.toInt(),
    0xFFC9B8FF.toInt() to 0xFF6FA8FF.toInt(),
    0xFF9DFFCB.toInt() to 0xFFE8FF7A.toInt(),
)

// The night behind the words: deep navy, lifted in the middle and darker at the corners.
private const val NIGHT = 0xFF060A1C.toInt()
private const val NIGHT_GLOW = 0xFF152255.toInt()
private const val NIGHT_EDGE = 0xFF020309.toInt()
private val LABEL = Color(205, 214, 255).copy(alpha = 0.55f)
// A halo of GLOW_BLUR dp in the word's colour.
private const val GLOW_BLUR = 5f
// The word indicator: the words themselves in small tracked capitals, LABEL_GAP dp apart (see the
// engine), the one on screen lit and underlined by a hairline MARK_HEIGHT dp tall, MARK_BELOW dp under
// the labels' middle, that glides to the next word as it forms, its width following the words'. The
// labels' middle sits ROW_ABOVE dp above the bottom safe area, the hint's bottom HINT_ABOVE dp above it.
private const val ROW_ABOVE = 44f
private const val HINT_ABOVE = 26f
private const val MARK_HEIGHT = 1.5f
private const val MARK_BELOW = 16f
private const val DIM = 0.36f
// Tracking of the labels (dp); Android spaces each letter by it, so the mark leaves it out.
private const val TRACKING = 2.4f
private val INK = Color(0xFFEEF1FF)
// Edges kept clear around the labels at the top and sides, in dp.
private const val EDGE = 28f
// A touch shorter than TAP_MS that stays within TAP_SLOP dp is a tap.
private const val TAP_MS = 300L
private const val TAP_SLOP = 12f
// The sprite is drawn at this many pixels per dp of its size.
private const val SPRITE_PX = 3

/**
 * Thousands of small glowing particles that spell a word on a deep navy night. Every few seconds they
 * flow into the next word: each particle sets off a moment after its neighbour to the left, swings
 * along a curved path through a slowly turning field, and settles into the new letters while the
 * colours shift to the new word's. A finger pushes the particles aside like sand and they spring
 * back when it moves on; a tap forms the next word.
 *
 * @param words The words the particles spell, in order. Short words (up to six letters) read best.
 * @param palettes Two colours per word (ARGB), left to right across it.
 * @param autoPlay Cycles the words on a loop, with one pass of a finger through the third word.
 * @param onWord Called with a word's index when it starts to form after a tap.
 */
@Composable
fun ParticleWordMorph(
    modifier: Modifier = Modifier,
    words: List<String> = WORDS,
    palettes: List<Pair<Int, Int>> = PALETTES,
    autoPlay: Boolean = false,
    onWord: ((Int) -> Unit)? = null,
) {
    ParticleWordMorphScreen(modifier, words.ifEmpty { WORDS }, palettes.ifEmpty { PALETTES }, autoPlay, onWord)
}

@Composable
private fun ParticleWordMorphScreen(
    modifier: Modifier,
    words: List<String>,
    palettes: List<Pair<Int, Int>>,
    autoPlay: Boolean,
    onWord: ((Int) -> Unit)?,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val density = LocalDensity.current
    val wordChanged by rememberUpdatedState(onWord)
    val motion = remember(words) { ParticleWordMorphMotion(words, auto) }
    val paints = remember { ParticleWordMorphPaints() }
    // Bumped once a frame, so the particles are drawn again.
    var frame by remember { mutableLongStateOf(0L) }

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    SideEffect { motion.reducedMotion = reducedMotion }

    LaunchedEffect(motion, auto) { motion.resetLoop(auto) }

    LaunchedEffect(motion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0.016f else min((now - last) / 1e9f, 0.048f)
                last = now
                motion.prepare()
                if (dt > 0f) motion.step(dt)
                frame = now
            }
        }
    }

    fun glow(): Int {
        val a = palettes[motion.colorFrom % palettes.size]
        val b = palettes[motion.colorTo % palettes.size]
        return mixColor(a.second, b.second, motion.shift)
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = "Particles spelling ${words.joinToString(", ")}. Touch to scatter them; tap for the next word." }
            .pointerInput(motion) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var travel = 0f
                    motion.touched()
                    motion.fingerX = start.x / density.density
                    motion.fingerY = start.y / density.density
                    motion.fingerTarget = 1f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dx = (change.position.x - start.x) / density.density
                        val dy = (change.position.y - start.y) / density.density
                        travel = max(travel, sqrt(dx * dx + dy * dy))
                        if (!change.pressed) {
                            if (change.uptimeMillis - down.uptimeMillis <= TAP_MS && travel <= TAP_SLOP) {
                                // A tap forms the next word, or the word whose label it hit.
                                val next = motion.tap(change.position.x / density.density, change.position.y / density.density)
                                if (next != null) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    wordChanged?.invoke(next)
                                }
                            }
                            break
                        }
                        motion.touched()
                        motion.fingerX = change.position.x / density.density
                        motion.fingerY = change.position.y / density.density
                    }
                    motion.fingerTarget = 0f
                }
            },
    ) {
        motion.width = maxWidth.value
        motion.height = maxHeight.value
        val width = maxWidth.value
        val height = maxHeight.value
        // The labels' middle (dp from the top), and each label laid out once: its width (with its
        // tracking) and left edge, the row centred.
        val rowY = height - safeBottom - ROW_ABOVE
        motion.rowY = rowY
        val measurer = rememberTextMeasurer()
        val labelStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = TRACKING.sp, lineHeight = 16.sp)
        val labels = remember(words, width, density) {
            val layouts = words.map { measurer.measure(it, labelStyle, softWrap = false, maxLines = 1) }
            val ws = FloatArray(words.size) { layouts[it].size.width / density.density }
            val total = ws.sum() + LABEL_GAP * max(0, words.size - 1)
            var x = (width - total) / 2f
            val xs = FloatArray(words.size) { k -> x.also { x += ws[k] + LABEL_GAP } }
            motion.labelX = xs
            motion.labelW = ws
            Triple(layouts, xs, ws)
        }

        // The night.
        Box(
            Modifier.fillMaxSize().drawBehind {
                scale(density.density, density.density, pivot = Offset.Zero) {
                    drawIntoCanvas { paints.night(it.nativeCanvas, width, height) }
                }
            },
        )

        // The particles in one layer, tinted by the word's gradient and haloed in its colour.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (frame < 0L) return@graphicsLayer
                    compositingStrategy = CompositingStrategy.Offscreen
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = ParticleWordMorphGlow.effect(glow(), sigmaToRadius(GLOW_BLUR * density.density)).asComposeRenderEffect()
                    }
                }
                .drawBehind {
                    if (frame < 0L || !motion.ready) return@drawBehind
                    scale(density.density, density.density, pivot = Offset.Zero) {
                        drawIntoCanvas {
                            val a = palettes[motion.colorFrom % palettes.size]
                            val b = palettes[motion.colorTo % palettes.size]
                            val t = motion.shift
                            paints.particles(it.nativeCanvas, motion, width, height, mixColor(a.first, b.first, t), mixColor(a.second, b.second, t))
                        }
                    }
                },
        )

        // Which word is on screen: the words in a row of small capitals, the current one lit and
        // underlined. The hairline glides from word to word as the particles fly, stretching or
        // shrinking to each word's width, and the labels brighten and dim with it.
        Box(
            Modifier.fillMaxSize().drawBehind {
                if (frame < 0L) return@drawBehind
                val (layouts, xs, ws) = labels
                val count = minOf(layouts.size, xs.size, ws.size)
                val m = motion.mark.toFloat()
                if (count == 0 || !m.isFinite()) return@drawBehind
                val px = density.density
                for (k in 0 until count) {
                    val lit = DIM + (1f - DIM) * clamp01(1f - abs(m - k))
                    val layout = layouts[k]
                    drawText(layout, color = INK, topLeft = Offset(xs[k] * px, rowY * px - layout.size.height / 2f), alpha = lit)
                }
                val c = m.coerceIn(0f, (count - 1).toFloat())
                val a = c.toInt()
                val b = min(count - 1, a + 1)
                val t = c - a
                // Android sets half the tracking before each letter and half after it.
                val x = xs[a] + (xs[b] - xs[a]) * t + TRACKING / 2f
                val w = ws[a] + (ws[b] - ws[a]) * t - TRACKING
                if (!x.isFinite() || !w.isFinite() || w <= 0f) return@drawBehind
                drawRoundRect(
                    INK,
                    topLeft = Offset(x * px, (rowY + MARK_BELOW) * px),
                    size = Size(w * px, MARK_HEIGHT * px),
                    cornerRadius = CornerRadius(MARK_HEIGHT * px / 2f),
                )
            },
        )

        val label = TextStyle(color = LABEL, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 2.2.sp, fontFeatureSettings = "tnum")
        Row(
            Modifier.fillMaxWidth().padding(start = EDGE.dp, end = EDGE.dp, top = (safeTop + 22f).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText("WORDS IN DUST", style = label)
            BasicText("%,d PARTICLES".format(java.util.Locale.US, COUNT), style = label)
        }
        // The hint's bottom sits HINT_ABOVE dp above the word row's middle.
        Box(Modifier.fillMaxSize().padding(bottom = max(0f, safeBottom + ROW_ABOVE + HINT_ABOVE).dp), contentAlignment = Alignment.BottomCenter) {
            BasicText(
                "Touch to scatter · Tap for the next word",
                Modifier.fillMaxWidth(),
                style = TextStyle(color = LABEL, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, textAlign = TextAlign.Center),
            )
        }
    }
}

/** The halo: the layer blurred, tinted in the word's colour and laid under itself (Android 12+). */
@RequiresApi(Build.VERSION_CODES.S)
private object ParticleWordMorphGlow {
    fun effect(color: Int, radius: Float): RenderEffect {
        val blurred = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
        val tinted = RenderEffect.createColorFilterEffect(BlendModeColorFilter(color, BlendMode.SRC_IN), blurred)
        return RenderEffect.createBlendModeEffect(tinted, RenderEffect.createOffsetEffect(0f, 0f), BlendMode.SRC_OVER)
    }
}

private class ParticleWordMorphPaints {
    /** A soft white dot: solid in the middle, fading out towards its edge. */
    private val sprite: Bitmap = run {
        val side = (SPRITE * SPRITE_PX).toInt()
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            side / 2f, side / 2f, side / 2f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xD9FFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        Canvas(bitmap).drawCircle(side / 2f, side / 2f, side / 2f, paint)
        bitmap
    }
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(sprite, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private val verts = FloatArray(COUNT * 8)
    private val texs = FloatArray(COUNT * 8).also {
        val s = SPRITE * SPRITE_PX
        for (i in 0 until COUNT) {
            val o = i * 8
            it[o] = 0f; it[o + 1] = 0f
            it[o + 2] = s; it[o + 3] = 0f
            it[o + 4] = s; it[o + 5] = s
            it[o + 6] = 0f; it[o + 7] = s
        }
    }
    private val indices = ShortArray(COUNT * 6).also {
        for (i in 0 until COUNT) {
            val v = (i * 4).toShort()
            val o = i * 6
            it[o] = v; it[o + 1] = (v + 1).toShort(); it[o + 2] = (v + 2).toShort()
            it[o + 3] = v; it[o + 4] = (v + 2).toShort(); it[o + 5] = (v + 3).toShort()
        }
    }
    // Below Android 10 a hardware canvas has no vertex meshes: round points in a few sizes instead.
    private val point = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = 0xFFFFFFFF.toInt() }
    private val bucketPoints = FloatArray(COUNT * 2)
    private val tint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
    private val nightPaint = Paint()
    private var nightFor = Pair(-1f, -1f)

    fun night(canvas: Canvas, width: Float, height: Float) {
        if (nightFor != Pair(width, height)) {
            nightFor = Pair(width, height)
            nightPaint.shader = RadialGradient(
                width / 2f, height * WORD_AT, max(width, height) * 0.78f,
                intArrayOf(NIGHT_GLOW, NIGHT, NIGHT_EDGE), floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width, height, nightPaint)
    }

    fun particles(canvas: Canvas, m: ParticleWordMorphMotion, width: Float, height: Float, left: Int, right: Int) {
        val layer = canvas.saveLayer(0f, 0f, width, height, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (i in 0 until COUNT) {
                val half = SPRITE * m.scale[i] / 2f
                val x = m.px[i]
                val y = m.py[i]
                val o = i * 8
                verts[o] = x - half; verts[o + 1] = y - half
                verts[o + 2] = x + half; verts[o + 3] = y - half
                verts[o + 4] = x + half; verts[o + 5] = y + half
                verts[o + 6] = x - half; verts[o + 7] = y + half
            }
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, verts.size, verts, 0, texs, 0, null, 0, indices, 0, indices.size, spritePaint)
        } else {
            // Six sizes, each a batch of round points about as wide as the soft dot's bright core.
            for (bucket in 0 until 6) {
                var n = 0
                var sum = 0f
                for (i in 0 until COUNT) {
                    if (((m.scale[i] / 0.2f - 0.55f) / 0.75f * 6f).toInt().coerceIn(0, 5) != bucket) continue
                    bucketPoints[n++] = m.px[i]
                    bucketPoints[n++] = m.py[i]
                    sum += m.scale[i]
                }
                if (n == 0) continue
                point.strokeWidth = SPRITE * (sum / (n / 2)) * 0.62f
                canvas.drawPoints(bucketPoints, 0, n, point)
            }
        }
        tint.shader = LinearGradient(WORD_MARGIN, 0f, width - WORD_MARGIN, height * 0.2f, left, right, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, tint)
        canvas.restoreToCount(layer)
    }
}
