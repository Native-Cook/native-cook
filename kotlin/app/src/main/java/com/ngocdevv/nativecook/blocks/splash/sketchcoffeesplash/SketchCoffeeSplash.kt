package com.ngocdevv.nativecook.blocks.splash.sketchcoffeesplash

import android.app.Activity
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Sketch Coffee Splash: the launch of an invented coffee shop, Inkpot Coffee Co., drawn in white ink on
// ultramarine. Every line is a hand-drawn ribbon (uneven width, tapered ends) that "boils": it is redrawn
// with a slightly different wobble 8 times a second, from four fixed variants.
//
// Timeline of one 10 s loop (autoPlay), the same as the Swift version, t in seconds:
//   0.00-1.20  the welcome at rest: title, a tilted paper cup with coffee splashing out, drops bobbing.
//   1.20-1.90  it leaves: button shrinks to a dot and goes, title slides off right, notes left, the cup
//              drops away to the bottom right, turning.
//   2.20-2.60  a top-view cup pops up from a dot (back-out), "0%" fades in.
//   2.50-3.10  the arm slides in from the bottom right holding a spoon.
//   2.95-6.95  the spoon stirs (0.85 s a turn, eased in and out over 0.4 s); the percentage counts 1 to 100
//              from 3.0 to 6.55.
//   6.70-7.15  the hand pulls out; 7.00-7.30 the cup shrinks away.
//   7.30-7.68  the button comes back; 7.40-8.20 the title and notes wipe back in.
//   7.50-8.40  the paper cup bounces in on a damped spring, the splash grows out and the drops fly out.
//   8.40-10.0  at rest again, identical to t = 0.

private const val LOOP = 10.0

private object Script {
    const val EXIT_AT = 1.2
    const val CUP_IN_AT = 2.2
    const val HAND_IN_AT = 2.5
    const val STIR_FROM = 2.95
    const val STIR_TO = 6.55
    const val RAMP = 0.4
    const val PCT_FROM = 3.0
    const val PCT_TO = 6.55
    const val SCRUB_FROM = 3.1
    const val SKIP_TO = 6.6
    const val HAND_OUT_AT = 6.7
    const val CUP_OUT_AT = 7.0
    const val BUTTON_IN_AT = 7.3
    const val TITLE_IN_AT = 7.4
    const val NOTES_IN_AT = 7.45
    const val CUP_BACK_AT = 7.5
    const val SPLASH_AT = 7.75
    const val DROPS_AT = 7.8
    const val REST_AT = 8.4
    /** The spoon's speed, radians a second (0.85 s a turn). */
    const val OMEGA = 2 * PI / 0.85
    const val BOIL_RATE = 8.0
    const val REST_TILT = -14.0

    fun clamp01(v: Double) = min(1.0, max(0.0, v))
    fun span(t: Double, from: Double, length: Double) = clamp01((t - from) / length)
    fun easeIn(u: Double) = u * u * u
    fun easeOut(u: Double) = 1 - (1 - u).pow(3)
    fun easeOutQuint(u: Double) = 1 - (1 - u).pow(5)
    fun backOut(u: Double, s: Double = 1.7): Double {
        val v = u - 1
        return 1 + (s + 1) * v * v * v + s * v * v
    }
    fun backIn(u: Double, s: Double = 1.7) = (s + 1) * u * u * u - s * u * u

    /** A damped spring from 0 to 1 that overshoots by about 8% and settles exactly at u = 1. */
    fun spring(u: Double): Double {
        val c = clamp01(u)
        return 1 - exp(-5 * c) * cos(2.5 * PI * c) * (1 - c)
    }

    /** How far the spoon has turned (seconds at full speed), with its speed ramped in and out. */
    fun stirTravel(t: Double): Double {
        val a = STIR_FROM
        val b = STIR_TO
        val r = RAMP
        return when {
            t < a -> 0.0
            t < a + r -> (t - a) * (t - a) / (2 * r)
            t < b -> r / 2 + (t - a - r)
            t < b + r -> r / 2 + (b - a - r) + (t - b) - (t - b) * (t - b) / (2 * r)
            else -> b - a
        }
    }

    fun stirSpeed(t: Double) = clamp01((t - STIR_FROM) / RAMP) * (1 - clamp01((t - STIR_TO) / RAMP))
    fun percent(t: Double) = 100 * clamp01((t - PCT_FROM) / (PCT_TO - PCT_FROM))
}

/**
 * The script's time, worked out from a start time rather than added up frame by frame, so the loop keeps
 * a constant period; a touch moves the start or holds the time. Not Compose state: only the draw reads it.
 */
private class SketchCoffeeClock {
    var now = 0.0
    private var origin = Double.NaN
    private var running = false
    var held: Double? = null
        private set
    private var lastAngle: Double? = null
    private var scrubbing = false

    fun time(autoPlay: Boolean): Double {
        held?.let { return it }
        if (origin.isNaN()) origin = now
        val e = max(0.0, now - origin)
        if (autoPlay) return e % LOOP
        if (running && e < LOOP) return e
        return 0.0
    }

    private fun jump(t: Double) {
        origin = now - t
        running = true
        held = null
    }

    /** Returns true when the stirring crossed another 10%. */
    fun dragChanged(px: Double, py: Double, sx: Double, sy: Double, cx: Double, cy: Double, autoPlay: Boolean): Boolean {
        val t = held ?: time(autoPlay)
        if (!scrubbing) {
            val near = hypot(sx - cx, sy - cy) < 150
            if (!near || t < Script.SCRUB_FROM || t >= Script.PCT_TO || hypot(px - sx, py - sy) <= 6) return false
            scrubbing = true
            held = t
            lastAngle = atan2(py - cy, px - cx)
            return false
        }
        val a = atan2(py - cy, px - cx)
        val last = lastAngle
        val h = held
        lastAngle = a
        if (last == null || h == null) return false
        var d = a - last
        if (d > PI) d -= 2 * PI
        if (d < -PI) d += 2 * PI
        if (d <= 0) return false
        val next = min(Script.PCT_TO, h + d / Script.OMEGA)
        held = next
        return (Script.percent(next) / 10).toInt() != (Script.percent(h) / 10).toInt()
    }

    /** Returns true when the touch was a tap that did something. */
    fun dragEnded(dx: Double, dy: Double, autoPlay: Boolean): Boolean {
        if (scrubbing) {
            scrubbing = false
            lastAngle = null
            jump(held ?: Script.SCRUB_FROM)
            return false
        }
        if (hypot(dx, dy) >= 10) return false
        val t = time(autoPlay)
        if (t < Script.EXIT_AT || t >= Script.REST_AT) {
            jump(Script.EXIT_AT)
            return true
        }
        if (t >= Script.CUP_IN_AT && t < Script.SKIP_TO) {
            jump(Script.SKIP_TO)
            return true
        }
        return false
    }
}

@Composable
fun SketchCoffeeSplash(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    fun reducedNow() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember { mutableStateOf(reducedNow()) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            reduced = reducedNow()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // White status bar icons on the blue.
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }

    val clock = remember { SketchCoffeeClock() }
    val painter = remember { SketchCoffeeSplashPainter(context) }
    // Bumped every frame and read only by the draw, so a frame redraws the canvas and nothing else.
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, reduced) {
        if (!active || reduced) return@LaunchedEffect
        while (true) {
            withFrameNanos { nanos ->
                clock.now = nanos / 1e9
                frame = nanos
            }
        }
    }
    val top = WindowInsets.statusBars.getTop(density) / density.density
    val bottom = WindowInsets.navigationBars.getBottom(density) / density.density

    Canvas(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = "Inkpot Coffee Co. Welcome to our coffee haven! 12 Linden Row, Old Town. Open 7 am to 8 pm: coffee, milkshakes, desserts." }
            .pointerInput(reduced, autoPlay) {
                if (reduced) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val d = density.density.toDouble()
                    val sx = down.position.x / d
                    val sy = down.position.y / d
                    val cx = size.width / d / 2
                    val cy = size.height / d * 0.5
                    var last = down.position
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        last = change.position
                        if (clock.dragChanged(last.x / d, last.y / d, sx, sy, cx, cy, autoPlay)) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        change.consume()
                    } while (event.changes.any { it.pressed })
                    if (clock.dragEnded((last.x - down.position.x) / d, (last.y - down.position.y) / d, autoPlay)) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                }
            },
    ) {
        if (frame < 0L) return@Canvas
        val d = density.density
        val canvas = drawContext.canvas.nativeCanvas
        canvas.drawColor(SketchCoffeeSplashPainter.GROUND)
        val t = if (reduced) 0.0 else clock.time(autoPlay)
        // The boil runs on the script's clock while it loops, so a recorded loop repeats exactly.
        val ambient = when {
            reduced -> 0.0
            autoPlay || clock.held != null -> t
            else -> clock.now % 1000
        }
        val layout = SketchCoffeeSplashLayout(size.width / d.toDouble(), size.height / d.toDouble(), top.toDouble(), bottom.toDouble())
        canvas.save()
        canvas.scale(d, d)
        painter.draw(canvas, t, ambient, reduced, layout)
        canvas.restore()
    }
}

internal class SketchCoffeeSplashLayout(val width: Double, val height: Double, val top: Double, val bottom: Double) {
    val cupX get() = width / 2
    val cupY get() = height * 0.5
    val rimX get() = width * 0.656
    val rimY get() = height - 258
    val buttonX get() = 38.0
    val buttonY get() = height - 52
}

private class SketchCoffeeSplashPainter(context: android.content.Context) {
    companion object {
        const val GROUND = 0xFF2641A8.toInt()
        const val INK = 0xFFFFFFFF.toInt()
        const val SHADOW = 0xFFB2D6F6.toInt()
    }

    private val hand: Typeface = Typeface.createFromAsset(context.assets, "sketchcoffeesplash/sketchcoffeesplash_CoveredByYourGrace.ttf")
    private val pen: Typeface = Typeface.createFromAsset(context.assets, "sketchcoffeesplash/sketchcoffeesplash_ReenieBeanie.ttf")
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val A = SketchCoffeeSplashArt
    private val K = SketchCoffeeSplashSketch

    fun draw(canvas: NativeCanvas, t: Double, ambient: Double, still: Boolean, layout: SketchCoffeeSplashLayout) {
        val variant = if (still) 0 else floor(ambient * Script.BOIL_RATE).toInt() % K.VARIANTS
        drawWelcomeArt(canvas, t, if (still) 0.0 else ambient, variant, layout)
        drawWelcomeText(canvas, t, layout)
        drawButton(canvas, t, layout)
        drawLoader(canvas, t, variant, layout)
    }

    // ---- Welcome ----

    /** Where the paper cup is (x, y) and how far it is turned (degrees), leaving and coming back. */
    private fun cupPlacement(t: Double, l: SketchCoffeeSplashLayout): DoubleArray {
        if (t < 5) {
            val u = Script.easeIn(Script.span(t, Script.EXIT_AT, 0.65))
            val lift = 14 * sin(PI * Script.span(t, Script.EXIT_AT, 0.3))
            return doubleArrayOf(l.rimX + l.width * 0.8 * u, l.rimY + l.height * 0.34 * u - lift, Script.REST_TILT + 30 * u)
        }
        val p = Script.spring(Script.span(t, Script.CUP_BACK_AT, 0.9))
        val fx = l.rimX + l.width * 0.62
        val fy = l.rimY + l.height * 0.36
        return doubleArrayOf(fx + (l.rimX - fx) * p, fy + (l.rimY - fy) * p, Script.REST_TILT + 32 * (1 - p))
    }

    private fun drawWelcomeArt(canvas: NativeCanvas, t: Double, ambient: Double, variant: Int, l: SketchCoffeeSplashLayout) {
        if (t >= 1.95 && t < Script.CUP_BACK_AT) return
        val place = cupPlacement(t, l)
        val back = t >= 5
        canvas.save()
        canvas.translate(place[0].toFloat(), place[1].toFloat())
        canvas.rotate(place[2].toFloat())

        // The splash grows out of the cup, from its left part, on its way back in.
        val grow = if (back) Script.spring(Script.span(t, Script.SPLASH_AT, 0.8)) else 1.0
        if (grow > 0.001) {
            val bx = -60f
            val by = -6f
            canvas.save()
            canvas.translate(bx, by)
            canvas.scale((0.55 + 0.45 * grow).toFloat(), grow.toFloat())
            canvas.translate(-bx, -by)
            K.draw(A.splashInk[variant], canvas, paint, GROUND, INK)
            canvas.restore()
        }

        // Drops: thrown out of the cup to their places, then bobbing.
        val lx = -40.0
        val ly = -20.0
        A.dropCentres.forEachIndexed { i, (cx, cy) ->
            val u = if (back) Script.span(t, Script.DROPS_AT + 0.04 * i, 0.6) else 1.0
            if (u <= 0) return@forEachIndexed
            val q = Script.backOut(u, 1.4)
            val bob = 2.4 * sin(2 * PI * ambient / 2.5 + i * 1.7)
            val px = lx + (cx - lx) * q
            val py = ly + (cy - ly) * q + bob
            val s = (0.35 + 0.65 * Script.easeOut(u)).toFloat()
            canvas.save()
            canvas.translate(px.toFloat(), py.toFloat())
            canvas.scale(s, s)
            canvas.translate(-cx.toFloat(), -cy.toFloat())
            K.draw(listOf(A.dropInk[variant][i]), canvas, paint, GROUND, INK)
            canvas.restore()
        }

        K.draw(A.cupInk[variant], canvas, paint, GROUND, INK)
        canvas.restore()
    }

    /** How far a block of text is pushed off screen, leaving and coming back. */
    private fun slide(t: Double, outAt: Double, inAt: Double, distance: Double): Double =
        if (t < 5) distance * Script.easeIn(Script.span(t, outAt, 0.5))
        else distance * (1 - Script.easeOutQuint(Script.span(t, inAt, 0.75)))

    /** Draws one line of text with its box's vertical centre at [y], left- or right-aligned at [x]. */
    private fun label(canvas: NativeCanvas, s: String, x: Double, y: Double, face: Typeface, size: Float, alpha: Double = 1.0, right: Boolean = false, spacing: Float = 0f, centre: Boolean = false) {
        text.typeface = face
        text.textSize = size
        text.letterSpacing = spacing
        text.color = INK
        text.alpha = (255 * Script.clamp01(alpha)).toInt()
        text.textAlign = when {
            centre -> Paint.Align.CENTER
            right -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
        val fm = text.fontMetrics
        val baseline = y - (fm.ascent + fm.descent) / 2
        canvas.drawText(s, x.toFloat(), baseline.toFloat(), text)
    }

    private fun drawWelcomeText(canvas: NativeCanvas, t: Double, l: SketchCoffeeSplashLayout) {
        if (t >= 1.95 && t < Script.TITLE_IN_AT) return
        val w = l.width
        val top = l.top
        val h = l.height
        fun dx(i: Int) = slide(t, Script.EXIT_AT + 0.05 + 0.04 * i, Script.TITLE_IN_AT + 0.05 * i, w * 1.1)
        label(canvas, "Welcome to", 20 + dx(0), top + 66, medium, 50f, spacing = -0.02f)
        label(canvas, "our coffee", w - 22 + dx(1), top + 116, medium, 50f, right = true, spacing = -0.02f)
        label(canvas, "haven!", w - 18 + dx(2), top + 176, hand, 78f, right = true)
        label(canvas, "Inkpot Coffee Co.", w - 203 + dx(3), top + 230, medium, 14f, alpha = 0.9)
        label(canvas, "12 Linden Row, Old Town", w - 203 + dx(4), top + 250, regular, 14f, alpha = 0.8)

        fun nx(i: Int) = slide(t, Script.EXIT_AT + 0.1 + 0.03 * i, Script.NOTES_IN_AT + 0.04 * i, -w * 0.6)
        // The pen line is thin: a second pass a hair to the right gives it a marker's weight.
        label(canvas, "7 am - 8 pm", 20 + nx(0), h - 232, pen, 26f)
        label(canvas, "7 am - 8 pm", 20.7 + nx(0), h - 231.7, pen, 26f)
        label(canvas, "Coffee", 20 + nx(1), h - 203, regular, 15f, alpha = 0.9)
        label(canvas, "Milkshakes", 20 + nx(2), h - 179, regular, 15f, alpha = 0.9)
        label(canvas, "Desserts", 20 + nx(3), h - 155, regular, 15f, alpha = 0.9)
    }

    private fun drawButton(canvas: NativeCanvas, t: Double, l: SketchCoffeeSplashLayout) {
        val scale: Double
        val alpha: Double
        if (t < 5) {
            scale = 1 - 0.8 * Script.easeIn(Script.span(t, Script.EXIT_AT, 0.22))
            alpha = 1 - Script.span(t, Script.EXIT_AT + 0.22, 0.13)
        } else {
            alpha = Script.span(t, Script.BUTTON_IN_AT, 0.08)
            scale = 0.2 + 0.8 * Script.backOut(Script.span(t, Script.BUTTON_IN_AT + 0.08, 0.3), 2.2)
        }
        if (alpha <= 0.001 || scale <= 0.001) return
        val cx = l.buttonX.toFloat()
        val cy = l.buttonY.toFloat()
        val s = scale.toFloat()
        paint.color = INK
        paint.alpha = (255 * alpha).toInt()
        canvas.drawCircle(cx, cy, 24 * s, paint)
        val chevron = Script.clamp01((scale - 0.6) / 0.4)
        if (chevron > 0) {
            line.color = GROUND
            line.alpha = (255 * alpha * chevron).toInt()
            line.strokeWidth = 2.2f
            val p = Path()
            p.moveTo(cx - 4 * s, cy - 9 * s)
            p.lineTo(cx + 5 * s, cy)
            p.lineTo(cx - 4 * s, cy + 9 * s)
            canvas.drawPath(p, line)
        }
    }

    // ---- Loader ----

    private fun drawLoader(canvas: NativeCanvas, t: Double, variant: Int, l: SketchCoffeeSplashLayout) {
        if (t < Script.CUP_IN_AT || t >= Script.CUP_OUT_AT + 0.32) return
        val cx = l.cupX
        val cy = l.cupY
        val grow = Script.backOut(Script.span(t, Script.CUP_IN_AT, 0.4), 2.0)
        val shrink = 1 - Script.backIn(Script.span(t, Script.CUP_OUT_AT, 0.3), 2.2)
        val scale = max(0.0, grow * shrink)

        // The percentage.
        val pctAlpha = Script.span(t, Script.CUP_IN_AT + 0.15, 0.25) * (1 - Script.span(t, Script.CUP_OUT_AT, 0.25))
        if (pctAlpha > 0) {
            val s = "${Script.percent(t).toInt()}%"
            label(canvas, s, cx, cy + 96, pen, 40f, alpha = pctAlpha, spacing = 0.05f, centre = true)
            label(canvas, s, cx + 0.8, cy + 96.3, pen, 40f, alpha = pctAlpha, spacing = 0.05f, centre = true)
        }
        if (scale <= 0.001) return

        // The cup: a pale shadow, the cup itself (ground inside), its sketched ring and handle.
        val r = A.CUP_RADIUS
        canvas.save()
        canvas.translate(cx.toFloat(), cy.toFloat())
        canvas.scale(scale.toFloat(), scale.toFloat())
        paint.alpha = 255
        paint.color = SHADOW
        rect.set(-r - 2, -r + 6, r - 2, r + 6); canvas.drawOval(rect, paint)
        rect.set(r - 4, -2f, r + 18, 11f); canvas.drawRoundRect(rect, 5f, 5f, paint)
        paint.color = GROUND
        rect.set(-r, -r, r, r); canvas.drawOval(rect, paint)
        rect.set(r - 2, -6f, r + 18, 6f); canvas.drawRoundRect(rect, 5f, 5f, paint)
        K.draw(A.topInk[variant], canvas, paint, GROUND, INK)

        // The stir.
        val theta = PI + Script.OMEGA * Script.stirTravel(t)
        val swirlAlpha = Script.stirSpeed(t)
        if (swirlAlpha > 0.01) {
            canvas.save()
            canvas.rotate(Math.toDegrees(theta).toFloat())
            K.draw(A.swirlInk[variant], canvas, paint, GROUND, INK, (255 * swirlAlpha).toInt())
            canvas.restore()
        }
        canvas.restore()

        // The hand, arriving and leaving along its arm.
        val enter = 1 - Script.easeOut(Script.span(t, Script.HAND_IN_AT, 0.6))
        val leave = Script.easeIn(Script.span(t, Script.HAND_OUT_AT, 0.45))
        if (enter + leave >= 1) return
        val away = A.ARM_AWAY * (enter + leave)
        val relX = A.STIR_RADIUS * cos(theta)
        val relY = A.STIR_RADIUS * sin(theta)
        val tipX = cx + relX + A.ARM_DX * away
        val tipY = cy + relY + A.ARM_DY * away
        val pinchX = cx + A.PINCH_X + 0.35 * relX + A.ARM_DX * away
        val pinchY = cy + A.PINCH_Y + 0.35 * relY + A.ARM_DY * away
        // Keep the arm pointing at the same shoulder off screen.
        val shoulderX = cx + A.PINCH_X + A.ARM_DX * 420
        val shoulderY = cy + A.PINCH_Y + A.ARM_DY * 420
        val restAngle = atan2(A.ARM_DY.toDouble(), A.ARM_DX.toDouble())
        val nowAngle = atan2(shoulderY - pinchY, shoulderX - pinchX)
        val turn = Math.toDegrees((nowAngle - restAngle) * (1 - enter - leave)).toFloat()
        canvas.save()
        canvas.translate(pinchX.toFloat(), pinchY.toFloat())
        canvas.rotate(turn)
        K.draw(A.handInk[variant], canvas, paint, GROUND, INK)
        canvas.restore()

        // The spoon: its handle into the fist, and a small bowl in the coffee.
        val spoon = K.ribbon(doubleArrayOf(tipX, tipY, pinchX, pinchY), false, 4.2, 98, variant, 0.35)
        paint.color = INK
        paint.alpha = 255
        canvas.drawPath(K.path(spoon.outline), paint)
        val angle = Math.toDegrees(atan2(pinchY - tipY, pinchX - tipX)).toFloat()
        canvas.save()
        canvas.translate(tipX.toFloat(), tipY.toFloat())
        canvas.rotate(angle)
        rect.set(-5.5f, -4.2f, 5.5f, 4.2f)
        canvas.drawOval(rect, paint)
        canvas.restore()
        canvas.save()
        canvas.translate(pinchX.toFloat(), pinchY.toFloat())
        canvas.rotate(turn)
        K.draw(A.thumbInk[variant], canvas, paint, GROUND, INK)
        canvas.restore()
    }
}
