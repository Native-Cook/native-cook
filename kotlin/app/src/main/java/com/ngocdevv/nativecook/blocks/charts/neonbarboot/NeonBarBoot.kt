package com.ngocdevv.nativecook.blocks.charts.neonbarboot

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** One set of figures the card can show: the total, and the bars as fractions of the tallest. */
data class NeonBarBootSet(val total: Double, val bars: List<Double>)

val NeonBarBootSamples = listOf(
    NeonBarBootSet(29924.95, listOf(0.35, 0.63, 0.9, 0.8, 0.55, 0.62, 0.4, 0.62, 0.76, 0.82, 0.6, 0.55, 0.7, 0.78, 0.66, 0.54, 0.45)),
    NeonBarBootSet(31208.4, listOf(0.48, 0.52, 0.71, 0.93, 0.84, 0.58, 0.66, 0.5, 0.69, 0.88, 0.95, 0.72, 0.6, 0.67, 0.81, 0.74, 0.62)),
    NeonBarBootSet(27412.1, listOf(0.6, 0.44, 0.52, 0.68, 0.73, 0.49, 0.38, 0.57, 0.64, 0.53, 0.71, 0.86, 0.79, 0.58, 0.47, 0.6, 0.69)),
)

private object K {
    val BACKGROUND = Color(0xFF0C0C10)
    val SPOTLIGHT = rgba(74, 47, 166, 0.55f)
    val SPOTLIGHT_OUT = rgba(74, 47, 166, 0f)
    val CARD_FILL = rgba(255, 255, 255, 0.03f)
    val CARD_EDGE = rgba(91, 69, 201, 0.45f)
    val CARD_EDGE_BRIGHT = rgba(140, 110, 255, 0.75f)
    val CARD_GLOW = rgba(110, 70, 230, 0.22f)
    val CARD_GLOW_OUT = rgba(110, 70, 230, 0f)
    val BAR_TOP = Color(0xFF8A5BFF)
    val BAR_MIDDLE = Color(0xFF5B35D6)
    val BAR_TAIL = rgba(91, 53, 214, 0f)
    // React Native sets the glow paint's alpha outright, so only the colour of BAR_GLOW is used.
    val BAR_GLOW = rgba(160, 120, 255, 1f)
    val SWEEP = rgba(255, 255, 255, 0.1f)
    val VIOLET = Color(0xFF7B4DE0)
    val FROST = rgba(255, 255, 255, 0.25f)
    val CAPTION = Color(0xFFA9A6C0)
    val INK = Color.White
    val VALUE_GLOW = rgba(157, 123, 255, 0.45f)

    // Layout, in dp.
    const val CARD_WIDTH = 345f
    const val CARD_HEIGHT = 380f
    const val CARD_RADIUS = 24f
    const val CARD_PADDING = 28f
    const val BAR_WIDTH = 4f
    const val BAR_TALLEST = 190f
    // The bars' last share fades into the card.
    const val BAR_FADE = 0.15f
    const val SQUARE = 80f
    const val SQUARE_RADIUS = 18f
    // Where the frosted square rests over the violet one.
    const val OVERLAP = 24f
    const val FROST_BLUR = 12f
    const val SWEEP_WIDTH = 60f
    // How bright the tip of a bar lights up when it arrives.
    const val TIP_GLOW = 0.45
    const val SPOT_RADIUS = 330f
    // Skia blur sigmas of the bars' haze and the tips' glow.
    const val HAZE_BLUR = 4f
    const val TIP_BLUR = 5f
    const val HAZE_ALPHA = 0.35
    const val VALUE_SIZE = 30f
    const val VALUE_LINE = 38f
    const val VALUE_GLOW_RADIUS = 10f

    // Motion, in seconds since the boot started.
    const val WOBBLE_FOR = 0.8
    const val RISE_AT = 0.8
    const val RISE_FOR = 0.6
    const val CARD_AT = 0.8
    const val CARD_FOR = 0.6
    const val CARD_DROP = 120.0
    const val GROW_AT = 1.4
    const val GROW_FOR = 0.55
    const val GROW_GAP = 0.035
    // The bars overshoot their height by this share as they arrive.
    const val OVERSHOOT = 0.03
    const val NUMBER_AT = 1.4
    const val SWEEP_AT = 3.0
    const val SWEEP_FOR = 0.9
    // Breathing on the bar heights once they stand: amplitude, period, and the lag from bar to bar.
    const val BREATH = 0.03
    const val BREATH_PERIOD = 2.4
    const val BREATH_LAG = 0.12
    // A refresh moves every bar to its new height, a little after its left neighbour.
    const val REFRESH_FOR = 0.7
    const val REFRESH_GAP = 0.02
    // Seconds since the power-down started: bars sink right to left, the card drops, the squares return.
    const val SINK_FOR = 0.35
    const val SINK_GAP = 0.025
    const val CARD_OUT_AT = 0.35
    const val CARD_OUT_FOR = 0.45
    const val RETURN_AT = 0.6
    const val RETURN_FOR = 0.65
    const val DOWN_DONE = RETURN_AT + RETURN_FOR

    // The autoPlay timeline, in seconds from the start of each loop.
    const val LOOP = 9.0
    const val BOOT = 0.8
    const val REFRESH = 5.0
    const val POWER_DOWN = 7.4
    // Seconds after the last touch before the screen powers down and the loop starts again.
    const val USER_HOLD = 3.0
    // Without the loop the screen boots once, this long after it opens.
    const val FIRST_BOOT = 0.4

    fun clamp01(t: Double) = t.coerceIn(0.0, 1.0)
    fun easeOut(t: Double) = 1 - (1 - clamp01(t)).pow(3)
    fun easeIn(t: Double) = clamp01(t).pow(3)
    fun easeInOut(t: Double): Double {
        val x = clamp01(t)
        return if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    }

    /** Ease out with a small overshoot past 1 before settling. */
    fun arrive(t: Double, overshoot: Double): Double {
        val x = clamp01(t)
        val s = 1.70158 * (overshoot / 0.1)
        return 1 + (s + 1) * (x - 1).pow(3) + s * (x - 1).pow(2)
    }

    /** A kick that starts and ends at 0: a damped swing, as from a tap. */
    fun swing(t: Double): Double {
        if (t <= 0 || t >= WOBBLE_FOR) return 0.0
        return exp(-5 * t) * sin(t * TAU * 2.2)
    }

    fun money(value: Double, currency: String): String {
        val cents = (value * 100).roundToInt()
        val whole = (cents / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return "$currency$whole.${(cents % 100).toString().padStart(2, '0')}"
    }
}

/** When the boot, the last refresh and the power-down began, in seconds on the block's clock (-1: not). */
private data class Run(
    val bootAt: Double = -1.0,
    val refreshAt: Double = -1.0,
    val downAt: Double = -1.0,
    val fromSet: Int = 0,
    val toSet: Int = 0,
)

/** Paints made once and reused every frame. */
private class Paints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val frostBlur = Paint(Paint.ANTI_ALIAS_FLAG)
    val haze = Paint(Paint.ANTI_ALIAS_FLAG)
    val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    val roundPath = android.graphics.Path()
    val violet = Matrix()
    val frost = Matrix()
    val frostInverse = Matrix()

    init {
        // Hardware canvases honour mask filters from API 28; below, the glows draw crisp.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            frostBlur.maskFilter = BlurMaskFilter(sigmaToRadius(K.FROST_BLUR), BlurMaskFilter.Blur.NORMAL)
            // Skia's solid blur: the shape itself, with a blurred copy around it.
            haze.maskFilter = BlurMaskFilter(sigmaToRadius(K.HAZE_BLUR), BlurMaskFilter.Blur.SOLID)
            glow.maskFilter = BlurMaskFilter(sigmaToRadius(K.TIP_BLUR), BlurMaskFilter.Blur.SOLID)
        }
    }
}

/**
 * A dashboard boot: two app-icon squares wobble and fly up, a glass card rises with its border
 * drawing round, the total counts up digit by digit and seventeen neon bars grow in a wave, then
 * breathe. Tapping the card refreshes it with the next set of figures; tapping the squares boots it.
 */
@Composable
fun NeonBarBoot(
    modifier: Modifier = Modifier,
    title: String = "Total Sales",
    currency: String = "$",
    sets: List<NeonBarBootSet> = NeonBarBootSamples,
    /** Boots, refreshes once and powers down by itself every LOOP seconds. */
    autoPlay: Boolean = false,
    /** Called with the set the card moves to on a refresh. */
    onRefresh: ((NeonBarBootSet, Int) -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current.density
    val birth = remember { System.nanoTime() }
    val odometer = remember { NeonBarBootOdometer() }
    val paints = remember { Paints() }
    var run by remember { mutableStateOf(Run()) }
    var touches by remember { mutableIntStateOf(0) }
    var lastTouch by remember { mutableDoubleStateOf(0.0) }
    val time = remember { mutableDoubleStateOf(0.0) }
    // Read by the actions, which the loop and the tap handler call long after the first composition.
    val still by rememberUpdatedState(reduced)
    val currentSets by rememberUpdatedState(sets)
    val currentCurrency by rememberUpdatedState(currency)
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    fun clock() = (System.nanoTime() - birth) / 1e9

    fun boot(at: Double) {
        if (run.bootAt >= 0) return
        run = Run(bootAt = at)
        odometer.show(K.money(currentSets[0].total, currentCurrency), at, K.NUMBER_AT, fresh = true, still = still)
    }

    fun refresh(at: Double, byHand: Boolean) {
        val r = run
        if (r.bootAt < 0 || r.downAt >= 0 || at - r.bootAt < K.GROW_AT) return
        val next = (r.toSet + 1) % currentSets.size
        run = r.copy(fromSet = r.toSet, toSet = next, refreshAt = at)
        odometer.show(K.money(currentSets[next].total, currentCurrency), at, 0.0, fresh = false, still = still)
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        currentOnRefresh?.invoke(currentSets[next], next)
    }

    fun powerDown(at: Double) {
        if (run.bootAt < 0 || run.downAt >= 0) return
        run = run.copy(downAt = at)
    }

    fun settle(at: Double) {
        if (run.downAt >= 0 && at - run.downAt >= K.DOWN_DONE) run = Run()
    }

    // A tap on the squares boots the screen; a tap on the card refreshes it. Either takes over from the loop.
    fun press() {
        val now = clock()
        settle(now)
        if (run.bootAt < 0) boot(now) else refresh(now, byHand = true)
        lastTouch = now
        touches += 1
    }

    suspend fun waitUntil(at: Double) {
        while (true) {
            val left = at - clock()
            if (left <= 0) return
            delay((left * 1000).toLong().coerceAtLeast(1))
        }
    }

    // Each step is stamped with its time on the loop, not the time the coroutine woke, so every loop is identical.
    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) {
            if (touches == 0) {
                val at = clock() + K.FIRST_BOOT
                waitUntil(at)
                boot(at)
            }
            return@LaunchedEffect
        }
        var start = clock()
        if (touches > 0) {
            val down = lastTouch + K.USER_HOLD
            waitUntil(down)
            powerDown(down)
            start = down + K.DOWN_DONE + 0.2
            waitUntil(start)
            settle(start)
        }
        while (true) {
            waitUntil(start + K.BOOT)
            boot(start + K.BOOT)
            waitUntil(start + K.REFRESH)
            refresh(start + K.REFRESH, byHand = false)
            waitUntil(start + K.POWER_DOWN)
            powerDown(start + K.POWER_DOWN)
            start += K.LOOP
            waitUntil(start)
            settle(start)
        }
    }

    // A finished power-down leaves the screen at rest.
    LaunchedEffect(run.downAt) {
        val downAt = run.downAt
        if (downAt < 0) return@LaunchedEffect
        waitUntil(downAt + K.DOWN_DONE)
        settle(clock())
    }

    // The clock only runs while something is on screen.
    val booted = run.bootAt >= 0
    LaunchedEffect(booted) {
        if (!booted) return@LaunchedEffect
        while (true) withFrameNanos { time.doubleValue = (it - birth) / 1e9 }
    }

    fun sinceBoot(): Double = if (run.bootAt < 0) -1.0 else time.doubleValue - run.bootAt
    fun sinceDown(): Double = if (run.downAt < 0) -1.0 else time.doubleValue - run.downAt
    fun cardIn(): Double = sinceBoot().let { b -> if (b < 0) 0.0 else K.easeOut((b - K.CARD_AT) / K.CARD_FOR) }
    fun cardOut(): Double = sinceDown().let { d -> if (d < 0) 0.0 else K.easeIn((d - K.CARD_OUT_AT) / K.CARD_OUT_FOR) }
    fun cardShift(): Double = if (still) 0.0 else K.CARD_DROP * (1 - cardIn()) + K.CARD_DROP * 1.3 * cardOut()
    fun cardOpacity(): Double = cardIn() * (1 - cardOut())

    val label = "$title ${K.money(sets[run.toSet.coerceAtMost(sets.size - 1)].total, currency)}"
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(K.BACKGROUND)
            .semantics { contentDescription = label }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { press() },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val cardWidth = min(K.CARD_WIDTH, width - 48)
        val cardHeight = min(K.CARD_HEIGHT, height * 0.5f)
        val card = Rect(Offset((width - cardWidth) / 2, height / 2 - cardHeight / 2 + 24), Size(cardWidth, cardHeight))

        Canvas(Modifier.fillMaxSize()) {
            scale(density, density, pivot = Offset.Zero) {
                val b = sinceBoot()
                val d = sinceDown()
                drawBackdrop(width, height, paints)
                drawSquares(width, height, b, d, still, paints)
                drawCard(card, b, cardShift(), cardOpacity(), if (still) cardIn() else if (b < 0) 0.0 else K.easeInOut((b - K.CARD_AT) / K.CARD_FOR), still)
                drawBars(card, b, d, time.doubleValue, run, currentSets, still, paints)
            }
        }

        Column(
            Modifier
                .offset((card.left + K.CARD_PADDING).dp, (card.top + K.CARD_PADDING).dp)
                .graphicsLayer {
                    translationY = cardShift().toFloat() * this.density
                    alpha = cardOpacity().toFloat()
                },
        ) {
            BasicText(
                title,
                Modifier.padding(bottom = 2.dp).height(18.dp),
                style = TextStyle(
                    color = K.CAPTION, fontSize = 13.sp, lineHeight = 18.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
            odometer.Figure(
                TextStyle(
                    color = K.INK, fontSize = K.VALUE_SIZE.sp, lineHeight = K.VALUE_LINE.sp, fontWeight = FontWeight.Light,
                    fontFeatureSettings = "tnum",
                    shadow = Shadow(K.VALUE_GLOW, blurRadius = K.VALUE_GLOW_RADIUS * density),
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                ),
                K.VALUE_LINE,
            ) { time.doubleValue }
        }
    }
}

private fun spotShader(width: Float) = RadialGradient(
    width / 2, 0f, K.SPOT_RADIUS, K.SPOTLIGHT.toArgb(), K.SPOTLIGHT_OUT.toArgb(), Shader.TileMode.CLAMP,
)

private fun DrawScope.drawBackdrop(width: Float, height: Float, paints: Paints) {
    drawRect(K.BACKGROUND, size = Size(width, height))
    drawIntoCanvas { canvas ->
        paints.fill.shader = spotShader(width)
        paints.fill.alpha = 255
        canvas.nativeCanvas.drawRect(0f, 0f, width, height, paints.fill)
        paints.fill.shader = null
    }
}

private fun DrawScope.drawSquares(width: Float, height: Float, b: Double, d: Double, still: Boolean, paints: Paints) {
    val cx = width / 2
    val cy = height / 2
    val away = -(cy + K.SQUARE * 2).toDouble()
    val y = when {
        still -> 0.0
        d >= 0 -> away * (1 - K.arrive((d - K.RETURN_AT) / K.RETURN_FOR, 0.08))
        b < 0 -> 0.0
        else -> away * K.easeIn((b - K.RISE_AT) / K.RISE_FOR)
    }
    val opacity = when {
        !still -> 1.0
        d >= 0 -> K.clamp01((d - K.RETURN_AT) / 0.3)
        b < 0 -> 1.0
        else -> 1 - K.clamp01((b - K.RISE_AT) / 0.3)
    }
    if (opacity <= 0) return
    val kick = if (still || d >= 0) 0.0 else K.swing(b)
    val alpha = (opacity * 255).roundToInt()

    // Skia's group transforms, applied about each square's own centre.
    val vx = cx - K.OVERLAP / 2
    val vy = cy - K.OVERLAP / 2
    val violetScale = (1 - 0.15 * kick).toFloat()
    paints.violet.apply {
        setTranslate(-vx, -vy)
        postScale(violetScale, violetScale)
        postRotate((-18 * kick).toFloat())
        postTranslate(vx, vy + y.toFloat())
    }
    val fx = cx + K.OVERLAP / 2
    val fy = cy + K.OVERLAP / 2
    paints.frost.apply {
        setTranslate(-fx, -fy)
        postRotate((22 * kick).toFloat())
        postTranslate(fx + 20 * kick.toFloat(), fy + (y * 1.08 + 20 * kick).toFloat())
        invert(paints.frostInverse)
    }
    val half = K.SQUARE / 2
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        val fill = paints.fill
        nc.save()
        nc.concat(paints.violet)
        fill.color = K.VIOLET.toArgb()
        fill.alpha = alpha
        nc.drawRoundRect(vx - half, vy - half, vx + half, vy + half, K.SQUARE_RADIUS, K.SQUARE_RADIUS, fill)
        nc.restore()

        // A backdrop blur: what lies under the frosted square, drawn again inside its outline. The
        // background is too smooth for a blur to show, so only the violet square is blurred.
        nc.save()
        nc.concat(paints.frost)
        paints.roundPath.rewind()
        paints.roundPath.addRoundRect(fx - half, fy - half, fx + half, fy + half, K.SQUARE_RADIUS, K.SQUARE_RADIUS, android.graphics.Path.Direction.CW)
        nc.clipPath(paints.roundPath)
        nc.concat(paints.frostInverse)
        fill.color = K.BACKGROUND.toArgb()
        fill.alpha = alpha
        nc.drawRect(0f, 0f, width, height, fill)
        fill.shader = spotShader(width)
        fill.alpha = alpha
        nc.drawRect(0f, 0f, width, height, fill)
        fill.shader = null
        nc.concat(paints.violet)
        val blur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) paints.frostBlur else fill
        blur.color = K.VIOLET.toArgb()
        blur.alpha = alpha
        nc.drawRoundRect(vx - half, vy - half, vx + half, vy + half, K.SQUARE_RADIUS, K.SQUARE_RADIUS, blur)
        nc.restore()

        nc.save()
        nc.concat(paints.frost)
        fill.color = K.FROST.toArgb()
        fill.alpha = (K.FROST.alpha * opacity * 255).roundToInt()
        nc.drawRoundRect(fx - half, fy - half, fx + half, fy + half, K.SQUARE_RADIUS, K.SQUARE_RADIUS, fill)
        nc.restore()
    }
}

/** The card's outline as Skia draws a rounded rect: from the foot of the left side, clockwise. */
private fun outline(rect: Rect): Path {
    val r = K.CARD_RADIUS
    return Path().apply {
        moveTo(rect.left, rect.bottom - r)
        lineTo(rect.left, rect.top + r)
        arcTo(Rect(rect.left, rect.top, rect.left + 2 * r, rect.top + 2 * r), 180f, 90f, false)
        lineTo(rect.right - r, rect.top)
        arcTo(Rect(rect.right - 2 * r, rect.top, rect.right, rect.top + 2 * r), 270f, 90f, false)
        lineTo(rect.right, rect.bottom - r)
        arcTo(Rect(rect.right - 2 * r, rect.bottom - 2 * r, rect.right, rect.bottom), 0f, 90f, false)
        lineTo(rect.left + r, rect.bottom)
        arcTo(Rect(rect.left, rect.bottom - 2 * r, rect.left + 2 * r, rect.bottom), 90f, 90f, false)
        close()
    }
}

private fun DrawScope.drawCard(card: Rect, b: Double, shift: Double, opacity: Double, border: Double, still: Boolean) {
    if (opacity <= 0) return
    val alpha = opacity.toFloat()
    translate(0f, shift.toFloat()) {
        val shape = Path().apply { addRoundRect(RoundRect(card, CornerRadius(K.CARD_RADIUS))) }
        clipPath(shape) {
            drawRect(K.CARD_FILL, card.topLeft, card.size, alpha)
            drawRect(
                Brush.radialGradient(listOf(K.CARD_GLOW, K.CARD_GLOW_OUT), Offset(card.center.x, card.top), card.width * 0.7f),
                card.topLeft, card.size, alpha,
            )
            if (!still && b >= K.SWEEP_AT && b <= K.SWEEP_AT + K.SWEEP_FOR) {
                val x = (card.left - K.SWEEP_WIDTH + (card.width + K.SWEEP_WIDTH) * K.easeInOut((b - K.SWEEP_AT) / K.SWEEP_FOR)).toFloat()
                drawRect(
                    Brush.linearGradient(listOf(K.SWEEP.copy(alpha = 0f), K.SWEEP, K.SWEEP.copy(alpha = 0f)), Offset(x, 0f), Offset(x + K.SWEEP_WIDTH, 0f)),
                    Offset(x, card.top), Size(K.SWEEP_WIDTH, card.height), alpha,
                )
            }
        }
        if (border <= 0) return@translate
        val full = outline(card)
        val measure = PathMeasure().apply { setPath(full, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * border.toFloat(), drawn, true)
        drawPath(
            drawn,
            Brush.linearGradient(
                0f to K.CARD_EDGE_BRIGHT, 0.45f to K.CARD_EDGE, 1f to K.CARD_EDGE,
                start = card.topLeft, end = card.bottomRight,
            ),
            alpha,
            Stroke(1f),
        )
    }
}

private fun DrawScope.drawBars(card: Rect, b: Double, d: Double, t: Double, run: Run, sets: List<NeonBarBootSet>, still: Boolean, paints: Paints) {
    if (b < 0) return
    val scale = card.height / K.CARD_HEIGHT
    val barsLeft = card.left + K.CARD_PADDING
    val pitch = (card.width - K.CARD_PADDING * 2) / 17
    val base = card.bottom - K.CARD_PADDING
    val tallest = K.BAR_TALLEST * scale
    val from = sets[run.fromSet].bars
    val to = sets[run.toSet].bars
    val count = sets[0].bars.size
    val r = if (run.refreshAt < 0) -1.0 else t - run.refreshAt
    val breathIn = K.clamp01((b - K.GROW_AT - 1.2) / 0.6)
    val colors = intArrayOf(K.BAR_TOP.toArgb(), K.BAR_MIDDLE.toArgb(), K.BAR_TAIL.toArgb())
    val stops = floatArrayOf(0f, 1 - K.BAR_FADE * 2, 1f)
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        val paint = paints.fill
        for (i in 0 until count) {
            val grown = if (still) 1.0 else K.arrive((b - K.GROW_AT - i * K.GROW_GAP) / K.GROW_FOR, K.OVERSHOOT)
            val shown = if (still) K.clamp01((b - K.GROW_AT) / 0.3) else 1.0
            if (b < K.GROW_AT + i * K.GROW_GAP && !still) continue
            val k = if (r < 0 || still) 1.0 else K.arrive((r - i * K.REFRESH_GAP) / K.REFRESH_FOR, 0.05)
            val target = from[i] + (to[i] - from[i]) * k
            val breath = if (still) 1.0 else 1 + K.BREATH * breathIn * sin(TAU * (b - K.GROW_AT - i * K.BREATH_LAG) / K.BREATH_PERIOD)
            val sink = when {
                d < 0 -> 1.0
                still -> 1 - K.clamp01((d - K.CARD_OUT_AT) / 0.3)
                else -> 1 - K.easeIn((d - (count - 1 - i) * K.SINK_GAP) / K.SINK_FOR)
            }
            val h = (tallest * target * grown * breath * (if (still) 1.0 else sink)).toFloat()
            val alpha = shown * (if (still) sink else 1.0)
            if (h < 1 || alpha <= 0) continue
            val x = barsLeft + pitch * (i + 0.5f)
            val top = base - h
            val shader = LinearGradient(0f, top, 0f, base, colors, stops, Shader.TileMode.CLAMP)
            paints.haze.shader = shader
            paints.haze.alpha = (K.HAZE_ALPHA * alpha * 255).roundToInt()
            nc.drawRoundRect(x - K.BAR_WIDTH, top, x + K.BAR_WIDTH, base, K.BAR_WIDTH, K.BAR_WIDTH, paints.haze)
            paint.shader = shader
            paint.alpha = (alpha * 255).roundToInt()
            nc.drawRoundRect(x - K.BAR_WIDTH / 2, top, x + K.BAR_WIDTH / 2, base, K.BAR_WIDTH / 2, K.BAR_WIDTH / 2, paint)
            paint.shader = null
            // The top lights up as the bar arrives.
            val lit = K.clamp01((grown - 0.85) / 0.15) * alpha * (if (d < 0) 1.0 else sink)
            if (lit > 0) {
                paints.glow.color = K.BAR_GLOW.toArgb()
                paints.glow.alpha = (K.TIP_GLOW * lit * 255).roundToInt()
                nc.drawCircle(x, top + K.BAR_WIDTH / 2, K.BAR_WIDTH, paints.glow)
            }
        }
    }
}
