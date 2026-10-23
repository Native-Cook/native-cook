package com.ngocdevv.nativecook.blocks.text.neonsignaturewrite

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Neon Signature Write: a script word on a dark wall that writes itself in neon.

 Timeline of the self-playing loop (LOOP = 9.6 s, three segments of 3.2 s):
 segment k opens with word k lit, the resting state.
   0.00–0.80  word k is lit; its glow breathes once (+10 %).
   0.80–1.20  word k switches off: it flickers twice, then the tube cools to black.
   1.20–1.30  dark.
   1.30–2.80  word k + 1 is written (the "write" clock τ runs 0 → 1.5 s). Three tubes trace the outline
              of every letter: mint first, rose 0.18 s later, the word's own colour 0.36 s later, each over
              1.1 s. Within a tube, a contour starts later the further right it sits (up to 50 % of the
              tube's run) and takes 50 % of it. The mint and rose heads burn white-hot and throw sparks.
   2.80–3.20  ignite (τ 1.5 → 1.9): the letters fill with the word's gradient after two flickers while the
              tubes fade; the glow overshoots by 45 % and settles.
 Segment 3 writes word 0 again, so the period ends exactly where it began.

 Touch: dragging across the sign rewrites the shown word with the pen under the finger (left edge of the
 word = nothing, right edge = every tube closed); letting go finishes it and lights it. A tap switches the
 sign off and writes the next word. 2 s after the last touch the sign goes back to the first word, lit,
 and the loop starts over. Reduced motion: the first word is shown lit and still; a tap cross-fades to
 the next one.
*/

/** The self-playing loop, in seconds. */
const val LOOP = 9.6f

private object M {
    const val SEGMENT = 3.2f
    const val HOLD = 0.8f
    const val OFF = 0.4f
    const val DARK = 0.1f
    const val WRITE = 1.5f
    const val IGNITE = 0.4f
    /** τ when the sign is fully lit. */
    const val LIT = WRITE + IGNITE
    /** The three tubes: when each starts on τ, and how long it runs. */
    val LAGS = floatArrayOf(0f, 0.18f, 0.36f)
    const val RUN = 1.1f
    const val STAGGER = 0.5f
    const val SPAN = 0.5f
    /** τ when the last tube closes: the right edge of a drag. */
    const val CLOSED = 0.36f + RUN
    const val IDLE = 2f
    const val FADE = 0.25f
    const val SWITCH_OVER = OFF + DARK + LIT
}

private val Wall = Color(0xFF060509)
private val Mint = Color(0xFF3DFFC4)
private val Rose = Color(0xFFFF6FAE)
private val HeadTint = Color(0xFFD8FFF4)
private val SparkTint = Color(0xFF9FFCFF)

/** What the sign shows at one moment. */
private data class Look(
    val word: Int,
    /** The write clock: 0…1.5 writing, 1.5…1.9 igniting, 1.9 lit. */
    val tau: Float,
    /** 0 on, 1 switched off. */
    val off: Float = 0f,
    /** Extra glow while lit. */
    val breath: Float = 0f,
    /** The whole sign's opacity (the reduced-motion cross-fade). */
    val alpha: Float = 1f,
)

private sealed interface Manual {
    data class Scrub(val word: Int, val tau: Float) : Manual
    data class Play(val word: Int, val from: Float, val start: Long) : Manual
    /** Switch [from] off, then write [to]. */
    data class Next(val from: Int, val to: Int, val start: Long) : Manual
    /** Reduced motion: cross-fade from one lit word to another. */
    data class Fade(val from: Int, val to: Int, val start: Long) : Manual
}

private fun seconds(from: Long, to: Long) = (to - from) / 1e9f

/** `from` switches off, a beat of dark, then `to` is written and lit; `e` counts from the switch-off. */
private fun switchOver(from: Int, to: Int, e: Float): Look = when {
    e < M.OFF -> Look(from, M.LIT, off = e / M.OFF)
    e < M.OFF + M.DARK -> Look(to, 0f)
    else -> Look(to, min(e - M.OFF - M.DARK, M.LIT))
}

/** Fill brightness while igniting: two flickers, then on. */
private val IgniteKeys = floatArrayOf(0f, 0f, 0.08f, 0.85f, 0.16f, 0.15f, 0.26f, 0.95f, 0.34f, 0.4f, 0.5f, 1f, 1f, 1f)
/** Brightness while switching off: dips and catches twice, then cools. */
private val OffKeys = floatArrayOf(0f, 1f, 0.1f, 0.25f, 0.18f, 0.9f, 0.28f, 0.15f, 0.36f, 0.7f, 0.55f, 0.35f, 1f, 0f)

/** Piecewise-linear lookup in (x, y) pairs. */
private fun keyed(keys: FloatArray, x: Float): Float {
    if (x <= keys[0]) return keys[1]
    var i = 2
    while (i < keys.size) {
        if (x <= keys[i]) {
            val x0 = keys[i - 2]; val y0 = keys[i - 1]; val x1 = keys[i]; val y1 = keys[i + 1]
            return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
        }
        i += 2
    }
    return keys[keys.size - 1]
}

/**
 * A script word on a dark wall that writes itself in neon: three coloured tubes trace the outline of
 * every letter with white-hot, sparking pen heads, then the word flickers on in its own gradient.
 */
@Composable
fun NeonSignatureWrite(
    modifier: Modifier = Modifier,
    words: List<NeonSignatureWriteWord> = NeonSignatureWriteWord.samples,
    /** Writes the words one after another by itself, every [LOOP] seconds round. */
    autoPlay: Boolean = false,
    caption: String = "Open late",
    hint: String = "Drag across the sign to write it",
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val geometry = remember(context) {
        NeonSignatureWriteGeometry(Typeface.createFromAsset(context.assets, "neonsignaturewrite/neonsignaturewrite_Hurricane-Regular.ttf"))
    }
    val painter = remember { NeonSignatureWritePainter() }
    val signs by rememberUpdatedState(words)
    val playing by rememberUpdatedState(autoPlay)

    var now by remember { mutableLongStateOf(System.nanoTime()) }
    var loopStart by remember { mutableLongStateOf(System.nanoTime()) }
    var manual by remember { mutableStateOf<Manual?>(null) }
    var touches by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val paused = manual == null && (reduced || !autoPlay)

    fun look(at: Long): Look {
        val n = signs.size
        when (val m = manual) {
            is Manual.Scrub -> return Look(m.word, m.tau)
            is Manual.Play -> return Look(m.word, min(m.from + seconds(m.start, at), M.LIT))
            is Manual.Next -> return switchOver(m.from, m.to, seconds(m.start, at))
            is Manual.Fade -> {
                val e = seconds(m.start, at)
                return if (e < M.FADE) Look(m.from, M.LIT, alpha = 1f - e / M.FADE)
                else Look(m.to, M.LIT, alpha = min((e - M.FADE) / M.FADE, 1f))
            }
            null -> Unit
        }
        if (!playing || reduced) return Look(0, M.LIT)
        val e = seconds(loopStart, at).mod(LOOP)
        val k = (e / M.SEGMENT).toInt().coerceAtMost(2) % n
        val l = e - k * M.SEGMENT
        if (l < M.HOLD) return Look(k, M.LIT, breath = sin(PI.toFloat() * l / M.HOLD))
        return switchOver(k, (k + 1) % n, l - M.HOLD)
    }

    fun finished(m: Manual, at: Long): Boolean = when (m) {
        is Manual.Scrub -> false
        is Manual.Play -> m.from + seconds(m.start, at) >= M.LIT
        is Manual.Next -> seconds(m.start, at) >= M.SWITCH_OVER
        is Manual.Fade -> seconds(m.start, at) >= M.FADE * 2
    }

    // The frame clock runs only while something moves.
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        while (isActive) withFrameNanos { now = System.nanoTime() }
    }
    LaunchedEffect(Unit) { loopStart = System.nanoTime() }

    // 2 s after the last touch, once what it started has finished, the sign returns to the first word
    // and the loop starts over from its resting frame.
    LaunchedEffect(touches) {
        if (touches == 0 || dragging) return@LaunchedEffect
        delay((M.IDLE * 1000).toLong())
        if (dragging) return@LaunchedEffect
        while (manual?.let { !finished(it, System.nanoTime()) } == true) delay(50)
        val current = look(System.nanoTime()).word
        if (current != 0) {
            val start = System.nanoTime()
            manual = if (reduced) Manual.Fade(current, 0, start) else Manual.Next(current, 0, start)
            delay(((if (reduced) M.FADE * 2 else M.SWITCH_OVER) * 1000).toLong())
        }
        loopStart = System.nanoTime()
        manual = null
    }

    val shownWord by remember { derivedStateOf { look(now).word } }
    val lit by remember { derivedStateOf { look(now).let { it.tau >= M.WRITE && it.off < 0.5f } } }

    BoxWithConstraints(modifier.fillMaxSize().background(Wall)) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = signs[shownWord].text }
                .pointerInput(widthDp, heightDp) {
                    fun haptic(soft: Boolean) {
                        view.performHapticFeedback(
                            if (soft && Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK,
                        )
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        dragging = true
                        touches++
                        val grabbed = look(System.nanoTime()).word
                        var scrubbing = false
                        while (true) {
                            val change = awaitPointerEventChange() ?: break
                            if (!change.pressed) break
                            if (reduced) continue
                            val moved = (change.position - down.position).getDistance() / density > 8f
                            if (!moved && !scrubbing) continue
                            if (!scrubbing) {
                                scrubbing = true
                                haptic(false)
                            }
                            val box = geometry.words(signs.map { it.text }, widthDp, heightDp)[grabbed].box
                            val fraction = ((change.position.x / density - box.left) / box.width()).coerceIn(0f, 1f)
                            manual = Manual.Scrub(grabbed, fraction * M.CLOSED)
                            change.consume()
                        }
                        val at = System.nanoTime()
                        val m = manual
                        if (reduced) {
                            val from = look(at).word
                            manual = Manual.Fade(from, (from + 1) % signs.size, at)
                            haptic(false)
                        } else if (scrubbing && m is Manual.Scrub) {
                            manual = Manual.Play(m.word, m.tau, at)
                            val mark = touches + 1
                            view.postDelayed({ if (touches == mark && !dragging) haptic(true) }, (max(M.WRITE - m.tau, 0f) * 1000).toLong())
                        } else {
                            val from = look(at).word
                            manual = Manual.Next(from, (from + 1) % signs.size, at)
                            haptic(false)
                            val mark = touches + 1
                            view.postDelayed({ if (touches == mark && !dragging) haptic(true) }, ((M.OFF + M.DARK + M.WRITE) * 1000).toLong())
                        }
                        dragging = false
                        touches++
                    }
                },
        ) {
            val look = look(now)
            val shapes = geometry.words(signs.map { it.text }, widthDp, heightDp)
            if (look.word >= shapes.size) return@Canvas
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density, density)
                painter.draw(c, widthDp, heightDp, look, shapes[look.word], signs[look.word])
                c.restore()
            }
        }

        Chrome(
            words = words,
            current = shownWord,
            lit = lit,
            caption = caption,
            hint = hint,
        )
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitPointerEventChange() =
    awaitPointerEvent().changes.firstOrNull()

@Composable
private fun Chrome(words: List<NeonSignatureWriteWord>, current: Int, lit: Boolean, caption: String, hint: String) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val word = words[current]
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .drawBehind {
                        if (lit) {
                            drawIntoCanvas {
                                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = word.glow.toArgb()
                                    maskFilter = BlurMaskFilter(sigmaToRadius(5.dp.toPx()), BlurMaskFilter.Blur.NORMAL)
                                }
                                it.nativeCanvas.drawCircle(size.width / 2, size.height / 2, size.width / 2, p)
                            }
                        }
                    }
                    .background(if (lit) word.glow else Color.White.copy(alpha = 0.25f), CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            BasicText(caption.uppercase(), style = mono(12f, FontWeight.SemiBold, Color.White.copy(alpha = 0.72f), 2.4f), maxLines = 1)
            Spacer(Modifier.weight(1f).width(12.dp))
            BasicText(
                "%02d / %02d".format(current + 1, words.size),
                style = mono(12f, FontWeight.Medium, Color.White.copy(alpha = 0.42f), 0f),
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BasicText(hint, style = plain(13f, FontWeight.Normal, Color.White.copy(alpha = 0.42f)), maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                words.forEachIndexed { index, w ->
                    val selected = index == current
                    Box(
                        Modifier
                            .height(32.dp)
                            .background(if (selected) w.glow.copy(alpha = 0.12f) else Color.Transparent, CircleShape)
                            .border(1.dp, if (selected) w.glow.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.1f), CircleShape)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            w.text,
                            style = plain(14f, if (selected) FontWeight.SemiBold else FontWeight.Normal, Color.White.copy(alpha = if (selected) 0.95f else 0.36f)),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun plain(size: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun mono(size: Float, weight: FontWeight, color: Color, tracking: Float) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    fontFamily = FontFamily.Monospace,
    letterSpacing = tracking.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Draws one look of the sign, in dp, onto a canvas already scaled to dp. */
private class NeonSignatureWritePainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tube = Path()
    private val hot = Path()
    private val heads = Path()
    private val spark = Path()
    private val pos = FloatArray(2)
    private val blurs = HashMap<Float, BlurMaskFilter>()
    private var wallKey: Any? = null
    private var wallShader: Shader? = null
    private var fillKey: Any? = null
    private var tubeShader: Shader? = null
    private var coreShader: Shader? = null

    private class SparkDraw(val x: Float, val y: Float, val tx: Float, val ty: Float, val life: Float, val width: Float, val white: Boolean)
    private val sparks = ArrayList<SparkDraw>()

    private fun blur(r: Float) = blurs.getOrPut(r) { BlurMaskFilter(sigmaToRadius(r), BlurMaskFilter.Blur.NORMAL) }

    private fun Paint.set(color: Color, alpha: Float) {
        shader = null
        this.color = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)).toArgb()
    }

    fun draw(c: Canvas, width: Float, height: Float, look: Look, shape: NeonSignatureWriteGeometry.Word, colours: NeonSignatureWriteWord) {
        val ignite = ((look.tau - M.WRITE) / M.IGNITE).coerceIn(0f, 1f)
        val level = (if (look.tau >= M.WRITE) keyed(IgniteKeys, ignite) else 0f) * keyed(OffKeys, look.off) * look.alpha
        val gain = 1f + 0.45f * (if (ignite > 0.4f && ignite < 1f) sin(PI.toFloat() * (ignite - 0.4f) / 0.6f) else 0f) + 0.1f * look.breath
        val tubes = (1f - ignite) * look.alpha
        val writing = min(look.tau / M.WRITE, 1f) * tubes

        // Light thrown on the wall.
        val light = max(level * gain, writing * 0.55f)
        if (light > 0.002f) {
            val key = Triple(width, height, colours.glow)
            if (key != wallKey) {
                wallKey = key
                wallShader = RadialGradient(
                    width / 2f, height * 0.45f, width * 0.95f,
                    intArrayOf(colours.glow.copy(alpha = 0.2f).toArgb(), colours.glow.copy(alpha = 0.06f).toArgb(), Color.Transparent.toArgb()),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
                )
            }
            fill.maskFilter = null
            fill.shader = wallShader
            fill.alpha = (min(light, 1f) * 255).toInt()
            c.drawRect(0f, 0f, width, height, fill)
            fill.shader = null
        }

        if (tubes > 0.002f && look.tau > 0f) drawTubes(c, shape, colours, min(look.tau, M.WRITE), tubes)
        if (level > 0.002f) drawFill(c, shape, colours, level, gain)
    }

    private fun drawTubes(c: Canvas, shape: NeonSignatureWriteGeometry.Word, colours: NeonSignatureWriteWord, tau: Float, alpha: Float) {
        heads.reset()
        hot.reset()
        sparks.clear()
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        for (layer in 0 until 3) {
            val run = ((tau - M.LAGS[layer]) / M.RUN).coerceIn(0f, 1f)
            if (run <= 0f) continue
            val colour = when (layer) { 0 -> Mint; 1 -> Rose; else -> colours.glow }
            tube.reset()
            for (contour in shape.contours) {
                val p = ((run - M.STAGGER * contour.delay) / M.SPAN).coerceIn(0f, 1f)
                if (p <= 0f) continue
                contour.measure.getSegment(0f, p * contour.length, tube, true)
                if (layer >= 2) continue
                if (p < 1f) {
                    contour.measure.getPosTan(p * contour.length, pos, null)
                    heads.addCircle(pos[0], pos[1], 1.6f, Path.Direction.CW)
                    contour.measure.getSegment(max(p - 0.07f, 0f) * contour.length, p * contour.length, hot, true)
                }
                for (s in contour.sparks) {
                    if (s.layer != layer || p < s.at) continue
                    val age = (p - s.at) * M.SPAN * M.RUN
                    if (age >= s.life) continue
                    val x = s.origin.x + s.vx * age
                    val y = s.origin.y + s.vy * age + 0.5f * 260f * age * age
                    val vy = s.vy + 260f * age
                    val speed = max(hypot(s.vx, vy), 1f)
                    sparks += SparkDraw(x, y, x - s.vx / speed * s.length, y - vy / speed * s.length, 1f - age / s.life, s.width, s.white)
                }
            }
            stroke.maskFilter = blur(5f)
            stroke.strokeWidth = 3.5f
            stroke.strokeCap = Paint.Cap.BUTT
            stroke.strokeJoin = Paint.Join.MITER
            stroke.set(colour, 0.6f * alpha)
            c.drawPath(tube, stroke)
            stroke.maskFilter = null
            stroke.strokeWidth = 1.7f
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.strokeJoin = Paint.Join.ROUND
            stroke.set(colour, alpha)
            c.drawPath(tube, stroke)
            stroke.strokeWidth = 0.6f
            stroke.strokeCap = Paint.Cap.BUTT
            stroke.strokeJoin = Paint.Join.MITER
            stroke.set(Color.White, 0.4f * alpha)
            c.drawPath(tube, stroke)
        }
        // The pen heads: white-hot ends with a halo, and the sparks they throw.
        stroke.maskFilter = blur(5f)
        stroke.strokeWidth = 4f
        stroke.set(Color.White, 0.9f * alpha)
        c.drawPath(hot, stroke)
        fill.maskFilter = blur(5f)
        fill.set(HeadTint, alpha)
        c.drawPath(heads, fill)
        stroke.maskFilter = null
        fill.maskFilter = null
        stroke.strokeWidth = 1.8f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.set(Color.White, alpha)
        c.drawPath(hot, stroke)
        fill.set(Color.White, alpha)
        c.drawPath(heads, fill)
        for (s in sparks) {
            spark.reset()
            spark.moveTo(s.tx, s.ty)
            spark.lineTo(s.x, s.y)
            stroke.strokeWidth = s.width
            stroke.set(if (s.white) Color.White else SparkTint, alpha * s.life)
            c.drawPath(spark, stroke)
        }
    }

    private fun drawFill(c: Canvas, shape: NeonSignatureWriteGeometry.Word, colours: NeonSignatureWriteWord, level: Float, gain: Float) {
        if (fillKey !== shape) {
            fillKey = shape
            tubeShader = LinearGradient(
                shape.left.x, shape.left.y, shape.right.x, shape.right.y,
                colours.start.toArgb(), colours.end.toArgb(), Shader.TileMode.CLAMP,
            )
            coreShader = LinearGradient(
                shape.left.x, shape.left.y, shape.right.x, shape.right.y,
                whiten(colours.start, 0.5f).toArgb(), whiten(colours.end, 0.45f).toArgb(), Shader.TileMode.CLAMP,
            )
        }
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeJoin = Paint.Join.MITER
        // The halo is one layer (fill and outline blurred together), faded as a whole.
        val halo = min(level * 0.55f * gain, 1f)
        c.saveLayerAlpha(null, (halo * 255).toInt())
        fill.maskFilter = blur(18f)
        fill.set(colours.glow, 1f)
        c.drawPath(shape.fill, fill)
        stroke.maskFilter = blur(18f)
        stroke.strokeWidth = 6f
        stroke.set(colours.glow, 1f)
        c.drawPath(shape.fill, stroke)
        c.restore()

        stroke.maskFilter = blur(3f)
        stroke.strokeWidth = 3.2f
        stroke.shader = tubeShader
        stroke.color = android.graphics.Color.WHITE
        stroke.alpha = (min(level * gain, 1f) * 255).toInt()
        c.drawPath(shape.fill, stroke)

        fill.maskFilter = null
        stroke.maskFilter = null
        stroke.strokeWidth = 1.2f
        stroke.alpha = (level * 255).toInt()
        c.drawPath(shape.fill, stroke)
        stroke.shader = null
        fill.shader = coreShader
        fill.color = android.graphics.Color.WHITE
        fill.alpha = (level * 255).toInt()
        c.drawPath(shape.fill, fill)
        fill.shader = null
    }

    /** [colour] moved [amount] of the way to white. */
    private fun whiten(colour: Color, amount: Float) =
        Color(colour.red + (1f - colour.red) * amount, colour.green + (1f - colour.green) * amount, colour.blue + (1f - colour.blue) * amount, colour.alpha)
}
