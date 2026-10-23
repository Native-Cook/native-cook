package com.ngocdevv.nativecook.blocks.forms.splitbillclaim

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BlurMaskFilter
import android.graphics.Paint
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

import java.math.BigDecimal
/** An item on the receipt; its price is in units of the currency. */
data class BillItem(val name: String, val price: Double)
private val Items = listOf(BillItem("Sparkling Water - 0,5 l", 1.5), BillItem("Ginger Lemonade - 0,5 l", 3.25), BillItem("Pizza - Margherita", 21.0))
const val LOOP = 9.8f
private val Grabs = floatArrayOf(0.8f, 2.9f, 5.3f, 7f)
private const val GRAB_FOR = 0.15f
private const val PULL_FOR = 0.6f
private const val HOLD_FOR = 0.35f
private const val PULL_TO = 98f
private const val USER_HOLD = 3f
private const val RESET_FOR = 0.9f
private val Sheet = Color(0xFFF3F5F8)
private val Ink = Color(0xFF1F2A3D)
private val Mute = Color(0xFF7C8798)
private val Grip = Color(0xFFC3CAD6)
private val Tint = Color(0xFFEDF3FF)
private val Accent = Color(0xFF1E6BF2)
private const val MARGIN = 22f
private const val CARD = 96f
private const val CARD_GAP = 8f
private const val CARD_RADIUS = 6f
private const val GRIP_X = 30f
private const val NAME_X = 54f
private const val PRICE_INSET = 26f
private const val AVATAR = 46f
private const val AVATAR_SMALL = 22f
private const val BADGE = 18f
private const val SEAT_X = NAME_X + AVATAR_SMALL / 2f
private const val SEAT_Y = 15f
private const val NAME_LIFT = 12f
private const val NAME_PUSH = 22f
private const val TOUCH = 50f
private const val TOUCH_X = 88f
private const val PANEL = 60f
private const val PAY = 58f
private const val BAR_GAP = 6f
private const val PULL_MAX = 104f
private const val CLAIM_AT = 68f
private const val BACK_MASS = 1f
private const val BACK_STIFFNESS = 260f
private const val BACK_DAMPING = 22f
private const val HOP = 460
private const val HOP_HEIGHT = 20f
private const val BAR_MASS = 1f
private const val BAR_STIFFNESS = 190f
private const val BAR_DAMPING = 20f
private const val BAR_AFTER = 320
private const val COUNT = 520
private const val RISE = 420
private const val RISE_AFTER = 180
private const val RISE_STAGGER = 90
private const val TOUCH_IN_MS = 140
private const val TOUCH_OUT_MS = 160
private const val BAR_OUT_MS = 260
private const val PAY_PRESS_MS = 110
private const val FRAME_MAX_MS = 64f
private const val RISE_DISTANCE = 25f
private const val PAN_ACTIVATE = 10f
private const val PAN_FAIL_Y = 14f
private const val SHADOW_SPILL = 48f
private const val SPRING_ENERGY_THRESHOLD = 6e-9f
private val QuadInOut = Easing { t -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f }
private val Linear = Easing { it }
private val PullEase = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val Hair = PathParser().parsePathString("M14.6 21C13.4 12.6 17.6 8 24.2 8C31 8 34.6 12.4 33.4 20.6C32.4 17.6 30.4 15.6 27.4 15C23.6 16.8 18.6 16.6 15.8 15.6C15.2 17.2 15 19 14.6 21Z").toPath()
private val BackPath = PathParser().parsePathString("M7.5 1.5L1.5 7.5L7.5 13.5").toPath()
private fun smooth(t: Float, from: Float, to: Float): Float { val u = ((t - from) / (to - from)).coerceIn(0f, 1f); return u * u * (3f - 2f * u) }
private fun rounded(value: Double) = floor(value + 0.5)
private fun number(value: Double): String = if (value.isFinite() && kotlin.math.abs(value) < 1e21) BigDecimal.valueOf(if (value == 0.0) 0.0 else value).stripTrailingZeros().toPlainString() else value.toString().replace("E", "e+").replace("e+-", "e-").replace(".0e", "e")
// Keep the source's remainder format, including its unusual result for negative cents.
private fun money(cents: Double, symbol: String) = "$symbol${number(floor(cents / 100))}.${number(cents % 100).padStart(2, '0')}"
private fun mix(a: Color, b: Color, t: Float): Color {
    val u = t.coerceIn(0f, 1f)
    fun channel(x: Float, y: Float) = ((x.toDouble().pow(2.2) + (y.toDouble().pow(2.2) - x.toDouble().pow(2.2)) * u).pow(1.0 / 2.2) * 255).roundToInt() / 255f
    return Color(channel(a.red, b.red), channel(a.green, b.green), channel(a.blue, b.blue), a.alpha + (b.alpha - a.alpha) * u)
}
private fun textStyle(size: Float, line: Float, weight: FontWeight, color: Color, tabular: Boolean = false, spacing: Float = 0f) = TextStyle(
    color = color, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp, fontFeatureSettings = if (tabular) "tnum" else null,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None), platformStyle = PlatformTextStyle(includeFontPadding = false),
)
private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }
/** Never clocks keep running; System callers explicitly jump under reduced motion. */
private class Motion(initial: Float) : State<Float> {
    private val state = mutableFloatStateOf(initial)
    override val value get() = state.floatValue
    private var target = initial
    private var velocity = 0f
    private var mass = 1f
    private var stiffness = 1f
    private var damping = 1f
    private var energy0 = 0f
    private var springing = false
    private var origin = 0L
    private var duration = 0f
    private var from = initial
    private var curve: Easing = QuadInOut
    private var timing = false
    private var completed: (() -> Unit)? = null
    fun cancel() { timing = false; springing = false; completed = null; velocity = 0f }
    fun direct(next: Float) { cancel(); state.floatValue = next; target = next; velocity = 0f }
    val active get() = timing || springing
    fun spring(next: Float, m: Float, k: Float, c: Float, still: Boolean = false) {
        if (still) { direct(next); return }
        if (value == next) { direct(next); return }
        if (springing && target == next) return
        if ((next > value && velocity < 0f) || (next < value && velocity > 0f)) velocity = 0f
        energy0 = 0.5f * k * (value - next).pow(2)
        mass = m; stiffness = k; damping = c; target = next; springing = true; timing = false; completed = null
    }
    fun tween(next: Float, ms: Int, easing: Easing = QuadInOut, higherOrder: Boolean = false, still: Boolean = false, onFinished: (() -> Unit)? = null) {
        if (still) { direct(next); onFinished?.invoke(); return }
        if (value == next && !higherOrder) { direct(next); onFinished?.invoke(); return }
        if (timing && target == next) { duration = ms * 1e6f; curve = easing; completed = onFinished; return }
        from = value; target = next; duration = ms * 1e6f; curve = easing; origin = System.nanoTime(); timing = true; springing = false; completed = onFinished; velocity = 0f
    }
    fun advance(now: Long, dt: Float) {
        if (timing) {
            val t = ((now - origin) / duration).coerceIn(0f, 1f)
            state.floatValue = from + (target - from) * curve.transform(t)
            if (t >= 1f) { timing = false; val finish = completed; completed = null; finish?.invoke(); return }
        }
        if (!springing) return
        val omega = sqrt(stiffness / mass); val decay = damping / (2f * mass); val e = value - target
        val next: Float
        if (decay < omega) {
            val w = sqrt(omega * omega - decay * decay); val s = sin(w * dt); val c = cos(w * dt); val envelope = exp(-decay * dt)
            next = envelope * (e * c + (velocity + decay * e) / w * s)
            velocity = envelope * (velocity * c - (decay * velocity + omega * omega * e) / w * s)
        } else {
            val envelope = exp(-omega * dt); val b = velocity + omega * e
            next = envelope * (e + b * dt); velocity = envelope * (velocity - omega * b * dt)
        }
        state.floatValue = target + next
        val energy = 0.5f * stiffness * next * next + 0.5f * mass * velocity * velocity
        if (energy0 == 0f || energy / energy0 <= SPRING_ENERGY_THRESHOLD) { state.floatValue = target; velocity = 0f; springing = false }
    }
}


private class ShareCounter : State<Double> {
    private val state = mutableStateOf(0.0)
    override val value get() = state.value
    private var target = 0.0
    private var from = 0.0
    private var origin = 0L
    private var active = false
    fun to(next: Double) {
        if (value == next) { state.value = next; active = false; target = next; return }
        if (active && target == next) return
        from = value; target = next; origin = System.nanoTime(); active = true
    }
    fun advance(now: Long) {
        if (!active) return
        val u = ((now - origin) / (COUNT * 1e6)).coerceIn(0.0, 1.0)
        state.value = from + (target - from) * (1 - (1 - u).pow(3))
        if (u >= 1.0) active = false
    }
}

/** A replacement delay continues its preceding delay and trajectory until its child starts. */
private class BarClock : State<Float> {
    private val state = mutableFloatStateOf(0f)
    override val value get() = state.floatValue
    private class Delay(val to: Float, val origin: Long, val delay: Int, initial: Float, var previous: Delay?) {
        var current = initial
        private var child: Motion? = null
        private var last = origin
        var finished = false; private set
        fun advance(now: Long) {
            if (finished) return
            if (now - origin >= delay * 1_000_000L) {
                if (child == null) {
                    // The delayed wrapper has no spring velocity for the child to inherit.
                    child = Motion(current).also { if (to > 0f) it.spring(to, BAR_MASS, BAR_STIFFNESS, BAR_DAMPING) else it.tween(to, BAR_OUT_MS, higherOrder = true) }
                    previous = null; last = now
                }
                child!!.advance(now, ((now - last) / 1e9f).coerceIn(0f, FRAME_MAX_MS / 1000f)); last = now
                current = child!!.value; finished = !child!!.active
            } else previous?.let {
                it.advance(now); current = it.current
                if (it.finished) previous = null
            }
        }
    }
    private var animation: Delay? = null
    fun to(next: Float, still: Boolean) {
        if (still) { state.floatValue = next; animation = null; return }
        animation = Delay(next, System.nanoTime(), if (next > 0f && value > 0f) 0 else BAR_AFTER, value, animation)
    }
    fun advance(now: Long) { animation?.let { it.advance(now); state.floatValue = it.current; if (it.finished) animation = null } }
}
private class RowMotion(index: Int, claimed: Boolean) {
    val x = Motion(0f)
    val mine = Motion(if (claimed) 1f else 0f)
    val touch = Motion(0f)
    var from by mutableFloatStateOf(PULL_TO)
    var armed = false
    val rise = mutableFloatStateOf(0f)
    private val appears = System.nanoTime() + (RISE_AFTER + index * RISE_STAGGER) * 1_000_000L
    fun advance(now: Long, dt: Float, still: Boolean) {
        x.advance(now, dt); mine.advance(now, dt); touch.advance(now, dt)
        val u = if (still) 1f else ((now - appears) / (RISE * 1e6f)).coerceIn(0f, 1f)
        rise.floatValue = QuadInOut.transform(u)
    }
    fun settle(still: Boolean) { touch.tween(0f, TOUCH_OUT_MS, still = still); x.spring(0f, BACK_MASS, BACK_STIFFNESS, BACK_DAMPING, still) }
    fun grab() { touch.tween(1f, TOUCH_IN_MS) }
    fun pull(to: Float, seconds: Float) { x.tween(to, (seconds * 1000).roundToInt(), PullEase) }
    fun letGo(still: Boolean, toggle: () -> Unit) {
        val pulled = x.value
        touch.tween(0f, TOUCH_OUT_MS); x.spring(0f, BACK_MASS, BACK_STIFFNESS, BACK_DAMPING, still)
        if (pulled >= CLAIM_AT) { from = pulled; toggle() }
    }
}

/** Claim receipt rows by pulling them right; the drawn portrait hops into each claimed card. */
@Composable
fun SplitBillClaim(
    autoPlay: Boolean = false,
    autoPlayRows: List<Int> = listOf(0, 2),
    items: List<BillItem> = Items,
    symbol: String = "$",
    title: String = "Pick your items",
    hint: String = "Swipe right to assign an item to yourself.",
    backLabel: String = "Back",
    totalLabel: String = "In total",
    payLabel: String = "Pay",
    accent: Color = Accent,
    onChange: ((claimed: List<Int>) -> Unit)? = null,
    onPay: ((share: Double, claimed: List<Int>) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val reduced by rememberUpdatedState(still)
    val view = LocalView.current
    val density = LocalDensity.current
    val changed by rememberUpdatedState(onChange)
    val latestItems by rememberUpdatedState(items)
    val latestCents by rememberUpdatedState(items.map { rounded(it.price * 100) })
    var claimed by remember { mutableStateOf(items.map { false }) }
    var count by remember { mutableIntStateOf(1) }
    var touches by remember { mutableIntStateOf(0) }
    var counted by remember { mutableStateOf(0.0) }
    val share = remember { ShareCounter() }
    val bar = remember { BarClock() }
    val registered = remember { mutableMapOf<Int, RowMotion>() }
    registered.keys.filter { it >= items.size }.forEach { registered.remove(it) }
    val rows = items.indices.map { registered.getOrPut(it) { RowMotion(it, claimed.getOrNull(it) == true) } }
    val latestRows by rememberUpdatedState(rows)
    fun apply(next: List<Boolean>) {
        val mine = next.indices.filter { next[it] }
        val sum = mine.sumOf { latestCents.getOrNull(it) ?: Double.NaN }
        claimed = next; if (mine.isNotEmpty()) count = mine.size
        share.to(sum); bar.to(if (mine.isNotEmpty()) 1f else 0f, reduced); changed?.invoke(mine)
    }
    fun toggle(index: Int, hand: Boolean) {
        if (hand) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        apply(claimed.mapIndexed { at, yes -> if (at == index) !yes else yes })
    }
    fun touched() { latestRows.forEach { it.settle(reduced) }; touches++ }
    val latestApply by rememberUpdatedState(::apply)
    val latestToggle by rememberUpdatedState(::toggle)
    val latestTouched by rememberUpdatedState(::touched)
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        while (true) {
            val now = withFrameNanos { it }; val dt = ((now - last) / 1e9f).coerceIn(0f, FRAME_MAX_MS / 1000f); last = now
            latestRows.forEach { it.advance(now, dt, reduced) }; share.advance(now); bar.advance(now)
            counted = rounded(share.value)
        }
    }
    LaunchedEffect(autoPlay, autoPlayRows, items, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong()); latestRows.forEach { it.settle(reduced) }; latestApply(latestItems.map { false })
            delay((RESET_FOR * 1000).toLong())
        }
        val order = autoPlayRows + autoPlayRows.asReversed()
        coroutineScope {
            var origin = System.nanoTime()
            while (true) {
                val cycle = origin
                for ((index, row) in order.withIndex()) {
                    val at = Grabs[index % Grabs.size] + floor(index.toFloat() / Grabs.size) * LOOP
                    fun after(seconds: Float, run: () -> Unit) = launch {
                        delay(max(0L, (cycle + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000)); run()
                    }
                    after(at) { latestRows.getOrNull(row)?.grab() }
                    after(at + GRAB_FOR) { latestRows.getOrNull(row)?.pull(PULL_TO, PULL_FOR) }
                    after(at + GRAB_FOR + PULL_FOR + HOLD_FOR) { latestRows.getOrNull(row)?.letGo(reduced) { latestToggle(row, false) } }
                }
                delay(max(0L, (cycle + (LOOP * 1e9f).toLong() - System.nanoTime()) / 1_000_000)); origin += (LOOP * 1e9f).toLong()
            }
        }
    }
    val mine = claimed.indices.filter { claimed[it] }
    val total = latestCents.sum()
    BoxWithConstraints(modifier.fillMaxSize().background(Sheet)) {
        val width = maxWidth.value; val height = maxHeight.value
        val inset = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
        val bottom = max(inset.calculateBottomPadding().value, 12f) + 6f
        Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) {
            Column(Modifier.fillMaxWidth().background(Color.White).padding(start = (MARGIN + 2f).dp, end = (MARGIN + 2f).dp, top = (inset.calculateTopPadding().value + 4f).dp, bottom = 28.dp)) {
                Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    Box {
                        Row(Modifier.graphicsLayer { alpha = if (pressed) 0.5f else 1f }, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(9.dp, 15.dp).clearAndSetSemantics {}) { surface(0.dp) { _, _, _, _ -> drawPath(BackPath, accent, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)) } }
                            BasicText(backLabel, style = textStyle(17f, 22f, FontWeight.Medium, accent))
                        }
                        Box(Modifier.matchParentSize().bleed(10.dp).clickable(interaction, indication = null, role = Role.Button) { touched(); onBack?.invoke() }.semantics { contentDescription = backLabel })
                    }
                    Avatar(30f)
                }
                BasicText(title, style = textStyle(24f, 30f, FontWeight.Bold, Ink, spacing = -0.2f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 18.dp))
                BasicText(hint, style = textStyle(14f, 19f, FontWeight.Normal, Mute), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
            Column(Modifier.padding(horizontal = MARGIN.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().height(74.dp).padding(start = 4.dp, end = 4.dp, top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    BasicText(totalLabel, style = textStyle(15f, 20f, FontWeight.Normal, Ink))
                    BasicText(money(total, symbol), style = textStyle(15f, 20f, FontWeight.SemiBold, Ink, true))
                }
                for ((index, item) in items.withIndex()) key(index) { ClaimRow(item, claimed.getOrNull(index) == true, symbol, accent, rows[index], still, { hand -> latestToggle(index, hand) }, { latestTouched() }) }
            }
        }
        val barHeight = PANEL + BAR_GAP + PAY
        Column(Modifier.offset(x = MARGIN.dp, y = (height - bottom - barHeight).dp).width(max(0f, width - MARGIN * 2f).dp).graphicsLayer { translationY = (barHeight + bottom + 24f) * (1f - bar.value) * density.density }
            .then(if (mine.isEmpty()) Modifier.clearAndSetSemantics {} else Modifier)) {
            ShadowBox(2f, 5f, rgba(24, 39, 75, 0.07f), CARD_RADIUS, Modifier.fillMaxWidth().height(PANEL.dp)) {
                Row(Modifier.fillMaxSize().background(Color.White, RoundedCornerShape(CARD_RADIUS.dp)).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    BasicText(if (count == 1) "1 item" else "$count items", style = textStyle(15f, 20f, FontWeight.Normal, Ink))
                    BasicText(money(counted, symbol), style = textStyle(20f, 25f, FontWeight.SemiBold, Ink, true))
                }
            }
            Box(Modifier.padding(top = BAR_GAP.dp).fillMaxWidth().height(PAY.dp)) {
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val alpha by cssTween(if (pressed) 0.82f else 1f, PAY_PRESS_MS)
                val scale by cssTween(if (pressed) 0.985f else 1f, PAY_PRESS_MS)
                Box(Modifier.fillMaxSize().then(if (mine.isNotEmpty()) Modifier.clickable(interaction, indication = null, role = Role.Button) { touched(); view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); onPay?.invoke(counted / 100, mine) } else Modifier)) {
                    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }.background(accent, RoundedCornerShape(CARD_RADIUS.dp)), contentAlignment = Alignment.Center) {
                        BasicText("${money(counted, symbol)} - $payLabel", style = textStyle(16f, 21f, FontWeight.SemiBold, Color.White, true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** One bounded paint handles the changing card blur instead of caching a filter for every frame. */
private class ShadowPaint {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sigma = Float.NaN
    fun draw(canvas: android.graphics.Canvas, color: Color, blur: Float, x: Float, y: Float, width: Float, height: Float, radius: Float) {
        paint.color = color.copy(alpha = 1f).toArgb()
        if (Build.VERSION.SDK_INT >= 28) {
            if (sigma != blur) { sigma = blur; paint.maskFilter = BlurMaskFilter(sigmaToRadius(blur), BlurMaskFilter.Blur.NORMAL) }
            paint.alpha = (color.alpha * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawRoundRect(x, y, x + width, y + height, radius, radius, paint)
        } else for (layer in 6 downTo 1) {
            val outer = layer * 0.5f; val inner = (layer - 1) * 0.5f
            val weight = 0.5f * (exp(-inner * inner / 2f) - exp(-outer * outer / 2f)); val spread = outer * blur
            paint.alpha = (color.alpha * weight * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawRoundRect(x - spread, y - spread, x + width + spread, y + height + spread, radius + spread, radius + spread, paint)
        }
    }
}
@Composable
private fun ShadowBox(offset: Float, sigma: Float, color: Color, radius: Float, modifier: Modifier, content: @Composable () -> Unit) {
    val shadow = remember { ShadowPaint() }
    Box(modifier) {
        Canvas(Modifier.matchParentSize().bleed(SHADOW_SPILL.dp).clearAndSetSemantics {}) { surface(SHADOW_SPILL.dp) { x, y, w, h -> drawIntoCanvas { shadow.draw(it.nativeCanvas, color, sigma, x, y + offset, w, h, radius) } } }
        content()
    }
}

/** The source SVG portrait, drawn in its fixed 48-point box at every size. */
@Composable
private fun Avatar(size: Float, modifier: Modifier = Modifier) {
    val clip = remember { Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, 48f, 48f)) } }
    Canvas(modifier.size(size.dp).clearAndSetSemantics {}) { surface(0.dp) { _, _, _, _ ->
        withTransform({ scale(size / 48f, size / 48f, Offset.Zero) }) { clipPath(clip) {
            drawRect(Color(0xFFDFE7F5), size = Size(48f, 48f))
            drawOval(Color(0xFF27364F), Offset(5f, 36f), Size(38f, 26f))
            drawRoundRect(Color(0xFFE7B38F), Offset(20f, 28f), Size(8f, 10f), CornerRadius(3f))
            drawOval(Color(0xFFF2C5A3), Offset(15.4f, 11.5f), Size(17.2f, 20f))
            drawPath(Hair, Color(0xFF232733))
        } }
    } }
}
@Composable
private fun Badge(accent: Color, minus: Boolean) {
    Canvas(Modifier.offset(x = (AVATAR - BADGE + 3f).dp, y = (AVATAR - BADGE + 1f).dp).size(BADGE.dp).clearAndSetSemantics {}) { surface(0.dp) { _, _, _, _ ->
        drawCircle(accent, BADGE / 2f, Offset(BADGE / 2f, BADGE / 2f))
        drawCircle(Color.White, BADGE / 2f - 1f, Offset(BADGE / 2f, BADGE / 2f), style = Stroke(2f))
        drawRoundRect(Color.White, Offset(BADGE / 2f - 4f, BADGE / 2f - 1f), Size(8f, 2f), CornerRadius(1f))
        if (!minus) drawRoundRect(Color.White, Offset(BADGE / 2f - 1f, BADGE / 2f - 4f), Size(2f, 8f), CornerRadius(1f))
    } }
}

@Composable
private fun ClaimRow(item: BillItem, claimed: Boolean, symbol: String, accent: Color, motion: RowMotion, still: Boolean, onToggle: (Boolean) -> Unit, onTouch: () -> Unit) {
    val view = LocalView.current
    val density = LocalDensity.current
    val reduced by rememberUpdatedState(still)
    val toggle by rememberUpdatedState(onToggle)
    val touch by rememberUpdatedState(onTouch)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    LaunchedEffect(claimed) { motion.mine.tween(if (claimed) 1f else 0f, HOP, Linear, still = still) }
    val x = motion.x.value; val t = motion.mine.value; val e = smooth(t, 0f, 1f)
    val lifted = smooth(x, 0f, 40f)
    val shown = smooth(x, 18f, CLAIM_AT)
    val hopping = t > 0.001f && t < 0.999f
    val fill = mix(Color.White, Tint, smooth(t, 0.3f, 1f))
    val grip = mix(Grip, accent, t)
    val namePush = NAME_PUSH * (smooth(t, 0f, 0.3f) - smooth(t, 0.6f, 1f))
    val nameLift = -NAME_LIFT * smooth(t, 0.25f, 0.9f)
    val hopX = motion.from / 2f - 4f + (x + SEAT_X - (motion.from / 2f - 4f)) * e - AVATAR / 2f
    val hopY = SEAT_Y * e - HOP_HEIGHT * 6.75f * t * (1f - t) * (1f - t)
    val hopScale = 1f + (AVATAR_SMALL / AVATAR - 1f) * smooth(t, 0.2f, 0.8f)
    Box(Modifier.fillMaxWidth().height((CARD + CARD_GAP).dp).padding(bottom = CARD_GAP.dp).graphicsLayer {
        alpha = motion.rise.floatValue; translationY = (1f - motion.rise.floatValue) * RISE_DISTANCE * density.density; compositingStrategy = CompositingStrategy.ModulateAlpha
    }, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.size(AVATAR.dp).graphicsLayer {
            alpha = if (hopping) 0f else shown
            translationX = (x / 2f - 4f - AVATAR / 2f) * density.density
            val scale = 0.5f + 0.5f * shown + if (x >= CLAIM_AT) 0.06f else 0f
            scaleX = scale; scaleY = scale; compositingStrategy = CompositingStrategy.ModulateAlpha
        }) {
            ShadowBox(6f, 7f, rgba(24, 39, 75, 0.18f), AVATAR / 2f, Modifier.size(AVATAR.dp)) { Avatar(AVATAR) }
            Badge(accent, claimed)
        }
        ShadowBox(2f + 6f * lifted, 4f + 8f * lifted, rgba(24, 39, 75, 0.05f + 0.07f * lifted), CARD_RADIUS,
            Modifier.fillMaxWidth().height(CARD.dp).graphicsLayer { translationX = x * density.density }.onGloballyPositioned { coordinates[0] = it }
                .pointerInput(motion) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var origin = coordinates[0]?.localToRoot(down.position) ?: down.position
                        touch()
                        var active = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!pointer.pressed || pointer.isConsumed) break
                                val screen = coordinates[0]?.localToRoot(pointer.position) ?: pointer.position
                                val dx = (screen.x - origin.x) / density.density; val dy = (screen.y - origin.y) / density.density
                                if (!active && (dy < -PAN_FAIL_Y || dy > PAN_FAIL_Y)) break
                                if (!active && dx > PAN_ACTIVATE) { active = true; origin = screen }
                                if (active) {
                                    val pull = max(0f, (screen.x - origin.x) / density.density)
                                    motion.x.direct(if (pull < PULL_MAX) pull else PULL_MAX + (pull - PULL_MAX) / 4f)
                                    val past = motion.x.value >= CLAIM_AT
                                    if (past != motion.armed) { motion.armed = past; view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
                                    pointer.consume()
                                }
                            }
                        } finally { motion.armed = false; motion.letGo(reduced) { toggle(true) } }
                    }
                }.semantics(mergeDescendants = true) {
                    contentDescription = "${item.name}, ${money(rounded(item.price * 100), symbol)}${if (claimed) ", yours" else ""}"
                    stateDescription = if (claimed) "Swipe right to give it back" else "Swipe right to take it"
                    onClick { toggle(true); true }
                }) {
            Row(Modifier.fillMaxSize().background(fill, RoundedCornerShape(CARD_RADIUS.dp)), verticalAlignment = Alignment.CenterVertically) {
                BasicText(item.name, style = textStyle(15.5f, 20f, FontWeight.Medium, Ink), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = NAME_X.dp, end = 12.dp).graphicsLayer { translationX = namePush * density.density; translationY = nameLift * density.density })
                BasicText(money(rounded(item.price * 100), symbol), style = textStyle(15.5f, 20f, FontWeight.SemiBold, Ink, true), modifier = Modifier.padding(end = PRICE_INSET.dp))
            }
            Row(Modifier.align(Alignment.CenterStart).offset(x = (GRIP_X - 4f).dp).graphicsLayer { alpha = 1f - smooth(t, 0f, 0.3f) + smooth(t, 0.6f, 1f) }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { Box(Modifier.size(2.dp, 13.dp).background(grip, RoundedCornerShape(1.dp))) }
            }
        }
        Avatar(AVATAR, Modifier.graphicsLayer {
            alpha = if (claimed) if (t > 0.001f) 1f else 0f else smooth(t, 0f, 0.35f)
            translationX = hopX * density.density; translationY = hopY * density.density; scaleX = hopScale; scaleY = hopScale
        })
        Box(Modifier.size(TOUCH.dp).graphicsLayer {
            alpha = motion.touch.value * 0.5f
            translationX = (x + TOUCH_X - TOUCH / 2f) * density.density
            val scale = 0.6f + 0.4f * motion.touch.value; scaleX = scale; scaleY = scale
        }.background(Color(0xFF8A94A6), RoundedCornerShape((TOUCH / 2f).dp)))
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
