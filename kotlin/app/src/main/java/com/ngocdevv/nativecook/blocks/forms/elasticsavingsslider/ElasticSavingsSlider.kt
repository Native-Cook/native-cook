package com.ngocdevv.nativecook.blocks.forms.elasticsavingsslider

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import java.math.BigDecimal

/** What an option takes off the yearly bill, in units of the currency. */
data class SavingOption(val label: String, val saving: Double)
private val Options = listOf(
    SavingOption("Daily savings", 35.0), SavingOption("LED lighting", 70.0),
    SavingOption("Thermostat", 53.0), SavingOption("Smart thermostat", 76.0),
    SavingOption("Solar panels", 407.0), SavingOption("Heat pump", 222.0),
)
private val Backdrop = Color(0xFF202622)
private val BannerFill = Color(0xFF333E38)
private val CardFill = Color(0xFFFBFCF7)
private val Sage = Color(0xFFEDF0E7)
private val SagePressed = Color(0xFFE1E6DA)
private val Coral = Color(0xFFF65A3A)
private val Ink = Color(0xFF1D231F)
private val Ring = Color(0xFFCBD6CE)
private val StopDot = Color(0xFF515753)
private const val SIDE = 18f
private const val BANNER = 92f
private const val PICTURE = 64f
private const val CARD_PADDING = 25f
private const val CARD_RADIUS = 46f
private const val PANEL = 180f
private const val AMOUNT = 86f
private const val AMOUNT_STRETCH = 1.24f
private const val CHIP = 44f
private const val CHIP_GAP = 6f
private const val CHECK = 22f
private const val TRACK = 60f
private const val TRACK_INSET = 5f
private const val THUMB = TRACK - TRACK_INSET * 2f
private const val BOLT_INSET = THUMB / 2f
private const val STOP_DOTS = 4
private const val BUTTON = 60f
private const val BOLT_DAMPING = 13f
private const val BOLT_STIFFNESS = 150f
private const val BOLT_MASS = 1f
private const val SNAP_DAMPING = 11f
private const val SNAP_STIFFNESS = 210f
private const val SNAP_MASS = 1f
private const val THIN_PER_POINT = 0.2f
private const val THIN_MOST = 9f
private const val LEAN_PER_POINT = 0.9f
private const val LEAN_MOST = 28f
private const val GIVE = 14f
private const val CHECK_DAMPING = 15f
private const val CHECK_STIFFNESS = 260f
private const val CHECK_MASS = 0.8f
private const val CHIP_MS = 200
private const val SQUASH_Y = 0.72f
private const val SQUASH_X = 1.1f
private const val DIGIT_DAMPING = 9f
private const val DIGIT_STIFFNESS = 300f
private const val DIGIT_MASS = 0.7f
private const val COUNT_MS = 360
private const val SPRING_ENERGY_THRESHOLD = 6e-9f
/** autoPlay: one round every LOOP seconds. */
const val LOOP = 7.5f
private data class Drag(val at: Float, val to: Int, val lasts: Float)
private val Drags = listOf(Drag(0.8f, 1, 0.35f), Drag(1.9f, 2, 0.3f), Drag(2.4f, 4, 0.55f), Drag(3.8f, 5, 0.35f), Drag(4.45f, 6, 0.35f), Drag(6f, 0, 0.55f))
private const val USER_HOLD = 3f
private val Bolt = PathParser().parsePathString("M11.2 1L2.6 12.6L8.3 12.6L6.6 21L15.4 9.2L9.7 9.2Z").toPath()
private val CheckMark = PathParser().parsePathString("M6.4 11.4L9.7 14.6L15.7 8").toPath()
private val Roof = PathParser().parsePathString("M0 20L45 11L49 16L0 28Z").toPath()
private val RoofEdge = PathParser().parsePathString("M0 28L49 16L49 21L0 34Z").toPath()
private val Glass = PathParser().parsePathString("M0 34L38 24.5L38 64L0 64Z").toPath()
private val Mullions = PathParser().parsePathString("M12 31L12 64M25 27.8L25 64M0 47L38 42").toPath()
private val WingRoof = PathParser().parsePathString("M28 33L64 28L64 36L28 40Z").toPath()
private val Wing = PathParser().parsePathString("M31 40L64 36L64 64L31 64Z").toPath()
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val QuadInOut = Easing { t -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f }
private val QuadOut = Easing { t -> 1f - (1f - t) * (1f - t) }
private fun number(value: Double): String {
    if (!value.isFinite()) return value.toString()
    val decimal = BigDecimal.valueOf(value).stripTrailingZeros()
    return if (abs(value) >= 1e21 || (value != 0.0 && abs(value) < 1e-6)) decimal.toString().replace("E", "e") else decimal.toPlainString()
}
private fun textStyle(size: Float, line: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** A retargetable analytic spring retains velocity while the bolt chases a moving fill. */
private class Motion(initial: Float) {
    var value by mutableStateOf(initial)
        private set
    private var velocity = 0f
    private var target = initial
    private var from = initial
    private var origin = 0L
    private var duration = 0f
    private var curve: Easing = QuadInOut
    private var mode = 0
    private var mass = 1f
    private var stiffness = 1f
    private var damping = 1f
    private var initialEnergy = 0f
    val active get() = mode != 0
    fun direct(next: Float) { value = next; target = next; velocity = 0f; mode = 0 }
    fun cancel() { velocity = 0f; mode = 0 }
    fun timing(next: Float, seconds: Float, easing: Easing) {
        from = value; target = next; duration = seconds; curve = easing; origin = System.nanoTime(); velocity = 0f; mode = 1
    }
    fun spring(next: Float, m: Float, k: Float, c: Float) {
        val continuing = mode == 2 && target == next && mass == m && stiffness == k && damping == c
        if (!continuing) {
            if (mode != 2 || (next > value && velocity < 0f) || (next < value && velocity > 0f)) velocity = 0f
            initialEnergy = 0.5f * k * (value - next) * (value - next)
        }
        target = next; mass = m; stiffness = k; damping = c; mode = 2
    }
    fun advance(now: Long, dt: Float) {
        if (mode == 1) {
            val u = ((now - origin) / (duration * 1e9f)).coerceIn(0f, 1f)
            value = from + (target - from) * curve.transform(u)
            if (u >= 1f) mode = 0
        } else if (mode == 2 && dt > 0f) {
            val a = damping / (2f * mass)
            val omega2 = stiffness / mass
            val wd = sqrt(omega2 - a * a)
            val e = value - target
            val sine = sin(wd * dt)
            val cosine = cos(wd * dt)
            val decay = exp(-a * dt)
            val next = decay * (e * cosine + (velocity + a * e) / wd * sine)
            velocity = decay * (velocity * cosine - (a * velocity + omega2 * e) / wd * sine)
            value = target + next
            val energy = 0.5f * stiffness * next * next + 0.5f * mass * velocity * velocity
            if (initialEnergy == 0f || energy / initialEnergy <= SPRING_ENERGY_THRESHOLD) { value = target; velocity = 0f; mode = 0 }
        }
    }
}
private class SavingCounter {
    var value by mutableStateOf(0.0)
        private set
    private var from = 0.0
    private var target = 0.0
    private var origin = 0L
    fun timing(next: Double) { from = value; target = next; origin = System.nanoTime() }
    fun advance(now: Long) {
        val u = ((now - origin) / (COUNT_MS * 1e6)).coerceIn(0.0, 1.0)
        val rest = 1.0 - u
        value = from + (target - from) * (1.0 - rest * rest * rest)
    }
}

private class Slider {
    val reach = Motion(THUMB)
    val bolt = Motion(THUMB)
    val saved = SavingCounter()
    var driving = false
    var byHand = false
    var grip = 0f
    var lastLevel: Int? = null
}

/** Six savings options, a poster-sized bill and a slider whose bolt trails its elastic fill. */
@Composable
fun ElasticSavingsSlider(
    bill: Double = 1326.0,
    symbol: String = "$",
    options: List<SavingOption> = Options,
    address: String = "13 Alondra Blvd",
    hint: String = "Fine-tune your home\nfor more accurate results",
    fineTuneLabel: String = "Fine-tune",
    billLabel: String = "Estimated Annual Bill",
    reportLabel: String = "See your full report",
    autoPlay: Boolean = false,
    onChange: ((on: List<Boolean>, bill: Double) -> Unit)? = null,
    onFineTune: (() -> Unit)? = null,
    onReport: ((on: List<Boolean>, bill: Double) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val reduced by rememberUpdatedState(still)
    val density = LocalDensity.current
    val view = LocalView.current
    val givenOptions by rememberUpdatedState(options)
    val changed by rememberUpdatedState(onChange)
    var on by remember { mutableStateOf(options.map { false }) }
    var counted by remember { mutableStateOf(0.0) }
    var onVersion by remember { mutableIntStateOf(0) }
    var trackWidth by remember { mutableStateOf(316f) }
    var touches by remember { mutableIntStateOf(0) }
    val slider = remember { Slider() }
    val saving = options.indices.sumOf { if (on.getOrNull(it) == true) options[it].saving else 0.0 }
    val left = max(0.0, bill - saving)
    val remaining by rememberUpdatedState(left)
    val count = options.size
    fun stepNow() = (trackWidth - TRACK_INSET * 2f - THUMB) / max(1, givenOptions.size)
    fun levelNow() = floor((slider.reach.value - THUMB) / stepNow() + 0.5f).toInt().coerceIn(0, givenOptions.size)
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    fun touched() { touches++ }
    fun setOn(next: List<Boolean>) { on = next; onVersion++ }
    fun arrive(level: Int, hand: Boolean) { if (hand) tick(); setOn(givenOptions.indices.map { it < level }) }
    fun reached() {
        val level = levelNow()
        val old = slider.lastLevel
        slider.lastLevel = level
        if (old != null && level != old && slider.driving) arrive(level, slider.byHand)
    }
    fun dragTo(level: Int, seconds: Float) {
        slider.driving = true; slider.byHand = false
        slider.reach.timing(THUMB + level * stepNow(), seconds, QuadInOut)
    }
    fun toggle(index: Int) {
        touched(); tick()
        val next = on.mapIndexed { i, value -> if (i == index) !value else value }
        setOn(next); slider.driving = false
        slider.reach.spring(THUMB + next.count { it } * stepNow(), SNAP_MASS, SNAP_STIFFNESS, SNAP_DAMPING)
    }
    fun begin(x: Float) {
        slider.reach.cancel(); slider.driving = true; slider.byHand = true
        val end = TRACK_INSET + slider.reach.value
        val onEnd = x > end - THUMB - 8f && x < end + 16f
        slider.grip = if (onEnd) end - x else BOLT_INSET
        if (!onEnd) slider.reach.timing(min(trackWidth - TRACK_INSET * 2f, max(THUMB, x - TRACK_INSET + BOLT_INSET)), 0.14f, QuadOut)
        touched()
    }
    fun update(x: Float) {
        val to = x - TRACK_INSET + slider.grip
        val roomNow = trackWidth - TRACK_INSET * 2f
        val over = if (to < THUMB) to - THUMB else if (to > roomNow) to - roomNow else 0f
        slider.reach.direct(to - over + GIVE * over / (GIVE + abs(over)))
        reached()
    }
    fun finalizeDrag() { slider.reach.spring(THUMB + levelNow() * stepNow(), SNAP_MASS, SNAP_STIFFNESS, SNAP_DAMPING); touched() }
    val latestBegin by rememberUpdatedState(::begin)
    val latestUpdate by rememberUpdatedState(::update)
    val latestFinalize by rememberUpdatedState(::finalizeDrag)
    val latestDragTo by rememberUpdatedState(::dragTo)
    LaunchedEffect(onVersion) { changed?.invoke(on, remaining) }
    LaunchedEffect(saving) { slider.saved.timing(saving) }
    LaunchedEffect(Unit) {
        slider.lastLevel = levelNow()
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceIn(0f, 0.064f)
            last = now
            slider.reach.advance(now, dt)
            if (reduced) slider.bolt.direct(slider.reach.value)
            else { slider.bolt.spring(slider.reach.value, BOLT_MASS, BOLT_STIFFNESS, BOLT_DAMPING); slider.bolt.advance(now, dt) }
            slider.saved.advance(now)
            counted = floor(slider.saved.value + 0.5)
            reached()
        }
    }
    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong()); latestDragTo(0, 0.5f)
            delay(1300)
        }
        coroutineScope {
            var origin = System.nanoTime()
            while (true) {
                val start = origin
                for (drag in Drags) launch {
                    delay(max(0L, (start + (drag.at * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
                    latestDragTo(drag.to, drag.lasts)
                }
                delay(max(0L, (start + (LOOP * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
                origin += (LOOP * 1e9f).toLong()
            }
        }
    }
    val rows = options.indices.chunked(2).reversed()
    Box(modifier.fillMaxSize().background(Backdrop).pointerInput(Unit) {
        awaitPointerEventScope { while (true) { val event = awaitPointerEvent(PointerEventPass.Initial); if (event.changes.any { it.changedToDownIgnoreConsumed() }) touched() } }
    }) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        Box(Modifier.fillMaxSize().padding(start = SIDE.dp, end = SIDE.dp, top = insets.calculateTopPadding(), bottom = max(insets.calculateBottomPadding().value, 12f).dp), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().wrapContentHeight(unbounded = true)) {
                Row(Modifier.fillMaxWidth().height(BANNER.dp).background(BannerFill, CircleShape).padding(start = 13.dp, end = 19.dp), verticalAlignment = Alignment.CenterVertically) {
                    Picture()
                    Column(Modifier.weight(1f).padding(start = 14.dp, end = 4.dp)) {
                        BasicText(address, style = textStyle(15f, 19f, FontWeight.ExtraBold, Color.White, -0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(hint, style = textStyle(13.5f, 17.5f, FontWeight.Normal, Color(0xFFF2F5F0), -0.35f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    val fineInteraction = remember { MutableInteractionSource() }
                    val finePressed by fineInteraction.collectIsPressedAsState()
                    Box(Modifier.height(36.dp).background(if (finePressed) Color.White.copy(alpha = 0.12f) else Color.Transparent, CircleShape)
                        .border(1.5.dp, Color(0xFFF2F5F0), CircleShape).clickable(fineInteraction, indication = null, role = Role.Button) { onFineTune?.invoke() }.padding(horizontal = (11f + 1.5f).dp), contentAlignment = Alignment.Center) {
                        BasicText(fineTuneLabel, style = textStyle(14.5f, 18f, FontWeight.Medium, Color(0xFFF2F5F0)))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().background(CardFill, RoundedCornerShape(CARD_RADIUS.dp)).padding(CARD_PADDING.dp)) {
                    Box(Modifier.fillMaxWidth().height(PANEL.dp).clip(RoundedCornerShape(18.dp)).background(Sage)) {
                        BasicText(billLabel, style = textStyle(14.5f, 18f, FontWeight.Medium, Ink, -0.2f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 21.dp, top = 22.dp))
                        Row(Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp).height(31.dp).widthIn(min = 71.dp).background(CardFill, RoundedCornerShape(5.dp)).padding(horizontal = 10.dp)
                            .clearAndSetSemantics { contentDescription = "Saving $symbol${number(saving)}" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                            BasicText("$symbol${number(counted)}", style = textStyle(15f, 19f, FontWeight.Medium, Ink).copy(fontFeatureSettings = "tnum"))
                            Spacer(Modifier.width(6.dp))
                            Canvas(Modifier.size(11.dp, 10.dp)) { surface(0.dp) { _, _, _, _ -> drawPath(Path().apply { moveTo(0f, 0f); lineTo(11f, 0f); lineTo(5.5f, 10f); close() }, Ink) } }
                        }
                        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 15.dp).wrapContentWidth(unbounded = true).height(AMOUNT.dp).graphicsLayer { scaleY = AMOUNT_STRETCH; transformOrigin = TransformOrigin(0.5f, 1f) }
                            .clearAndSetSemantics { contentDescription = "$billLabel $symbol${number(left)}" }, verticalAlignment = Alignment.Bottom) {
                            Digit(symbol, still)
                            val digits = number(left)
                            for ((index, char) in digits.withIndex()) key(digits.length - index) { Digit(char.toString(), still) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(CHIP_GAP.dp)) {
                        for (row in rows) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((CHIP_GAP + 1f).dp)) {
                            for (index in row) key(options[index].label) { Chip(options[index].label, on.getOrNull(index) == true, Modifier.weight(1f)) { toggle(index) } }
                        }
                    }
                    Spacer(Modifier.height(27.dp))
                    Canvas(Modifier.fillMaxWidth().height(TRACK.dp).onSizeChanged { trackWidth = it.width / density.density }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume(); latestBegin(down.position.x / density.density)
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!finger.pressed) { finger.consume(); break }
                                        if (finger.position != finger.previousPosition) { latestUpdate(finger.position.x / density.density); finger.consume() }
                                    }
                                } finally { latestFinalize() }
                            }
                        }.semantics {
                            contentDescription = "Options switched on"
                            progressBarRangeInfo = ProgressBarRangeInfo(on.count { it }.toFloat(), 0f..count.toFloat(), max(0, count - 1))
                            customActions = listOf(
                                CustomAccessibilityAction("Increment") { touched(); dragTo((on.count { it } + 1).coerceIn(0, count), 0.25f); true },
                                CustomAccessibilityAction("Decrement") { touched(); dragTo((on.count { it } - 1).coerceIn(0, count), 0.25f); true },
                            )
                            setProgress { next -> touched(); dragTo(next.roundToInt().coerceIn(0, count), 0.25f); true }
                        }) {
                        surface(0.dp) { _, _, w, _ ->
                            drawRoundRect(Backdrop, size = Size(w, TRACK), cornerRadius = CornerRadius(TRACK / 2f))
                            for (index in 0 until STOP_DOTS) drawCircle(StopDot, 3.5f, Offset(TRACK_INSET + (index + 1) * (w - TRACK_INSET * 2f) / (STOP_DOTS + 1f), TRACK / 2f))
                            val reach = slider.reach.value
                            val bolt = if (still) reach else slider.bolt.value
                            val lag = reach - bolt
                            val height = THUMB - min(THIN_MOST, abs(lag) * THIN_PER_POINT)
                            drawRoundRect(Coral, Offset(TRACK_INSET, (TRACK - height) / 2f), Size(max(height, reach), height), CornerRadius(height / 2f))
                            val lean = (lag * LEAN_PER_POINT).coerceIn(-LEAN_MOST, LEAN_MOST)
                            val x = TRACK_INSET - 9f + max(THUMB, min(bolt, reach)) - BOLT_INSET
                            withTransform({ translate(x, TRACK / 2f - 11f); rotate(-lean, Offset(9f, 11f)); scale(1f + abs(lean) / 160f, 1f + abs(lean) / 160f, pivot = Offset(9f, 11f)); scale(1.12f, 1.12f, pivot = Offset(9f, 11f)) }) {
                                drawPath(Bolt, Color.White); drawPath(Bolt, Color.White, style = Stroke(1.6f, join = StrokeJoin.Round))
                            }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    val reportInteraction = remember { MutableInteractionSource() }
                    val reportPressed by reportInteraction.collectIsPressedAsState()
                    val reportPress = rememberCssMotion(if (reportPressed) 1f else 0f, 120)
                    val reportFill = mix(Sage, SagePressed, reportPress.value)
                    Box(Modifier.fillMaxWidth().height(BUTTON.dp).background(reportFill, RoundedCornerShape(18.dp))
                        .clickable(reportInteraction, indication = null, role = Role.Button) { onReport?.invoke(on, left) }, contentAlignment = Alignment.Center) {
                        BasicText(reportLabel, style = textStyle(16.5f, 21f, FontWeight.Bold, Ink, -0.3f))
                    }
                }
            }
        }
    }
}

private fun mix(from: Color, to: Color, t: Float) = Color(
    from.red + (to.red - from.red) * t, from.green + (to.green - from.green) * t,
    from.blue + (to.blue - from.blue) * t, from.alpha + (to.alpha - from.alpha) * t,
)

/** RN CSS transitions run even when system motion is reduced. */
@Composable
private fun rememberCssMotion(target: Float, milliseconds: Int): Motion {
    val motion = remember { Motion(target) }
    LaunchedEffect(target, milliseconds) {
        motion.timing(target, milliseconds / 1000f, CssEase)
        var last = System.nanoTime()
        while (motion.active) {
            val now = withFrameNanos { it }
            motion.advance(now, ((now - last) / 1e9f).coerceIn(0f, 0.064f))
            last = now
        }
    }
    return motion
}

@Composable
private fun Digit(char: String, still: Boolean) {
    val risen = remember { Motion(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(char, still) {
        if (first) { first = false; return@LaunchedEffect }
        risen.direct(0f)
        if (still) risen.timing(1f, 0.16f, QuadInOut) else risen.spring(1f, DIGIT_MASS, DIGIT_STIFFNESS, DIGIT_DAMPING)
        var last = System.nanoTime()
        while (risen.active) { val now = withFrameNanos { it }; risen.advance(now, ((now - last) / 1e9f).coerceIn(0f, 0.064f)); last = now }
    }
    BasicText(char, style = textStyle(AMOUNT, AMOUNT, FontWeight.Black, Coral, -4f), modifier = Modifier.height(AMOUNT.dp).graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 1f)
        if (still) alpha = risen.value.coerceIn(0f, 1f)
        else { scaleX = SQUASH_X + (1f - SQUASH_X) * risen.value; scaleY = SQUASH_Y + (1f - SQUASH_Y) * risen.value }
    })
}

@Composable
private fun Chip(label: String, on: Boolean, modifier: Modifier, onPress: () -> Unit) {
    val popped = remember { Motion(if (on) 1f else 0f) }
    LaunchedEffect(on) {
        if (on) popped.spring(1f, CHECK_MASS, CHECK_STIFFNESS, CHECK_DAMPING) else popped.timing(0f, 0.14f, QuadInOut)
        var last = System.nanoTime()
        while (popped.active) { val now = withFrameNanos { it }; popped.advance(now, ((now - last) / 1e9f).coerceIn(0f, 0.064f)); last = now }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberCssMotion(if (pressed) 1f else 0f, 120)
    val fill = mix(Sage, SagePressed, press.value)
    val ink = rememberCssMotion(if (on) 1f else 0.16f, CHIP_MS)
    Row(modifier.height(CHIP.dp).background(fill, CircleShape).clickable(interaction, indication = null, role = Role.Switch, onClick = onPress)
        .semantics { contentDescription = label; toggleableState = if (on) ToggleableState.On else ToggleableState.Off }
        .padding(start = 15.dp, end = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = textStyle(13f, 18f, FontWeight.SemiBold, Ink, -0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).graphicsLayer { alpha = ink.value })
        Box(Modifier.size(CHECK.dp)) {
            Canvas(Modifier.fillMaxSize()) { surface(0.dp) { _, _, _, _ -> drawCircle(Ring, CHECK / 2f - 2.5f, Offset(CHECK / 2f, CHECK / 2f), style = Stroke(5f)) } }
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = (popped.value * 3f).coerceIn(0f, 1f); scaleX = popped.value; scaleY = popped.value }) {
                surface(0.dp) { _, _, _, _ -> drawCircle(Ink, CHECK / 2f, Offset(CHECK / 2f, CHECK / 2f)); drawPath(CheckMark, Color.White, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            }
        }
    }
}

@Composable
private fun Picture() {
    Canvas(Modifier.size(PICTURE.dp).clip(RoundedCornerShape(18.dp)).clearAndSetSemantics {}) {
        surface(0.dp) { _, _, _, _ ->
            drawRect(Brush.verticalGradient(listOf(Color(0xFF6D98DE), Color(0xFFA9C6EE), Color(0xFFD6E3F3)), endY = PICTURE), size = Size(PICTURE, PICTURE))
            drawPath(Roof, Color(0xFF3A4763)); drawPath(RoofEdge, Color(0xFFD4683B)); drawPath(Glass, Color(0xFFCBD9EC))
            drawPath(Mullions, Color(0xFFF4F7FB), style = Stroke(1.2f)); drawPath(Wing, Color(0xFFB99F9D)); drawPath(WingRoof, Color(0xFFDD7444))
        }
    }
}
