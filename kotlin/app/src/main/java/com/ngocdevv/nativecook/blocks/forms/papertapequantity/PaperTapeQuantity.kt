package com.ngocdevv.nativecook.blocks.forms.papertapequantity

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A finish's bead colour, lit side and dark side. */
data class Finish(val name: String, val color: Color, val light: Color, val dark: Color)
private val Finishes = listOf(
    Finish("Cyber-Tech", Color(0xFFF08A1C), Color(0xFFFFD08A), Color(0xFFA85408)),
    Finish("Blue-Steel", Color(0xFF2F4DE0), Color(0xFF93A6FF), Color(0xFF16268F)),
)
private val Surface = Color(0xFFE3E3E1)
private val Ink = Color(0xFF2D2D2F)
private val Text = Color(0xFF4A4A4C)
private val Mute = Color(0xFF8B8A88)
private val Accent = Color(0xFFF26A1B)
private const val MARGIN = 16f
private const val NAV = 44f
private const val ROUND = 40f
private const val SPEAKER = 172f
private const val BEAD = 30f
private const val LANE = 132f
private const val ROW = 46f
private const val BAR_GAP = 22f
private const val STEP_MASS = 1f
private const val STEP_STIFFNESS = 330f
private const val STEP_DAMPING = 23f
private const val NUDGE = 0.14f
private const val NUDGE_IN = 90
private const val RUBBER = 0.25f
private const val FLICK = 0.12f
private const val HOLD_IN = 60
private const val HOLD_OUT = 160
private const val TOTAL_FADE = 140
private const val LOOP = 7.4f
private const val FIRST_AT = 0.8f
private const val STEP_EVERY = 0.9f
private const val TURN_PAUSE = 0.4f
private const val PRESS_FOR = 0.14f
private val Presses = intArrayOf(1, 1, 1, -1, -1, -1)
private const val USER_HOLD = 3f
private const val RETURN = 0.8f
private const val SPRING_ENERGY_THRESHOLD = 6e-9f
private val QuadInOut = Easing { t -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f }
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private fun textStyle(size: Float, line: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }
private fun money(symbol: String, value: Double): String {
    val raw = when {
        !value.isFinite() -> value.toString()
        abs(value) >= 1e21 -> BigDecimal.valueOf(value).stripTrailingZeros().toString().replace("E", "e")
        else -> {
            val rounded = BigDecimal(value).setScale(2, RoundingMode.HALF_UP)
            (if (value < 0.0 && rounded.signum() == 0) "-" else "") + rounded.toPlainString()
        }
    }
    return symbol + raw.replace(Regex("\\B(?=(\\d{3})+(?!\\d))"), ",")
}

/** A state readable by the drawing-only Roller, with timing and the literal step spring. */
private class TapeMotion(initial: Float) : State<Float> {
    private val state = mutableFloatStateOf(initial)
    override val value get() = state.floatValue
    private var velocity = 0f
    private var target = initial
    private var from = initial
    private var initialEnergy = 0f
    private var origin = 0L
    private var duration = 0f
    private var curve: Easing = QuadInOut
    private var mode = 0
    private var after: Float? = null
    fun direct(next: Float) { state.floatValue = next; target = next; velocity = 0f; mode = 0; after = null }
    fun cancel() { velocity = 0f; mode = 0; after = null }
    fun timing(next: Float, milliseconds: Int, still: Boolean, easing: Easing = QuadInOut, then: Float? = null) {
        if (still) { direct(next); return }
        from = value; target = next; duration = milliseconds * 1e6f; origin = System.nanoTime(); curve = easing
        velocity = 0f; mode = 1; after = then
    }
    fun spring(next: Float, still: Boolean, speed: Float = 0f) {
        if (still) { direct(next); return }
        val continuing = mode == 2 && target == next && speed == 0f
        if (!continuing) {
            velocity = (if (mode == 2) velocity else 0f) + speed
            if ((next > value && velocity < 0f) || (next < value && velocity > 0f)) velocity = 0f
            initialEnergy = 0.5f * STEP_STIFFNESS * (value - next) * (value - next) + 0.5f * STEP_MASS * speed * speed
        }
        target = next; mode = 2; after = null
    }
    fun advance(now: Long, dt: Float) {
        if (mode == 1) {
            val u = ((now - origin) / duration).coerceIn(0f, 1f)
            state.floatValue = from + (target - from) * curve.transform(u)
            if (u >= 1f) { mode = 0; val next = after; after = null; if (next != null) spring(next, false) }
        } else if (mode == 2) {
            val omega = sqrt(STEP_STIFFNESS / STEP_MASS)
            val decay = STEP_DAMPING / (2f * STEP_MASS)
            val e = value - target
            val envelope = exp(-decay * dt)
            val next: Float
            if (decay < omega) {
                val damped = sqrt(omega * omega - decay * decay)
                val s = sin(damped * dt); val c = cos(damped * dt)
                next = envelope * (e * c + (velocity + decay * e) / damped * s)
                velocity = envelope * (velocity * c - (decay * velocity + omega * omega * e) / damped * s)
            } else {
                val critical = exp(-omega * dt)
                val b = velocity + omega * e
                next = critical * (e + b * dt)
                velocity = critical * (velocity - omega * b * dt)
            }
            state.floatValue = target + next
            val energy = 0.5f * STEP_STIFFNESS * next * next + 0.5f * STEP_MASS * velocity * velocity
            if (initialEnergy == 0f || energy / initialEnergy <= SPRING_ENERGY_THRESHOLD) direct(target)
        }
    }
}
private class Tape(first: Int) {
    val place = TapeMotion(first.toFloat())
    val up = TapeMotion(0f)
    val down = TapeMotion(0f)
    var caught = first.toFloat()
    var dragging = false
    var nearest = first
}

/** A clay product screen with a paper quantity tape that shoots over an orange drum. */
@Composable
fun PaperTapeQuantity(
    quantity: Double = 1.0,
    max: Int = 4,
    price: Double = 49.0,
    symbol: String = "$",
    name: String = "Halo Speaker",
    description: String = "Desk speaker · 12-hour battery",
    finishes: List<Finish> = Finishes,
    finish: Int = 1,
    finishLabel: String = "Finish",
    addLabel: String = "Add to cart",
    deliveryLabel: String = "Order today to get it by Friday",
    accent: Color = Accent,
    autoPlay: Boolean = false,
    onChange: ((quantity: Int) -> Unit)? = null,
    onAdd: ((quantity: Int, total: Double) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onBag: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val reduced by rememberUpdatedState(still)
    val view = LocalView.current
    val density = LocalDensity.current
    val ceiling by rememberUpdatedState(max.coerceAtLeast(1))
    val first = min(ceiling, max(1, floor(quantity + 0.5).toInt()))
    val initial by rememberUpdatedState(first)
    val changed by rememberUpdatedState(onChange)
    var count by remember { mutableIntStateOf(first) }
    var chosen by remember { mutableIntStateOf(finish) }
    var touches by remember { mutableIntStateOf(0) }
    val tape = remember { Tape(first) }
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    fun haptic(kind: String) {
        view.performHapticFeedback(when (kind) {
            "tick" -> HapticFeedbackConstants.CLOCK_TICK
            "limit" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            "press" -> HapticFeedbackConstants.VIRTUAL_KEY
            else -> HapticFeedbackConstants.CONTEXT_CLICK
        })
    }
    fun touched() { touches++ }
    fun set(next: Int, felt: Boolean) { count = next; if (felt) haptic("tick"); changed?.invoke(next) }
    fun nearest() = floor(tape.place.value + 0.5f).toInt().coerceIn(1, ceiling)
    fun followed() {
        val next = nearest()
        if (tape.dragging && next != tape.nearest) set(next, true)
        tape.nearest = next
    }
    fun step(by: Int, felt: Boolean) {
        val next = (count + by).coerceIn(1, ceiling)
        if (next == count) {
            if (felt) haptic("limit")
            if (!reduced) tape.place.timing(count + by * NUDGE, NUDGE_IN, false, then = count.toFloat())
            return
        }
        if (felt) haptic("step")
        count = next; changed?.invoke(next)
        tape.place.spring(next.toFloat(), reduced)
    }
    fun reset() { count = initial; tape.place.spring(initial.toFloat(), reduced) }
    fun settle(to: Int) { count = to; changed?.invoke(to); touched() }
    val latestStep by rememberUpdatedState(::step)
    val latestReset by rememberUpdatedState(::reset)
    val latestSettle by rememberUpdatedState(::settle)
    val latestFollowed by rememberUpdatedState(::followed)
    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceIn(0f, 0.064f); last = now
            tape.place.advance(now, dt); tape.up.advance(now, dt); tape.down.advance(now, dt)
            latestFollowed()
        }
    }
    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        try {
            if (touches > 0) { delay((USER_HOLD * 1000).toLong()); latestReset(); delay((RETURN * 1000).toLong()) }
            coroutineScope {
                var origin = System.nanoTime()
                while (true) {
                    val start = origin
                    var at = FIRST_AT
                    Presses.forEachIndexed { index, by ->
                        if (index > 0 && by != Presses[index - 1]) at += TURN_PAUSE
                        val due = at
                        launch {
                            delay(max(0L, (start + ((due - PRESS_FOR) * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
                            val held = if (by > 0) tape.up else tape.down
                            held.timing(1f, HOLD_IN, reduced)
                            delay(max(0L, (start + (due * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
                            held.timing(0f, HOLD_OUT, reduced); latestStep(by, false)
                        }
                        at += STEP_EVERY
                    }
                    delay(max(0L, (start + (LOOP * 1e9f).toLong() - System.nanoTime()) / 1_000_000)); origin += (LOOP * 1e9f).toLong()
                }
            }
        } finally { tape.up.direct(0f); tape.down.direct(0f) }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Surface)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val lane = reach(ceiling)
        val finishArea = max(0f, lane - BAR_GAP)
        val bottom = max(lane - 38f, 24f) + max(insets.calculateBottomPadding().value - 14f, 8f)
        val fixed = NAV + 14f + 20f + 32f + 2f + 20f + 15f + finishArea + 15f + BAR_GAP + BAR + 26f + 24f + bottom
        val hero = max(120f, height - insets.calculateTopPadding().value - fixed)
        Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).padding(top = insets.calculateTopPadding())) {
            Row(Modifier.fillMaxWidth().height(NAV.dp).padding(horizontal = MARGIN.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Round("Back", false) { touched(); onBack?.invoke() }
                Round("Bag", true) { touched(); onBag?.invoke() }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.padding(horizontal = MARGIN.dp).fillMaxWidth().height(hero.dp), contentAlignment = Alignment.Center) {
                val shadow = remember { PaperTapeQuantityPaint() }
                Canvas(Modifier.matchParentSize()) { surface(0.dp) { _, _, w, h ->
                    drawRoundRect(Surface, size = Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(30f))
                    drawIntoCanvas {
                        shadow.inset(it.nativeCanvas, 0f, 0f, w, h, 30f, 4f, 5f, rgba(60, 50, 40, 0.13f), 5f)
                        shadow.inset(it.nativeCanvas, 0f, 0f, w, h, 30f, -4f, -4f, Color.White.copy(alpha = 0.85f), 5f)
                    }
                } }
                Speaker(finishes[chosen])
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = (MARGIN + 4f).dp), verticalAlignment = Alignment.Bottom) {
                BasicText(name, style = textStyle(26f, 32f, FontWeight.Bold, Ink, -0.3f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).alignByBaseline())
                Spacer(Modifier.width(12.dp))
                BasicText(money(symbol, price), style = textStyle(20f, 26f, FontWeight.SemiBold, Ink).copy(fontFeatureSettings = "tnum"), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
            }
            BasicText(description, style = textStyle(15f, 20f, FontWeight.Normal, Mute), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, start = (MARGIN + 4f).dp, end = (MARGIN + 4f).dp).height(20.dp))
            Rule()
            Box(Modifier.fillMaxWidth().height(finishArea.dp).padding(start = LANE.dp).semantics { contentDescription = finishLabel }, contentAlignment = Alignment.CenterStart) {
                Column(Modifier.wrapContentHeight(unbounded = true)) {
                    BasicText(finishLabel.uppercase(Locale.US), style = textStyle(12f, 16f, FontWeight.SemiBold, Mute, 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                    for ((index, item) in finishes.withIndex()) Row(Modifier.fillMaxWidth().height(ROW.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) { touched(); haptic("tick"); chosen = index }
                        .semantics { selected = index == chosen }, verticalAlignment = Alignment.CenterVertically) {
                        Bead(item, index == chosen)
                        BasicText(item.name, style = textStyle(19f, 24f, FontWeight.Medium, if (index == chosen) Ink else Mute), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            Rule()
            Spacer(Modifier.height(BAR_GAP.dp))
            Box(Modifier.fillMaxWidth().height(BAR.dp).zIndex(1f)) {
                Roller(width, MARGIN, ceiling, tape.place, tape.up, tape.down, (count < ceiling), (count > 1), still,
                    Modifier.offset(y = -(lane + BLEED).dp))
                Box(Modifier.offset(x = (MARGIN + DRUM_AT - DRUM_WIDTH / 2f - 6f).dp).size((DRUM_WIDTH + 12f).dp, BAR.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            touched()
                            val tracker = VelocityTracker(); tracker.addPosition(down.uptimeMillis, down.position)
                            var active = false
                            var completed = false
                            var translation = 0f
                            var originY = down.position.y
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(finger.uptimeMillis, finger.position)
                                    translation = (finger.position.y - originY) / density.density
                                    if (!finger.pressed) { completed = !finger.isConsumed; if (active) finger.consume(); break }
                                    if (finger.isConsumed) break
                                    if (!active && abs(translation) > 3f) {
                                        active = true; originY = finger.position.y; translation = 0f
                                        tape.place.cancel(); tape.caught = tape.place.value; tape.dragging = true
                                    }
                                    if (active) {
                                        val raw = tape.caught - translation / PITCH
                                        val to = if (raw < 1f) 1f - (1f - raw) * RUBBER else if (raw > ceiling) ceiling + (raw - ceiling) * RUBBER else raw
                                        tape.place.direct(to); latestFollowed(); finger.consume()
                                    }
                                }
                                if (active && completed) {
                                    tape.dragging = false
                                    val speed = -tracker.calculateVelocity().y / density.density / PITCH
                                    val to = floor(tape.place.value + speed * FLICK + 0.5f).toInt().coerceIn(1, ceiling)
                                    tape.place.spring(to.toFloat(), reduced, speed); latestSettle(to)
                                }
                            } finally { tape.dragging = false }
                        }
                    }.semantics {
                        contentDescription = "Quantity"
                        progressBarRangeInfo = ProgressBarRangeInfo(count.toFloat(), 1f..ceiling.toFloat(), max(0, ceiling - 1))
                        customActions = listOf(
                            CustomAccessibilityAction("Increment") { touched(); step(1, true); true },
                            CustomAccessibilityAction("Decrement") { touched(); step(-1, true); true },
                        )
                    })
                Arrow(tape.up, still, "One more", true, Modifier.offset(x = (MARGIN + TILE_AT).dp, y = ((BAR - TILE_HEIGHT) / 2f).dp)) { touched(); step(1, true) }
                Arrow(tape.down, still, "One fewer", false, Modifier.offset(x = (MARGIN + TILE_AT).dp, y = (BAR / 2f).dp)) { touched(); step(-1, true) }
                val addInteraction = remember { MutableInteractionSource() }
                val pressed by addInteraction.collectIsPressedAsState()
                val alpha by cssTween(if (pressed) 0.45f else 1f, 120)
                Box(Modifier.offset(x = (MARGIN + TILE_AT + TILE_WIDTH + 14f).dp).width((width - MARGIN * 2f - TILE_AT - TILE_WIDTH - 14f).dp).height(BAR.dp)
                    .clickable(addInteraction, indication = null, role = Role.Button) { touched(); haptic("press"); onAdd?.invoke(count, price * count) }
                    .semantics { contentDescription = "$addLabel, $count, ${money(symbol, price * count)}" }, contentAlignment = Alignment.CenterStart) {
                    Column(Modifier.graphicsLayer { this.alpha = alpha }) {
                        BasicText(addLabel, style = textStyle(21f, 26f, FontWeight.Medium, Text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IndependentFade(count, money(symbol, price * count), TOTAL_FADE, still) { total ->
                            BasicText(total, style = textStyle(15f, 20f, FontWeight.Medium, Mute).copy(fontFeatureSettings = "tnum"), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 26.dp).height(24.dp)) {
                Box(Modifier.align(Alignment.CenterStart).offset(x = (MARGIN + 6f).dp).size(8.dp).background(accent, androidx.compose.foundation.shape.RoundedCornerShape(1.5.dp)))
                Row(Modifier.offset(x = LANE.dp).height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(20.dp)) { surface(0.dp) { _, _, _, _ ->
                        drawCircle(Text, 8.5f, Offset(10f, 10f)); drawLine(Surface, Offset(10f, 5f), Offset(10f, 10f), 1.6f, StrokeCap.Round); drawLine(Surface, Offset(10f, 10f), Offset(14f, 12f), 1.6f, StrokeCap.Round)
                    } }
                    BasicText(deliveryLabel, style = textStyle(15f, 20f, FontWeight.Normal, Text), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Spacer(Modifier.height(bottom.dp))
        }
    }
}

@Composable
private fun cssTween(target: Float, milliseconds: Int): State<Float> {
    val value = remember { mutableFloatStateOf(target) }
    LaunchedEffect(target, milliseconds) {
        val from = value.floatValue; val origin = System.nanoTime()
        do {
            val u = ((withFrameNanos { it } - origin) / (milliseconds * 1e6f)).coerceIn(0f, 1f)
            value.floatValue = from + (target - from) * CssEase.transform(u)
        } while (u < 1f)
    }
    return value
}

@Composable
private fun Arrow(held: TapeMotion, still: Boolean, label: String, up: Boolean, modifier: Modifier, onPress: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) { held.timing(if (pressed) 1f else 0f, if (pressed) HOLD_IN else HOLD_OUT, still) }
    Box(modifier.offset(x = (-6).dp, y = if (up) (-14).dp else 0.dp).size((TILE_WIDTH + 6f + 8f).dp, (TILE_HEIGHT / 2f + 14f).dp).clickable(interaction, indication = null, role = Role.Button, onClick = onPress).semantics { contentDescription = label })
}

@Composable
private fun Rule() {
    Canvas(Modifier.padding(top = 14.dp, start = MARGIN.dp).fillMaxWidth().height(1.dp)) { surface(0.dp) { _, _, w, _ ->
        drawRect(Color.Black.copy(alpha = 0.07f), size = Size(w, 1f)); drawRect(Color.White.copy(alpha = 0.75f), Offset(0f, 1f), Size(w, 1f))
    } }
}

@Composable
private fun Round(label: String, bag: Boolean, onPress: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by cssTween(if (pressed) 0.94f else 1f, 120)
    val paint = remember { PaperTapeQuantityPaint() }
    Box(Modifier.size(ROUND.dp)) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }.bleed(24.dp)) {
            surface(24.dp) { x, y, _, _ ->
                drawIntoCanvas {
                    paint.round(it.nativeCanvas, x + 5f, y + 6f, ROUND, ROUND, ROUND / 2f, rgba(60, 50, 40, 0.16f), 6f)
                    paint.round(it.nativeCanvas, x - 4f, y - 4f, ROUND, ROUND, ROUND / 2f, Color.White.copy(alpha = 0.9f), 5f)
                }
                drawCircle(Surface, ROUND / 2f, Offset(x + ROUND / 2f, y + ROUND / 2f))
                val icon = Path().apply {
                    if (bag) { moveTo(x + 12f, y + 17f); lineTo(x + 28f, y + 17f); lineTo(x + 29f, y + 29f); lineTo(x + 11f, y + 29f); close(); moveTo(x + 16f, y + 17f); cubicTo(x + 16f, y + 10f, x + 24f, y + 10f, x + 24f, y + 17f) }
                    else { moveTo(x + 23f, y + 13f); lineTo(x + 16f, y + 20f); lineTo(x + 23f, y + 27f) }
                }
                drawPath(icon, Text, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Box(Modifier.matchParentSize().bleed(6.dp).clickable(interaction, indication = null, role = Role.Button, onClick = onPress).semantics { contentDescription = label })
    }
}

@Composable
private fun Bead(finish: Finish, on: Boolean) {
    val paint = remember { PaperTapeQuantityPaint() }
    Canvas(Modifier.size(BEAD.dp).clipToBounds().clearAndSetSemantics {}) { surface(0.dp) { _, _, _, _ ->
        val c = BEAD / 2f
        drawIntoCanvas {
            if (on) paint.circle(it.nativeCanvas, c, c, 11f, finish.color, 4f, 0.55f)
            paint.circle(it.nativeCanvas, c + 1f, c + 2f, 9f, Color.Black.copy(alpha = 0.25f), 2f)
        }
        drawCircle(Color(0xFFF6F5F3), 9.5f, Offset(c, c))
        drawCircle(Brush.radialGradient(0f to finish.light, 0.5f to finish.color, 1f to finish.dark, center = Offset(c - 2.5f, c - 3f), radius = 10f), if (on) 7f else 7.6f, Offset(c, c))
        drawIntoCanvas { paint.oval(it.nativeCanvas, c - 4.6f, c - 5.4f, 5f, 3f, Color.White.copy(alpha = 0.75f), 0.8f) }
    } }
}

@Composable
private fun Speaker(finish: Finish) {
    val paint = remember { PaperTapeQuantityPaint() }
    Canvas(Modifier.requiredSize(SPEAKER.dp).clipToBounds().clearAndSetSemantics {}) { surface(0.dp) { _, _, _, _ ->
        val c = SPEAKER / 2f
        drawIntoCanvas {
            paint.circle(it.nativeCanvas, c + 8f, c + 11f, 62f, rgba(60, 50, 40, 0.3f), 14f)
            paint.circle(it.nativeCanvas, c - 6f, c - 7f, 62f, Color.White.copy(alpha = 0.95f), 10f)
        }
        drawCircle(Brush.radialGradient(0f to Color(0xFFF6F5F3), 0.55f to Color(0xFFE4E3E1), 1f to Color(0xFFCBCAC8), center = Offset(c - 22f, c - 26f), radius = 110f), 66f, Offset(c, c))
        drawCircle(Brush.radialGradient(0f to Color(0xFFE9E8E6), 0.55f to Color(0xFFD6D5D3), 1f to Color(0xFFC2C1BF), center = Offset(c + 14f, c + 18f), radius = 80f), 47f, Offset(c, c))
        drawCircle(Color.White.copy(alpha = 0.8f), 47f, Offset(c, c), style = Stroke(1.2f))
        for (index in 0 until 20) {
            val angle = index / 20f * Math.PI.toFloat() * 2f
            val x = c + cos(angle) * 34f; val y = c + sin(angle) * 34f
            drawCircle(Color.White.copy(alpha = 0.8f), 2.3f, Offset(x + 0.6f, y + 0.8f)); drawCircle(Color(0xFF9F9E9C), 2.3f, Offset(x, y))
        }
        drawIntoCanvas { paint.circle(it.nativeCanvas, c, c, 21f, finish.color, 9f, 0.5f) }
        drawCircle(Color(0xFFF4F3F1), 17f, Offset(c, c))
        drawCircle(Brush.radialGradient(0f to finish.light, 0.5f to finish.color, 1f to finish.dark, center = Offset(c - 4.5f, c - 5.5f), radius = 18f), 13.5f, Offset(c, c))
        drawIntoCanvas { paint.oval(it.nativeCanvas, c - 8.5f, c - 9.5f, 9f, 5.5f, Color.White.copy(alpha = 0.7f), 1.4f) }
    } }
}

/** Key reversals create new entering text while each departing snapshot finishes its own fade. */
private class FadeDeck<T>(var chosen: Int, var current: T) {
    data class Departure<T>(val id: Long, val value: T, val origin: Long)
    var sequence = 0L
    var entering: Long? = null
    var departing = emptyList<Departure<T>>()
    fun update(next: Int, value: T, still: Boolean) {
        if (next != chosen) {
            val now = System.nanoTime()
            sequence++
            if (!still) departing = departing + Departure(sequence, current, now)
            entering = if (still) null else now
            chosen = next
        }
        current = value
        if (still) { departing = emptyList(); entering = null }
    }
}

@Composable
private fun <T> IndependentFade(chosen: Int, value: T, milliseconds: Int, still: Boolean, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val deck = remember { FadeDeck(chosen, value).apply { entering = if (still) null else System.nanoTime() } }
    var now by remember { mutableStateOf(System.nanoTime()) }
    deck.update(chosen, value, still)
    val duration = milliseconds * 1_000_000L
    LaunchedEffect(deck.sequence, still) {
        while (deck.entering != null || deck.departing.isNotEmpty()) {
            val frame = withFrameNanos { it }
            deck.departing = deck.departing.filter { frame - it.origin < duration }
            if (deck.entering?.let { frame - it >= duration } == true) deck.entering = null
            now = frame
        }
    }
    fun progress(origin: Long) = QuadInOut.transform(((now - origin).toFloat() / duration).coerceIn(0f, 1f))
    Box(modifier) {
        for (layer in deck.departing) key(layer.id) {
            Box(Modifier.graphicsLayer { alpha = 1f - progress(layer.origin); compositingStrategy = CompositingStrategy.ModulateAlpha }) { content(layer.value) }
        }
        Box(Modifier.graphicsLayer { alpha = deck.entering?.let(::progress) ?: 1f; compositingStrategy = CompositingStrategy.ModulateAlpha }) { content(deck.current) }
    }
}
