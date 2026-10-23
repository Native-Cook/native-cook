package com.ngocdevv.nativecook.blocks.forms.stackingcupstepper

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.math.BigDecimal
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

/** The target paper-body height and whole-cup scale for a drink size. */
data class CupSize(val label: String, val volume: String, val caffeine: Double, val body: Float, val scale: Float)
data class Order(val size: CupSize, val quantity: Int, val caffeine: Double)
private val SIZES = listOf(
    CupSize("Small", "237 ml", 75.0, 62f, 1f),
    CupSize("Medium", "355 ml", 110.0, 84f, 1.02f),
    CupSize("Large", "473 ml", 150.0, 76f, 1.3f),
    CupSize("Extra Large", "591 ml", 190.0, 90f, 1.38f),
)
private val HOURS = listOf("8 AM", "10 AM", "12 PM", "2 PM")
private const val BACKGROUND = "#000000"
private const val SHEET = "#171717"
private const val HAIRLINE = "#242424"
private const val CHIP = "#242424"
private const val INK = "#FFFFFF"
private const val TITLE_INK = "#DADADA"
private const val MUTE = "#8C8C8C"
private const val PILL = "#222222"
private const val PILL_PRESSED = "#303030"
private const val PILL_INK = "#6C6C6C"
private const val VOLUME_OFF = "#2E2E2E"
private const val ROUND = "#2B2B2B"
private const val ROUND_PRESSED = "#3D3D3D"
private const val CONFIRM_PRESSED = "#BDBDBD"
private const val TICK_EARLY = "#C78A4E"
private const val TICK_NOW = "#F2F2F2"
private const val TICK_LATE = "#3F9C7B"
private const val MARGIN = 16f
private const val NAV = 44f
private const val TICKS = 28
private const val TICK_LEFT = 82f
private const val TICK_STEP = 12.3f
private const val TICK_AT = 10
private const val HEADER = 58f
private const val PILL_HEIGHT = 37f
private const val PILL_GAP = 7f
private const val STEPPER_WIDTH = 145f
private const val STEPPER_HEIGHT = 38f
private const val ROUND_SIZE = 32f
private const val CONFIRM = 40f
private const val GROUND = 30f
private const val HEADROOM = 10f
private const val MAX_CUPS = 5
private val SLOTS = listOf(
    listOf(Offset(0f, 0f)),
    listOf(Offset(-40f, 0f), Offset(40f, 0f)),
    listOf(Offset(-46f, -30f), Offset(46f, -30f), Offset(0f, 2f)),
    listOf(Offset(-50f, -32f), Offset(50f, -32f), Offset(-38f, 3f), Offset(38f, 3f)),
    listOf(Offset(-38f, -33f), Offset(38f, -33f), Offset(-74f, 2f), Offset(74f, 2f), Offset(0f, 5f)),
)
private data class Spring(val mass: Double, val stiffness: Double, val damping: Double)
private val STRETCH = Spring(1.0, 170.0, 13.0)
private val SLIDE = Spring(1.0, 210.0, 17.0)
private val THUMB = Spring(1.0, 320.0, 28.0)
private const val HOP_AFTER = .14
private const val HOP_FOR = .56
private const val HOP_HEIGHT = 10.0
private const val HOP_SPLIT = .76
private const val HOP_BOUNCE = .16
private const val FALL = .42
private const val DROP = 150.0
private const val SIDE = 46.0
private const val TILT = 16.0
private const val WOBBLE = 7.0
private const val WOBBLE_FADE = 8.0
private const val WOBBLE_RATE = 17.0
private const val SQUASH = .1
private const val SQUASH_FADE = 10.0
private const val SQUASH_RATE = 22.0
private const val SETTLED = 1.6
private const val NUDGE = 2.4
private const val LEAVE = .24
private const val LEAVE_RISE = 46.0
private const val LEAVE_SIDE = 26.0
private const val LEAVE_TILT = 14.0
private const val COUNT = .45
private const val ROLL = 9f
const val LOOP = 9.6
private val STEPS = listOf(
    Triple(.8, "size", 1), Triple(2.0, "size", 2), Triple(3.3, "more", 0),
    Triple(4.5, "more", 0), Triple(5.8, "less", 0), Triple(6.8, "less", 0), Triple(7.8, "size", 0),
)
private const val PRESS_FOR = .14
private const val USER_HOLD = 3.0
private const val RESET_FOR = .9
private const val RESET_STEP_MS = 300L
private const val ROLL_MS = 220
private const val PRESS_MS = 120
private const val LABEL_MS = 200
private const val CONFIRM_MS = 100
private const val AGE_FADE = .08
private const val SPRING_FRAME_CAP = .064
private const val SPRING_ENERGY_THRESHOLD = 6e-9
private const val NAV_PADDING = 4f
private const val NAV_BUTTON = 48f
private const val BACK_ICON = 22f
private const val INFO_ICON = 26f
private const val SUBTITLE_ICON = 16f
private const val SUBTITLE_TOP = 26f
private const val SUBTITLE_HEIGHT = 22f
private const val SUBTITLE_PADDING = 13f
private const val TIMELINE_TOP = 18f
private const val TIMELINE_HEIGHT = 96f
private const val SUGGESTED_WIDTH = 120f
private const val SUGGESTED_HEIGHT = 26f
private const val SUGGESTED_PADDING = 10f
private const val POINTER_TOP = 5f
private const val POINTER_WIDTH = 4f
private const val POINTER_HEIGHT = 6f
private const val TICK_BOTTOM = 30f
private const val TICK_MAJOR = 20f
private const val TICK_MINOR = 15f
private const val TICK_WIDTH = 1.5f
private const val HOUR_BOTTOM = 6f
private const val HOUR_WIDTH = 60f
private const val SHEET_TOP = 20f
private const val SHEET_RADIUS = 22f
private const val TIME_HEIGHT = 28f
private const val TIME_PADDING = 14f
private const val CUPS_MIN = 150f
private const val FIGURE_TOP = 1f
private const val UNIT_LEFT = 6f
private const val SIZES_TOP = 26f
private const val PILL_PADDING = 15f
private const val VOLUME_TOP = 6f
private const val STEPPER_TOP = 26f
private const val STEPPER_PADDING = 3f
private const val ROUND_SLOP = 8f
private const val ROUND_SCALE = .9
private const val ROUND_DISABLED = .4
private const val ROUND_ICON = 18f
private const val CONFIRM_TOP = 40f
private const val BOTTOM = 16f
private const val PRESS_RETENTION = 20f
private const val PRESS_RETENTION_BOTTOM = 30f
private const val MIN_PRESS_DURATION_MS = 130L
private const val SUBTITLE_INK = "#B4B4B4"
private const val SUBTITLE_INFO = "#4A4A4A"
private const val SUGGESTED_FILL = "#1A1A1A"
private const val SUGGESTED_INK = "#D0D0D0"
private const val POINTER_INK = "#C9C9C9"
private const val HOUR_INK = "#8A8A8A"
private const val UNIT_INK = "#9A9A9A"
private const val STEPPER_FILL = "#1F1F1F"
private const val STEPPER_ICON_UNITS = 512f
private const val STEPPER_ICON_FONT_CENTER = 191.5f
// Source Ionicons glyph outlines keep the icons independent of a font package.
private val STEPPER_ICON_PATHS = mapOf(
    "arrow-back" to androidx.compose.ui.graphics.vector.PathParser().parsePathString("M238 359Q238 359 238 359Q246 361 255 357Q263 353 266.0 345.0Q269 337 267 328Q265 324 211 270Q211 270 157 216H288L419 215L423 213Q431 209 434 200Q437 192 434 184Q431 175 423 171Q423 171 419 169L289 168Q159 168 159.0 167.5Q159 167 212.0 113.5Q265 60 267 56Q272 40 259 30Q252 24 244 24Q241 24 236.0 26.0Q231 28 156.0 102.5Q81 177 79 181Q74 190 78 200Q80 205 154.0 279.5Q228 354 231.0 356.0Q234 358 238 359Z").toPath(),
    "information-circle" to androidx.compose.ui.graphics.vector.PathParser().parsePathString("M238 391Q241 392 260 391Q295 391 325 380Q371 362 404.0 325.5Q437 289 450 242Q455 220 455 192Q455 163 449 141Q436 89 397.5 50.5Q359 12 307 -1Q285 -7 256 -7Q228 -7 206 -2Q147 14 106.0 60.0Q65 106 58 167Q54 192 58 217Q67 293 123 341Q173 384 238 391ZM265 308Q256 311 247 308Q236 304 232 293Q230 289 230.0 283.5Q230 278 233 272Q239 261 252 258Q257 257 262 259Q274 262 279.0 272.0Q284 282 280.0 293.0Q276 304 265 308ZM267 234Q267 234 267 234Q265 236 244.0 236.0Q223 236 220 234Q207 226 214 213Q219 204 235 204Q235 204 244 204V160V116H228Q214 116 210.5 115.0Q207 114 203 110Q197 101 202 92Q205 88 208 86Q208 86 212 84H257Q309 83 314 88Q320 93 320 100Q320 107 315 112Q310 116 291 116Q291 116 276 116V170Q276 223 275 226Q273 231 267 234Z").toPath(),
    "remove" to androidx.compose.ui.graphics.vector.PathParser().parsePathString("M108 208Q108 208 108 208Q111 208 257 208Q257 208 404 208L408 206Q416 201 416.0 192.0Q416 183 408 178Q408 178 405 176H256H107L104 178Q91 186 98 200Q101 206 108 208Z").toPath(),
    "add" to androidx.compose.ui.graphics.vector.PathParser().parsePathString("M248 349Q248 349 248 349Q253 353 259.5 351.5Q266 350 270 344Q270 344 272 341V275V208H338H404L408 206Q416 201 416.0 192.0Q416 183 408 178Q408 178 405 176H339H272V109V43L270 40Q265 32 256.0 32.0Q247 32 242 40Q242 40 240 43V109V176H173H107L104 178Q96 183 96.0 192.0Q96 201 103 205Q103 205 107 208H173H240V275V341L242 344Q244 347 248 349Z").toPath(),
)
private val CssEase = CubicBezierEasing(.25f, .1f, .25f, 1f)
private val Linear = Easing { it }
private val QuadIn = Easing { it * it }
private val CubicOut = Easing { 1f - (1f - it).pow(3) }
private fun color(hex: String) = Color(android.graphics.Color.parseColor(hex))
private fun blend(a: Color, b: Color, t: Double) = rgba(
    ((a.red + (b.red - a.red) * t) * 255.0).roundToInt(),
    ((a.green + (b.green - a.green) * t) * 255.0).roundToInt(),
    ((a.blue + (b.blue - a.blue) * t) * 255.0).roundToInt(),
    (a.alpha + (b.alpha - a.alpha) * t).toFloat(),
)
private fun clamp01(t: Double) = t.coerceIn(0.0, 1.0)
private fun hopAt(t: Double): Double {
    if (t <= 0.0 || t >= 1.0) return 0.0
    if (t < HOP_SPLIT) return 4.0 * t * (HOP_SPLIT - t) / (HOP_SPLIT * HOP_SPLIT)
    val u = (t - HOP_SPLIT) / (1.0 - HOP_SPLIT)
    return HOP_BOUNCE * 4.0 * u * (1.0 - u)
}
private fun number(value: Double): String {
    if (value == 0.0) return "0"
    if (!value.isFinite()) return if (value.isNaN()) "NaN" else if (value > 0) "Infinity" else "-Infinity"
    if (abs(value) >= 1e-6 && abs(value) < 1e21) return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    val raw = value.toString().lowercase().replace(".0e", "e")
    val pieces = raw.split('e')
    if (pieces.size < 2) return raw
    val exponent = pieces[1].toInt()
    return "${pieces[0]}e${if (exponent >= 0) "+" else ""}$exponent"
}
private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }
private class FrameTime(var now: Long? = null)
private class Motion(initial: Double) : State<Double> {
    private val state = mutableStateOf(initial)
    override val value get() = state.value
    private var target = initial
    private var velocity = 0.0
    private var config = Spring(1.0, 1.0, 1.0)
    private var energy0 = 0.0
    private var springing = false
    private var springTimestamp = 0L
    private var timing = false
    private var origin = 0L
    private var duration = 0.0
    private var from = initial
    private var curve: Easing = Linear
    private var completion: (() -> Unit)? = null
    fun cancel() { timing = false; springing = false; completion = null }
    fun direct(next: Double) { cancel(); velocity = 0.0; state.value = next; target = next }
    fun spring(next: Double, spring: Spring, still: Boolean, startingAt: Long) {
        if (still || value == next) { direct(next); return }
        if ((next > value && velocity < 0.0) || (next < value && velocity > 0.0)) velocity = 0.0
        if (springing && target == next && config == spring) { advance(startingAt); return }
        if (!springing) springTimestamp = startingAt
        energy0 = .5 * spring.stiffness * (value - next).pow(2)
        target = next; config = spring; springing = true; timing = false; completion = null
        advance(startingAt)
    }
    fun tween(next: Double, ms: Double, easing: Easing = Linear, still: Boolean = false, startingAt: Long, onFinished: (() -> Unit)? = null) {
        if (still || value == next) { direct(next); onFinished?.invoke(); return }
        if (timing && target == next) { duration = ms * 1e6; curve = easing; completion = onFinished; return }
        from = value; target = next; origin = startingAt; duration = ms * 1e6; curve = easing; timing = true; springing = false; velocity = 0.0; completion = onFinished
    }
    fun advance(now: Long) {
        if (timing) {
            val u = ((now - origin) / duration).coerceIn(0.0, 1.0)
            state.value = from + (target - from) * if (curve === Linear) u else curve.transform(u.toFloat()).toDouble()
            if (u >= 1.0) { timing = false; val finished = completion; completion = null; finished?.invoke() }
        }
        if (!springing) return
        val dt = min(SPRING_FRAME_CAP, ((now - springTimestamp) / 1e9).coerceAtLeast(0.0)); springTimestamp = now
        val omega = sqrt(config.stiffness / config.mass); val decay = config.damping / (2.0 * config.mass); val e = value - target
        val next: Double
        if (decay < omega) {
            val w = sqrt(omega * omega - decay * decay); val s = sin(w * dt); val c = cos(w * dt); val envelope = exp(-decay * dt)
            next = envelope * (e * c + (velocity + decay * e) / w * s)
            velocity = envelope * (velocity * c - (decay * velocity + omega * omega * e) / w * s)
        } else { val envelope = exp(-omega * dt); val b = velocity + omega * e; next = envelope * (e + b * dt); velocity = envelope * (velocity - omega * b * dt) }
        state.value = target + next
        val energy = .5 * config.stiffness * next * next + .5 * config.mass * velocity * velocity
        if (energy0 == 0.0 || energy / energy0 <= SPRING_ENERGY_THRESHOLD) { state.value = target; velocity = 0.0; springing = false }
    }
}

/** Controllers are keyed by pile index, retaining a leaving cup's place until it disappears. */
private class PiledCup(val index: Int, quantity: Int) {
    private val initial = SLOTS[quantity - 1].getOrNull(index) ?: Offset.Zero
    var was = index < quantity
    val x = Motion(if (was) initial.x.toDouble() else 0.0)
    val y = Motion(if (was) initial.y.toDouble() else 0.0)
    val age = Motion(if (was) SETTLED else -1.0)
    val away = Motion(0.0)
    private var slot = if (was) initial else Offset.Zero
    fun update(quantity: Int, still: Boolean, now: Long) {
        val present = index < quantity
        val target = if (present) SLOTS[quantity - 1][index] else Offset.Zero
        if (present && !was) {
            away.direct(0.0); x.direct(target.x.toDouble()); y.direct(target.y.toDouble()); age.direct(0.0)
            age.tween(SETTLED, SETTLED * 1000.0, still = still, startingAt = now)
        } else if (present && target != slot) {
            x.spring(target.x.toDouble(), SLIDE, still, now); y.spring(target.y.toDouble(), SLIDE, still, now)
        } else if (was && !present) {
            age.cancel()
            away.tween(1.0, LEAVE * 1000.0, QuadIn, still, now) { age.direct(-1.0); away.direct(0.0) }
        }
        was = present; slot = target
    }
    fun advance(now: Long) { x.advance(now); y.advance(now); age.advance(now); away.advance(now) }
    fun transform(size: Double, landed: Double): DoubleArray {
        val fallen = clamp01(age.value / FALL); val since = age.value - FALL
        var tilt = TILT * (1.0 - fallen); var squash = 0.0
        if (since > 0.0 && age.value < SETTLED) {
            tilt = -WOBBLE * exp(-WOBBLE_FADE * since) * sin(WOBBLE_RATE * since)
            squash = SQUASH * exp(-SQUASH_FADE * since) * cos(SQUASH_RATE * since)
        } else if (age.value >= SETTLED && landed > 0.0) tilt = (if (index % 2 == 0) -1.0 else 1.0) * NUDGE * exp(-WOBBLE_FADE * landed) * sin(WOBBLE_RATE * landed)
        val gone = away.value
        return doubleArrayOf(x.value * size + SIDE * (1.0 - fallen) + LEAVE_SIDE * gone, y.value * size - DROP * (1.0 - fallen * fallen) - LEAVE_RISE * gone * (2.0 - gone), tilt + LEAVE_TILT * gone, size * (1.0 + squash * .6) * (1.0 - .3 * gone), size * (1.0 - squash) * (1.0 - .3 * gone))
    }
    fun alpha() = if (age.value < 0.0) 0f else (min(1.0, age.value / AGE_FADE) * (1.0 - away.value * away.value)).toFloat()
}
private class HopDelay(var at: Long? = null)

@Composable
private fun StepperIcon(name: String, size: Float, ink: Color) {
    val path = STEPPER_ICON_PATHS[name] ?: return
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.align(Alignment.Center).size(size.dp)) {
            surface(0.dp) { _, _, _, _ ->
                val unit = size / STEPPER_ICON_UNITS
                withTransform({
                    translate(0f, size / 2f + STEPPER_ICON_FONT_CENTER * unit)
                    scale(unit, -unit, Offset.Zero)
                }) { drawPath(path, ink) }
            }
        }
    }
}

private class SizeVisual(chosen: Boolean) {
    val on = Motion(if (chosen) 1.0 else 0.0)
    val hold = Motion(0.0)
}
private class RoundVisual(enabled: Boolean) {
    val hold = Motion(0.0)
    val alpha = Motion(if (enabled) 1.0 else ROUND_DISABLED)
}
private data class Pill(val x: Float, val width: Float)
private data class SizeMeasure(val label: TextLayoutResult, val volume: TextLayoutResult, val pillWidth: Float, val columnWidth: Float)
private fun textStyle(size: Float, line: Float, weight: Int, ink: Color = color(INK), align: TextAlign = TextAlign.Center, spacing: Float = 0f, tabular: Boolean = false) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight), color = ink,
    textAlign = align, letterSpacing = spacing.sp, fontFeatureSettings = if (tabular) "tnum" else null,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None), platformStyle = PlatformTextStyle(includeFontPadding = true),
)

/** A drink size stretches the shared cup shape; quantity changes retain each cup's place. */
@Composable
fun StackingCupStepper(
    title: String = "Caffeine Window",
    subtitle: String = "Suggested beverages",
    suggestedTime: String = "10:30 AM",
    hours: List<String> = HOURS,
    drink: String = "Caffè Latte",
    time: String = "9:41 AM",
    sizes: List<CupSize> = SIZES,
    size: Int = 0,
    quantity: Int = 1,
    max: Int = 3,
    figureLabel: String = "Caffeine content",
    unit: String = "mg",
    confirmLabel: String = "Confirm",
    autoPlay: Boolean = false,
    onChange: ((Order) -> Unit)? = null,
    onConfirm: ((Order) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val reduced by rememberUpdatedState(still)
    val currentSizes by rememberUpdatedState(sizes)
    val changed by rememberUpdatedState(onChange)
    val confirmed by rememberUpdatedState(onConfirm)
    val back by rememberUpdatedState(onBack)
    val info by rememberUpdatedState(onInfo)
    val view = LocalView.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val controlScope = rememberCoroutineScope()
    val frameTime = remember { FrameTime() }
    fun timestamp() = frameTime.now ?: System.nanoTime()
    val most = max.coerceIn(1, MAX_CUPS)
    val startSize = size.coerceIn(0, max(0, sizes.lastIndex))
    val startCups = quantity.coerceIn(1, most)
    var sizeIndex by remember { mutableIntStateOf(startSize) }
    var cups by remember { mutableIntStateOf(startCups) }
    val initialSize = remember { sizes.getOrNull(startSize) ?: SIZES.first() }
    var figure by remember { mutableStateOf(initialSize.caffeine * startCups) }
    var autoPressed by remember { mutableStateOf<String?>(null) }
    var touches by remember { mutableIntStateOf(0) }
    val body = remember { Motion(initialSize.body.toDouble()) }
    val scale = remember { Motion(initialSize.scale.toDouble()) }
    val hop = remember { Motion(1.0) }
    val hopDelay = remember { HopDelay() }
    val landed = remember { Motion(SETTLED) }
    val fit = remember { Motion(1.0) }
    val thumbX = remember { Motion(0.0) }
    val thumbWidth = remember { Motion(0.0) }
    val counted = remember { Motion(figure) }
    val roll = remember { Motion(0.0) }
    val chosen = sizes.getOrNull(sizeIndex) ?: sizes.firstOrNull() ?: initialSize
    val total = chosen.caffeine * cups
    val cupShape = remember { CupShape(initialSize.body, 0f) }
    val cupArt = remember { CupArt() }
    val cupStorage = remember { mutableListOf<PiledCup>() }
    while (cupStorage.size < most) cupStorage += PiledCup(cupStorage.size, cups)
    while (cupStorage.size > most) cupStorage.removeAt(cupStorage.lastIndex)
    val pile = cupStorage.toList()
    val currentPile by rememberUpdatedState(pile)
    val sizeStorage = remember { mutableMapOf<String, SizeVisual>() }
    sizeStorage.keys.retainAll(sizes.map { it.label }.toSet())
    val sizeVisuals = sizes.mapIndexed { index, item -> sizeStorage.getOrPut(item.label) { SizeVisual(index == sizeIndex) } }
    val currentSizeVisuals by rememberUpdatedState(sizeVisuals)
    val lessVisual = remember { RoundVisual(startCups > 1) }
    val moreVisual = remember { RoundVisual(startCups < most) }
    val confirmHold = remember { Motion(0.0) }
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    fun changeSize(index: Int, auto: Boolean) {
        if (index == sizeIndex) return
        val next = currentSizes.getOrNull(index) ?: return
        val previous = currentSizes.getOrNull(sizeIndex) ?: chosen
        if (!auto) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        sizeIndex = index; changed?.invoke(Order(next, cups, next.caffeine * cups))
        body.spring(next.body.toDouble(), STRETCH, reduced, timestamp())
        scale.spring(next.scale.toDouble(), STRETCH, reduced, timestamp())
        if (cupHeight(next.body) * next.scale > cupHeight(previous.body) * previous.scale) {
            hop.direct(0.0); hopDelay.at = null
            if (reduced) hop.direct(1.0)
            else hopDelay.at = timestamp() + (HOP_AFTER * 1e9).toLong()
        }
    }
    fun step(by: Int, auto: Boolean) {
        val next = (cups + by).coerceIn(1, most)
        if (next == cups) return
        val current = currentSizes.getOrNull(sizeIndex) ?: chosen
        if (!auto) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        cups = next; changed?.invoke(Order(current, next, current.caffeine * next))
        roll.direct(by.toDouble()); roll.tween(0.0, ROLL_MS.toDouble(), CubicOut, reduced, timestamp())
        if (by > 0) { landed.direct(-FALL); landed.tween(SETTLED, (SETTLED + FALL) * 1000.0, still = reduced, startingAt = timestamp()) }
    }
    fun touched() { autoPressed = null; touches++ }
    fun confirm() {
        touched()
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        val selected = currentSizes.getOrNull(sizeIndex) ?: chosen
        confirmed?.invoke(Order(selected, cups, selected.caffeine * cups))
    }
    val latestSize by rememberUpdatedState(::changeSize)
    val latestStep by rememberUpdatedState(::step)
    val latestTouched by rememberUpdatedState(::touched)
    val latestConfirm by rememberUpdatedState(::confirm)
    val currentIndex by rememberUpdatedState(sizeIndex)
    val currentQuantity by rememberUpdatedState(cups)
    LaunchedEffect(total) { counted.tween(total, COUNT * 1000.0, CubicOut, reduced, timestamp()) }
    LaunchedEffect(cups, most) { pile.forEach { it.update(cups, reduced, timestamp()) } }
    LaunchedEffect(sizeIndex, sizes) {
        sizeVisuals.forEachIndexed { index, visual -> visual.on.tween(if (index == sizeIndex) 1.0 else 0.0, LABEL_MS.toDouble(), CssEase, startingAt = timestamp()) }
    }
    LaunchedEffect(autoPlay, touches, sizes.size, startSize, startCups) {
        if (!autoPlay || sizes.isEmpty()) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            while (currentQuantity != startCups) { latestStep(if (currentQuantity > startCups) -1 else 1, true); delay(RESET_STEP_MS) }
            if (currentIndex != startSize) { latestSize(startSize, true); delay((RESET_FOR * 1000).toLong()) }
        }
        var origin = System.nanoTime()
        while (true) {
            for ((at, what, index) in STEPS) {
                delay(max(0L, (origin + (at * 1e9).toLong() - System.nanoTime()) / 1_000_000))
                autoPressed = if (what == "size") "size-$index" else what
                delay(max(0L, (origin + ((at + PRESS_FOR) * 1e9).toLong() - System.nanoTime()) / 1_000_000))
                autoPressed = null
                if (what == "size") latestSize(min(index, sizes.lastIndex), true) else latestStep(if (what == "more") 1 else -1, true)
            }
            delay(max(0L, (origin + (LOOP * 1e9).toLong() - System.nanoTime()) / 1_000_000)); origin += (LOOP * 1e9).toLong()
        }
    }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { now ->
            frameTime.now = now
            try {
                listOf(body, scale, hop, landed, fit, thumbX, thumbWidth, counted, roll, lessVisual.hold, lessVisual.alpha, moreVisual.hold, moreVisual.alpha, confirmHold).forEach { it.advance(now) }
                currentPile.forEach { it.advance(now) }
                currentSizeVisuals.forEach { it.on.advance(now); it.hold.advance(now) }
                val next = floor(counted.value + .5)
                if (figure != next) figure = next
                if (hopDelay.at?.let { now >= it } == true) {
                    hopDelay.at = null; hop.tween(1.0, HOP_FOR * 1000.0, still = reduced, startingAt = now)
                }
            } finally { frameTime.now = null }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(color(BACKGROUND))) {
        val width = maxWidth.value; val height = maxHeight.value
        val labelStyle = textStyle(15f, 20f, 500)
        val volumeStyle = textStyle(10.5f, 13f, 700)
        val figureStyle = textStyle(30f, 36f, 600, tabular = true)
        val unitStyle = textStyle(13f, 18f, 400, color(UNIT_INK))
        val figureLabelStyle = textStyle(15f, 20f, 400, color(MUTE))
        fun measure(text: String, style: TextStyle, available: Float = width, lines: Int = Int.MAX_VALUE) = measurer.measure(AnnotatedString(text), style, maxLines = lines, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (max(1f, available) * density.density).roundToInt()))
        val measures = sizes.map { item ->
            val label = remember(item.label, labelStyle, width, density) { measure(item.label, labelStyle, width - PILL_PADDING * 2f) }
            val volume = remember(item.volume, volumeStyle, width, density) { measure(item.volume, volumeStyle) }
            val pillWidth = label.size.width / density.density + PILL_PADDING * 2f
            SizeMeasure(label, volume, pillWidth, max(pillWidth, volume.size.width / density.density))
        }
        var runningX = 0f
        val pills = measures.map { item -> Pill(runningX, item.columnWidth).also { runningX += item.columnWidth + PILL_GAP } }
        val sizesWidth = if (measures.isEmpty()) 0f else runningX - PILL_GAP
        val sizesHeight = measures.maxOfOrNull { PILL_HEIGHT + VOLUME_TOP + it.volume.size.height / density.density } ?: 0f
        val measuredFigure = remember(figure, figureStyle, width, density) { measure(number(figure), figureStyle) }
        val measuredUnit = remember(unit, unitStyle, width, density) { measure(unit, unitStyle, max(1f, width - UNIT_LEFT)) }
        val measuredLabel = remember(figureLabel, figureLabelStyle, width, density) { measure(figureLabel, figureLabelStyle) }
        val figureHeight = measuredFigure.size.height / density.density; val unitHeight = measuredUnit.size.height / density.density
        val figureBase = measuredFigure.firstBaseline / density.density; val unitBase = measuredUnit.firstBaseline / density.density
        val baseline = max(figureBase, unitBase)
        val figureRowHeight = baseline + max(figureHeight - figureBase, unitHeight - unitBase)
        val labelHeight = measuredLabel.size.height / density.density
        val bottom = max(safe.calculateBottomPadding().value, BOTTOM) + BOTTOM
        val sheetY = safe.calculateTopPadding().value + NAV + SUBTITLE_TOP + SUBTITLE_HEIGHT + TIMELINE_TOP + TIMELINE_HEIGHT + SHEET_TOP
        val sheetHeight = max(0f, height - sheetY)
        val fixed = HEADER + labelHeight + FIGURE_TOP + figureRowHeight + SIZES_TOP + sizesHeight + STEPPER_TOP + STEPPER_HEIGHT + CONFIRM_TOP + CONFIRM + bottom
        val cupsHeight = max(CUPS_MIN, sheetHeight - fixed)
        val figureLabelY = HEADER + cupsHeight
        val figureRowY = figureLabelY + labelHeight + FIGURE_TOP
        val sizesY = figureRowY + figureRowHeight + SIZES_TOP
        val stepperY = sizesY + sizesHeight + STEPPER_TOP
        val confirmY = stepperY + STEPPER_HEIGHT
        LaunchedEffect(width, cupsHeight, cups, chosen) {
            if (width <= 0f) return@LaunchedEffect
            val slots = SLOTS[cups - 1]
            val reach = slots.maxOf { abs(it.x) }; val rise = slots.maxOf { -it.y }
            val wide = (reach * 2f + CUP_WIDTH) * chosen.scale + MARGIN * 2f
            val tall = (cupHeight(chosen.body) + rise + HOP_HEIGHT.toFloat()) * chosen.scale
            fit.spring(min(1f, min(width / wide, (cupsHeight - GROUND - HEADROOM) / tall)).toDouble(), SLIDE, reduced, timestamp())
        }
        val pill = pills.getOrNull(sizeIndex)
        LaunchedEffect(pill) {
            if (pill != null) {
                val placed = thumbWidth.value > 0.0
                if (placed) { thumbX.spring(pill.x.toDouble(), THUMB, reduced, timestamp()); thumbWidth.spring(pill.width.toDouble(), THUMB, reduced, timestamp()) }
                else { thumbX.direct(pill.x.toDouble()); thumbWidth.direct(pill.width.toDouble()) }
            }
        }
        val navY = safe.calculateTopPadding().value
        StepperControl("Back", true, Modifier.offset(NAV_PADDING.dp, navY.dp).size(NAV_BUTTON.dp, NAV.dp), controlScope, action = { latestTouched(); back?.invoke() }) { StepperIcon("arrow-back", BACK_ICON, color(TITLE_INK)) }
        BasicText(title, Modifier.offset((NAV_PADDING + NAV_BUTTON).dp, navY.dp).size(max(1f, width - (NAV_PADDING + NAV_BUTTON) * 2f).dp, NAV.dp).wrapContentSize(Alignment.Center), style = textStyle(17f, 22f, 600, color(TITLE_INK)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        StepperControl("About", true, Modifier.offset((width - NAV_PADDING - NAV_BUTTON).dp, navY.dp).size(NAV_BUTTON.dp, NAV.dp), controlScope, action = { latestTouched(); info?.invoke() }) { StepperIcon("information-circle", INFO_ICON, color(TITLE_INK)) }
        val subtitleY = navY + NAV + SUBTITLE_TOP
        BasicText(subtitle, Modifier.offset(SUBTITLE_PADDING.dp, subtitleY.dp).size(max(1f, width - SUBTITLE_PADDING * 2f).dp, SUBTITLE_HEIGHT.dp).wrapContentSize(Alignment.CenterStart), style = textStyle(15f, 20f, 400, color(SUBTITLE_INK), TextAlign.Start), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.offset((width - SUBTITLE_PADDING - SUBTITLE_ICON).dp, (subtitleY + (SUBTITLE_HEIGHT - SUBTITLE_ICON) / 2f).dp).size(SUBTITLE_ICON.dp).clearAndSetSemantics {}) { StepperIcon("information-circle", SUBTITLE_ICON, color(SUBTITLE_INFO)) }
        Timeline(subtitle, suggestedTime, hours, Modifier.offset(y = (subtitleY + SUBTITLE_HEIGHT + TIMELINE_TOP).dp).size(width.dp, TIMELINE_HEIGHT.dp))
        Box(Modifier.offset(y = sheetY.dp).size(width.dp, sheetHeight.dp).background(color(SHEET), RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp))) {
            val timeStyle = textStyle(13f, 17f, 600, tabular = true)
            val timeMeasured = remember(time, timeStyle, width, density) { measure(time, timeStyle, width - MARGIN * 2f - TIME_PADDING * 2f) }
            val timeWidth = timeMeasured.size.width / density.density + TIME_PADDING * 2f
            BasicText(drink, Modifier.offset(MARGIN.dp, 0.dp).size(max(1f, width - MARGIN * 2f).dp, (HEADER - 1f / density.density).dp).wrapContentSize(Alignment.CenterStart), style = textStyle(16f, 21f, 700, align = TextAlign.Start), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.offset((width - MARGIN - timeWidth).dp, ((HEADER - 1f / density.density - TIME_HEIGHT) / 2f).dp).size(timeWidth.dp, TIME_HEIGHT.dp).background(color(CHIP), RoundedCornerShape(TIME_HEIGHT.dp / 2f)), contentAlignment = Alignment.Center) { BasicText(time, style = timeStyle) }
            Box(Modifier.offset(y = (HEADER - 1f / density.density).dp).size(width.dp, (1f / density.density).dp).background(color(HAIRLINE)).clearAndSetSemantics {})
            Canvas(Modifier.offset(y = HEADER.dp).size(width.dp, cupsHeight.dp).clipToBounds().semantics { role = Role.Image; contentDescription = "$cups × ${chosen.label} $drink" }) {
                surface(0.dp) { _, _, _, _ ->
                    cupShape.update(body.value.toFloat(), (HOP_HEIGHT * hopAt(hop.value)).toFloat())
                    withTransform({ translate(width / 2f, cupsHeight - GROUND); scale(fit.value.toFloat(), fit.value.toFloat(), Offset.Zero) }) {
                        pile.forEach { cup ->
                            val alpha = cup.alpha()
                            if (alpha > 0f) {
                                val t = cup.transform(scale.value, landed.value)
                                withTransform({ translate(t[0].toFloat(), t[1].toFloat()); rotate(t[2].toFloat(), Offset.Zero); scale(t[3].toFloat(), t[4].toFloat(), Offset.Zero) }) { cupArt.draw(this, cupShape, alpha) }
                            }
                        }
                    }
                }
            }
            BasicText(figureLabel, Modifier.offset(y = figureLabelY.dp).size(width.dp, labelHeight.dp), style = figureLabelStyle)
            val figureWidth = measuredFigure.size.width / density.density; val unitWidth = measuredUnit.size.width / density.density
            val figureX = (width - figureWidth - UNIT_LEFT - unitWidth) / 2f
            Box(Modifier.offset(y = figureRowY.dp).size(width.dp, figureRowHeight.dp).semantics(mergeDescendants = true) { contentDescription = "${number(total)} $unit" }) {
                BasicText(number(figure), Modifier.offset(figureX.dp, (baseline - figureBase).dp).size(figureWidth.dp, figureHeight.dp).clearAndSetSemantics {}, style = figureStyle)
                BasicText(unit, Modifier.offset((figureX + figureWidth + UNIT_LEFT).dp, (baseline - unitBase).dp).size(unitWidth.dp, unitHeight.dp).clearAndSetSemantics {}, style = unitStyle)
            }
            Box(Modifier.offset(((width - sizesWidth) / 2f).dp, sizesY.dp).wrapContentSize(Alignment.TopStart, unbounded = true).size(sizesWidth.dp, sizesHeight.dp).selectableGroup()) {
                sizes.forEachIndexed { index, item ->
                    val metrics = measures[index]; val visual = sizeVisuals[index]
                    androidx.compose.runtime.key(item.label) {
                        StepperControl("${item.label}, ${item.volume}", true, Modifier.offset(pills[index].x.dp, 0.dp).size(metrics.columnWidth.dp, sizesHeight.dp).semantics { selected = index == sizeIndex }, controlScope, role = Role.RadioButton, action = { latestTouched(); latestSize(index, false) }) { pressed ->
                            LaunchedEffect(pressed, autoPressed == "size-$index") { visual.hold.tween(if (pressed || autoPressed == "size-$index") 1.0 else 0.0, PRESS_MS.toDouble(), CssEase, startingAt = timestamp()) }
                            Box(Modifier.fillMaxSize()) {
                                Box(Modifier.offset(((metrics.columnWidth - metrics.pillWidth) / 2f).dp, 0.dp).size(metrics.pillWidth.dp, PILL_HEIGHT.dp).background(blend(color(PILL), color(PILL_PRESSED), visual.hold.value), RoundedCornerShape(PILL_HEIGHT.dp / 2f)))
                                BasicText(item.volume, Modifier.offset(y = (PILL_HEIGHT + VOLUME_TOP).dp).size(metrics.columnWidth.dp, (metrics.volume.size.height / density.density).dp).clearAndSetSemantics {}, style = volumeStyle.copy(color = blend(color(VOLUME_OFF), color(INK), visual.on.value)))
                            }
                        }
                    }
                }
                Box(Modifier.offset(thumbX.value.toFloat().dp, 0.dp).size(max(0f, thumbWidth.value.toFloat()).dp, PILL_HEIGHT.dp).background(color(INK), RoundedCornerShape(PILL_HEIGHT.dp / 2f)).clearAndSetSemantics {})
                var labelX = 0f
                sizes.forEachIndexed { index, item ->
                    val metrics = measures[index]
                    Box(Modifier.offset(labelX.dp, 0.dp).size(metrics.pillWidth.dp, PILL_HEIGHT.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                        BasicText(item.label, Modifier.size((metrics.pillWidth - PILL_PADDING * 2f).dp, (metrics.label.size.height / density.density).dp), style = labelStyle.copy(color = blend(color(PILL_INK), Color.Black, sizeVisuals[index].on.value)))
                    }
                    labelX += metrics.pillWidth + PILL_GAP
                }
            }
            Box(Modifier.offset(((width - STEPPER_WIDTH) / 2f).dp, stepperY.dp).size(STEPPER_WIDTH.dp, STEPPER_HEIGHT.dp).background(color(STEPPER_FILL), RoundedCornerShape(STEPPER_HEIGHT.dp / 2f)).semantics { progressBarRangeInfo = ProgressBarRangeInfo(cups.toFloat(), 1f..most.toFloat(), max(0, most - 2)) }) {
                Round("remove", "One less", cups > 1, autoPressed == "less", lessVisual, Modifier.offset((STEPPER_PADDING - ROUND_SLOP).dp, (STEPPER_PADDING - ROUND_SLOP).dp).wrapContentSize(Alignment.TopStart, unbounded = true).size((ROUND_SIZE + ROUND_SLOP * 2f).dp), controlScope, ::timestamp) { latestTouched(); latestStep(-1, false) }
                BasicText(cups.toString(), Modifier.align(Alignment.Center).graphicsLayer { alpha = (1.0 - abs(roll.value)).toFloat(); translationY = roll.value.toFloat() * ROLL * density.density }, style = textStyle(15f, 20f, 500, tabular = true))
                Round("add", "One more", cups < most, autoPressed == "more", moreVisual, Modifier.offset((STEPPER_WIDTH - STEPPER_PADDING - ROUND_SIZE - ROUND_SLOP).dp, (STEPPER_PADDING - ROUND_SLOP).dp).wrapContentSize(Alignment.TopStart, unbounded = true).size((ROUND_SIZE + ROUND_SLOP * 2f).dp), controlScope, ::timestamp) { latestTouched(); latestStep(1, false) }
            }
            StepperControl(confirmLabel, true, Modifier.offset(y = confirmY.dp).wrapContentSize(Alignment.TopStart, unbounded = true).size(width.dp, (CONFIRM_TOP + CONFIRM).dp), controlScope, action = latestConfirm) { pressed ->
                LaunchedEffect(pressed) { confirmHold.tween(if (pressed) 1.0 else 0.0, CONFIRM_MS.toDouble(), CssEase, startingAt = timestamp()) }
                Box(Modifier.offset(MARGIN.dp, CONFIRM_TOP.dp).size(max(1f, width - MARGIN * 2f).dp, CONFIRM.dp).background(blend(color(INK), color(CONFIRM_PRESSED), confirmHold.value), RoundedCornerShape(CONFIRM.dp / 2f)), contentAlignment = Alignment.Center) { BasicText(confirmLabel, style = textStyle(15f, 20f, 600, Color.Black)) }
            }
        }
    }
}

/** Ticks stay at their literal positions; narrow screens crop the still ruler. */
@Composable
private fun Timeline(subtitle: String, suggestedTime: String, hours: List<String>, modifier: Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = textStyle(12f, 16f, 500, color(SUGGESTED_INK), spacing = 1f)
    val measured = remember(suggestedTime, style, density) { measurer.measure(AnnotatedString(suggestedTime), style, constraints = Constraints(maxWidth = ((SUGGESTED_WIDTH - SUGGESTED_PADDING * 2f) * density.density).roundToInt())) }
    val chipWidth = measured.size.width / density.density + SUGGESTED_PADDING * 2f
    val suggestedX = TICK_LEFT + TICK_AT * TICK_STEP
    val pointer = remember { Path().apply { moveTo(-POINTER_WIDTH, 0f); lineTo(POINTER_WIDTH, 0f); lineTo(0f, POINTER_HEIGHT); close() } }
    Box(modifier.clipToBounds().semantics(mergeDescendants = true) { contentDescription = "$subtitle: $suggestedTime" }) {
        Box(Modifier.offset((suggestedX - chipWidth / 2f).dp, 0.dp).size(chipWidth.dp, SUGGESTED_HEIGHT.dp).background(color(SUGGESTED_FILL), RoundedCornerShape(SUGGESTED_HEIGHT.dp / 2f)).clearAndSetSemantics {}, contentAlignment = Alignment.Center) { BasicText(suggestedTime, style = style) }
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            surface(0.dp) { _, _, _, _ ->
                withTransform({ translate(suggestedX, SUGGESTED_HEIGHT + POINTER_TOP) }) { drawPath(pointer, color(POINTER_INK)) }
                repeat(TICKS) { index ->
                    val height = if (index % 4 == 0) TICK_MAJOR else TICK_MINOR
                    drawRoundRect(color(if (index < TICK_AT) TICK_EARLY else if (index == TICK_AT) TICK_NOW else TICK_LATE), Offset(TICK_LEFT + index * TICK_STEP - TICK_WIDTH / 2f, TIMELINE_HEIGHT - TICK_BOTTOM - height), Size(TICK_WIDTH, height), CornerRadius(1f))
                }
            }
        }
        val hourStyle = textStyle(10f, 13f, 500, color(HOUR_INK))
        hours.forEachIndexed { index, hour ->
            val text = remember(hour, hourStyle, density) { measurer.measure(AnnotatedString(hour), hourStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (HOUR_WIDTH * density.density).roundToInt())) }
            val height = text.size.height / density.density
            BasicText(hour, Modifier.offset((TICK_LEFT + index * 8f * TICK_STEP - HOUR_WIDTH / 2f).dp, (TIMELINE_HEIGHT - HOUR_BOTTOM - height).dp).size(HOUR_WIDTH.dp, height.dp).clearAndSetSemantics {}, style = hourStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Round(icon: String, label: String, enabled: Boolean, held: Boolean, visual: RoundVisual, modifier: Modifier, scope: CoroutineScope, timestamp: () -> Long, onPress: () -> Unit) {
    StepperControl(label, enabled, modifier, scope, slop = ROUND_SLOP, visualBounds = Rect(ROUND_SLOP, ROUND_SLOP, ROUND_SLOP + ROUND_SIZE, ROUND_SLOP + ROUND_SIZE), action = onPress) { pressed ->
        LaunchedEffect(pressed, held) { visual.hold.tween(if (pressed || held) 1.0 else 0.0, PRESS_MS.toDouble(), CssEase, startingAt = timestamp()) }
        LaunchedEffect(enabled) { visual.alpha.tween(if (enabled) 1.0 else ROUND_DISABLED, PRESS_MS.toDouble(), CssEase, startingAt = timestamp()) }
        Box(Modifier.offset(ROUND_SLOP.dp, ROUND_SLOP.dp).size(ROUND_SIZE.dp).graphicsLayer {
            scaleX = (1.0 + (ROUND_SCALE - 1.0) * visual.hold.value).toFloat(); scaleY = scaleX
            alpha = visual.alpha.value.toFloat()
        }.background(blend(color(ROUND), color(ROUND_PRESSED), visual.hold.value), RoundedCornerShape(ROUND_SIZE.dp / 2f)).clearAndSetSemantics {}) { StepperIcon(icon, ROUND_ICON, color(INK)) }
    }
}
private class PressState { var alive = true; var latestOut: Job? = null }

/** CSS press state stays on for Pressability's minimum of 130 ms; release actions are immediate. */
@Composable
private fun StepperControl(label: String, enabled: Boolean, modifier: Modifier, scope: CoroutineScope, role: Role = Role.Button, slop: Float = 0f, visualBounds: Rect? = null, action: () -> Unit, content: @Composable (Boolean) -> Unit) {
    val state = remember { PressState() }
    var pressed by remember { mutableStateOf(false) }
    val granted by rememberUpdatedState(enabled)
    val currentAction by rememberUpdatedState(action)
    val view = LocalView.current
    DisposableEffect(state) {
        state.alive = true
        onDispose { state.alive = false; state.latestOut?.cancel() }
    }
    Box(modifier.semantics(mergeDescendants = true) {
        this.role = role; contentDescription = label
        if (!enabled) disabled()
        onClick { if (granted) { currentAction(); true } else false }
    }.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!granted || down.position.x < 0f || down.position.x > size.width || down.position.y < 0f || down.position.y > size.height) return@awaitEachGesture
            state.latestOut?.cancel(); state.latestOut = null
            var active = true
            var activatedAt: Long? = System.nanoTime()
            fun pressIn() { activatedAt = System.nanoTime(); pressed = true }
            fun pressOut() {
                val remaining = max(0L, MIN_PRESS_DURATION_MS * 1_000_000L - (System.nanoTime() - (activatedAt ?: 0L)))
                activatedAt = null
                if (remaining == 0L) pressed = false
                else state.latestOut = scope.launch { delay((remaining + 999_999L) / 1_000_000L); pressed = false }
            }
            val box = visualBounds ?: Rect(0f, 0f, size.width / density, size.height / density)
            val left = (box.left - slop - PRESS_RETENTION) * density; val right = (box.right + slop + PRESS_RETENTION) * density
            val top = (box.top - slop - PRESS_RETENTION) * density; val bottom = (box.bottom + slop + PRESS_RETENTION_BOTTOM) * density
            pressIn(); down.consume()
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (finger.isConsumed) break
                    val inside = finger.position.x > left && finger.position.x < right && finger.position.y > top && finger.position.y < bottom
                    if (inside != active) { active = inside; if (inside) pressIn() else pressOut() }
                    finger.consume()
                    if (!finger.pressed) {
                        if (active) { pressOut(); active = false; view.playSoundEffect(SoundEffectConstants.CLICK); currentAction() }
                        break
                    }
                }
                if (active) { active = false; pressOut() }
            } catch (cancelled: CancellationException) {
                if (active) scope.launch { yield(); if (state.alive) pressOut() }
                throw cancelled
            }
        }
    }, contentAlignment = Alignment.TopStart) { content(pressed) }
}
