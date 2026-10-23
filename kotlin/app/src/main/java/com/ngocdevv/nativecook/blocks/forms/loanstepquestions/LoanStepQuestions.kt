package com.ngocdevv.nativecook.blocks.forms.loanstepquestions

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.math.BigDecimal
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

/** The rounded monthly payment and total for the chosen amount and term. */
data class Loan(val amount: Double, val months: Double, val monthly: Double, val total: Double)
private const val BACKGROUND = "#FFFFFF"
private const val INK = "#3A3F58"
private const val PALE = "#C9CCD6"
private const val TICKS = "#C9CEEA"
private const val BLUE = "#3F5BD9"
private const val STRIP = "#F4F5F8"
private const val FOOTER = "#D9DBE1"
private const val TRACK = "#C4C8DC"
private const val MARGIN = 24f
private const val BAR = 44f
private const val QUESTION = 28f
private const val QUESTION_LINE = 36f
private const val LABEL_AT = 98f
private const val LABEL = 16f
private const val FIGURE = 80f
private const val FIGURE_BASE = 198f
private const val FIGURE_TRACKING = -2f
private const val RULER_AT = 256f
private const val STRIP_AT = 300f
private const val STRIP_HEIGHT = 46f
private const val STRIP_RADIUS = 8f
private const val STRIP_GAP = 10f
private const val SUMMARY_AT = 236f
private const val CONTENT = STRIP_AT + STRIP_HEIGHT
private const val LEAD = .24f
private const val FOOTER_HEIGHT = 56f
private const val TRACK_HEIGHT = 3f
private const val TURN = .9
private const val OUT = .52
private const val IN_AT = .42
private const val STAGGER = .12
private const val PARTS = 5
private const val TRAVEL = 70f
private const val SMEAR = 7f
private const val DIP = 14f
private const val GHOST = .26
private const val GHOST_FROM = .3f
private const val GHOST_LEFT = .4f
private const val FINE = 10.0
private const val GLIDE = .16
private data class Spring(val mass: Double, val stiffness: Double, val damping: Double)
private val SNAP = Spring(1.0, 190.0, 24.0)
private const val RUBBER = .25
const val LOOP = 9.6
private const val AUTO_AMOUNT = 47000.0
private const val AUTO_MONTHS = 29.0
private const val DRAG_AMOUNT_AT = .8
private const val DRAG_AMOUNT_FOR = 1.4
private const val NEXT_AT = 2.6
private const val DRAG_MONTHS_AT = 3.8
private const val DRAG_MONTHS_FOR = 1.1
private const val SUMMARY_NEXT_AT = 5.3
private const val RESTART_AT = 7.6
private const val PRESS_FOR = .14
private const val USER_HOLD = 3.0
private const val DOT = 56f
private const val OFFSET_DELAY_MS = 140
private const val DRAG_DOT_IN_MS = 140
private const val DRAG_DOT_OUT_MS = 220
private const val PRESS_DOT_IN_MS = 120
private const val PRESS_DOT_WAIT_MS = 180
private const val PRESS_DOT_OUT_MS = 240
private const val LINE_MS = 500
private const val NEXT_FILL_MS = 350
private const val NEXT_ALPHA_MS = 100
private const val RESUME_POLL_MS = 100L
private const val RESUME_AFTER = TURN + .5
private const val PAN_OFFSET = 6f
private const val PAN_FAIL = 16f
private const val RULER_HIT = 72f
private const val MIN_LEAD = 8f
private const val FOOTER_SAFE = 12f
private const val END_TEXT = 11f
private const val END_BASE = 4f
private const val RULER_LEFT = 26f
private const val RULER_RIGHT = 34f
private const val ACTION_GAP = 8f
private const val ACTION_ICON = 18f
private const val ACTION_TEXT = 13f
private const val ACTION_LINE = 18f
private const val ACTION_TRACKING = .6f
private const val COLLAPSE_ICON = 22f
private const val COLLAPSE_INSET = 11f
private const val PRESS_RETENTION = 20f
private const val PRESS_RETENTION_BOTTOM = 30f
private const val MIN_PRESS_DURATION_MS = 130L
private const val LEGACY_DIM = .22f
private const val SPRING_FRAME_CAP = .064
private const val SPRING_ENERGY_THRESHOLD = 6e-9
// Source glyph outlines keep the icons independent of a bundled font.
private data class LoanIconPath(val path: Path, val units: Float, val center: Float)
private val LOAN_ICON_PATHS = mapOf(
    "chevron-up" to LoanIconPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M246 285Q246 285 246 285Q250 288 256 288Q264 288 274.0 279.5Q284 271 345 209Q405 149 414.0 139.0Q423 129 424 122Q425 113 419.0 105.5Q413 98 404 97Q396 95 389 99Q386 100 321 165Q321 165 256 230L191 165Q126 100 123 99Q116 95 108 97Q99 98 93.0 105.5Q87 113 88 122Q89 129 98.0 139.0Q107 149 167 209Q241 283 246 285Z").toPath(), 512f, 191.5f),
    "chevron-down" to LoanIconPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M102 286Q102 286 102 286Q113 291 123 285Q127 283 191 219Q191 219 256 154L321 219Q385 283 389 285Q398 291 409 286Q426 279 424 262Q423 255 414.0 245.0Q405 235 345 175Q284 113 274.0 104.5Q264 96 256.0 96.0Q248 96 238.0 104.5Q228 113 167 175Q107 235 98.0 245.0Q89 255 88 262Q86 278 102 286Z").toPath(), 512f, 191.5f),
    "checkmark" to LoanIconPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M411 335Q411 335 411 335Q416 337 420 335Q428 333 431 325Q434 317 429.5 311.0Q425 305 313.0 177.5Q201 50 199 50Q194 47 188 49Q184 50 133.5 100.5Q83 151 81 153Q77 162 83 169Q91 180 103 174Q105 173 148 130Q148 130 191 87L193 90Q195 92 299.5 211.5Q404 331 406.0 332.5Q408 334 411 335Z").toPath(), 512f, 191.5f),
    "checkmark-done" to LoanIconPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M363 335Q373 338 379 331Q387 323 382 314Q381 311 313.0 232.5Q245 154 242 151Q236 147 228.5 149.0Q221 151 217.5 157.0Q214 163 218 170Q219 173 287.0 251.5Q355 330 358 333Q360 334 363 335ZM460 335Q471 338 477 330Q483 321 478 312Q476 310 362.5 180.5Q249 51 248 50Q242 47 235 49Q232 50 181.5 100.0Q131 150 130 153Q126 160 130.0 167.0Q134 174 142 176Q148 176 154.0 171.5Q160 167 195 131L239 88L242 90Q245 93 349.5 213.0Q454 333 456.0 334.0Q458 335 460 335ZM41 174Q41 174 41 174Q47 177 54 174Q57 173 106.0 124.5Q155 76 158 72Q163 63 156.0 54.0Q149 45 139 49Q136 50 85.5 100.5Q35 151 33.5 153.5Q32 156 32.0 160.0Q32 164 34 167Q36 172 41 174Z").toPath(), 512f, 191.5f),
    "arrow-collapse" to LoanIconPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M416 382 320 286V363H277V213H427V256H350L446 352ZM85 171V128H162L66 32L96 2L192 98V21H235V171Z").toPath(), 512f, 193.5f)
)
private val Linear = Easing { it }
private val QuadInOut = Easing { if (it < .5f) 2f * it * it else 1f - (-2f * it + 2f).pow(2) / 2f }
private val QuadOut = Easing { 1f - (1f - it).pow(2) }
private val CubicOut = Easing { 1f - (1f - it).pow(3) }
private val CubicInOut = Easing { if (it < .5f) 4f * it * it * it else 1f - (-2f * it + 2f).pow(3) / 2f }
private val CssEase = CubicBezierEasing(.25f, .1f, .25f, 1f)
private fun color(hex: String) = Color(android.graphics.Color.parseColor(hex))
private fun clamp(value: Double, low: Double, high: Double) = min(high, max(low, value))
private fun smooth(t: Double, from: Double, to: Double): Double { val u = clamp((t - from) / (to - from), 0.0, 1.0); return u * u * (3.0 - 2.0 * u) }
private fun placeOf(pages: Int, order: Int, from: Int, to: Int, progress: Double): Double {
    val here = (pages shr from) and 1; val there = (pages shr to) and 1
    if (here == 1 && there == 1) return 0.0
    val way = if (to > from) 1.0 else -1.0; val spread = 1.0 + (PARTS - 1) * STAGGER
    if (here == 1) { val u = clamp(clamp(progress / OUT, 0.0, 1.0) * spread - order * STAGGER, 0.0, 1.0); return -way * u * u }
    if (there == 1) { val u = 1.0 - clamp(clamp((progress - IN_AT) / (1.0 - IN_AT), 0.0, 1.0) * spread - order * STAGGER, 0.0, 1.0); return way * u * u * u }
    return 2.0
}
private fun number(value: Double): String {
    if (value == 0.0) return "0"
    if (!value.isFinite()) return if (value.isNaN()) "NaN" else if (value > 0) "Infinity" else "-Infinity"
    if (abs(value) >= 1e-6 && abs(value) < 1e21) return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    val parts = value.toString().lowercase().replace(".0e", "e").split('e')
    val exponent = parts[1].toInt()
    return "${parts[0]}e${if (exponent >= 0) "+" else ""}$exponent"
}
private fun rounded(value: Double): Double {
    if (!value.isFinite() || value == 0.0) return value
    val lower = floor(value)
    val result = if (value - lower < .5) lower else lower + 1.0
    return if (result == 0.0 && value < 0.0) -0.0 else result
}
private fun grouped(value: Double) = number(rounded(value)).replace(Regex("\\B(?=(\\d{3})+(?!\\d))"), ",")
private fun short(value: Double) = if (value >= 1000.0) "${number(rounded(value / 100.0) / 10.0)}k" else number(value)
private fun cost(amount: Double, months: Double, rate: Double): Loan {
    val monthlyRate = rate / 1200.0
    val monthly = if (monthlyRate == 0.0) amount / months else amount * monthlyRate / (1.0 - (1.0 + monthlyRate).pow(-months))
    return Loan(amount, months, rounded(monthly), rounded(monthly * months))
}
private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }
private class FrameTime(var now: Long? = null)

/** Keep the clipped page layer at full size while its contents travel and fade. */
@Composable
private fun LoanPart(bounds: Rect, place: Double, still: Boolean, content: @Composable () -> Unit) {
    val away = min(1.0, abs(place)).toFloat()
    val sigma = if (still) 0f else SMEAR * 4f * away * (1f - away)
    Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
        .size(bounds.width.dp, bounds.height.dp)
        .graphicsLayer {
            translationX = bounds.left * density
            translationY = (bounds.top + if (still) 0f else place.toFloat() * TRAVEL) * density
            alpha = (1f - away) * if (Build.VERSION.SDK_INT < 31) 1f - LEGACY_DIM * sigma / SMEAR else 1f
            clip = true
            compositingStrategy = CompositingStrategy.Offscreen
            renderEffect = if (Build.VERSION.SDK_INT >= 31 && sigma > 0f) BlurEffect(0f, sigmaToRadius(sigma * density), TileMode.Decal) else null
        }.clearAndSetSemantics {}) { content() }
}


@Composable
private fun LoanIcon(name: String, size: Float, ink: Color) {
    val art = LOAN_ICON_PATHS[name] ?: return
    Canvas(Modifier.size(size.dp).clearAndSetSemantics {}) {
        surface(0.dp) { _, _, _, _ ->
            val unit = size / art.units
            withTransform({
                translate(0f, size / 2f + art.center * unit)
                scale(unit, -unit, Offset.Zero)
            }) { drawPath(art.path, ink) }
        }
    }
}

// Glyph slots are keyed by their left index, so only changed characters ghost.
private class LoanFigureArt {
    private class Glyph(initial: String) {
        var current by mutableStateOf(initial)
        var before by mutableStateOf("")
        var fresh by androidx.compose.runtime.mutableFloatStateOf(1f)
        var began: Long? = null
    }
    private var pages by mutableStateOf(List(3) { emptyList<Glyph>() })
    private val figurePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = FIGURE
        typeface = if (Build.VERSION.SDK_INT >= 28) android.graphics.Typeface.create(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL), 300, false)
        else android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
    }
    private val endPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = END_TEXT
        typeface = if (Build.VERSION.SDK_INT >= 28) android.graphics.Typeface.create(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL), 500, false)
        else android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        color = android.graphics.Color.parseColor(INK)
    }
    private val widths = mutableMapOf<String, Float>()
    private val endWidths = mutableMapOf<String, Float>()
    private val digitWidth = ('0'..'9').maxOf { advance(it.toString()) } + FIGURE_TRACKING
    private val ink = android.graphics.Color.parseColor(INK)
    private val pale = android.graphics.Color.parseColor(PALE)

    private fun points(text: String): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            val point = text.codePointAt(index)
            result += String(Character.toChars(point))
            index += Character.charCount(point)
        }
        return result
    }
    private fun advance(text: String) = widths.getOrPut(text) { figurePaint.measureText(text) }

    fun update(texts: List<String>, now: Long, still: Boolean) {
        pages = List(3) { page ->
            val previous = pages[page]
            points(texts.getOrElse(page) { "" }).mapIndexed { index, char ->
                val glyph = previous.getOrNull(index) ?: Glyph(char)
                if (glyph.current != char) {
                    glyph.before = glyph.current
                    glyph.current = char
                    glyph.fresh = if (still) 1f else 0f
                    glyph.began = if (still) null else now
                }
                glyph
            }
        }
    }

    fun advance(now: Long) {
        pages.forEach { page -> page.forEach { glyph ->
            glyph.began?.let { began ->
                val elapsed = ((now - began) / (GHOST * 1e9)).coerceIn(0.0, 1.0).toFloat()
                glyph.fresh = QuadOut.transform(elapsed)
                if (elapsed >= 1f) glyph.began = null
            }
        } }
    }

    fun draw(scope: DrawScope, page: Int, x: Float, baseline: Float, symbol: String) {
        val glyphs = pages.getOrNull(page) ?: return
        val canvas = scope.drawContext.canvas.nativeCanvas
        var cursor = x
        glyphs.forEach { glyph ->
            val cell = if (glyph.current.length == 1 && glyph.current[0] in '0'..'9') digitWidth else advance(glyph.current) + FIGURE_TRACKING
            figurePaint.color = if (glyph.current == symbol) pale else ink
            if (glyph.before.isNotEmpty()) {
                figurePaint.alpha = (GHOST_LEFT * (1f - glyph.fresh) * 255f).roundToInt().coerceIn(0, 255)
                canvas.drawText(glyph.before, cursor + (cell - advance(glyph.before)) / 2f, baseline, figurePaint)
            }
            figurePaint.alpha = ((GHOST_FROM + (1f - GHOST_FROM) * glyph.fresh) * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawText(glyph.current, cursor + (cell - advance(glyph.current)) / 2f, baseline, figurePaint)
            cursor += cell
        }
    }

    fun endWidth(text: String): Float = endWidths.getOrPut(text) { points(text).sumOf { endPaint.measureText(it).toDouble() }.toFloat() }

    fun drawEnd(scope: DrawScope, text: String, x: Float, baseline: Float, alpha: Float) {
        endPaint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        scope.drawContext.canvas.nativeCanvas.drawText(text, x, baseline, endPaint)
    }
}


private sealed interface AnimationSpec {
    data class Time(val target: Double, val ms: Double, val ease: Easing = QuadInOut, val still: Boolean = false, val completion: ((Boolean) -> Unit)? = null) : AnimationSpec
    data class Bounce(val target: Double, val still: Boolean, val completion: ((Boolean) -> Unit)? = null) : AnimationSpec
    data class Delay(val ms: Double, val child: AnimationSpec, val still: Boolean = false) : AnimationSpec
    data class Sequence(val children: List<AnimationSpec>) : AnimationSpec
}
private interface Node {
    val value: Double
    val done: Boolean
    fun advance(now: Long)
    fun cancelCallback()
}
private class TimingNode(initial: Double, spec: AnimationSpec.Time, previous: Node?, now: Long) : Node {
    val target = spec.target
    var origin = now
    private var from = initial
    private val duration = spec.ms * 1e6
    private val curve = spec.ease
    private var completion = spec.completion
    private val still = spec.still
    override var value = initial
    override var done = false
    init { if (previous is TimingNode && previous.target == target && previous.origin != 0L) { origin = previous.origin; from = previous.from } }
    override fun advance(now: Long) {
        if (done) return
        val u = if (still || duration <= 0.0) 1.0 else clamp((now - origin) / duration, 0.0, 1.0)
        value = from + (target - from) * if (curve === Linear) u else curve.transform(u.toFloat()).toDouble()
        if (u >= 1.0) { value = target; done = true; origin = 0L; val finished = completion; completion = null; finished?.invoke(true) }
    }
    override fun cancelCallback() { val finished = completion; completion = null; finished?.invoke(false) }
}
private class SpringNode(initial: Double, spec: AnimationSpec.Bounce, previous: Node?, now: Long) : Node {
    val target = spec.target
    private var velocity: Double = (previous as? SpringNode)?.velocity ?: 0.0
    private var timestamp: Long = (previous as? SpringNode)?.timestamp?.takeIf { it != 0L } ?: now
    private val displacement: Double = if (previous is SpringNode && previous.target == target && previous.timestamp != 0L) previous.displacement else initial - target
    private val energy0 = .5 * SNAP.stiffness * displacement * displacement
    private var completion = spec.completion
    private val still = spec.still
    override var value = initial
    override var done = false
    init { if ((target > initial && velocity < 0.0) || (target < initial && velocity > 0.0)) velocity = 0.0 }
    override fun advance(now: Long) {
        if (done) return
        val dt = min(SPRING_FRAME_CAP, ((now - timestamp) / 1e9).coerceAtLeast(0.0)); timestamp = now
        val omega = sqrt(SNAP.stiffness / SNAP.mass); val decay = SNAP.damping / (2.0 * SNAP.mass); val e = value - target
        val next: Double
        if (decay < omega) { val w = sqrt(omega * omega - decay * decay); val s = sin(w * dt); val c = cos(w * dt); val envelope = exp(-decay * dt); next = envelope * (e * c + (velocity + decay * e) / w * s); velocity = envelope * (velocity * c - (decay * velocity + omega * omega * e) / w * s) }
        else { val envelope = exp(-omega * dt); val b = velocity + omega * e; next = envelope * (e + b * dt); velocity = envelope * (velocity - omega * b * dt) }
        value = target + next
        val energy = .5 * SNAP.stiffness * next * next + .5 * SNAP.mass * velocity * velocity
        if (still || energy0 == 0.0 || energy / energy0 <= SPRING_ENERGY_THRESHOLD) { value = target; velocity = 0.0; done = true; timestamp = 0L; val finished = completion; completion = null; finished?.invoke(true) }
    }
    override fun cancelCallback() { val finished = completion; completion = null; finished?.invoke(false) }
}
private fun node(initial: Double, spec: AnimationSpec, previous: Node?, now: Long): Node = when (spec) {
    is AnimationSpec.Time -> TimingNode(initial, spec, previous, now)
    is AnimationSpec.Bounce -> SpringNode(initial, spec, previous, now)
    is AnimationSpec.Delay -> DelayNode(initial, spec, previous, now)
    is AnimationSpec.Sequence -> SequenceNode(initial, spec.children, previous, now)
}
/** A delayed replacement lets the preceding animation continue until its child starts. */
private class DelayNode(initial: Double, private val spec: AnimationSpec.Delay, previous: Node?, now: Long) : Node {
    private val at = now + (spec.ms * 1e6).toLong()
    private var old = previous
    private var child: Node? = null
    private var current = initial
    override val value get() = child?.value ?: current
    override val done get() = child?.done == true
    override fun advance(now: Long) {
        if (child == null && !spec.still && now < at) { old?.advance(now); old?.let { current = it.value }; if (old?.done == true) old = null; return }
        if (child == null) { child = node(current, spec.child, old, now); old = null }
        child?.advance(now)
    }
    override fun cancelCallback() { child?.cancelCallback() }
}
private class SequenceNode(initial: Double, private val specs: List<AnimationSpec>, previous: Node?, now: Long) : Node {
    private var index = 0
    private var child = node(initial, specs[0], previous, now)
    override val value get() = child.value
    override val done get() = index == specs.lastIndex && child.done
    override fun advance(now: Long) {
        child.advance(now)
        while (child.done && index < specs.lastIndex) { val previous = child; index++; child = node(previous.value, specs[index], previous, now); child.advance(now) }
    }
    override fun cancelCallback() { child.cancelCallback() }
}
private class Scalar(initial: Double, private val clock: FrameTime) : State<Double> {
    private val state = mutableStateOf(initial)
    private var animation: Node? = null
    override val value get() = state.value
    fun cancel() { animation?.cancelCallback(); animation = null }
    fun direct(next: Double) { cancel(); state.value = next }
    fun assign(spec: AnimationSpec) {
        val previous = animation
        previous?.cancelCallback()
        val equal = when (spec) { is AnimationSpec.Time -> spec.target == value; is AnimationSpec.Bounce -> spec.target == value; else -> false }
        if (equal) {
            animation = null
            when (spec) { is AnimationSpec.Time -> spec.completion?.invoke(true); is AnimationSpec.Bounce -> spec.completion?.invoke(true); else -> Unit }
            return
        }
        val now = clock.now ?: System.nanoTime()
        animation = node(value, spec, previous, now)
        animation?.let { it.advance(now); state.value = it.value }
    }
    fun time(target: Double, ms: Double, curve: Easing = QuadInOut, still: Boolean = false, completion: ((Boolean) -> Unit)? = null) { assign(AnimationSpec.Time(target, ms, curve, still, completion)) }
    fun spring(target: Double, still: Boolean, completion: ((Boolean) -> Unit)? = null) { assign(AnimationSpec.Bounce(target, still, completion)) }
    fun advance(now: Long) { animation?.let { it.advance(now); state.value = it.value; if (it.done) animation = null } }
}
private class Reports(var amount: Double, var months: Double)
private class Reaction(var amount: Double? = null, var months: Double? = null)

/** A fixed marker and a pulled ruler answer two questions before a frozen summary. */
@Composable
fun LoanStepQuestions(
    amount: Double = 25000.0,
    minAmount: Double = 1000.0,
    maxAmount: Double = 100000.0,
    amountStep: Double = 1000.0,
    months: Double = 12.0,
    minMonths: Double = 6.0,
    maxMonths: Double = 96.0,
    rate: Double = 12.9,
    symbol: String = "$",
    questions: List<String> = listOf("How much would you like to borrow?", "Over how many months would you like to repay?", "Here is your loan. Does it look right?"),
    labels: List<String> = listOf("Amount", "Months", "Monthly payment"),
    monthlyLabel: String = "Monthly",
    totalLabel: String = "Total",
    amountLabel: String = "Amount",
    termLabel: String = "Term",
    rateLabel: String = "Rate",
    monthsUnit: String = "months",
    previousLabel: String = "PREV",
    nextLabel: String = "NEXT",
    confirmLabel: String = "CONFIRM",
    sentLabel: String = "SENT",
    autoPlay: Boolean = false,
    onChange: ((Loan) -> Unit)? = null,
    onConfirm: ((Loan) -> Unit)? = null,
    onCollapse: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val reduced by rememberUpdatedState(still)
    val currentRate by rememberUpdatedState(rate)
    val currentMinAmount by rememberUpdatedState(minAmount)
    val currentMaxAmount by rememberUpdatedState(maxAmount)
    val tickWorth = if (amountStep > 0.0 && amountStep.isFinite()) amountStep else 1000.0
    val currentStepWorth by rememberUpdatedState(tickWorth)
    val currentMinMonths by rememberUpdatedState(minMonths)
    val currentMaxMonths by rememberUpdatedState(maxMonths)
    val initialAmount by rememberUpdatedState(amount)
    val initialMonths by rememberUpdatedState(months)
    val changeCallback by rememberUpdatedState(onChange)
    val confirmCallback by rememberUpdatedState(onConfirm)
    val collapseCallback by rememberUpdatedState(onCollapse)
    val view = LocalView.current
    val density = LocalDensity.current
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val controlScope = rememberCoroutineScope()
    val clock = remember { FrameTime() }
    fun timestamp() = clock.now ?: System.nanoTime()
    var step by remember { mutableIntStateOf(0) }
    var turning by remember { mutableStateOf(false) }
    var chosenAmount by remember { mutableStateOf(clamp(amount, minAmount, maxAmount)) }
    var chosenMonths by remember { mutableStateOf(clamp(months, minMonths, maxMonths)) }
    var sent by remember { mutableStateOf(false) }
    var summed by remember { mutableStateOf<Loan?>(null) }
    var autoPressed by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var from by remember { mutableIntStateOf(0) }
    var to by remember { mutableIntStateOf(0) }
    var felt by remember { mutableStateOf(false) }
    val progress = remember { Scalar(1.0, clock) }
    fun amountPull(value: Double) = (value - currentMinAmount) / currentStepWorth * TICK_STEP
    fun monthsPull(value: Double) = (value - currentMinMonths) * TICK_STEP
    val amountOffset = remember { Scalar((clamp(amount, minAmount, maxAmount) - minAmount) / tickWorth * TICK_STEP, clock) }
    val monthsOffset = remember { Scalar((clamp(months, minMonths, maxMonths) - minMonths) * TICK_STEP, clock) }
    val line = remember { Scalar(0.0, clock) }
    val dotShown = remember { Scalar(0.0, clock) }
    var dotX by remember { mutableStateOf(0.0) }
    var dotY by remember { mutableStateOf(0.0) }
    var dotRides by remember { mutableStateOf(false) }
    var dragFrom by remember { mutableStateOf(0.0) }
    val nextFill = remember { Scalar(0.0, clock) }
    val nextAlpha = remember { Scalar(1.0, clock) }
    val figuresArt = remember { LoanFigureArt() }
    val reaction = remember { Reaction() }
    val reports = remember { Reports(chosenAmount, chosenMonths) }
    val loan = cost(chosenAmount, chosenMonths, rate)
    val summary = summed ?: loan
    val currentAmount by rememberUpdatedState(chosenAmount)
    val currentMonths by rememberUpdatedState(chosenMonths)
    val currentTurning by rememberUpdatedState(turning)
    val currentTo by rememberUpdatedState(to)
    val currentGestureEnabled by rememberUpdatedState(step < 2 && !turning)
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    fun touched() { autoPressed = false; dotShown.direct(0.0); touches++ }
    fun go(next: Int) {
        if (next == step || turning) return
        val previous = step
        turning = true; step = next
        if (next == 2) summed = cost(chosenAmount, chosenMonths, currentRate)
        from = previous; to = next; progress.direct(0.0)
        progress.time(1.0, (if (reduced) TURN / 2.0 else TURN) * 1000.0, Linear, reduced) { finished -> if (finished) turning = false }
    }
    fun next(auto: Boolean) {
        if (turning) return
        if (step < 2) { if (!auto) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); go(step + 1) }
        else if (!sent) {
            if (!auto) view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
            sent = true; confirmCallback?.invoke(summed ?: cost(chosenAmount, chosenMonths, currentRate))
        }
    }
    fun previous() { if (turning || step == 0) return; view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); sent = false; go(step - 1) }
    fun restart() {
        felt = false; sent = false
        val firstAmount = amountPull(clamp(initialAmount, currentMinAmount, currentMaxAmount))
        val firstMonths = monthsPull(clamp(initialMonths, currentMinMonths, currentMaxMonths))
        if (step == 2) { amountOffset.direct(firstAmount); monthsOffset.direct(firstMonths) }
        else { amountOffset.spring(firstAmount, reduced); monthsOffset.spring(firstMonths, reduced) }
        go(0)
    }
    fun nudge(by: Int) {
        val offset = if (step == 0) amountOffset else monthsOffset
        val pulled = if (step == 0) amountPull(chosenAmount) else monthsPull(chosenMonths)
        val reach = if (step == 0) amountPull(currentMaxAmount) else monthsPull(currentMaxMonths)
        offset.spring(clamp(rounded(pulled / TICK_STEP + by) * TICK_STEP, 0.0, reach), reduced)
    }
    val latestTouched by rememberUpdatedState(::touched)
    val latestNext by rememberUpdatedState(::next)
    val latestPrevious by rememberUpdatedState(::previous)
    val latestRestart by rememberUpdatedState(::restart)
    val latestNudge by rememberUpdatedState(::nudge)
    LaunchedEffect(step, sent) { line.time((step + if (sent) 1 else 0) / 3.0, LINE_MS.toDouble(), CubicOut, reduced) }
    LaunchedEffect(step == 2) { nextFill.time(if (step == 2) 1.0 else 0.0, NEXT_FILL_MS.toDouble(), CssEase) }
    LaunchedEffect(chosenAmount, chosenMonths, rate, onChange) {
        if (reports.amount != chosenAmount || reports.months != chosenMonths) {
            reports.amount = chosenAmount; reports.months = chosenMonths
            changeCallback?.invoke(cost(chosenAmount, chosenMonths, rate))
        }
    }
    val figures = listOf("$symbol${grouped(chosenAmount)}", number(chosenMonths), "$symbol${grouped(summary.monthly)}")
    LaunchedEffect(figures) { figuresArt.update(figures, timestamp(), reduced) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { now ->
            clock.now = now
            try {
                listOf(amountOffset, monthsOffset, progress, line, dotShown, nextFill, nextAlpha).forEach { it.advance(now) }
                figuresArt.advance(now)
                val amountTicks = rounded(amountOffset.value / TICK_STEP * FINE)
                if (reaction.amount != amountTicks) {
                    val old = reaction.amount; reaction.amount = amountTicks
                    chosenAmount = clamp(currentMinAmount + amountTicks * currentStepWorth / FINE, currentMinAmount, currentMaxAmount)
                    if (felt && old != null && floor(amountTicks / FINE) != floor(old / FINE)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                val monthTicks = rounded(monthsOffset.value / TICK_STEP)
                if (reaction.months != monthTicks) {
                    val old = reaction.months; reaction.months = monthTicks
                    chosenMonths = clamp(currentMinMonths + monthTicks, currentMinMonths, currentMaxMonths)
                    if (felt && old != null) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            } finally { clock.now = null }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(color(BACKGROUND))) {
        val width = maxWidth.value; val height = maxHeight.value
        val centre = width / 2f; val inner = max(1f, width - MARGIN * 2f)
        val footer = FOOTER_HEIGHT + max(safe.calculateBottomPadding().value, FOOTER_SAFE)
        val room = height - safe.calculateTopPadding().value - BAR - footer - CONTENT
        val top = safe.calculateTopPadding().value + BAR + max(MIN_LEAD, room * LEAD)
        val rulerY = top + RULER_AT
        val currentGeometry by rememberUpdatedState(floatArrayOf(centre, inner, rulerY, width * .75f, height - footer + TRACK_HEIGHT + (FOOTER_HEIGHT - TRACK_HEIGHT) / 2f))
        fun autoDrag(page: Int, seconds: Double) {
            felt = false
            val offset = if (page == 0) amountOffset else monthsOffset
            val start = if (page == 0) amountPull(currentAmount) else monthsPull(currentMonths)
            val rest = if (page == 0) amountPull(clamp(AUTO_AMOUNT, currentMinAmount, currentMaxAmount)) else monthsPull(clamp(AUTO_MONTHS, currentMinMonths, currentMaxMonths))
            val geometry = currentGeometry
            dragFrom = start; dotRides = true
            dotX = geometry[0] + clamp(rest - start, -geometry[1] * .7, geometry[1] * .7) / 2.0; dotY = geometry[2].toDouble()
            dotShown.assign(AnimationSpec.Sequence(listOf(AnimationSpec.Time(1.0, DRAG_DOT_IN_MS.toDouble(), still = reduced), AnimationSpec.Delay(seconds * 1000.0, AnimationSpec.Time(0.0, DRAG_DOT_OUT_MS.toDouble(), still = reduced), reduced))))
            offset.assign(AnimationSpec.Delay(OFFSET_DELAY_MS.toDouble(), AnimationSpec.Time(rest, seconds * 1000.0, CubicInOut, reduced), reduced))
        }
        fun autoPress() {
            val geometry = currentGeometry
            dotRides = false; dotX = geometry[3].toDouble(); dotY = geometry[4].toDouble()
            dotShown.assign(AnimationSpec.Sequence(listOf(AnimationSpec.Time(1.0, PRESS_DOT_IN_MS.toDouble(), still = reduced), AnimationSpec.Delay(PRESS_DOT_WAIT_MS.toDouble(), AnimationSpec.Time(0.0, PRESS_DOT_OUT_MS.toDouble(), still = reduced), reduced))))
            autoPressed = true
        }
        val latestDrag by rememberUpdatedState(::autoDrag)
        val latestPress by rememberUpdatedState(::autoPress)
        LaunchedEffect(autoPlay, touches) {
            if (!autoPlay) return@LaunchedEffect
            if (touches > 0) {
                delay((USER_HOLD * 1000.0).toLong())
                while (currentTurning) delay(RESUME_POLL_MS)
                latestRestart(); delay((RESUME_AFTER * 1000.0).toLong())
            }
            var origin = System.nanoTime()
            while (true) {
                val actions = listOf<Pair<Double, () -> Unit>>(
                    DRAG_AMOUNT_AT to { latestDrag(0, DRAG_AMOUNT_FOR) },
                    NEXT_AT to { latestPress() }, NEXT_AT + PRESS_FOR to { autoPressed = false; latestNext(true) },
                    DRAG_MONTHS_AT to { latestDrag(1, DRAG_MONTHS_FOR) },
                    SUMMARY_NEXT_AT to { latestPress() }, SUMMARY_NEXT_AT + PRESS_FOR to { autoPressed = false; latestNext(true) },
                    RESTART_AT to { latestRestart() },
                )
                for ((at, action) in actions) { delay(max(0L, (origin + (at * 1e9).toLong() - System.nanoTime()) / 1_000_000)); action() }
                delay(max(0L, (origin + (LOOP * 1e9).toLong() - System.nanoTime()) / 1_000_000)); origin += (LOOP * 1e9).toLong()
            }
        }
        fun part(pages: Int, order: Int) = placeOf(pages, order, from, to, progress.value)
        val questionBounds = Rect(0f, top - 30f, width, top + QUESTION_LINE * 2f + 30f)
        val labelBounds = Rect(0f, top + LABEL_AT - 30f, width, top + LABEL_AT + 52f)
        val figureBounds = Rect(0f, top + FIGURE_BASE - FIGURE - 16f, width, top + FIGURE_BASE + 44f)
        val rulerBounds = Rect(0f, rulerY - 56f, width, rulerY + 56f)
        fun stripBounds(y: Float) = Rect(0f, top + y - 30f, width, top + y + STRIP_HEIGHT + 30f)
        for (page in 0..2) {
            LoanPart(questionBounds, part(1 shl page, 0), still) { LoanParagraph(questions.getOrNull(page) ?: "", QUESTION, QUESTION_LINE, 300, color(INK), inner, MARGIN, top - questionBounds.top) }
            LoanPart(labelBounds, part(1 shl page, 1), still) { LoanParagraph(labels.getOrNull(page) ?: "", LABEL, 22f, 400, color(INK), inner, MARGIN, top + LABEL_AT - labelBounds.top) }
            LoanPart(figureBounds, part(1 shl page, 2), still) { Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) { surface(0.dp) { _, _, _, _ -> figuresArt.draw(this, page, MARGIN - 3f, top + FIGURE_BASE - figureBounds.top, symbol) } } }
        }
        val start = if (from == 2) to else from; val end = if (to == 2) from else to
        val monthsShare = start + (end - start) * smooth(progress.value, .3, .7)
        val shownOffset = amountOffset.value + (monthsOffset.value - amountOffset.value) * monthsShare
        val dip = if (from != to && from < 2 && to < 2 && !still) (if (from < to) -1f else 1f) * DIP * sin(2.0 * PI * smooth(progress.value, 0.0, 1.0)).toFloat() else 0f
        val rulerArt = remember(width) { RulerArt(MARGIN + RULER_LEFT, width - MARGIN - RULER_RIGHT, centre) }
        LoanPart(rulerBounds, part(3, 3), still) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                surface(0.dp) { _, _, _, _ ->
                    val y = rulerY - rulerBounds.top + dip
                    rulerArt.draw(this, shownOffset.toFloat(), MARGIN + RULER_LEFT, width - MARGIN - RULER_RIGHT, centre, y, color(TICKS), color(BLUE), color(BACKGROUND))
                    val ends = listOf(short(minAmount) to short(maxAmount), number(minMonths) to number(maxMonths))
                    ends.forEachIndexed { index, values ->
                        val alpha = (if (index == 0) 1.0 - monthsShare else monthsShare).toFloat()
                        figuresArt.drawEnd(this, values.first, MARGIN, y + END_BASE, alpha)
                        figuresArt.drawEnd(this, values.second, width - MARGIN - figuresArt.endWidth(values.second), y + END_BASE, alpha)
                    }
                }
            }
        }
        val normalStrip = stripBounds(STRIP_AT)
        LoanPart(normalStrip, part(3, 4), still) { LoanStrip(MARGIN, top + STRIP_AT - normalStrip.top, inner, "$monthlyLabel: $symbol${grouped(loan.monthly)}", "$totalLabel: $symbol${grouped(loan.total)}", color(INK), color(BLUE)) }
        val summaryFirst = stripBounds(SUMMARY_AT)
        LoanPart(summaryFirst, part(4, 3), still) { LoanStrip(MARGIN, top + SUMMARY_AT - summaryFirst.top, inner, "$amountLabel: $symbol${grouped(summary.amount)}", "$termLabel: ${number(summary.months)} $monthsUnit", color(INK), color(INK)) }
        val summarySecond = stripBounds(SUMMARY_AT + STRIP_HEIGHT + STRIP_GAP)
        LoanPart(summarySecond, part(4, 4), still) { LoanStrip(MARGIN, top + SUMMARY_AT + STRIP_HEIGHT + STRIP_GAP - summarySecond.top, inner, "$rateLabel: ${number(rate)}%", "$totalLabel: $symbol${grouped(summary.total)}", color(INK), color(BLUE)) }
        Box(Modifier.offset(MARGIN.dp, top.dp).size(inner.dp, FIGURE_BASE.dp).semantics { heading(); contentDescription = "${questions.getOrNull(step) ?: ""} ${labels.getOrNull(step) ?: ""}: ${if (step == 1) "${number(chosenMonths)} $monthsUnit" else figures[step]}" })
        val rulerSemantics = if (step == 2) Modifier.clearAndSetSemantics {} else Modifier.semantics {
            contentDescription = labels.getOrNull(min(step, 1)) ?: ""
            stateDescription = if (step == 0) figures[0] else "${number(chosenMonths)} $monthsUnit"
            customActions = listOf(CustomAccessibilityAction("Increment") { latestNudge(1); true }, CustomAccessibilityAction("Decrement") { latestNudge(-1); true })
        }
        Box(Modifier.offset(y = (rulerY - RULER_HIT / 2f).dp).size(width.dp, RULER_HIT.dp).then(rulerSemantics).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!currentGestureEnabled || down.position.x < 0f || down.position.x > size.width || down.position.y < 0f || down.position.y > size.height) return@awaitEachGesture
                var active = false; var origin = down.position; var caught = 0.0
                val initial = down.position
                val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                try {
                    while (true) {
                        val event = awaitPointerEvent(); val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed || !currentGestureEnabled) break
                        velocity.addPosition(change.uptimeMillis, change.position)
                        val dx = (change.position.x - initial.x) / density.density; val dy = (change.position.y - initial.y) / density.density
                        if (!active && abs(dy) > PAN_FAIL) break
                        if (!active && abs(dx) > PAN_OFFSET && change.pressed) {
                            active = true; origin = change.position
                            val offset = if (currentTo == 0) amountOffset else monthsOffset
                            offset.cancel(); caught = offset.value; dragFrom = caught; felt = true; latestTouched()
                        }
                        if (active && change.pressed) {
                            val offset = if (currentTo == 0) amountOffset else monthsOffset
                            val reach = if (currentTo == 0) amountPull(currentMaxAmount) else monthsPull(currentMaxMonths)
                            val pull = caught - (change.position.x - origin.x) / density.density
                            offset.direct(if (pull < 0.0) pull * RUBBER else if (pull > reach) reach + (pull - reach) * RUBBER else pull)
                            change.consume()
                        }
                        if (!change.pressed) {
                            if (active) {
                                val offset = if (currentTo == 0) amountOffset else monthsOffset
                                val reach = if (currentTo == 0) amountPull(currentMaxAmount) else monthsPull(currentMaxMonths)
                                val thrown = offset.value - velocity.calculateVelocity().x / density.density * GLIDE
                                offset.spring(clamp(rounded(thrown / TICK_STEP) * TICK_STEP, 0.0, reach), reduced) { felt = false }
                                change.consume()
                            }
                            break
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
            }
        })
        LoanControl("Short form", true, Modifier.offset((width - (MARGIN - COLLAPSE_INSET) - BAR).dp, safe.calculateTopPadding()).size(BAR.dp), controlScope, action = { latestTouched(); collapseCallback?.invoke() }) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoanIcon("arrow-collapse", COLLAPSE_ICON, color(INK)) } }
        val footerY = height - footer
        Box(Modifier.offset(y = footerY.dp).size(width.dp, footer.dp).background(color(FOOTER))) {
            Box(Modifier.size(width.dp, TRACK_HEIGHT.dp).background(color(TRACK)).clearAndSetSemantics {})
            Box(Modifier.size((width * line.value).toFloat().dp, TRACK_HEIGHT.dp).background(color(BLUE)).clearAndSetSemantics {})
            LoanControl(previousLabel, step != 0, Modifier.offset(y = TRACK_HEIGHT.dp).size((width / 2f).dp, (footer - TRACK_HEIGHT).dp), controlScope, action = { latestTouched(); latestPrevious() }) { pressed ->
                FooterRow(previousLabel, "chevron-up", if (step == 0) color(PALE) else color(BLUE), false, width / 2f, FOOTER_HEIGHT - TRACK_HEIGHT, (if (pressed) .5f else 1f))
            }
            val nextText = if (step == 2) if (sent) sentLabel else confirmLabel else nextLabel
            val nextIcon = if (step == 2) if (sent) "checkmark-done" else "checkmark" else "chevron-down"
            LoanControl(nextText, true, Modifier.offset((width / 2f).dp, TRACK_HEIGHT.dp).size((width / 2f).dp, (footer - TRACK_HEIGHT).dp), controlScope, action = { latestTouched(); latestNext(false) }) { pressed ->
                LaunchedEffect(pressed, autoPressed) { nextAlpha.time(if (pressed || autoPressed) .5 else 1.0, NEXT_ALPHA_MS.toDouble(), CssEase) }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = nextAlpha.value.toFloat() }.background(mix(color(FOOTER), color(BLUE), nextFill.value))) {
                    FooterRow(nextText, nextIcon, if (step == 2) Color.White else color(BLUE), true, width / 2f, FOOTER_HEIGHT - TRACK_HEIGHT, 1f)
                }
            }
        }
        val ridden = if (dotRides) (if (to == 0) amountOffset.value else monthsOffset.value) - dragFrom else 0.0
        Box(Modifier.size(DOT.dp).graphicsLayer { translationX = (dotX - ridden - DOT / 2f).toFloat() * density.density; translationY = (dotY - DOT / 2f).toFloat() * density.density; alpha = dotShown.value.toFloat(); scaleX = (.7 + .3 * dotShown.value).toFloat(); scaleY = scaleX }.background(rgba(63, 91, 217, .16f), RoundedCornerShape(DOT.dp / 2f)).clearAndSetSemantics {})
    }
}

private fun mix(a: Color, b: Color, t: Double) = rgba(
    ((a.red + (b.red - a.red) * t) * 255.0).roundToInt(), ((a.green + (b.green - a.green) * t) * 255.0).roundToInt(),
    ((a.blue + (b.blue - a.blue) * t) * 255.0).roundToInt(), (a.alpha + (b.alpha - a.alpha) * t).toFloat(),
)
private fun paragraphStyle(size: Float, line: Float, weight: Int, ink: Color, align: TextAlign, fontScale: Float) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = (size / fontScale).sp, lineHeight = (line / fontScale).sp,
    fontWeight = FontWeight(weight), color = ink, textAlign = align,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None), platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
private fun LoanParagraph(text: String, size: Float, line: Float, weight: Int, ink: Color, width: Float, x: Float, y: Float, align: TextAlign = TextAlign.Start) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = paragraphStyle(size, line, weight, ink, align, density.fontScale)
    val measured = remember(text, style, width, density) { measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = (max(1f, width) * density.density).roundToInt())) }
    BasicText(text, Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(width.dp, (measured.size.height / density.density).dp).graphicsLayer { translationX = x * density.density; translationY = y * density.density }.clearAndSetSemantics {}, style = style)
}

/** Both columns share the left paragraph's top, including when custom text wraps. */
@Composable
private fun LoanStrip(x: Float, y: Float, width: Float, left: String, right: String, leftInk: Color, rightInk: Color) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = paragraphStyle(13f, 18f, 500, leftInk, TextAlign.Center, density.fontScale)
    val measured = remember(left, style, width, density) { measurer.measure(AnnotatedString(left), style, constraints = Constraints(maxWidth = (width / 2f * density.density).roundToInt().coerceAtLeast(1))) }
    val textY = y + (STRIP_HEIGHT - measured.size.height / density.density) / 2f
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        surface(0.dp) { _, _, _, _ ->
            drawRoundRect(color(STRIP), Offset(x, y), androidx.compose.ui.geometry.Size(width, STRIP_HEIGHT), androidx.compose.ui.geometry.CornerRadius(STRIP_RADIUS))
            drawRect(color(BACKGROUND), Offset(x + width / 2f - .5f, y), androidx.compose.ui.geometry.Size(1f, STRIP_HEIGHT))
        }
    }
    LoanParagraph(left, 13f, 18f, 500, leftInk, width / 2f, x, textY, TextAlign.Center)
    LoanParagraph(right, 13f, 18f, 500, rightInk, width / 2f, x + width / 2f, textY, TextAlign.Center)
}

@Composable
private fun FooterRow(text: String, icon: String, ink: Color, right: Boolean, width: Float, height: Float, opacity: Float) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = ACTION_TEXT.sp, lineHeight = ACTION_LINE.sp, fontWeight = FontWeight.Bold, color = ink, letterSpacing = ACTION_TRACKING.sp, platformStyle = PlatformTextStyle(includeFontPadding = true), lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))
    val measured = remember(text, style, width, density) { measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = (width * density.density).roundToInt().coerceAtLeast(1))) }
    val textWidth = measured.size.width / density.density; val textHeight = measured.size.height / density.density
    val x = (width - textWidth - ACTION_GAP - ACTION_ICON) / 2f
    val iconY = (height - ACTION_ICON) / 2f
    val textY = if (right) iconY else (height - textHeight) / 2f
    Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(width.dp, height.dp).graphicsLayer { alpha = opacity }.clearAndSetSemantics {}) {
        val iconX = if (right) x + textWidth + ACTION_GAP else x
        val textX = if (right) x else x + ACTION_ICON + ACTION_GAP
        Box(Modifier.offset(iconX.dp, iconY.dp).size(ACTION_ICON.dp)) { LoanIcon(icon, ACTION_ICON, ink) }
        BasicText(text, Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(textWidth.dp, textHeight.dp).graphicsLayer { translationX = textX * density.density; translationY = textY * density.density }, style = style)
    }
}
private class PressState { var alive = true; var latestOut: Job? = null }

/** Press state has the source's retention and minimum duration; actions run at release. */
@Composable
private fun LoanControl(label: String, enabled: Boolean, modifier: Modifier, scope: CoroutineScope, action: () -> Unit, content: @Composable (Boolean) -> Unit) {
    val state = remember { PressState() }
    var pressed by remember { mutableStateOf(false) }
    val granted by rememberUpdatedState(enabled)
    val currentAction by rememberUpdatedState(action)
    val view = LocalView.current
    DisposableEffect(state) { state.alive = true; onDispose { state.alive = false; state.latestOut?.cancel() } }
    Box(modifier.semantics(mergeDescendants = true) {
        role = Role.Button; contentDescription = label
        if (!enabled) disabled()
        onClick { if (granted) { currentAction(); true } else false }
    }.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!granted || down.position.x < 0f || down.position.x > size.width || down.position.y < 0f || down.position.y > size.height) return@awaitEachGesture
            state.latestOut?.cancel(); state.latestOut = null
            var active = true; var activatedAt: Long? = System.nanoTime()
            fun pressIn() { activatedAt = System.nanoTime(); pressed = true }
            fun pressOut() {
                val remaining = max(0L, MIN_PRESS_DURATION_MS * 1_000_000L - (System.nanoTime() - (activatedAt ?: 0L)))
                activatedAt = null
                if (remaining == 0L) pressed = false
                else state.latestOut = scope.launch { delay((remaining + 999_999L) / 1_000_000L); pressed = false }
            }
            pressIn(); down.consume()
            try {
                while (true) {
                    val event = awaitPointerEvent(); val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (finger.isConsumed) break
                    val inside = finger.position.x > -PRESS_RETENTION * density && finger.position.x < size.width + PRESS_RETENTION * density && finger.position.y > -PRESS_RETENTION * density && finger.position.y < size.height + PRESS_RETENTION_BOTTOM * density
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
