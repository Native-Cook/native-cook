package com.ngocdevv.nativecook.blocks.playground.canopybeathop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

private const val LOOP = 10.0
private val LANES = doubleArrayOf(0.0, .45, -.45, -.8, 0.0, .8, .85, -.85, .85, -.85, .85, -.85, .85, 0.0, -.65, .65, -1.6, 0.0, 1.6, 0.0, -1.6, 0.0, .45, 0.0)
private val FACES = intArrayOf(0x8CFFCB, 0x91CBFF, 0xF58CF4, 0xFFE395, 0x8CFFCB)
private val NEONS = intArrayOf(0x00FFAB, 0x2AAEFF, 0xF82AEB, 0xFFC22D, 0x00FFAB)
private val GROUNDS = intArrayOf(0x0FB36B, 0x165FA5, 0xA00E9B, 0xAC7417, 0x0FB36B)
private fun smooth(value: Double): Double { val f = value.coerceIn(0.0, 1.0); return f * f * (3 - 2 * f) }
private fun index(n: Int) = (n % 24 + 24) % 24
private fun lane(n: Int) = LANES[index(n)]
private fun travelAt(time: Double): Double {
    val q = ((time - .75) / 8.5).coerceIn(0.0, 1.0); val r = .12 / 8.5
    val integrated = when { q < r -> q * q / (2 * r); q > 1 - r -> 1 - r - (1 - q).pow(2) / (2 * r); else -> q - r / 2 }
    return integrated / (1 - r) * 24
}
private fun opaque(hex: Int) = hex or (0xFF shl 24)
private fun alpha(color: Int, value: Double) = (color and 0xFFFFFF) or ((value.coerceIn(0.0, 1.0) * 255).toInt() shl 24)
private fun blend(a: Int, b: Int, f: Double): Int {
    fun channel(shift: Int) = (((a shr shift) and 255) + (((b shr shift) and 255) - ((a shr shift) and 255)) * f).toInt()
    return NativeColor.rgb(channel(16), channel(8), channel(0))
}

/** Cached artwork; the frame clock invalidates only the drawing surface. */
internal class CanopyBeatHopDrawing(context: Context) {
    private val art = listOf("day", "night", "violet", "amber").map { load(context, it) }
    private val courier = load(context, "courier")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val font = Typeface.create("sans-serif-rounded", Typeface.BOLD)
    private fun load(context: Context, name: String): Bitmap = context.assets.open("canopybeathop/canopybeathop_$name.png").use { BitmapFactory.decodeStream(it) }
    private fun reset(color: Int, style: Paint.Style = Paint.Style.FILL, width: Float = 1f) {
        paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true
        paint.color = color; paint.style = style; paint.strokeWidth = width
    }
    private fun tile(x: Double, y: Double, s: Double): Path {
        val pts = arrayOf(doubleArrayOf(x - 85 * s, y - 60 * s), doubleArrayOf(x + 85 * s, y - 60 * s), doubleArrayOf(x + 104 * s, y + 60 * s), doubleArrayOf(x - 104 * s, y + 60 * s))
        return Path().apply {
            for (i in 0..3) {
                val a = pts[(i + 3) % 4]; val b = pts[i]; val c = pts[(i + 1) % 4]
                val ex = (b[0] + (a[0] - b[0]) * .07).toFloat(); val ey = (b[1] + (a[1] - b[1]) * .07).toFloat()
                if (i == 0) moveTo(ex, ey) else lineTo(ex, ey)
                quadTo(b[0].toFloat(), b[1].toFloat(), (b[0] + (c[0] - b[0]) * .07).toFloat(), (b[1] + (c[1] - b[1]) * .07).toFloat())
            }
            close()
        }
    }
    private fun diamond(x: Double, y: Double, w: Double, h: Double) = Path().apply {
        moveTo(x.toFloat(), (y - h).toFloat()); lineTo((x + w).toFloat(), y.toFloat()); lineTo(x.toFloat(), (y + h).toFloat()); lineTo((x - w).toFloat(), y.toFloat()); close()
    }
    private fun text(canvas: NativeCanvas, value: String, x: Double, y: Double, size: Double, color: Int) {
        reset(color); paint.typeface = font; paint.textSize = size.toFloat(); paint.textAlign = Paint.Align.CENTER
        val metrics = paint.fontMetrics
        canvas.drawText(value, x.toFloat(), (y - (metrics.ascent + metrics.descent) / 2).toFloat(), paint)
    }
    private fun outlinedLabel(canvas: NativeCanvas, value: String, x: Double, y: Double, size: Double, ink: Int, opacity: Double) {
        text(canvas, value, x + 1.5, y + 3, size, alpha(NativeColor.BLACK, .25 * opacity))
        for (j in 0..7) { val angle = j * PI / 4; text(canvas, value, x + cos(angle) * 1.5, y + sin(angle) * 1.5, size, alpha(NativeColor.WHITE, opacity)) }
        text(canvas, value, x, y, size, alpha(ink, opacity))
    }
    private fun drawTile(canvas: NativeCanvas, x: Double, y: Double, s: Double, active: Boolean, neon: Int, face: Int, ground: Int, distance: Double) {
        val top = tile(x, y, s)
        val side = Path().apply {
            moveTo((x - 103 * s).toFloat(), (y + 57 * s).toFloat()); lineTo((x + 103 * s).toFloat(), (y + 57 * s).toFloat())
            lineTo((x + 101 * s).toFloat(), (y + 102 * s).toFloat()); lineTo((x - 101 * s).toFloat(), (y + 102 * s).toFloat()); close()
        }
        reset(NativeColor.WHITE)
        paint.shader = LinearGradient(x.toFloat(), (y + 50 * s).toFloat(), x.toFloat(), (y + 105 * s).toFloat(), intArrayOf(if (active) alpha(face, .8) else alpha(neon, .3), alpha(neon, .28), alpha(ground, 0.0)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(side, paint)
        reset(if (active) face else ground); paint.setShadowLayer((8 * s).toFloat(), 0f, 0f, alpha(neon, if (active) .9 else .5)); canvas.drawPath(top, paint)
        reset(alpha(neon, .8), Paint.Style.STROKE, (6 * s).toFloat()); paint.setShadowLayer((8 * s).toFloat(), 0f, 0f, alpha(neon, if (active) .9 else .5)); canvas.drawPath(top, paint)
        reset(opaque(0xEBFFFC), Paint.Style.STROKE, (2.8 * s).toFloat()); canvas.drawPath(top, paint)
        if (distance < -.04) for (j in 1..2) {
            reset(alpha(neon, ((.43 + distance * .3) / j).coerceAtLeast(0.0)), Paint.Style.STROKE, (2 * s).toFloat())
            paint.maskFilter = android.graphics.BlurMaskFilter((1.2 * s).toFloat(), android.graphics.BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(tile(x, y, s * (1 + j * .26)), paint)
        }
    }
    private fun drawCourier(canvas: NativeCanvas, x: Double, jump: Double, squash: Double, lean: Double) {
        val sw = 55 * (1 - jump * .3)
        reset(NativeColor.BLACK)
        paint.shader = RadialGradient(0f, 0f, (sw / 2).toFloat(), intArrayOf(alpha(NativeColor.BLACK, .27 - jump * .17), alpha(NativeColor.BLACK, .27 - jump * .17), NativeColor.TRANSPARENT), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        canvas.save(); canvas.translate(x.toFloat(), 621.5f); canvas.scale(1f, (13 / sw).toFloat()); canvas.drawCircle(0f, 0f, (sw / 2).toFloat(), paint); canvas.restore()
        canvas.save(); canvas.translate(x.toFloat(), (615 - jump * 170).toFloat()); canvas.rotate((lean * 180 / PI).toFloat())
        val scale = .89 + jump * .11
        canvas.scale((scale * (1 + squash * .5)).toFloat(), (scale * (1 - squash)).toFloat())
        reset(NativeColor.WHITE); canvas.drawBitmap(courier, null, RectF(-50f, -143f, 50f, 0f), paint); canvas.restore()
    }

    fun draw(canvas: NativeCanvas, time: Double, manualTravel: Double? = null, steering: Double? = null, still: Boolean = false, veil: Double = 0.0) {
        val theme: Int; val mix: Double
        when { time < 2.15 -> { theme = 0; mix = 0.0 }; time < 4.15 -> { theme = 0; mix = smooth((time - 2.15) / .35) }; time < 6.15 -> { theme = 1; mix = smooth((time - 4.15) / .35) }; time < 8.15 -> { theme = 2; mix = smooth((time - 6.15) / .35) }; else -> { theme = 3; mix = smooth((time - 8.15) / .35) } }
        reset(NativeColor.WHITE); canvas.drawBitmap(art[theme % 4], null, RectF(0f, 0f, 393f, 852f), paint)
        paint.alpha = (mix * 255).toInt(); canvas.drawBitmap(art[(theme + 1) % 4], null, RectF(0f, 0f, 393f, 852f), paint)
        val neon = blend(NEONS[theme], NEONS[theme + 1], mix); val face = blend(FACES[theme], FACES[theme + 1], mix); val ground = blend(GROUNDS[theme], GROUNDS[theme + 1], mix)
        val p = manualTravel ?: travelAt(time); val step = floor(p).toInt(); val f = p - floor(p)
        val resting = manualTravel == null && (time <= .75 || time >= 9.25)
        val jump = if (still || resting) 0.0 else 4 * f * (1 - f)
        val current = lane(step); val next = lane(step + 1); val playerLane = steering ?: (current + (next - current) * smooth(f))
        val playerX = 196.5 + playerLane * 100
        for (i in step + 6 downTo step - 2) {
            val d = i - p; if (d < -1.7 || d > 5.5) continue
            val s = if (d < 0) 1 - .8 * d else 1 / (1 + .8 * d); val y = if (d < 0) 615 - 270 * d else 225 + 390 * s - 15 * d; val n = index(i); val selected = lane(i)
            val positions = when (n) { in 5..10 -> doubleArrayOf(selected, selected + if (selected > 0) -1.9 else 1.9); in 16..21 -> doubleArrayOf(-1.6, 0.0, 1.6); else -> doubleArrayOf(selected) }
            for (position in positions) {
                val x = 196.5 + position * 100 * s; val active = kotlin.math.abs(position - selected) < .01; val hit = d <= .02
                val opacity = 1 - smooth((d - 4.8) / .7)
                if (opacity < 1) canvas.saveLayerAlpha(null, (opacity * 255).toInt()) else canvas.save()
                drawTile(canvas, x, y, s, active && !hit, neon, face, ground, d)
                if (active && !hit) {
                    if (n % 5 == 3 && d > .15) {
                        val cy = y - 22 * s; val w = 7 * s; val h = 17 * s
                        reset(NativeColor.WHITE); paint.shader = LinearGradient((x - w).toFloat(), (cy - h).toFloat(), (x + w).toFloat(), (cy + h).toFloat(), opaque(0xBCFFFF), opaque(0x147AFF), Shader.TileMode.CLAMP)
                        canvas.drawPath(diamond(x, cy, w, h), paint)
                        val facet = Path().apply { moveTo(x.toFloat(), (cy - h).toFloat()); lineTo(x.toFloat(), (cy + h).toFloat()); lineTo((x - w).toFloat(), cy.toFloat()); close() }
                        reset(alpha(opaque(0x9BEEFF), .7)); canvas.drawPath(facet, paint)
                    }
                    if (n % 4 == 1) { reset(alpha(NativeColor.WHITE, .5)); canvas.drawPath(diamond(x, y, 22 * s, 9 * s), paint) }
                }
                canvas.restore()
            }
        }
        val squash = if (still || resting) 0.0 else exp(-f * 22) * cos(f * 22) * .06
        val lean = if (still || resting) 0.0 else if (steering == null) (next - current) * sin(f * PI) * .05 else playerLane * .035
        drawCourier(canvas, playerX, jump, squash, lean)
        if (!still && !resting && step % 5 == 3 && f < .75) {
            val life = (f / .75).coerceAtMost(1.0); val radius = 46 * (1 - (1 - life).pow(3))
            for (j in 0..11) { val a = j * PI / 6; reset(alpha(opaque(if (j % 2 == 0) 0xD5FFFF else 0x63B9FF), 1 - life)); canvas.drawPath(diamond(playerX + cos(a) * radius, 533 - jump * 100 + sin(a) * radius * .65 - life * 20, 2.5 * (1 - life), 4 * (1 - life)), paint) }
        }
        val n = index(step); val perfect = !resting && n in intArrayOf(2, 3, 4, 9, 10, 18)
        val combo = if (n in 2..4) n - 1 else if (n in 9..10) n - 8 else 1
        if (perfect && !still) {
            val flash = .85 * exp(-f * 11)
            reset(NativeColor.WHITE); paint.shader = RadialGradient(196.5f, 86f, 83f, intArrayOf(alpha(NativeColor.WHITE, flash), alpha(NativeColor.WHITE, flash * .5), NativeColor.TRANSPARENT), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawOval(RectF(114f, 4f, 279f, 169f), paint)
        }
        val opacity = if (resting) 1.0 else .72 + .28 * exp(-f * 8)
        val size = (if (perfect) 39.0 else 34.0) * if (resting) 1.0 else 1 + .08 * exp(-f * 12)
        outlinedLabel(canvas, if (perfect) "PERFECT" else "GREAT", 196.5, 86.0, size, opaque(if (perfect) 0xF020D9 else 0x159FD0), opacity)
        if (perfect) outlinedLabel(canvas, "X$combo", 196.5, 133.0, 19.0, opaque(0xF43CE4), opacity)
        if (veil > 0) { reset(alpha(opaque(0x085E4F), veil)); canvas.drawRect(0f, 0f, 393f, 852f, paint) }
    }
}

private class CanopyBeatHopClock {
    val tick = mutableDoubleStateOf(0.0)
    var epoch = 0.0; var active = true; var restart = false; var now = 0.0; var resumeJob: Job? = null
    var manualStart: Double? = null; var manualTravel = 0.0; var lastTouch = -100.0
    var steering = 0.0; var dragging = false; var releaseTime = 0.0; var releaseVelocity = 0.0; var resuming = false
}

/** Same 10 s course and 393 × 852 model space as the Swift reference. */
@Composable
fun CanopyBeatHop(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current; val view = LocalView.current
    val reduced = rememberReducedMotion(); val scene = remember(context) { CanopyBeatHopDrawing(context) }; val state = remember { CanopyBeatHopClock() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) state.active = false
            if (event == Lifecycle.Event.ON_RESUME) { state.active = true; state.restart = true; state.manualStart = null }
        }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer); state.resumeJob?.cancel() }
    }
    LaunchedEffect(autoPlay, reduced, previewTime) {
        if (previewTime != null || reduced) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val time = (now - start) / 1e9
                state.now = time
                if (!state.active) { state.epoch = time; return@withFrameNanos }
                if (state.restart) { state.epoch = time; state.manualStart = null; state.resuming = false; state.restart = false }
                if (state.manualStart != null && !state.dragging && time - state.lastTouch >= 2.25) { state.manualStart = null; state.epoch = time; state.resuming = true }
                val needsFrame = autoPlay || state.manualStart != null || state.resuming
                if (state.resuming && time - state.epoch >= .25) state.resuming = false
                if (needsFrame) state.tick.doubleValue = time
            }
        }
    }
    fun steer(value: Double) {
        state.resumeJob?.cancel()
        val now = state.now
        if (state.manualStart == null) { state.manualTravel = travelAt(if (autoPlay) (now - state.epoch) % LOOP else 0.0); state.manualStart = now }
        state.steering = value.coerceIn(-1.35, 1.35); state.lastTouch = now
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (reduced) state.tick.doubleValue += .000001
    }
    fun resumeReduced() {
        state.resumeJob?.cancel()
        state.resumeJob = scope.launch { delay(2250); state.manualStart = null; state.resuming = false; state.tick.doubleValue += .000001 }
    }
    Canvas(Modifier.fillMaxSize().semantics {
        contentDescription = "Canopy Beat Hop game. Drag left or right to steer. Tap to hop."
        customActions = listOf(CustomAccessibilityAction("Move Left") { steer(-.75); if (reduced) resumeReduced(); true }, CustomAccessibilityAction("Move Right") { steer(.75); if (reduced) resumeReduced(); true }, CustomAccessibilityAction("Hop") { steer(0.0); if (reduced) resumeReduced(); true })
    }.pointerInput(reduced, autoPlay) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            steer((down.position.x / size.width * 393 - 196.5) / 100)
            state.dragging = true
            var lastX = down.position.x; var lastUptime = down.uptimeMillis; var velocity = 0.0
            do {
                val event = awaitPointerEvent(); val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dt = (change.uptimeMillis - lastUptime).coerceAtLeast(1) / 1000.0
                if (change.pressed) {
                    velocity = ((change.position.x - lastX) / size.width * 393 / 100 / dt).coerceIn(-3.0, 3.0)
                } else if (change.uptimeMillis - lastUptime > 100) {
                    velocity = 0.0
                }
                lastX = change.position.x; lastUptime = change.uptimeMillis
                state.steering = ((lastX / size.width * 393 - 196.5) / 100).coerceIn(-1.35, 1.35)
                state.lastTouch = state.now
                if (reduced) state.tick.doubleValue += .000001
                if (change.pressed) change.consume()
            } while (event.changes.any { it.id == down.id && it.pressed })
            state.dragging = false; state.releaseTime = state.now; state.lastTouch = state.now; state.releaseVelocity = velocity
            if (reduced) resumeReduced()
        }
    }) {
        val now = state.tick.doubleValue; val elapsed = (now - state.epoch).coerceAtLeast(0.0)
        val time = if (reduced) 0.0 else previewTime ?: if (autoPlay) elapsed % LOOP else 0.0
        val manual = state.manualStart?.let { state.manualTravel + if (reduced) 0.0 else (now - it) * (24 / 8.38) }
        val releaseAge = (now - state.releaseTime).coerceAtLeast(0.0)
        val lane = if (state.manualStart == null) null else state.steering + if (state.dragging || reduced) 0.0 else state.releaseVelocity * releaseAge * exp(-10 * releaseAge)
        val veil = if (manual == null) { if (state.resuming) 1 - smooth(elapsed / .25) else 0.0 } else if (state.dragging) 0.0 else smooth((now - state.lastTouch - 2) / .25)
        drawIntoCanvas { target ->
            val canvas = target.nativeCanvas; canvas.save(); canvas.scale(size.width / 393, size.height / 852)
            scene.draw(canvas, time, manual, lane, reduced, veil); canvas.restore()
        }
    }
}
