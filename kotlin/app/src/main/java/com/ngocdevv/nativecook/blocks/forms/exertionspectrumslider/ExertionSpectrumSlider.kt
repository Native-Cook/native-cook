package com.ngocdevv.nativecook.blocks.forms.exertionspectrumslider

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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A level's name and the explanation below it. */
data class Level(val name: String, val description: String)
/** A colour at a place along the spectrum, from zero to one. */
data class Stop(val at: Float, val color: Color)
private val Levels = listOf(
    Level("Very, Very Light", "Very easy activity, like slowly walking"),
    Level("Very Light", "Easy activity, like a casual walk"),
    Level("Light", "Light effort, breathing slightly harder than normal"),
    Level("Moderate", "Moderate effort, breathing\nheavier and sweating lightly"),
    Level("Somewhat Hard", "Starting to feel challenging,\ncan still talk in sentences"),
    Level("Hard", "Challenging effort, hard to speak in full sentences"),
    Level("Very Hard", "Very challenging, can only\nspeak a few words at a time"),
    Level("Very, Very Hard", "Extremely challenging, can barely speak"),
    Level("Extremely Hard", "Close to your limit, impossible\nto keep up for long"),
    Level("Maximal", "All-out effort, nothing left to give"),
)
private val Spectrum = listOf(
    Stop(0f, Color(0xFF1FB8A6)), Stop(0.06f, Color(0xFF19C3F0)), Stop(0.15f, Color(0xFF12B5F5)),
    Stop(0.205f, Color(0xFF5FD080)), Stop(0.25f, Color(0xFFF5E500)), Stop(0.35f, Color(0xFFFF9A00)),
    Stop(0.45f, Color(0xFFFF7A00)), Stop(0.55f, Color(0xFFF0142A)), Stop(0.65f, Color(0xFFEE1048)),
    Stop(0.75f, Color(0xFFD800D8)), Stop(0.85f, Color(0xFF9A10C8)), Stop(1f, Color(0xFF6A0DAD)),
)
private val Page = Color(0xFF0B0B0C)
private val Sheet = rgba(28, 28, 30, 0.82f)
private val Ink = Color.White
private val NumeralInk = Color(0xFFF5F5F5)
private val Mute = Color(0xFF9A9AA0)
private val Caption = Color(0xFF6F6F75)
private val Divider = Color.White.copy(alpha = 0.09f)
private val Above = Color(0xFFF0142A)
private val Below = Color(0xFF19C3F0)
private data class Smudge(val x: Float, val y: Float, val w: Float, val h: Float, val color: Color)
private val Smudges = listOf(
    Smudge(0.04f, 0.22f, 0.26f, 0.022f, Color(0xFF56565C)), Smudge(0.04f, 0.36f, 0.15f, 0.022f, Color(0xFF4A4A50)),
    Smudge(0.5f, 0.25f, 0.24f, 0.02f, Color(0xFF3C3C42)), Smudge(0.3f, 0.672f, 0.05f, 0.022f, Color(0xFF8B1FB4)),
    Smudge(0.31f, 0.722f, 0.12f, 0.026f, Color(0xFFD0202C)), Smudge(0.31f, 0.775f, 0.19f, 0.026f, Color(0xFFD6C400)),
    Smudge(0.03f, 0.674f, 0.2f, 0.014f, Color(0xFF3C3C42)), Smudge(0.03f, 0.726f, 0.24f, 0.014f, Color(0xFF3C3C42)),
    Smudge(0.72f, 0.726f, 0.2f, 0.014f, Color(0xFF3C3C42)),
)
private const val SMUDGE_BLUR = 9f
private const val SHEET_TOP = 8f
private const val SHEET_RADIUS = 30f
private const val MARGIN = 28f
private const val NUMERAL_SIZE = 112f
private const val NUMERAL_SLOT = 118f
private const val NUMERAL_CANVAS = 300f
private const val NUMERAL_WIDTH = 240f
private const val TRACK_MARGIN = 42f
private const val SLIDER = 116f
private const val THIN = 9f
private const val WIDE = 54f
private const val WIDE_CORNER = 5f
private const val BAND = 70f
private const val HATCH_STEP = 8f
private const val HATCH_WIDTH = 2.2f
private const val HATCH_ALPHA = 0.1f
private const val HATCH_REST = 0.45f
private const val SHADE = 0.46f
private const val THUMB = 28f
private const val THUMB_GROWN = 30f
private const val DOT = 11f
private const val DOT_GROWN = 24f
private const val TAG_WIDTH = 132f
private const val TAG_HEIGHT = 18f
private const val TAG_EDGE = 12f
private const val SAVE = 56f
private const val SAVE_RADIUS = 20f
private const val FOLLOW_MASS = 1f
private const val FOLLOW_STIFFNESS = 520f
private const val FOLLOW_DAMPING = 44f
private const val SWELL_MASS = 1f
private const val SWELL_STIFFNESS = 300f
private const val SWELL_DAMPING = 21f
private const val POP_MASS = 1f
private const val POP_STIFFNESS = 420f
private const val POP_DAMPING = 22f
private const val ROLL = 240
private const val ROLL_STEP = 86f
private const val ROLL_BLUR_X = 2.5f
private const val ROLL_BLUR_Y = 13f
private const val TEXT_FADE = 160
private const val TONE = 260
private const val NOTE = 200
private const val NOTE_SHIFT = 8f
private const val LOOP = 9.4f
private const val DOWN_AT = 0.8f
private const val UP_FROM = 1.15f
private const val UP_FOR = 3.1f
private const val PEAK = 8
private const val BACK_FROM = 4.7f
private const val BACK_FOR = 1.5f
private const val SETTLE = 4
private const val LIFT_AT = 6.6f
private const val CLEAR_AT = 7.4f
private const val PRESS_FOR = 0.15f
private const val USER_HOLD = 3f
private const val RETURN = 0.8f
private const val SPRING_ENERGY_THRESHOLD = 6e-9f
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val QuadInOut = Easing { t -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f }
private val CubicOut = Easing { 1f - (1f - it).pow(3) }
private fun clamp01(t: Float) = t.coerceIn(0f, 1f)
private fun glide(t: Float): Float { val u = clamp01(t); return 0.55f * u + 0.45f * u * u * (3f - 2f * u) }
private fun textStyle(size: Float, line: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

/** RGB under the thumb is rounded like the source; Save uses Reanimated's gamma-corrected mix. */
private fun colorAt(p: Float, stops: List<Stop>): Color {
    var i = 1
    while (i < stops.lastIndex && p > stops[i].at) i++
    val a = stops[i - 1]; val b = stops[i]
    val t = clamp01((p - a.at) / (b.at - a.at))
    fun channel(x: Float, y: Float) = (x * 255f + (y - x) * 255f * t).roundToInt().coerceIn(0, 255) / 255f
    return Color(channel(a.color.red, b.color.red), channel(a.color.green, b.color.green), channel(a.color.blue, b.color.blue))
}
private fun saveColor(level: Float, tones: List<Color>): Color {
    if (tones.size == 1) return tones[0]
    val p = level.coerceIn(1f, tones.size.toFloat()) - 1f
    val i = min(tones.lastIndex - 1, floor(p).toInt()); val t = p - i
    fun channel(a: Float, b: Float) = ((a.toDouble().pow(2.2) + (b.toDouble().pow(2.2) - a.toDouble().pow(2.2)) * t).pow(1.0 / 2.2) * 255).roundToInt() / 255f
    return Color(channel(tones[i].red, tones[i + 1].red), channel(tones[i].green, tones[i + 1].green), channel(tones[i].blue, tones[i + 1].blue))
}

/** Explicit trajectories retain the source's clocks when Android's animation scale is zero. */
private class Motion(initial: Float) : State<Float> {
    private val state = mutableFloatStateOf(initial)
    override val value get() = state.floatValue
    var target = initial; private set
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
    fun spring(next: Float, m: Float, k: Float, c: Float, still: Boolean) {
        if (still) { state.floatValue = next; target = next; velocity = 0f; springing = false; timing = false; return }
        if (springing && target == next) return
        if ((next > value && velocity < 0f) || (next < value && velocity > 0f)) velocity = 0f
        energy0 = 0.5f * k * (value - next).pow(2)
        mass = m; stiffness = k; damping = c; target = next; springing = true; timing = false
    }
    fun tween(next: Float, ms: Int, easing: Easing = QuadInOut) {
        if (timing && target == next) return
        from = value; target = next; duration = ms * 1e6f; curve = easing; origin = System.nanoTime(); timing = true; springing = false
    }
    fun advance(now: Long, dt: Float) {
        if (timing) {
            val t = ((now - origin) / duration).coerceIn(0f, 1f)
            state.floatValue = from + (target - from) * curve.transform(t)
            if (t >= 1f) timing = false
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
private class AutoClock {
    private var origin: Long? = null
    var previous = -1f; private set
    fun start() { origin = System.nanoTime(); previous = -1f }
    fun stop() { origin = null; previous = -1f }
    fun sample(now: Long): Pair<Float, Float> {
        val start = origin ?: return -1f to -1f
        val t = ((now - start) / 1e9f) % LOOP
        val was = if (previous > t) -1f else previous
        previous = t
        return t to was
    }
}

/** A rating sheet whose colour, numeral and usual-range hatching answer a spring-following thumb. */
@Composable
fun ExertionSpectrumSlider(
    value: Double = 2.0,
    levels: List<Level> = Levels,
    usualRange: Pair<Int, Int> = 2 to 4,
    spectrum: List<Stop> = Spectrum,
    autoPlay: Boolean = false,
    title: String = "How hard was it?",
    subtitle: String = "Rate the effort of this workout",
    basisLabel: String = "Based on your heart rate",
    rangeLabel: String = "Usual range",
    aboveLabel: String = "Above usual range",
    belowLabel: String = "Below usual range",
    aboveColor: Color = Above,
    belowColor: Color = Below,
    clearLabel: String = "Clear Entry",
    helpLabel: String = "How to Self-Assess",
    saveLabel: String = "Save",
    aboveNote: String = "Effort Above Your Usual Range",
    belowNote: String = "Effort Below Your Usual Range",
    onChange: ((value: Int) -> Unit)? = null,
    onSave: ((value: Int) -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (levels.isEmpty()) { Box(modifier.fillMaxSize().background(Page)); return }
    val count = levels.size
    val opening = floor(value + 0.5).toInt().coerceIn(1, count)
    val home = (opening - 0.5f) / count
    val rangeFrom = (usualRange.first - 1f) / count
    val rangeTo = usualRange.second.toFloat() / count
    val stops = if (spectrum.size >= 2) spectrum else Spectrum
    val still = rememberReducedMotion()
    val reduced by rememberUpdatedState(still)
    val latestHome by rememberUpdatedState(home)
    val latestCount by rememberUpdatedState(count)
    val rangeStart by rememberUpdatedState(rangeFrom)
    val rangeEnd by rememberUpdatedState(rangeTo)
    val changed by rememberUpdatedState(onChange)
    val clearedCallback by rememberUpdatedState(onClear)
    val view = LocalView.current
    val density = LocalDensity.current
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    var current by remember { mutableIntStateOf(opening) }
    var side by remember { mutableIntStateOf(0) }
    var lastSide by remember { mutableIntStateOf(1) }
    var autoPressed by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var holding by remember { mutableStateOf(false) }
    var byHand by remember { mutableStateOf(false) }
    var grip by remember { mutableFloatStateOf(0f) }
    val firstSide = if (home < rangeFrom) -1 else if (home > rangeTo) 1 else 0
    val place = remember { Motion(home) }
    val swell = remember { Motion(0f) }
    val roll = remember { Motion(opening.toFloat()) }
    val tone = remember { Motion(opening.toFloat()) }
    val above = remember { Motion(if (firstSide == 1) 1f else 0f) }
    val below = remember { Motion(if (firstSide == -1) 1f else 0f) }
    val outside = remember { Motion(if (firstSide == 0) 0f else 1f) }
    val clock = remember { AutoClock() }
    var previousSide by remember { mutableStateOf<Int?>(null) }
    fun haptic(step: Boolean) { view.performHapticFeedback(if (step) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONTEXT_CLICK) }
    fun target(next: Float) { place.spring(next, FOLLOW_MASS, FOLLOW_STIFFNESS, FOLLOW_DAMPING, reduced) }
    fun touching(next: Float) { swell.spring(next, SWELL_MASS, SWELL_STIFFNESS, SWELL_DAMPING, reduced) }
    fun touched() { autoPressed = false; touches++ }
    fun clear() { autoPressed = false; target(latestHome); clearedCallback?.invoke() }
    fun reactions() {
        val next = min(latestCount, floor(clamp01(place.value) * latestCount).toInt() + 1)
        if (next != current) {
            current = next; roll.tween(next.toFloat(), ROLL, CubicOut); tone.tween(next.toFloat(), TONE)
            if (byHand) haptic(true)
            changed?.invoke(next)
        }
        val where = if (place.value < rangeStart) -1 else if (place.value > rangeEnd) 1 else 0
        if (where != previousSide) {
            val initial = previousSide == null
            previousSide = where; side = where; if (where != 0) lastSide = where
            if (!initial) {
                val a = if (where == 1) 1f else 0f; val b = if (where == -1) 1f else 0f
                if (reduced) { above.tween(a, NOTE); below.tween(b, NOTE) }
                else { above.spring(a, POP_MASS, POP_STIFFNESS, POP_DAMPING, false); below.spring(b, POP_MASS, POP_STIFFNESS, POP_DAMPING, false) }
                outside.tween(if (where == 0) 0f else 1f, NOTE)
            }
        }
    }
    val latestTarget by rememberUpdatedState(::target)
    val latestTouching by rememberUpdatedState(::touching)
    val latestClear by rememberUpdatedState(::clear)
    val latestReactions by rememberUpdatedState(::reactions)
    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        while (true) {
            val now = withFrameNanos { it }; val dt = ((now - last) / 1e9f).coerceIn(0f, 0.064f); last = now
            val (t, was) = clock.sample(now)
            if (t >= 0f) {
                val peak = (min(latestCount, PEAK) - 0.5f) / latestCount
                val settle = (min(latestCount, SETTLE) - 0.5f) / latestCount
                if (was < DOWN_AT && t >= DOWN_AT) latestTouching(1f)
                if (t >= UP_FROM && t < BACK_FROM) latestTarget(latestHome + (peak - latestHome) * glide((t - UP_FROM) / UP_FOR))
                else if (t >= BACK_FROM && t < LIFT_AT) latestTarget(peak + (settle - peak) * glide((t - BACK_FROM) / BACK_FOR))
                if (was < LIFT_AT && t >= LIFT_AT) latestTouching(0f)
                if (was < CLEAR_AT && t >= CLEAR_AT) autoPressed = true
                if (was < CLEAR_AT + PRESS_FOR && t >= CLEAR_AT + PRESS_FOR) latestClear()
            }
            place.advance(now, dt); swell.advance(now, dt); latestReactions()
            roll.advance(now, dt); tone.advance(now, dt); above.advance(now, dt); below.advance(now, dt); outside.advance(now, dt)
        }
    }
    LaunchedEffect(autoPlay, touches, home) {
        if (!autoPlay) return@LaunchedEffect
        try {
            if (touches > 0) {
                delay((USER_HOLD * 1000).toLong())
                if (holding) return@LaunchedEffect
                byHand = false
                latestTarget(latestHome)
                delay((RETURN * 1000).toLong())
            }
            byHand = false; clock.start()
            kotlinx.coroutines.awaitCancellation()
        } finally { clock.stop() }
    }
    val tones = remember(levels, stops) { levels.indices.map { colorAt((it + 0.5f) / count, stops) } }
    val currentShown = current.coerceIn(1, count)
    val shown = levels[currentShown - 1]
    val isClear = currentShown == opening
    BoxWithConstraints(modifier.fillMaxSize().background(Page)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val inset = WindowInsets.safeDrawing.asPaddingValues()
        val sheetTop = inset.calculateTopPadding().value + SHEET_TOP
        val bottom = max(inset.calculateBottomPadding().value, 16f) + 8f
        val hairline = 1f / density.density
        val fixed = 30f + 30f + 6f + 20f + NUMERAL_SLOT + 14f + 70f + SLIDER + 22f + hairline + 48f + SAVE + bottom
        val unit = max(0f, height - sheetTop - fixed) / (1f + 1.15f + 3f * 0.55f)
        val x0 = TRACK_MARGIN; val x1 = width - TRACK_MARGIN; val span = max(0.01f, x1 - x0)
        val thumbX by remember(width) { derivedStateOf { x0 + clamp01(place.value) * span } }
        val tagX by remember(width) { derivedStateOf { max(TAG_EDGE, min(width - TAG_WIDTH - TAG_EDGE, thumbX - TAG_WIDTH / 2f)) } }
        BackPage()
        Column(Modifier.offset(y = sheetTop.dp).fillMaxWidth().height((height - sheetTop).dp)
            .background(Sheet, RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp))
            .wrapContentHeight(Alignment.Top, unbounded = true)) {
            Spacer(Modifier.height(30.dp))
            BasicText(title, style = textStyle(25f, 30f, FontWeight.Bold, Ink).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().height(30.dp))
            BasicText(subtitle, style = textStyle(16f, 20f, FontWeight.Normal, Mute).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(20.dp))
            Spacer(Modifier.height(unit.dp))
            Box(Modifier.fillMaxWidth().height(NUMERAL_SLOT.dp).semantics {
                contentDescription = "$title, $currentShown, ${shown.name}"
                progressBarRangeInfo = ProgressBarRangeInfo(currentShown.toFloat(), 1f..count.toFloat(), max(0, count - 1))
                customActions = listOf(
                    CustomAccessibilityAction("Increment") { touched(); target((min(count, currentShown + 1) - 0.5f) / count); true },
                    CustomAccessibilityAction("Decrement") { touched(); target((max(1, currentShown - 1) - 0.5f) / count); true },
                )
            }, contentAlignment = Alignment.Center) { Numeral(count, roll, still) }
            Box(Modifier.padding(top = 14.dp, start = MARGIN.dp, end = MARGIN.dp).fillMaxWidth().height(70.dp)) {
                IndependentFade(currentShown, shown, TEXT_FADE, still, Modifier.fillMaxWidth()) { text ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(text.name, style = textStyle(17f, 22f, FontWeight.Bold, Ink).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(text.description, style = textStyle(15f, 20f, FontWeight.Normal, Mute).copy(textAlign = TextAlign.Center), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height((unit * 1.15f).dp))
            Box(Modifier.fillMaxWidth().height(SLIDER.dp).pointerInput(width) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    clock.stop(); byHand = true; holding = true; touching(1f)
                    val at = (down.position.x / density.density - x0) / span
                    grip = if (abs(at - place.target) * span <= THUMB) at - place.target else 0f
                    target(clamp01(at - grip)); touched(); down.consume()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed || pointer.isConsumed) break
                            target(clamp01((pointer.position.x / density.density - x0) / span - grip)); pointer.consume()
                        }
                    } finally { holding = false; touching(0f); touched() }
                }
            }) {
                SpectrumTrack(width, count, rangeFrom, rangeTo, stops, place, swell, outside)
                BasicText(basisLabel.uppercase(Locale.US), style = textStyle(10.5f, 13f, FontWeight.Normal, Caption, 0.2f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(x = (x0 + 10f).dp).width(max(0f, width - x0 - 10f).dp).graphicsLayer { alpha = 1f - outside.value })
                BasicText(rangeLabel.uppercase(Locale.US), style = textStyle(10.5f, 13f, FontWeight.Normal, Caption, 0.2f).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).offset(x = (x0 + rangeFrom * span).dp).width(max(0f, (rangeTo - rangeFrom) * span).dp))
                Tag(aboveLabel, aboveColor, above, tagX, still)
                Tag(belowLabel, belowColor, below, tagX, still)
            }
            Spacer(Modifier.height((unit * 0.55f).dp))
            val clearInteraction = remember { MutableInteractionSource() }
            val clearPressed by clearInteraction.collectIsPressedAsState()
            val clearAlpha by cssTween(if (isClear) 0.35f else if (clearPressed || autoPressed) 0.5f else 1f, 150)
            Box(Modifier.fillMaxWidth().height(22.dp)) {
                BasicText(clearLabel, style = textStyle(17f, 22f, FontWeight.Normal, Ink).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = clearAlpha })
                Box(Modifier.matchParentSize().bleed(12.dp).clickable(clearInteraction, indication = null, enabled = !isClear, role = Role.Button) { touched(); haptic(false); clear() }.semantics { contentDescription = clearLabel })
            }
            Spacer(Modifier.height((unit * 0.55f).dp))
            Box(Modifier.padding(horizontal = MARGIN.dp).fillMaxWidth().height(hairline.dp).background(Divider))
            val helpInteraction = remember { MutableInteractionSource() }
            val helpPressed by helpInteraction.collectIsPressedAsState()
            val helpAlpha by cssTween(if (helpPressed) 0.5f else 1f, 120)
            Box(Modifier.padding(horizontal = MARGIN.dp).fillMaxWidth().height(48.dp).clickable(helpInteraction, indication = null, role = Role.Button) { touched(); haptic(false); onHelp?.invoke() }, contentAlignment = Alignment.CenterStart) {
                Row(Modifier.graphicsLayer { alpha = helpAlpha }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(helpLabel, style = textStyle(14f, 18f, FontWeight.Normal, Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Canvas(Modifier.size(14.dp).clearAndSetSemantics {}) { surface(0.dp) { _, _, _, _ -> drawLine(Ink, Offset(5f, 3f), Offset(9f, 7f), 1.5f, StrokeCap.Round); drawLine(Ink, Offset(9f, 7f), Offset(5f, 11f), 1.5f, StrokeCap.Round) } }
                }
            }
            Spacer(Modifier.height((unit * 0.55f).dp))
            val saveInteraction = remember { MutableInteractionSource() }
            val savePressed by saveInteraction.collectIsPressedAsState()
            val darken by cssTween(if (savePressed) 0.18f else 0f, 110)
            val shadow = Shadow(Color.Black.copy(alpha = 0.22f), Offset(0f, 0.5f * density.density), 3f * density.density)
            Box(Modifier.padding(horizontal = TRACK_MARGIN.dp).fillMaxWidth().height(SAVE.dp).clip(RoundedCornerShape(SAVE_RADIUS.dp)).background(saveColor(tone.value, tones))
                .clickable(saveInteraction, indication = null, role = Role.Button) { touched(); haptic(false); onSave?.invoke(currentShown) }
                .semantics { contentDescription = if (side == 0) saveLabel else "$saveLabel, ${if (side > 0) aboveNote else belowNote}" }, contentAlignment = Alignment.Center) {
                BasicText(saveLabel, style = textStyle(16f, 20f, FontWeight.Bold, Ink).copy(shadow = shadow), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { translationY = -NOTE_SHIFT * outside.value * density.density })
                BasicText(if (lastSide > 0) aboveNote else belowNote, style = textStyle(10.5f, 13f, FontWeight.Medium, Color.White.copy(alpha = 0.85f)).copy(shadow = shadow), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).graphicsLayer { alpha = outside.value })
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = darken)))
            }
            Spacer(Modifier.height(bottom.dp))
        }
    }
}

/** The identical command sequence lets the thin line and wedge blend, including spring overshoot. */
private fun trackPath(x0: Float, x1: Float, cy: Float, left: Float, right: Float, corner: Float): Path {
    val a = left / 2f; val b = right / 2f; val k = 0.5523f
    return Path().apply {
        moveTo(x0 + a, cy - a); lineTo(x1 - corner, cy - b)
        cubicTo(x1 - corner + corner * k, cy - b, x1, cy - b + corner - corner * k, x1, cy - b + corner)
        lineTo(x1, cy + b - corner)
        cubicTo(x1, cy + b - corner + corner * k, x1 - corner + corner * k, cy + b, x1 - corner, cy + b)
        lineTo(x0 + a, cy + a)
        cubicTo(x0 + a - a * k, cy + a, x0, cy + a * k, x0, cy)
        cubicTo(x0, cy - a * k, x0 + a - a * k, cy - a, x0 + a, cy - a); close()
    }
}
private fun hatchPath(x0: Float, x1: Float, cy: Float) = Path().apply {
    var x = x0 - BAND
    while (x < x1) { moveTo(x, cy + BAND / 2f); lineTo(x + BAND, cy - BAND / 2f); x += HATCH_STEP }
}

/** Older hardware canvases need a small fixed falloff in place of native mask-filter shadows. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.softFallback(color: Color, sigma: Float, x: Float, y: Float, width: Float, height: Float, radius: Float) {
    for (layer in 6 downTo 1) {
        val outer = layer * 0.5f; val inner = (layer - 1) * 0.5f
        val weight = 0.5f * (exp(-inner * inner / 2f) - exp(-outer * outer / 2f))
        val spread = outer * sigma
        drawRoundRect(color.copy(alpha = color.alpha * weight), Offset(x - spread, y - spread), Size(width + spread * 2f, height + spread * 2f), CornerRadius(radius + spread))
    }
}
@Composable
private fun BackPage() {
    val blots = remember { Smudges.map { SoftRoundRect(it.color, SMUDGE_BLUR) } }
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) { surface(0.dp) { _, _, w, h ->
        for ((index, smudge) in Smudges.withIndex()) {
            if (Build.VERSION.SDK_INT >= 28) drawIntoCanvas { blots[index].draw(it.nativeCanvas, smudge.x * w, smudge.y * h, smudge.w * w, smudge.h * h, 6f) }
            else softFallback(smudge.color, SMUDGE_BLUR, smudge.x * w, smudge.y * h, smudge.w * w, smudge.h * h, 6f)
        }
    } }
}

@Composable
private fun SpectrumTrack(width: Float, count: Int, rangeFrom: Float, rangeTo: Float, stops: List<Stop>, place: State<Float>, swell: State<Float>, outside: State<Float>) {
    val x0 = TRACK_MARGIN; val x1 = width - TRACK_MARGIN; val span = max(0.01f, x1 - x0); val cy = SLIDER / 2f
    val hatch = remember(width) { hatchPath(x0, x1, cy) }
    val spectrum = remember(width, stops) { Brush.horizontalGradient(*stops.map { it.at to it.color }.toTypedArray(), startX = x0, endX = x1) }
    val leftHatch = remember(width, rangeFrom) { Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = HATCH_ALPHA)), x0, x0 + rangeFrom * span) }
    val rightHatch = remember(width, rangeTo) { Brush.horizontalGradient(0f to Color.White.copy(alpha = HATCH_ALPHA), 0.55f to Color.White.copy(alpha = HATCH_ALPHA), 1f to Color.White.copy(alpha = 0f), startX = x0 + rangeTo * span, endX = x1) }
    val shadowColor = Color.Black.copy(alpha = 0.45f)
    val shadow = remember { SoftRoundRect(shadowColor, 5f) }
    Canvas(Modifier.fillMaxSize().clipToBounds().clearAndSetSemantics {}) { surface(0.dp) { _, _, _, _ ->
        val p = clamp01(place.value); val grow = swell.value; val thumbX = x0 + p * span
        val r = (THUMB + (THUMB_GROWN - THUMB) * grow) / 2f
        val dot = (DOT + (DOT_GROWN - DOT) * grow) / 2f
        val bandTop = cy - BAND / 2f; val bandBottom = cy + BAND / 2f
        clipRect(x0, bandTop, x0 + rangeFrom * span, bandBottom) { drawPath(hatch, leftHatch, style = Stroke(HATCH_WIDTH)) }
        clipRect(x0 + rangeTo * span, bandTop, x1, bandBottom) { drawPath(hatch, rightHatch, style = Stroke(HATCH_WIDTH)) }
        val edgeColor = Color.White.copy(alpha = 0.14f)
        drawLine(edgeColor, Offset(x0 + rangeFrom * span, bandTop), Offset(x0 + rangeFrom * span, bandBottom), 1f)
        drawLine(edgeColor, Offset(x0 + rangeTo * span, bandTop), Offset(x0 + rangeTo * span, bandBottom), 1f)
        val from = if (p > rangeTo) rangeTo else min(p, rangeFrom)
        val to = if (p > rangeTo) p else rangeFrom
        val lit = outside.value * (HATCH_REST + (1f - HATCH_REST) * clamp01(grow))
        clipRect(x0 + from * span, bandTop, x0 + (from + max(0f, to - from)) * span, bandBottom) { drawPath(hatch, spectrum, alpha = lit, style = Stroke(HATCH_WIDTH + 0.6f)) }
        drawLine(Ink, Offset(thumbX, bandTop), Offset(thumbX, bandBottom), 1.5f, alpha = lit)
        clipPath(trackPath(x0, x1, cy, THIN, THIN + (WIDE - THIN) * grow, THIN / 2f + (WIDE_CORNER - THIN / 2f) * grow)) {
            drawRect(spectrum, Offset(x0, 0f), Size(span, SLIDER))
            val left = min(place.value, rangeFrom) * span
            if (left > 0f) drawRect(Color.Black, Offset(x0, 0f), Size(left, SLIDER), alpha = SHADE)
            val shadeFrom = x0 + max(place.value, rangeTo) * span
            val shadeWidth = max(0f, x1 - shadeFrom)
            if (shadeWidth > 0f) drawRect(Color.Black, Offset(shadeFrom, 0f), Size(shadeWidth, SLIDER), alpha = SHADE)
            for (step in 1 until count) {
                val share = step.toFloat() / count
                val h = 2f + max(0f, grow) * (0.34f * (THIN + (WIDE - THIN) * share) - 2f)
                drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(x0 + share * span - 1f, cy - h / 2f), Size(2f, h), CornerRadius(1f))
            }
        }
        if (Build.VERSION.SDK_INT >= 28) drawIntoCanvas { shadow.draw(it.nativeCanvas, thumbX - r, cy + 2f - r, r * 2f, r * 2f, r) }
        else softFallback(shadowColor, 5f, thumbX - r, cy + 2f - r, r * 2f, r * 2f, r)
        drawCircle(Ink, r, Offset(thumbX, cy)); drawCircle(colorAt(place.value, stops), dot, Offset(thumbX, cy))
    } }
}

/** The blur is applied to the composed, faded digit strip after its vertical translations. */
@Composable
private fun Numeral(count: Int, roll: State<Float>, still: Boolean) {
    val density = LocalDensity.current
    val between by remember(roll, still) { derivedStateOf { if (still) 0f else sin(PI.toFloat() * (roll.value - floor(roll.value))) } }
    Box(Modifier.wrapContentSize(Alignment.Center, unbounded = true).requiredSize(NUMERAL_WIDTH.dp, NUMERAL_CANVAS.dp).clipToBounds().graphicsLayer {
        renderEffect = if (Build.VERSION.SDK_INT >= 31 && between > 0f) BlurEffect(sigmaToRadius(ROLL_BLUR_X * between * density.density), sigmaToRadius(ROLL_BLUR_Y * between * density.density), TileMode.Decal) else null
        alpha = if (Build.VERSION.SDK_INT < 31) 1f - between * 0.35f else 1f
    }.clearAndSetSemantics {}) {
        for (digit in 1..count) {
            val alpha = max(0f, 1f - abs(digit - roll.value))
            if (alpha > 0f) BasicText(digit.toString(), style = TextStyle(color = NumeralInk, fontSize = NUMERAL_SIZE.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp,
                textAlign = TextAlign.Center, platformStyle = PlatformTextStyle(includeFontPadding = false)), maxLines = 1,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().graphicsLayer {
                    this.alpha = alpha; translationY = if (still) 0f else (digit - roll.value) * ROLL_STEP * density.density
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                })
        }
    }
}
@Composable
private fun Tag(label: String, color: Color, shown: State<Float>, x: Float, still: Boolean) {
    val density = LocalDensity.current
    Box(Modifier.offset(x = x.dp, y = (-2).dp).size(TAG_WIDTH.dp, TAG_HEIGHT.dp).graphicsLayer {
        alpha = clamp01(shown.value * 2f)
        translationY = if (still) 0f else (1f - shown.value) * 6f * density.density
        val scale = if (still) 1f else 0.7f + 0.3f * shown.value
        scaleX = scale; scaleY = scale
    }.background(color, RoundedCornerShape(6.dp)).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        BasicText(label.uppercase(Locale.US), style = textStyle(10f, 13f, FontWeight.ExtraBold, Ink, 0.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** CSS press feedback continues under reduced motion. */
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

/** Departing words retain their own snapshots and complete their fade even on quick key reversals. */
private class FadeDeck<T>(var chosen: Int, var current: T) {
    data class Departure<T>(val id: Long, val value: T, val origin: Long)
    var sequence = 0L
    var entering: Long? = null
    var departing = emptyList<Departure<T>>()
    fun update(next: Int, value: T, still: Boolean) {
        if (next != chosen) {
            val now = System.nanoTime(); sequence++
            if (!still) departing = departing + Departure(sequence, current, now)
            entering = if (still) null else now; chosen = next
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
        for (layer in deck.departing) key(layer.id) { Box(Modifier.graphicsLayer { alpha = 1f - progress(layer.origin); compositingStrategy = CompositingStrategy.ModulateAlpha }) { content(layer.value) } }
        Box(Modifier.graphicsLayer { alpha = deck.entering?.let(::progress) ?: 1f; compositingStrategy = CompositingStrategy.ModulateAlpha }) { content(deck.current) }
    }
}
