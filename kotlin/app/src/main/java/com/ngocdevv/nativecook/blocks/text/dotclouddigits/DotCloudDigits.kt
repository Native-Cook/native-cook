package com.ngocdevv.nativecook.blocks.text.dotclouddigits

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Dot Cloud Digits — a month's numbers, each drawn by the letters of its own name. A port of the
 SwiftUI block (DotCloudDigits.swift); the timeline is the same:

 autoPlay (LOOP = 9 s, three numbers of 3 s each, k = 0, 1, 2 starting at 3k s):
   3k + 0.0 … 3k + 1.2  number k rests, fully formed (t = 0 is the screen's first look).
   3k + 1.2 … 3k + 3.0  transition k → k+1 (1.8 s). Each letter waits 0…0.3 s (seeded), then
                        travels 1.5 s along a curve through its cloud point (ease-in-out cubic);
                        the cloud turns 0.55 rad as it goes. Halfway it becomes the new name's
                        letter; it shrinks to 70 % and dims to 50 % there. Caption: the old one
                        fades out over the first 35 %, the new one in over the last 40 %.
   9.0                  back at number 0, formed: identical to t = 0.
 Background letters twinkle with whole-number cycles per loop, and a glint (opacity 0.8 → 1) runs
 along the outline's letters once every 3 s.

 Touch: a drag pushes the outline's letters away from the finger (radius 90 dp, up to 46 dp); on
 release they spring back (e^{-6t}·cos 11t). A tap moves to the next number. 2 s after the last
 touch the screen returns to the first number and the loop starts again.
 Reduced motion: no loop, no cloud, no twinkle; a tap cross-fades to the next number (0.4 s).
 */

data class DotCloudDigitsStat(val value: String, val word: String, val caption: String) {
    companion object {
        val samples = listOf(
            DotCloudDigitsStat("29", "TWENTY-NINE", "runs before 8 am"),
            DotCloudDigitsStat("12", "TWELVE", "new routes mapped"),
            DotCloudDigitsStat("64", "SIXTY-FOUR", "kilometres on trails"),
        )
    }
}

private const val LOOP = 9.0
private const val SLOT = 3.0
private const val HOLD = 1.2
private const val TRANSITION = 1.8
private const val MAX_DELAY = 0.3
private const val PARTICLES = 150
private const val STARS = 56
private const val LETTER_SIZE = 10.5f
private const val MAX_HEIGHT = 300f
private const val FONT_ASSET = "dotclouddigits/dotclouddigits_Poppins-SemiBold.ttf"

private val Ink = Color(0xFFF5F2ED)
private val Background = Color(0xFF040405)

private fun nowSeconds() = System.nanoTime() / 1e9

/** What is shown at a moment: number [a] turning into [b], [tau] seconds into the transition (null: resting on [b]). */
private data class DotCloudDigitsShown(val a: Int, val b: Int, val tau: Double?, val fade: Double? = null)

/** The block's state, held in snapshot state so the gesture and frame code never read stale values. */
private class DotCloudDigitsController(val count: Int, val autoPlay: Boolean, val reduced: Boolean) {
    /** When set, the autoPlay loop's t = 0 in seconds (it may lie in the future while a return plays). */
    var autoStart by mutableStateOf<Double?>(null)
    var from by mutableIntStateOf(0)
    var to by mutableIntStateOf(0)
    var since by mutableStateOf<Double?>(null)
    var finger by mutableStateOf<Offset?>(null)
    var pressedAt by mutableStateOf<Double?>(null)
    var releasedAt by mutableStateOf<Double?>(null)
    /** The frame clock, in seconds; read only in draw and layer lambdas. */
    var frame by mutableDoubleStateOf(nowSeconds())
    var resume: Job? = null

    fun state(at: Double): DotCloudDigitsShown {
        val start = autoStart
        if (start != null && at >= start && !reduced) {
            val e = ((at - start) % LOOP + LOOP) % LOOP
            val k = min(count - 1, (e / SLOT).toInt())
            val within = e - k * SLOT
            if (within < HOLD) return DotCloudDigitsShown(k, k, null)
            return DotCloudDigitsShown(k, (k + 1) % count, within - HOLD)
        }
        val s = since
        if (s != null) {
            val tau = at - s
            if (reduced) {
                val f = (tau / 0.4).coerceIn(0.0, 1.0)
                return DotCloudDigitsShown(from, to, null, if (f < 1) f else null)
            }
            if (tau < TRANSITION) return DotCloudDigitsShown(from, to, max(0.0, tau))
        }
        return DotCloudDigitsShown(to, to, null)
    }

    /** Takes the loop's current state over as the manual one. */
    fun takeOver(at: Double) {
        resume?.cancel()
        if (autoStart == null) return
        val s = state(at)
        from = s.a
        to = s.b
        since = s.tau?.let { at - it }
        autoStart = null
    }

    /** Moves to the next number; false while a change is still playing. */
    fun advance(at: Double): Boolean {
        takeOver(at)
        val s = since
        if (s != null && at - s < (if (reduced) 0.4 else TRANSITION)) return false
        from = to
        to = (to + 1) % count
        since = at
        return true
    }

    /** How hard the finger pushes, 0…1, springing back after release. */
    fun push(at: Double): Double {
        val pressed = pressedAt ?: return 0.0
        val held = 1 - exp(-12 * max(0.0, at - pressed))
        val released = releasedAt ?: return held
        val t = at - released
        if (t > 1.2) return 0.0
        return held * exp(-6 * t) * cos(11 * t)
    }
}

@Composable
fun DotCloudDigits(
    modifier: Modifier = Modifier,
    stats: List<DotCloudDigitsStat> = DotCloudDigitsStat.samples,
    title: String = "OCTOBER IN NUMBERS",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val typeface = remember { Typeface.createFromAsset(context.assets, FONT_ASSET) }
    val family = remember { FontFamily(Font(FONT_ASSET, context.assets)) }
    val c = remember(stats.size, autoPlay, reduced) { DotCloudDigitsController(stats.size, autoPlay, reduced) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(c) {
        if (autoPlay && !reduced) c.autoStart = nowSeconds()
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }

    fun scheduleResume() {
        c.resume?.cancel()
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            var now = nowSeconds()
            val s = c.since
            if (s != null && now - s < TRANSITION) {
                delay(((TRANSITION - (now - s)) * 1000).toLong())
                now = nowSeconds()
            }
            if (c.to != 0) {
                c.from = c.to
                c.to = 0
                c.since = now
                c.autoStart = now + TRANSITION
            } else {
                c.autoStart = now
            }
        }
    }

    fun advance() {
        val now = nowSeconds()
        if (c.advance(now)) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (reduced) scope.launch { delay(450); if (c.since == now) c.since = null }
        }
        scheduleResume()
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Background)
            .semantics {
                role = Role.Button
                contentDescription = title.lowercase().replaceFirstChar { it.uppercase() }
                onClick { advance(); true }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val density = LocalDensity.current.density
        val shapes = remember(width, height, stats) { DotCloudDigitsShapes(stats, typeface, width, height) }
        val words = shapes.words

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(c) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        c.takeOver(nowSeconds())
                        c.finger = down.position / density
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val d = change.position - down.position
                            if (!reduced && hypot(d.x, d.y) / density > 6f) {
                                if (!moved) {
                                    moved = true
                                    c.pressedAt = nowSeconds()
                                    c.releasedAt = null
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                c.finger = change.position / density
                                change.consume()
                            }
                        }
                        if (moved) {
                            c.releasedAt = nowSeconds()
                            scheduleResume()
                        } else {
                            advance()
                        }
                    }
                },
        ) {
            drawFigure(c, shapes, words, typeface, density)
        }

        // Header.
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = (top + 20).dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = TextStyle(fontFamily = family, fontSize = 12.sp, letterSpacing = 2.sp, color = Ink.copy(alpha = 0.55f))
            BasicText(title, style = style, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.CenterEnd) {
                stats.indices.forEach { i ->
                    BasicText(
                        "%02d / %02d".format(i + 1, stats.size),
                        style = style.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.graphicsLayer { alpha = captionAlpha(c.state(c.frame), i).first.toFloat() },
                    )
                }
            }
        }

        // Caption: every number's caption is composed once, and only their layers change per frame.
        Box(
            Modifier
                .offset(x = 24.dp, y = shapes.captionTop.dp)
                .width((width - 48).dp),
        ) {
            stats.forEachIndexed { i, stat ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val (alpha, dy) = captionAlpha(c.state(c.frame), i)
                            this.alpha = alpha.toFloat()
                            translationY = dy.toFloat() * density
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(
                        stat.caption,
                        style = TextStyle(fontFamily = family, fontSize = 22.sp, color = Ink, textAlign = TextAlign.Center),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    BasicText(
                        stat.word,
                        style = TextStyle(fontFamily = family, fontSize = 11.sp, letterSpacing = 3.sp, color = Ink.copy(alpha = 0.45f), textAlign = TextAlign.Center),
                        maxLines = 1,
                    )
                }
            }
        }

        // Page bar.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .offset(y = (height - max(bottomInset, 20f) - 28).dp),
        ) {
            val s = c.state(c.frame)
            val p = s.tau?.let { min(1.0, it / TRANSITION) } ?: (s.fade ?: 1.0)
            val bar = DotCloudDigitsMath.easeInOut(p)
            val resting = s.tau == null && s.fade == null
            val ons = stats.indices.map { i -> if (resting) (if (i == s.b) 1.0 else 0.0) else if (i == s.a) 1 - bar else if (i == s.b) bar else 0.0 }
            val widths = ons.map { (8 + 20 * it).toFloat() * density }
            val gap = 6 * density
            var x = (size.width - widths.sum() - gap * (widths.size - 1)) / 2
            ons.forEachIndexed { i, on ->
                drawRoundRect(
                    color = Ink.copy(alpha = (0.25 + 0.65 * on).toFloat()),
                    topLeft = Offset(x, 0f),
                    size = Size(widths[i], size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
                x += widths[i] + gap
            }
        }
    }
}

/** Caption i's opacity and vertical shift in dp for the shown state. */
private fun captionAlpha(s: DotCloudDigitsShown, i: Int): Pair<Double, Double> {
    val resting = s.tau == null && s.fade == null
    if (resting) return (if (i == s.b) 1.0 else 0.0) to 0.0
    val p = s.tau?.let { min(1.0, it / TRANSITION) } ?: s.fade!!
    val outT = if (s.tau != null) DotCloudDigitsMath.ease(min(1.0, p / 0.35)) else p
    val inT = if (s.tau != null) DotCloudDigitsMath.ease(max(0.0, (p - 0.6) / 0.4)) else p
    return when (i) {
        s.b -> inT to 8 * (1 - inT)
        s.a -> (1 - outT) to -6 * outT
        else -> 0.0 to 0.0
    }
}

private fun DrawScope.drawFigure(
    c: DotCloudDigitsController,
    shapes: DotCloudDigitsShapes,
    words: List<List<Char>>,
    typeface: Typeface,
    density: Float,
) {
    val now = c.frame
    val state = c.state(now)
    val reduce = c.reduced
    val loopT = c.autoStart?.let { now - it } ?: now
    val strength = if (reduce) 0.0 else c.push(now)
    val finger = c.finger
    val paint = shapes.paint
    paint.typeface = typeface
    val canvas = drawContext.canvas.nativeCanvas
    val ink = Ink.toArgb()
    paint.color = ink

    fun letter(ch: Char, x: Double, y: Double, scale: Double, alpha: Double) {
        val size = LETTER_SIZE * scale.toFloat() * density
        paint.textSize = size
        paint.alpha = (255 * alpha.coerceIn(0.0, 1.0)).roundToInt()
        // The capitals' centre sits 0.35 em above the baseline (cap height 0.7 em), as in the Swift symbols.
        canvas.drawText(ch.toString(), x.toFloat() * density, (y.toFloat() * density) + 0.35f * size, paint)
    }

    // Background letters.
    val starFlip = state.tau?.let { DotCloudDigitsMath.ease(min(1.0, it / 0.9)) } ?: 0.0
    shapes.stars.forEachIndexed { i, star ->
        val twinkle = if (reduce) 0.6 else 0.5 + 0.5 * sin(2 * PI * (star.cycles * loopT / LOOP) + star.phase)
        val swap = if (state.tau == null) 0.0 else sin(PI * starFlip)
        val word = if (starFlip < 0.5) words[state.a] else words[state.b]
        letter(word[i % word.size], star.x, star.y, star.scale, (star.base + 0.32 * twinkle) * (1 - 0.8 * swap))
    }

    // The figure.
    val a = shapes.targets[state.a]
    val b = shapes.targets[state.b]
    for (i in 0 until PARTICLES) {
        val fade = state.fade
        if (fade != null) {
            letter(words[state.a][i % words[state.a].size], a[i].x, a[i].y, 1.0, 1 - fade)
            letter(words[state.b][i % words[state.b].size], b[i].x, b[i].y, 1.0, fade)
            continue
        }
        var s = 0.0
        var p: DotCloudDigitsPoint
        val tau = state.tau
        if (tau != null) {
            s = DotCloudDigitsMath.easeInOut(((tau - shapes.delay[i]) / (TRANSITION - MAX_DELAY)).coerceIn(0.0, 1.0))
            p = DotCloudDigitsMath.travel(a[i], b[i], shapes.cloud[i], shapes.cloudCenter, s)
        } else {
            p = b[i]
        }
        if (strength != 0.0 && finger != null) {
            val dx = p.x - finger.x
            val dy = p.y - finger.y
            val dist = max(1.0, hypot(dx, dy))
            if (dist < 90) {
                val k = (1 - dist / 90).pow(2) * 46 * strength
                p = DotCloudDigitsPoint(p.x + dx / dist * k, p.y + dy / dist * k)
            }
        }
        val bump = sin(PI * s)
        val glint = if (reduce) 1.0 else 0.8 + 0.2 * max(0.0, cos(2 * PI * (i.toDouble() / PARTICLES - loopT / SLOT))).pow(6)
        val word = if (s < 0.5) words[state.a] else words[state.b]
        letter(word[i % word.size], p.x, p.y, 1 - 0.3 * bump, (1 - 0.5 * bump) * glint)
    }
}

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

// Geometry

internal data class DotCloudDigitsPoint(val x: Double, val y: Double)

private data class DotCloudDigitsStar(val x: Double, val y: Double, val scale: Double, val base: Double, val cycles: Int, val phase: Double)

/** Everything about the figures that depends only on the screen size (in dp). */
private class DotCloudDigitsShapes(stats: List<DotCloudDigitsStat>, typeface: Typeface, width: Float, height: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    val targets: List<List<DotCloudDigitsPoint>>
    val cloud: List<DotCloudDigitsPoint>
    val cloudCenter: DotCloudDigitsPoint
    val delay: List<Double>
    val stars: List<DotCloudDigitsStar>
    val words: List<List<Char>> = stats.map { s -> s.word.filter { it.isLetter() }.toList() }
    val captionTop: Float

    init {
        val outlines = stats.map { DotCloudDigitsMath.outline(it.value, typeface) }
        val maxW = outlines.maxOf { it.bounds.width() }.toDouble()
        val maxH = outlines.maxOf { it.bounds.height() }.toDouble()
        // The figures share one scale (dp per em) so that "12" is not bigger than "64".
        val scale = min((width - 64) / maxW, MAX_HEIGHT / maxH)
        val centerY = height * 0.45
        captionTop = (centerY + maxH * scale / 2 + 44).toFloat()
        targets = outlines.map { o ->
            val ox = width / 2 - o.bounds.centerX() * scale
            val oy = centerY - o.bounds.centerY() * scale
            DotCloudDigitsMath.sample(o.contours, PARTICLES).map { DotCloudDigitsPoint(ox + it.x * scale, oy + it.y * scale) }
        }
        val center = DotCloudDigitsPoint(width / 2.0, centerY + 70)
        cloudCenter = center
        cloud = (0 until PARTICLES).map { i ->
            val r = DotCloudDigitsMath.hash(i, 1)
            val a = DotCloudDigitsMath.hash(i, 2)
            val y = DotCloudDigitsMath.hash(i, 3)
            val spread = 0.35 + 0.65 * sqrt(r)
            DotCloudDigitsPoint(center.x + (a * 2 - 1) * (width * 0.44) * spread, center.y + (y * 2 - 1) * 48 * spread)
        }
        delay = (0 until PARTICLES).map { DotCloudDigitsMath.hash(it, 4) * MAX_DELAY }
        // Background letters below the header and anywhere but on the caption's band.
        val lo = captionTop - 12.0
        val hi = captionTop + 64.0
        stars = (0 until STARS).map { i ->
            var y = 104 + DotCloudDigitsMath.hash(i, 6) * (height - 164)
            if (y in lo..hi) y = if (y - lo < hi - y) lo - 4 - (y - lo) else hi + 4 + (hi - y)
            DotCloudDigitsStar(
                x = 12 + DotCloudDigitsMath.hash(i, 5) * (width - 24),
                y = y,
                scale = 0.8 + 0.25 * DotCloudDigitsMath.hash(i, 7),
                base = 0.12 + 0.18 * DotCloudDigitsMath.hash(i, 8),
                cycles = 1 + (DotCloudDigitsMath.hash(i, 9) * 3).toInt(),
                phase = DotCloudDigitsMath.hash(i, 10) * 2 * PI,
            )
        }
    }
}

internal object DotCloudDigitsMath {
    /** A seeded number in 0..<1 (the same 32-bit integer hash in every stack). */
    fun hash(i: Int, salt: Int): Double {
        var x = i * 374_761_393 + salt * 668_265_263
        x = (x xor (x ushr 13)) * 1_274_126_177
        x = x xor (x ushr 16)
        return (x.toLong() and 0xffffffffL).toDouble() / 4_294_967_296.0
    }

    fun ease(t: Double) = 1 - (1 - t).pow(3)

    fun easeInOut(t: Double) = if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

    /** A curve from [a] to [b] that passes the cloud point halfway, the cloud turning as it goes. */
    fun travel(a: DotCloudDigitsPoint, b: DotCloudDigitsPoint, cloud: DotCloudDigitsPoint, center: DotCloudDigitsPoint, s: Double): DotCloudDigitsPoint {
        // Turn the cloud point about the cloud's centre in a squashed space, so the band swirls flat.
        val angle = 0.55 * s
        val dx = cloud.x - center.x
        val dy = (cloud.y - center.y) * 3
        val rx = dx * cos(angle) - dy * sin(angle)
        val ry = dx * sin(angle) + dy * cos(angle)
        val cx = center.x + rx
        val cy = center.y + ry / 3
        val kx = 2 * cx - (a.x + b.x) / 2
        val ky = 2 * cy - (a.y + b.y) / 2
        val u = 1 - s
        return DotCloudDigitsPoint(u * u * a.x + 2 * s * u * kx + s * s * b.x, u * u * a.y + 2 * s * u * ky + s * s * b.y)
    }

    class Outline(val contours: List<List<DotCloudDigitsPoint>>, val bounds: RectF)

    /** The figure's outline at 1 dp per em, y down, from the glyphs placed by their advances (no kerning). */
    fun outline(text: String, typeface: Typeface): Outline {
        val unit = 1000f
        val paint = Paint().apply { this.typeface = typeface; textSize = unit }
        val contours = mutableListOf<List<DotCloudDigitsPoint>>()
        var x = 0f
        for (ch in text) {
            val path = Path()
            paint.getTextPath(ch.toString(), 0, 1, x, 0f, path)
            contours += flatten(path, unit)
            x += paint.measureText(ch.toString())
        }
        val all = contours.flatten()
        val bounds = RectF(
            all.minOf { it.x }.toFloat(), all.minOf { it.y }.toFloat(),
            all.maxOf { it.x }.toFloat(), all.maxOf { it.y }.toFloat(),
        )
        return Outline(contours, bounds)
    }

    /** Polylines of a path's contours (about 1/1000 em steps), each closed back to its start. */
    private fun flatten(path: Path, unit: Float): List<List<DotCloudDigitsPoint>> {
        val measure = PathMeasure(path, true)
        val result = mutableListOf<List<DotCloudDigitsPoint>>()
        val pos = FloatArray(2)
        do {
            val length = measure.length
            if (length < 1f) continue
            val steps = max(2, (length / 4f).toInt())
            result += (0..steps).map { k ->
                measure.getPosTan(length * k / steps, pos, null)
                DotCloudDigitsPoint(pos[0] / unit.toDouble(), pos[1] / unit.toDouble())
            }
        } while (measure.nextContour())
        return result
    }

    /**
     * [count] points spread evenly along all the contours: each gets its share of the total length
     * (rounded; the longest takes the remainder), and its points sit in the middle of equal steps.
     */
    fun sample(contours: List<List<DotCloudDigitsPoint>>, count: Int): List<DotCloudDigitsPoint> {
        val lengths = contours.map(::length)
        val total = lengths.sum()
        val counts = lengths.map { (it / total * count).roundToInt() }.toMutableList()
        val longest = lengths.indices.maxBy { lengths[it] }
        counts[longest] += count - counts.sum()
        val points = mutableListOf<DotCloudDigitsPoint>()
        contours.forEachIndexed { ci, c ->
            if (counts[ci] <= 0) return@forEachIndexed
            val step = lengths[ci] / counts[ci]
            for (k in 0 until counts[ci]) points += point(c, (k + 0.5) * step)
        }
        return points
    }

    private fun length(c: List<DotCloudDigitsPoint>): Double =
        (0 until c.size - 1).sumOf { hypot(c[it + 1].x - c[it].x, c[it + 1].y - c[it].y) }

    private fun point(c: List<DotCloudDigitsPoint>, distance: Double): DotCloudDigitsPoint {
        var walked = 0.0
        for (j in 0 until c.size - 1) {
            val seg = hypot(c[j + 1].x - c[j].x, c[j + 1].y - c[j].y)
            if (walked + seg >= distance && seg > 0) {
                val t = (distance - walked) / seg
                return DotCloudDigitsPoint(c[j].x + (c[j + 1].x - c[j].x) * t, c[j].y + (c[j + 1].y - c[j].y) * t)
            }
            walked += seg
        }
        return c.last()
    }
}
