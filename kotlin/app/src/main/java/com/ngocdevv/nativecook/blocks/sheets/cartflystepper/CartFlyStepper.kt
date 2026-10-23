package com.ngocdevv.nativecook.blocks.sheets.cartflystepper

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** autoPlay: one round of taps every LOOP seconds. */
const val LOOP = 10.0

// After a touch autoPlay waits this long, empties the basket, and starts over from rest.
private const val USER_HOLD = 3.0
// Holding "+" or "−" repeats after HOLD_AFTER, every REPEAT seconds.
private const val HOLD_AFTER = 0.4
private const val REPEAT = 0.15

// Events on the run's clock.
private const val ADD = 0
private const val REMOVE = 1
private const val RESET = 2
private const val LIFT = 3
// autoPlay's round, in seconds from its start: single taps, a held burst, more taps, a lift as the
// last token lands, and the counts rolling back to 0.
private val ROUND = listOf(
    0.8 to ADD, 1.4 to ADD,
    2.1 to ADD, 2.28 to ADD, 2.46 to ADD, 2.64 to ADD, 2.82 to ADD,
    3.6 to ADD, 4.1 to ADD,
    4.7 to ADD, 4.88 to ADD, 5.06 to ADD,
    5.6 to LIFT,
    8.4 to RESET,
)

// Motion, in seconds.
private const val FLIGHT = 0.6
private const val POP = 0.15
private const val ROLL = 0.25
private const val RESET_ROLL = 0.4
private const val PRESS_FOR = 0.3
private const val LIFT_FOR = 0.6
private const val BUMP = 0.32
private const val BUMP_DECAY = 7.0
private const val BUMP_PERIOD = 0.36
private const val BUMP_FOR = 0.6
// Events older than this have finished every animation they started, and are folded into the counts.
private const val SETTLE = 1.5

// Sizes in dp.
private const val SHEET_GAP = 10f
private const val SHEET_RADIUS = 32f
private const val SIDE = 20f
private const val GRABBER_WIDTH = 36f
private const val GRABBER_HEIGHT = 5f
private const val PANEL_SHARE = 0.3f
private const val PANEL_MIN = 160f
private const val PANEL_MAX = 280f
private const val PANEL_RADIUS = 22f
private const val PLATE_SHARE = 0.78f
private const val ROW_HEIGHT = 60f
private const val ROW_RADIUS = 16f
private const val CHECK = 22f
private const val BAR = 56f
private const val BAR_MARGIN = 16f
private const val FADE = 48f
private const val STEPPER_WIDTH = 220f
private const val GLYPH_INSET = 32f
private const val GLYPH = 14f
private const val GLYPH_STROKE = 2f
private const val PRESS_RADIUS = 20f
private const val BASKET_GAP = 40f
private const val BASKET = 24f
private const val BASKET_COUNT_GAP = 18f
private const val TOKEN = 30f
private const val TOKEN_RADIUS = 9f
private const val TOKEN_PLATE = 22f
private const val TOKEN_END_SCALE = 0.45f
// The flight's arc rises this far above the higher of its two ends, at its control point.
private const val ARC_LIFT = 120f
private const val COUNT_LINE = 24f
private const val LIFT_SCALE = 0.04f
private const val LIFT_RISE = 10f
private const val BASKET_X = SIDE + STEPPER_WIDTH + BASKET_GAP

private val Screen = Color(0xFF000000)
private val Sheet = Color(0xFFFFFFFF)
private val Grabber = Color(0xFFD0D0D4)
private val PanelFill = Color(0xFFF3F3F5)
private val Ink = Color(0xFF111111)
private val Body = Color(0xFF555555)
private val Mute = Color(0xFF888888)
private val PillFill = Color(0xFFEEEEEE)
private val PillInk = Color(0xFF666666)
private val RowFill = Color(0xFFF2F2F4)
private val CheckEdge = Color(0xFFC8C8CC)
private val Divider = Color(0xFFECECEE)
private val StepperFill = Color(0xFFECECEE)
private const val INK = 0x111111

data class CartOption(val name: String, val price: String)

private val Options = listOf(
    CartOption("Vegan", "+ 0,00"),
    CartOption("Extra avocado", "+ 1,50"),
    CartOption("Chicken instead of salmon", "+ 2,00"),
)

/** What the screen shows at one moment. */
private class CartMoment(var stepFrom: Int, var basketFrom: Int) {
    var stepTo = stepFrom
    var stepAt = -1e9
    var stepFor = ROLL
    var basketTo = basketFrom
    var basketAt = -1e9
    var bump = 0.0
    var lift = 0.0
    var pressMinus = 0.0
    var pressPlus = 0.0
    val tokens = ArrayList<Double>()
}

private fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
private fun easeOut(x: Double) = 1.0 - (1.0 - x).pow(3)
private fun easeIn(x: Double) = x * x
private fun pressCurve(dt: Double) = if (dt < 0 || dt > PRESS_FOR) 0.0 else sin(Math.PI * dt / PRESS_FOR)

/** The basket's kick when a token lands: up to about 1.25, then settling back to 1. */
private fun kick(dt: Double) = if (dt < 0 || dt > BUMP_FOR) 0.0 else BUMP * exp(-BUMP_DECAY * dt) * sin(2 * Math.PI * dt / BUMP_PERIOD)

/** Everything the screen shows at [t], from the events so far and the counts they started from. */
private fun sceneOf(t: Double, events: List<Pair<Double, Int>>, baseStep: Int, baseBasket: Int): CartMoment {
    val s = CartMoment(baseStep, baseBasket)
    var step = baseStep
    var basket = baseBasket
    for ((at, kind) in events) {
        if (at > t) break
        val before = step
        when (kind) {
            ADD -> {
                step += 1
                s.pressPlus = max(s.pressPlus, pressCurve(t - at))
                val landing = at + FLIGHT
                if (landing <= t) {
                    s.basketFrom = basket
                    basket += 1
                    s.basketTo = basket
                    s.basketAt = landing
                    s.bump += kick(t - landing)
                } else {
                    s.tokens.add(t - at)
                }
            }
            REMOVE -> {
                step = max(0, step - 1)
                s.pressMinus = max(s.pressMinus, pressCurve(t - at))
            }
            RESET -> {
                if (basket != 0) {
                    s.basketFrom = basket
                    basket = 0
                    s.basketTo = 0
                    s.basketAt = at
                }
                step = 0
            }
            LIFT -> {
                val x = clamp((t - at) / LIFT_FOR)
                s.lift = max(s.lift, if (x <= 0 || x >= 1) 0.0 else 0.5 - 0.5 * cos(2 * Math.PI * x))
            }
        }
        if (step != before) {
            s.stepFrom = before
            s.stepTo = step
            s.stepAt = at
            s.stepFor = if (kind == RESET) RESET_ROLL else ROLL
        }
    }
    s.bump = s.bump.coerceIn(-0.3, 0.3)
    return s
}

/** autoPlay's current round, at absolute times. */
private fun roundAt(now: Double, start: Double): List<Pair<Double, Int>> {
    val base = start + floor((now - start) / LOOP) * LOOP
    return ROUND.map { (at, kind) -> (at + base) to kind }
}

/** The clock and the events, kept out of composition so touches and frames share them. */
private class CartFlyStepperClock {
    /** Seconds on the frame clock. */
    var now = 0.0
    /** Bumped by a touch so the frame loop starts when nothing else is running it. */
    val wake = mutableIntStateOf(0)
    // The counts on show, which only change a few times a second.
    val stepFrom = mutableIntStateOf(0)
    val stepTo = mutableIntStateOf(0)
    val basketFrom = mutableIntStateOf(0)
    val basketTo = mutableIntStateOf(0)
    /** What the screen shows, a new one every frame; read while drawing, so each frame redraws. */
    val scene = mutableStateOf(CartMoment(0, 0))
    // autoPlay's round start (-1 when it is off), and the events made by hand.
    var loopStart = -1.0
    val hand = ArrayList<Pair<Double, Int>>()
    var baseStep = 0
    var baseBasket = 0
    var touchedAt = -1e9
    var held = false

    fun events(at: Double): List<Pair<Double, Int>> = if (loopStart < 0 || at < loopStart) hand else roundAt(at, loopStart)

    /** Brings the clock up to the moment of a touch, which may come while the frame loop is stopped. */
    private fun fresh(): Double {
        now = max(now, System.nanoTime() / 1e9)
        return now
    }

    /** Notes a touch, which holds off autoPlay for USER_HOLD seconds. */
    fun touch() {
        touchedAt = fresh()
    }

    /** A touch during autoPlay takes over: the round so far becomes the events made by hand. */
    fun takeOver() {
        val t = fresh()
        touchedAt = t
        if (loopStart < 0) return
        if (t >= loopStart) {
            hand.clear()
            for (e in roundAt(t, loopStart)) if (e.first <= t) hand.add(e)
            baseStep = 0
            baseBasket = 0
        }
        loopStart = -1.0
    }

    /** Folds events whose animations are over into the counts, so the list stays short. */
    fun fold() {
        val t = now
        while (hand.isNotEmpty() && hand[0].first < t - SETTLE) {
            when (hand[0].second) {
                ADD -> {
                    baseStep += 1
                    baseBasket += 1
                }
                REMOVE -> baseStep = max(0, baseStep - 1)
                RESET -> {
                    baseStep = 0
                    baseBasket = 0
                }
            }
            hand.removeAt(0)
        }
    }

    /** Adds or removes one by hand; false when there was nothing to remove. */
    fun press(kind: Int): Boolean {
        takeOver()
        val t = now
        if (kind == REMOVE && sceneOf(t, hand, baseStep, baseBasket).stepTo <= 0) return false
        fold()
        hand.add(t to kind)
        wake.intValue++
        return true
    }

    /** Whether anything on screen is still moving. */
    fun moving() = held || loopStart >= 0 || (hand.isNotEmpty() && now - hand.last().first < SETTLE)
}

/** Paints for the overlay, reused from frame to frame. Mask-filter blurs need API 28 on hardware canvases. */
private class CartFlyStepperPaints {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val plate = CartFlyStepperPlate()
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = cartArgb(INK, 1f)
    }
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(sigmaToRadius(4f), BlurMaskFilter.Blur.NORMAL) }
    /** A basket in a 24 × 24 box: handles, body, and two slats. */
    val basket = Path().apply {
        moveTo(6f, 10f); lineTo(9f, 4f)
        moveTo(18f, 10f); lineTo(15f, 4f)
        moveTo(3f, 10f); lineTo(21f, 10f)
        lineTo(19.2f, 19.4f)
        quadTo(18.9f, 21f, 17.3f, 21f)
        lineTo(6.7f, 21f)
        quadTo(5.1f, 21f, 4.8f, 19.4f)
        close()
        moveTo(9.5f, 13.5f); lineTo(9.5f, 17.5f)
        moveTo(14.5f, 13.5f); lineTo(14.5f, 17.5f)
    }
}

/** The stepper's glyphs, the basket, and the tokens in flight, in sheet dp. */
private fun drawOverlay(canvas: android.graphics.Canvas, paints: CartFlyStepperPaints, s: CartMoment, barY: Float, reduced: Boolean) {
    val minusX = SIDE + GLYPH_INSET
    val plusX = SIDE + STEPPER_WIDTH - GLYPH_INSET

    // "−" and "+", each pressed in when tapped.
    for ((x, pressed, plus) in listOf(Triple(minusX, s.pressMinus.toFloat(), false), Triple(plusX, s.pressPlus.toFloat(), true))) {
        if (pressed > 0f) {
            paints.fill.color = cartArgb(0xFFFFFF, 0.95f * pressed)
            canvas.drawCircle(x, barY, PRESS_RADIUS * (0.8f + 0.2f * pressed), paints.fill)
        }
        val g = GLYPH / 2f * (1f - 0.12f * pressed)
        paints.line.strokeWidth = GLYPH_STROKE
        canvas.drawLine(x - g, barY, x + g, barY, paints.line)
        if (plus) canvas.drawLine(x, barY - g, x, barY + g, paints.line)
    }

    // The basket, kicked by each token that lands in it.
    val scale = if (reduced) 1f else 1f + s.bump.toFloat()
    canvas.save()
    canvas.translate(BASKET_X, barY)
    canvas.scale(scale, scale)
    canvas.translate(-BASKET / 2f, -BASKET / 2f)
    paints.line.strokeWidth = 1.8f
    canvas.drawPath(paints.basket, paints.line)
    canvas.restore()

    // Tokens on their arcs from "+" to the basket.
    if (reduced) return
    val sx = plusX
    val sy = barY
    val ex = BASKET_X
    val ey = barY
    val cx = (sx + ex) / 2f
    val cy = min(sy, ey) - ARC_LIFT
    for (dt in s.tokens) {
        val q = easeIn(clamp(dt / FLIGHT)).toFloat()
        val x = (1 - q) * (1 - q) * sx + 2 * (1 - q) * q * cx + q * q * ex
        val y = (1 - q) * (1 - q) * sy + 2 * (1 - q) * q * cy + q * q * ey
        val popped = easeOut(clamp(dt / POP)).toFloat()
        val k = (0.5f + 0.5f * popped) * (1f + (TOKEN_END_SCALE - 1f) * q)
        val alpha = (clamp(dt / 0.06) * clamp((FLIGHT - dt) / 0.08)).toFloat()
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(-12f + 30f * q)
        canvas.scale(k, k)
        if (paints.canBlur) {
            paints.shadow.color = cartArgb(0x000000, 0.18f * alpha)
            canvas.drawRoundRect(RectF(-TOKEN / 2f, -TOKEN / 2f + 2f, TOKEN / 2f, TOKEN / 2f + 2f), TOKEN_RADIUS, TOKEN_RADIUS, paints.shadow)
        }
        paints.fill.color = cartArgb(0xFFFFFF, alpha)
        canvas.drawRoundRect(RectF(-TOKEN / 2f, -TOKEN / 2f, TOKEN / 2f, TOKEN / 2f), TOKEN_RADIUS, TOKEN_RADIUS, paints.fill)
        paints.plate.draw(canvas, 0f, 0f, TOKEN_PLATE, alpha)
        canvas.restore()
    }
}

private fun style(size: Float, line: Float, color: Color, weight: FontWeight = FontWeight.Normal, align: TextAlign = TextAlign.Start) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    textAlign = align,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** A number that rolls from `from` to `to`: up when it grows, down when it shrinks; a fade under reduced motion. */
@Composable
private fun RollingCount(from: Int, to: Int, progress: () -> Float, reduced: Boolean, size: Float, modifier: Modifier = Modifier) {
    val dir = if (to >= from) 1f else -1f
    val density = LocalDensity.current.density
    val shift = if (reduced) 0f else COUNT_LINE * density
    val text = style(size, COUNT_LINE, Ink, FontWeight.Medium, TextAlign.Center)
    Box(modifier.height(COUNT_LINE.dp).widthIn(min = 28.dp).clip(RoundedCornerShape(0.dp)), contentAlignment = Alignment.Center) {
        if (from != to) {
            Text(
                text = "$from",
                style = text,
                modifier = Modifier.graphicsLayer {
                    val p = progress()
                    alpha = 1f - p
                    translationY = -dir * shift * p
                },
            )
        }
        Text(
            text = "$to",
            style = text,
            modifier = Modifier.graphicsLayer {
                if (from != to) {
                    val p = progress()
                    alpha = p
                    translationY = dir * shift * (1f - p)
                }
            },
        )
    }
}

/**
 * A product sheet whose "+" sends a small token along an arc into the basket for every item added:
 * tokens overlap in a burst, the basket kicks as each lands, and both counts roll.
 *
 * @param autoPlay Taps "+" by itself every [LOOP] seconds, singly and in held bursts, and empties the basket at the end.
 * @param onChange Called with the stepper's count whenever it changes by hand.
 */
@Composable
fun CartFlyStepper(
    modifier: Modifier = Modifier,
    title: String = "Ginger-Soy Salmon Fillet",
    description: String = "edamame, cucumber, avocado,\nherbs, white rice",
    price: String = "$9.95",
    optionsTitle: String = "Options",
    optionalLabel: String = "Optional",
    options: List<CartOption> = Options,
    autoPlay: Boolean = false,
    onChange: (Int) -> Unit = {},
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val clock = remember { CartFlyStepperClock() }
    val paints = remember { CartFlyStepperPaints() }
    val change by rememberUpdatedState(onChange)
    val wake by clock.wake
    val checked = remember { mutableStateListOf<Int>() }

    // The frame loop runs while autoPlay is on or something moves, and stops when all is still.
    LaunchedEffect(autoPlay, wake) {
        do {
            withFrameNanos { nanos ->
                val now = nanos / 1e9
                clock.now = now
                if (autoPlay && clock.loopStart < 0 && !clock.held && now - clock.touchedAt >= USER_HOLD) {
                    val s = sceneOf(now, clock.hand, clock.baseStep, clock.baseBasket)
                    if (s.stepTo != 0 || s.basketTo != 0) {
                        // Empty the basket first, then start the round again from rest.
                        clock.hand.add(now to RESET)
                        clock.loopStart = now + RESET_ROLL + 0.2
                    } else {
                        clock.hand.clear()
                        clock.baseStep = 0
                        clock.baseBasket = 0
                        clock.loopStart = now
                    }
                }
                val s = sceneOf(now, clock.events(now), clock.baseStep, clock.baseBasket)
                clock.scene.value = s
                clock.stepFrom.intValue = s.stepFrom
                clock.stepTo.intValue = s.stepTo
                clock.basketFrom.intValue = s.basketFrom
                clock.basketTo.intValue = s.basketTo
            }
        } while (autoPlay || clock.moving())
    }

    /** A press counts at once; held, it repeats after HOLD_AFTER, every REPEAT. */
    fun press(kind: Int) {
        if (!clock.press(kind)) return
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        change(clock.sceneAfterPress())
    }

    val stepFrom by clock.stepFrom
    val stepTo by clock.stepTo
    val basketFrom by clock.basketFrom
    val basketTo by clock.basketTo

    BoxWithConstraints(modifier.fillMaxSize().background(Screen)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val bottom = insets.calculateBottomPadding().value
        val height = maxHeight.value
        val sheetTop = top + SHEET_GAP
        val sheetHeight = height - sheetTop
        val panel = (height * PANEL_SHARE).coerceIn(PANEL_MIN, PANEL_MAX)
        val barY = sheetHeight - max(bottom, BAR_MARGIN) - 6f - BAR / 2f
        val density = LocalDensity.current.density

        Box(
            Modifier
                .offset(y = sheetTop.dp)
                .fillMaxWidth()
                .height(sheetHeight.dp)
                .graphicsLayer {
                    val lift = if (reduced) 0f else clock.scene.value.lift.toFloat()
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    scaleX = 1f - LIFT_SCALE * lift
                    scaleY = 1f - LIFT_SCALE * lift
                    translationY = -LIFT_RISE * lift * density
                }
                .clip(RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp))
                .background(Sheet),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = SIDE.dp)) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally)
                        .size(GRABBER_WIDTH.dp, GRABBER_HEIGHT.dp)
                        .background(Grabber, CircleShape),
                )
                Canvas(
                    Modifier
                        .padding(top = 11.dp)
                        .fillMaxWidth()
                        .height(panel.dp)
                        .background(PanelFill, RoundedCornerShape(PANEL_RADIUS.dp)),
                ) {
                    drawIntoCanvas {
                        val native = it.nativeCanvas
                        native.save()
                        native.scale(density, density)
                        paints.plate.draw(native, size.width / density / 2f, panel / 2f, panel * PLATE_SHARE, 1f)
                        native.restore()
                    }
                }
                Text(title, style = style(20f, 26f, Ink, FontWeight.SemiBold), modifier = Modifier.padding(top = 20.dp))
                Text(description, style = style(14f, 19f, Body), modifier = Modifier.padding(top = 4.dp))
                Text(price, style = style(16f, 22f, Ink, FontWeight.Medium), modifier = Modifier.padding(top = 8.dp))
                Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(1.dp).background(Divider))
                Row(Modifier.padding(top = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(optionsTitle, style = style(18f, 24f, Ink, FontWeight.SemiBold))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.height(20.dp).background(PillFill, CircleShape).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Text(optionalLabel, style = style(11f, 14f, PillInk))
                    }
                }
                options.forEachIndexed { i, option ->
                    val on = i in checked
                    Row(
                        Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .height(ROW_HEIGHT.dp)
                            .clip(RoundedCornerShape(ROW_RADIUS.dp))
                            .background(RowFill)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Checkbox) {
                                clock.takeOver()
                                clock.wake.intValue++
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                if (on) checked.remove(i) else checked.add(i)
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(option.name, style = style(14f, 18f, Ink, FontWeight.SemiBold))
                            Text(option.price, style = style(12f, 16f, Mute))
                        }
                        Box(
                            Modifier
                                .size(CHECK.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (on) Ink else Color.Transparent)
                                .border(1.5.dp, if (on) Ink else CheckEdge, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(Icons.Rounded.Check, contentDescription = null, tint = Sheet, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            // The bottom bar, over a fade that lets the list run out under it.
            Column(Modifier.offset(y = (barY - BAR / 2f - FADE).dp).fillMaxWidth().fillMaxHeight()) {
                Box(Modifier.fillMaxWidth().height(FADE.dp).background(Brush.verticalGradient(listOf(Sheet.copy(alpha = 0f), Sheet))))
                Box(Modifier.fillMaxSize().background(Sheet))
            }
            Row(
                Modifier
                    .offset(SIDE.dp, (barY - BAR / 2f).dp)
                    .size(STEPPER_WIDTH.dp, BAR.dp)
                    .background(StepperFill, CircleShape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (kind in listOf(REMOVE, ADD)) {
                    if (kind == ADD) {
                        RollingCount(stepFrom, stepTo, { clock.scene.value.let { clamp((clock.now - it.stepAt) / it.stepFor).toFloat() } }, reduced, 18f, Modifier.weight(1f))
                    }
                    Box(
                        Modifier
                            .width((GLYPH_INSET * 2f).dp)
                            .fillMaxHeight()
                            .semantics {
                                role = Role.Button
                                contentDescription = if (kind == ADD) "Add one" else "Remove one"
                                onClick {
                                    press(kind)
                                    true
                                }
                            }
                            .pointerInput(kind) {
                                coroutineScope {
                                    awaitEachGesture {
                                        awaitFirstDown()
                                        clock.held = true
                                        press(kind)
                                        val repeat = launch {
                                            delay((HOLD_AFTER * 1000).toLong())
                                            while (true) {
                                                press(kind)
                                                delay((REPEAT * 1000).toLong())
                                            }
                                        }
                                        waitForUpOrCancellation()
                                        repeat.cancel()
                                        clock.held = false
                                        clock.touch()
                                        clock.wake.intValue++
                                    }
                                }
                            },
                    )
                }
            }
            RollingCount(
                basketFrom, basketTo,
                { clamp((clock.now - clock.scene.value.basketAt) / ROLL).toFloat() },
                reduced, 16f,
                Modifier.offset((BASKET_X + BASKET_COUNT_GAP).dp, (barY - COUNT_LINE / 2f).dp),
            )
            Canvas(Modifier.fillMaxSize()) {
                val scene = clock.scene.value
                drawIntoCanvas {
                    val native = it.nativeCanvas
                    native.save()
                    native.scale(density, density)
                    drawOverlay(native, paints, scene, barY, reduced)
                    native.restore()
                }
            }
        }
    }
}

/** The stepper's count right after a press, from the events so far. */
private fun CartFlyStepperClock.sceneAfterPress() = sceneOf(now, hand, baseStep, baseBasket).stepTo

@Preview
@Composable
private fun CartFlyStepperPreview() {
    CartFlyStepper(autoPlay = true)
}
