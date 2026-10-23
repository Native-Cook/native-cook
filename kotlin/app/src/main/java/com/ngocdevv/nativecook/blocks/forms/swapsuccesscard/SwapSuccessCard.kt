package com.ngocdevv.nativecook.blocks.forms.swapsuccesscard

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.SoftRoundRect
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
/** A token and the two colours of its round mark. */
data class Token(val symbol: String, val balance: Double, val disc: Color, val dot: Color)
/** The exchange captured when Swap is released. */
data class Exchange(val from: Token, val to: Token, val amount: Double, val received: Double)
private val Tokens = listOf(Token("VLA", 1456.0, Color.White, Color(0xFF1A1A1A)), Token("ORO", 12678.0, Color(0xFFF7931A), Color(0xFFA35B00)))
private const val RATE = 11908.0 / 456.0
private val Modes = listOf("Swap", "Limit", "Bridge")
const val LOOP = 9.6f
private const val TAP_AT = 0.8f
private const val TYPE_AT = 1.1f
private const val TYPE_EVERY = 0.15f
private const val SWITCH_AT = 2.75f
private const val PRESS_AT = 3.9f
private const val PRESS_FOR = 0.15f
private const val LEAVE_AT = 7.3f
private const val USER_HOLD = 3f
private const val BLINK = LOOP / 8f
private const val CONTENT_OUT = 0.28f
private const val CONTRACT_AT = 0.24f
private const val CONTRACT_FOR = 0.34f
private const val DOT_AT = 0.5f
private const val DOT_IN = 0.08f
private const val BLOOM_AT = 0.62f
private const val BLOOM_OMEGA = 15f
private const val BLOOM_ZETA = 0.55f
private const val DOT_SCALE = 0.17f
private const val CHECK_AT = 0.74f
private const val CHECK_FOR = 0.3f
private const val TITLE_AT = 0.7f
private const val TITLE_FOR = 0.48f
private const val TITLE_RISE = 22f
private const val TITLE_BLUR = 12f
private const val LINES_AT = 1.08f
private const val LINE_GAP = 0.1f
private const val LINE_FOR = 0.3f
private const val LINE_RISE = 4f
private const val SWAP_END = 1.6f
private const val OUT_FOR = 0.4f
private const val EMPTY_FOR = 0.2f
private const val IN_FOR = 0.7f
private const val TURN_HALF = OUT_FOR + EMPTY_FOR / 2f
private const val TURN_END = OUT_FOR + EMPTY_FOR + IN_FOR
private const val OUT_SCALE = 0.96f
private const val IN_SCALE = 0.94f
private const val IN_BLUR = 16f
private const val FLIP_MASS = 1f
private const val FLIP_STIFFNESS = 190f
private const val FLIP_DAMPING = 17f
private const val CROSS_GROW = 0.05f
private const val CROSS_SHRINK = 0.1f
private const val CROSS_DIM = 0.7f
private val Sky = listOf(Color(0xFF12917C), Color(0xFF0E6E66), Color(0xFF0A4A47))
private val Frame = listOf(Color(0xFF16A870), Color(0xFF0F8A5E), Color(0xFF0B4A36), Color(0xFF0A1210), Color(0xFF0B0B0B))
private val FrameAt = floatArrayOf(0f, 0.16f, 0.44f, 0.66f, 1f)
private const val POOL_X = 0.52f
private const val POOL_Y = 0.5f
private const val POOL_RX = 0.48f
private const val POOL_RY = 0.42f
private val Pool = listOf(rgba(9, 11, 10, 0.9f), rgba(9, 11, 10, 0.78f), rgba(9, 11, 10, 0f))
private val Panel = Color(0xFF0D0D0D)
private val Well = Color(0xFF151615)
private val Hairline = Color.White.copy(alpha = 0.08f)
private val Ink = Color.White
private val Mute = Color(0xFF8E9794)
private val Faint = Color(0xFF5F6B67)
private val Mint = Color(0xFF1EE39A)
private val MintDeep = Color(0xFF0FB67A)
private val MintLight = Color(0xFF7CFBDD)
private val ButtonInk = Color(0xFF06281C)
private const val CARD_WIDTH = 342f
private const val CARD_HEIGHT = 340f
private const val CARD_RADIUS = 10f
private const val CARD_MARGIN = 30f
private const val HALO = 10f
private const val SPILL = 44f
private const val PANEL_X = 12f
private const val PANEL_Y = 60f
private const val PANEL_HEIGHT = 217f
private const val PANEL_RADIUS = 8f
private const val WELL_INSET = 6f
private const val WELL_Y = 105f
private const val WELL_HEIGHT = 106f
private const val WELL_RADIUS = 8f
private const val BUTTON_X = 17f
private const val BUTTON_Y = 287f
private const val BUTTON_HEIGHT = 37f
private const val BUTTON_RADIUS = 5f
private const val BUTTON_SQUEEZE = 4f
private const val DONE_HEIGHT = 232f
private const val DONE_PANEL_Y = 20f
private const val DONE_PANEL_HEIGHT = 188f
private const val DONE_TOP = (CARD_HEIGHT - DONE_HEIGHT) / 2f
private const val SLOT = 101f
private const val ROW_TOP = 28f
private const val ROW_HEIGHT = 70f
private const val TEXT_LEFT = 18f
private const val SWITCH = 28f
private const val SWITCH_Y = 97f
private const val RING_Y = DONE_TOP + DONE_PANEL_Y + 48f
private const val RING_RADIUS = 29f
private const val RING_DISC = 23.5f
private const val RING_CANVAS = 96f
private const val SUCCESS_TITLE_Y = DONE_TOP + DONE_PANEL_Y + 92f
private val Check = PathParser().parsePathString("M-9 0.5L-3 6.5L9.5 -6").toPath()
private const val KEYBOARD_GAP = 14f
private const val KEYBOARD_MS = 250
private const val PRESS_IN_MS = 80
private const val PRESS_OUT_MS = 160
private const val SWITCH_PRESS_MS = 120
private const val REST_POLL_MS = 100L
private const val REST_SWITCH_MS = 700L
private const val FRAME_MAX_MS = 64f
private const val INPUT_LIMIT = 11
private const val SPRING_ENERGY_THRESHOLD = 6e-9f
private val Linear = Easing { it }
private val QuadInOut = Easing { t -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f }
private val CubicOut = Easing { 1f - (1f - it).pow(3) }
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val AmountFit = TextAutoSize.StepBased(minFontSize = 16.2.sp, maxFontSize = 27.sp, stepSize = 0.25.sp)
private val TitleFit = TextAutoSize.StepBased(minFontSize = 1.sp, maxFontSize = 19.5.sp, stepSize = 0.25.sp)
private fun smooth(t: Float, from: Float, to: Float): Float { val u = ((t - from) / (to - from)).coerceIn(0f, 1f); return u * u * (3f - 2f * u) }
private fun shownSwap(swap: Float, turn: Float) = if (turn >= TURN_HALF) -1f else swap
private fun springLeft(t: Float): Float {
    if (t <= 0f) return 1f
    val damped = BLOOM_OMEGA * sqrt(1f - BLOOM_ZETA * BLOOM_ZETA)
    return exp(-BLOOM_ZETA * BLOOM_OMEGA * t) * (cos(damped * t) + BLOOM_ZETA * BLOOM_OMEGA / damped * sin(damped * t))
}
private val Grouping = Regex("\\B(?=([0-9]{3})+(?![0-9]))")
private fun grouped(draft: String): String { val whole = draft.substringBefore('.'); return whole.replace(Grouping, ",") + if ('.' in draft) ".${draft.substringAfter('.').substringBefore('.')}" else "" }
private fun cleaned(text: String): String {
    val parts = text.replace(',', '.').replace(Regex("[^0-9.]"), "").split('.')
    val digits = parts[0].replace(Regex("^0+(?=[0-9])"), "").take(7)
    return if (parts.size == 1) digits else "${digits.ifEmpty { "0" }}.${parts.drop(1).joinToString("").take(2)}"
}
private fun number(value: Double): String = if (value == 0.0) "0" else if (value.isFinite() && abs(value) >= 1e-6 && abs(value) < 1e21) BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() else value.toString().replace("E", "e+").replace("e+-", "e-").replace(".0e", "e")
private fun fixed(value: Double, places: Int): String {
    if (!value.isFinite() || abs(value) >= 1e21) return number(value)
    val rounded = BigDecimal(value).setScale(places, RoundingMode.HALF_UP)
    return (if (value < 0 && rounded.signum() == 0) "-" else "") + rounded.toPlainString()
}
private fun String.jsTrim() = trim { it in '\u0009'..'\u000D' || it == '\u0020' || it == '\u00A0' || it == '\u1680' || it in '\u2000'..'\u200A' || it == '\u2028' || it == '\u2029' || it == '\u202F' || it == '\u205F' || it == '\u3000' || it == '\uFEFF' }
private val Decimal = Regex("^[+-]?(?:Infinity|(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)$")
private fun numberOf(text: String): Double {
    val value = text.jsTrim()
    val parsed = when {
        value.isEmpty() -> 0.0
        Regex("^0[xX][0-9a-fA-F]+$").matches(value) -> BigInteger(value.drop(2), 16).toDouble()
        Regex("^0[bB][01]+$").matches(value) -> BigInteger(value.drop(2), 2).toDouble()
        Regex("^0[oO][0-7]+$").matches(value) -> BigInteger(value.drop(2), 8).toDouble()
        Decimal.matches(value) -> value.toDoubleOrNull() ?: Double.NaN
        else -> Double.NaN
    }
    return parsed
}
private fun valueOf(text: String): Double { val parsed = numberOf(text); return if (parsed.isNaN() || parsed == 0.0) 0.0 else parsed }
private fun textStyle(size: Float, line: Float, weight: FontWeight, color: Color, tabular: Boolean = false, spacing: Float = 0f) = TextStyle(
    color = color, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp, fontFeatureSettings = if (tabular) "tnum" else null,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None), platformStyle = PlatformTextStyle(includeFontPadding = false),
)
// A React Native input ref is allowed to be absent while the editable row changes.
private fun FocusRequester.focusIfMounted() { try { requestFocus() } catch (_: IllegalStateException) { } }
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
    fun spring(next: Float, m: Float, k: Float, c: Float, still: Boolean = false) {
        if (still) { direct(next); return }
        if (value == next) { direct(next); return }
        if (springing && target == next) return
        if ((next > value && velocity < 0f) || (next < value && velocity > 0f)) velocity = 0f
        energy0 = 0.5f * k * (value - next).pow(2)
        mass = m; stiffness = k; damping = c; target = next; springing = true; timing = false; completed = null
    }
    fun tween(next: Float, ms: Int, easing: Easing = QuadInOut, still: Boolean = false, onFinished: (() -> Unit)? = null) {
        if (still) { direct(next); onFinished?.invoke(); return }
        if (value == next) { direct(next); onFinished?.invoke(); return }
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



/** Each shape has its own mask blur; the pool and the bar's flare remain sharp. */
private class ChromePaint {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastBlur = Float.NaN
    fun round(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Color, sigma: Float, alpha: Float = 1f, shader: Shader? = null, stroke: Float = 0f) {
        paint.color = color.copy(alpha = 1f).toArgb(); paint.shader = shader
        paint.style = if (stroke > 0f) Paint.Style.STROKE else Paint.Style.FILL; paint.strokeWidth = stroke
        if (Build.VERSION.SDK_INT >= 28 || sigma <= 0f) {
            if (lastBlur != sigma) { lastBlur = sigma; paint.maskFilter = if (sigma > 0f && Build.VERSION.SDK_INT >= 28) BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL) else null }
            paint.alpha = (color.alpha * alpha * 255).roundToInt().coerceIn(0, 255)
            canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint)
        } else {
            val saved = canvas.saveLayer(null, null)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            if (stroke == 0f) {
                paint.alpha = (color.alpha * alpha * 0.5f * 255).roundToInt().coerceIn(0, 255)
                canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint)
            }
            for (layer in 6 downTo 1) {
                val outer = layer * 0.5f; val inner = (layer - 1) * 0.5f; val spread = outer * sigma
                var weight = 0.5f * (exp(-inner * inner / 2f) - exp(-outer * outer / 2f))
                if (stroke > 0f) { paint.strokeWidth = stroke + spread * 2f; weight *= stroke / paint.strokeWidth }
                paint.alpha = (color.alpha * alpha * weight * 255).roundToInt().coerceIn(0, 255)
                if (stroke > 0f) canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint)
                else canvas.drawRoundRect(x - spread, y - spread, x + w + spread, y + h + spread, r + spread, r + spread, paint)
            }
            paint.xfermode = null; canvas.restoreToCount(saved)
        }
        paint.shader = null
    }
}
@Composable
private fun Chrome(width: Float, swap: State<Float>, turn: State<Float>, press: State<Float>, still: Boolean) {
    val paint = remember { ChromePaint() }
    val pool = remember { Brush.radialGradient(0f to Pool[0], 0.6f to Pool[1], 1f to Pool[2], center = Offset.Zero, radius = 1f) }
    val flare = remember { Brush.radialGradient(0f to MintLight, 0.45f to MintLight.copy(alpha = 0.5f), 1f to MintLight.copy(alpha = 0f), center = Offset.Zero, radius = 1f) }
    Canvas(Modifier.fillMaxSize().bleed(SPILL.dp).clearAndSetSemantics {}) { surface(SPILL.dp) { ox, oy, _, _ ->
        val s = shownSwap(swap.value, turn.value)
        val contracted = smooth(s, CONTRACT_AT, CONTRACT_AT + CONTRACT_FOR)
        val emptied = 1f - smooth(s, 0f, CONTENT_OUT)
        val top = oy + DONE_TOP * contracted
        val h = CARD_HEIGHT + (DONE_HEIGHT - CARD_HEIGHT) * contracted
        val panelWidth = width - PANEL_X * 2f
        val panelTop = top + PANEL_Y + (DONE_PANEL_Y - PANEL_Y) * contracted
        val panelHeight = PANEL_HEIGHT + (DONE_PANEL_HEIGHT - PANEL_HEIGHT) * contracted
        val sigma = if (still || turn.value < TURN_HALF) 0f else IN_BLUR * (1f - smooth(turn.value, OUT_FOR + EMPTY_FOR, TURN_END - 0.15f))
        drawIntoCanvas { c ->
            paint.round(c.nativeCanvas, ox - HALO, top - HALO, width + HALO * 2f, h + HALO * 2f, CARD_RADIUS + 6f, Color.White.copy(alpha = 0.045f), sigma)
            paint.round(c.nativeCanvas, ox - HALO + 0.5f, top - HALO, width + HALO * 2f - 1f, h + HALO * 2f, CARD_RADIUS + 6f, Color.White.copy(alpha = 0.07f), sigma, stroke = 1f)
            val gradient = LinearGradient(0f, top, 0f, top + h, Frame.map { it.toArgb() }.toIntArray(), FrameAt, Shader.TileMode.CLAMP)
            paint.round(c.nativeCanvas, ox, top, width, h, CARD_RADIUS, Color.White, sigma, shader = gradient)
        }
        withTransform({ translate(ox + width * POOL_X, top + h * POOL_Y); scale(width * POOL_RX, h * POOL_RY, Offset.Zero) }) { drawCircle(pool, 1f, Offset.Zero) }
        drawIntoCanvas { c ->
            paint.round(c.nativeCanvas, ox + 0.5f, top, width - 1f, h, CARD_RADIUS, Color.White.copy(alpha = 0.1f), sigma, stroke = 1f)
            paint.round(c.nativeCanvas, ox + PANEL_X, panelTop, panelWidth, panelHeight, PANEL_RADIUS, Panel, sigma)
            paint.round(c.nativeCanvas, ox + PANEL_X + 0.5f, panelTop, panelWidth - 1f, panelHeight, PANEL_RADIUS, Hairline, sigma, stroke = 1f)
            val wellX = ox + PANEL_X + WELL_INSET; val wellY = oy + PANEL_Y + WELL_Y; val wellWidth = panelWidth - WELL_INSET * 2f
            paint.round(c.nativeCanvas, wellX, wellY, wellWidth, WELL_HEIGHT, WELL_RADIUS, Well, sigma, emptied)
            paint.round(c.nativeCanvas, wellX + 0.5f, wellY + 0.5f, wellWidth - 1f, WELL_HEIGHT - 1f, WELL_RADIUS, Color.White.copy(alpha = 0.05f), sigma, emptied, stroke = 1f)
            val barY = oy + BUTTON_Y; val barX = ox + BUTTON_X + BUTTON_SQUEEZE * press.value
            val barWidth = width - BUTTON_X * 2f - BUTTON_SQUEEZE * 2f * press.value
            val gradient = LinearGradient(0f, barY, 0f, barY + BUTTON_HEIGHT, intArrayOf(MintDeep.toArgb(), Mint.toArgb(), Mint.toArgb()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            paint.round(c.nativeCanvas, barX, barY, barWidth, BUTTON_HEIGHT, BUTTON_RADIUS, Color.White, sigma, emptied, gradient)
        }
        val barY = oy + BUTTON_Y
        val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(ox + BUTTON_X + BUTTON_SQUEEZE, barY, ox + width - BUTTON_X - BUTTON_SQUEEZE, barY + BUTTON_HEIGHT, CornerRadius(BUTTON_RADIUS))) }
        clipPath(clip) { withTransform({ translate(ox + width / 2f, barY + BUTTON_HEIGHT * 1.05f); scale(width * 0.36f, BUTTON_HEIGHT * 0.95f, Offset.Zero) }) { drawCircle(flare, 1f, Offset.Zero, alpha = emptied) } }
        drawRoundRect(Color.Black, Offset(ox + BUTTON_X + BUTTON_SQUEEZE * press.value, barY), Size(width - BUTTON_X * 2f - BUTTON_SQUEEZE * 2f * press.value, BUTTON_HEIGHT), CornerRadius(BUTTON_RADIUS), alpha = emptied * 0.3f * press.value)
    } }
}
@Composable
private fun Ring(swap: State<Float>, turn: State<Float>, still: Boolean) {
    val measure = remember { PathMeasure().apply { setPath(Check, false) } }
    Canvas(Modifier.size(RING_CANVAS.dp).clearAndSetSemantics {}) { surface(0.dp) { _, _, _, _ ->
        val s = shownSwap(swap.value, turn.value)
        val opacity = smooth(s, DOT_AT, DOT_AT + DOT_IN)
        val left = if (still) 1f - smooth(s, BLOOM_AT, BLOOM_AT + 0.2f) else springLeft(s - BLOOM_AT)
        val scale = 1f - (1f - DOT_SCALE) * left
        val centre = Offset(RING_CANVAS / 2f, RING_CANVAS / 2f)
        val check = Path(); measure.getSegment(0f, measure.length * smooth(s, CHECK_AT, CHECK_AT + CHECK_FOR), check, true)
        withTransform({ scale(scale, scale, centre) }) {
            drawCircle(Ink, RING_RADIUS, centre, alpha = opacity)
            drawCircle(Brush.verticalGradient(listOf(MintDeep, Mint), centre.y - RING_DISC, centre.y + RING_DISC), RING_DISC, centre, alpha = opacity)
            withTransform({ translate(centre.x, centre.y) }) { drawPath(check, Ink, alpha = opacity, style = Stroke(3.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
    } }
}

private enum class Phase { Form, Done, Turning }
private data class AutoEvent(val at: Long, val order: Long, val action: () -> Unit)

/** A token exchange form whose emptied frame contracts around a blooming ringed check. */
@Composable
fun SwapSuccessCard(
    autoPlay: Boolean = false,
    tokens: List<Token> = Tokens,
    rate: Double = RATE,
    demoAmount: String = "456.00",
    title: String = "Exchange",
    modes: List<String> = Modes,
    fromLabel: String = "From",
    toLabel: String = "To",
    balanceLabel: String = "Balance",
    maxLabel: String = "Max",
    swapLabel: String = "Swap",
    successTitle: String = "Successful Exchange",
    successLine: String = "You have successfully exchanged",
    onSwap: ((exchange: Exchange) -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    onPickToken: ((token: Token) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val pair = if (tokens.size == 2) tokens else Tokens
    val still = rememberReducedMotion()
    val reduced by rememberUpdatedState(still)
    val swapped by rememberUpdatedState(onSwap)
    val done by rememberUpdatedState(onDone)
    val view = LocalView.current
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val firstInput = remember { FocusRequester() }; val secondInput = remember { FocusRequester() }
    var draft by remember { mutableStateOf("") }
    var flipped by remember { mutableStateOf(false) }
    var mode by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    var autoCaret by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(Phase.Form) }
    var receipt by remember { mutableStateOf("") }
    var touches by remember { mutableIntStateOf(0) }
    var swappedByHand by remember { mutableStateOf(false) }
    var checkReached by remember { mutableStateOf(false) }
    val swap = remember { Motion(-1f) }; val turn = remember { Motion(0f) }; val flip = remember { Motion(0f) }
    val press = remember { Motion(0f) }; val lift = remember { Motion(0f) }
    val time = remember { mutableFloatStateOf(0f) }
    // Read through state: the functions below are called through references kept from the first composition.
    val from by rememberUpdatedState(pair[if (flipped) 1 else 0]); val to by rememberUpdatedState(pair[if (flipped) 0 else 1])
    val price = if (flipped) 1.0 / rate else rate
    val value by rememberUpdatedState(valueOf(draft))
    val received by rememberUpdatedState(if (value > 0.0) fixed(value * price, 2) else "")
    val amounts = if (flipped) listOf(received, draft) else listOf(draft, received)
    fun dismiss() { keyboard?.hide(); focus.clearFocus(); focused = false }
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    fun touched() { autoCaret = false; touches++ }
    fun switchTokens(hand: Boolean) {
        if (phase != Phase.Form) return
        if (hand) tick()
        dismiss(); draft = received
        val previous = flipped; flipped = !previous
        flip.spring(if (previous) 0f else 1f, FLIP_MASS, FLIP_STIFFNESS, FLIP_DAMPING, reduced)
    }
    fun hold(down: Boolean) { press.tween(if (down) 1f else 0f, if (down) PRESS_IN_MS else PRESS_OUT_MS, still = reduced) }
    fun exchange(hand: Boolean) {
        if (phase != Phase.Form) return
        if (value <= 0.0) { if (hand) (if (flipped) secondInput else firstInput).focusIfMounted(); return }
        if (hand) tick()
        swappedByHand = hand; checkReached = false; dismiss(); autoCaret = false
        receipt = "${grouped(fixed(value, 2))} ${from.symbol} for ${grouped(received)} ${to.symbol}"
        phase = Phase.Done; swapped?.invoke(Exchange(from, to, value, numberOf(received)))
        swap.direct(0f); swap.tween(SWAP_END, (SWAP_END * 1000).roundToInt(), Linear)
    }
    fun home() { phase = Phase.Form; done?.invoke() }
    fun emptied() {
        draft = ""; flipped = false; flip.direct(0f)
        turn.tween(TURN_END, ((TURN_END - TURN_HALF) * 1000).roundToInt(), Linear) { swap.direct(-1f); turn.direct(0f); home() }
    }
    fun leave() {
        if (phase != Phase.Done) return
        phase = Phase.Turning
        turn.tween(TURN_HALF, (TURN_HALF * 1000).roundToInt(), Linear) { emptied() }
    }
    val latestSwitch by rememberUpdatedState(::switchTokens)
    val latestExchange by rememberUpdatedState(::exchange)
    val latestLeave by rememberUpdatedState(::leave)
    val latestHold by rememberUpdatedState(::hold)
    val latestTouched by rememberUpdatedState(::touched)
    val latestDismiss by rememberUpdatedState(::dismiss)
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    LaunchedEffect(Unit) {
        val origin = System.nanoTime(); var last = origin
        while (true) {
            val now = withFrameNanos { it }; val dt = ((now - last) / 1e9f).coerceIn(0f, FRAME_MAX_MS / 1000f); last = now
            // The source's outer repeat uses System, so reduced motion stops the caret clock at LOOP.
            val elapsed = (now - origin) / 1e9f
            time.floatValue = if (reduced) min(LOOP, elapsed) else elapsed % LOOP
            flip.advance(now, dt); press.advance(now, dt); lift.advance(now, dt); swap.advance(now, dt); turn.advance(now, dt)
            val reached = swap.value >= CHECK_AT + CHECK_FOR
            if (reached && !checkReached && swappedByHand) view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
            checkReached = reached
        }
    }
    LaunchedEffect(autoPlay, touches, demoAmount) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            while (true) {
                if (phase == Phase.Done) latestLeave()
                if (phase != Phase.Form) { delay(REST_POLL_MS); continue }
                latestDismiss()
                if (flipped) { draft = ""; delay(REST_POLL_MS); latestSwitch(false); delay(REST_SWITCH_MS); continue }
                draft = ""; break
            }
        }
        val queue = java.util.PriorityQueue(compareBy<AutoEvent> { it.at }.thenBy { it.order }); var sequence = 0L
        fun cycle(origin: Long) {
            fun at(seconds: Float, run: () -> Unit) { queue += AutoEvent(origin + (seconds * 1000).roundToLong(), sequence++, run) }
            at(TAP_AT) { autoCaret = true }
            for (index in demoAmount.indices) at(TYPE_AT + index * TYPE_EVERY) { draft = demoAmount.substring(0, index + 1) }
            at(SWITCH_AT) { autoCaret = false; latestSwitch(false) }
            at(PRESS_AT) { latestHold(true) }
            at(PRESS_AT + PRESS_FOR) { latestHold(false); latestExchange(false) }
            at(LEAVE_AT) { latestLeave() }
            at(LOOP) { cycle(origin + (LOOP * 1000).roundToLong()) }
        }
        cycle(System.nanoTime() / 1_000_000L)
        while (true) {
            val event = queue.remove()
            delay(max(0L, event.at - System.nanoTime() / 1_000_000L)); event.action()
        }
    }
    var fullHeight by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.fillMaxSize().background(Sky.last())) {
        val screenWidth = maxWidth.value; val screenHeight = maxHeight.value
        if (screenHeight > fullHeight) fullHeight = screenHeight
        val width = max(1f, min(CARD_WIDTH, screenWidth - CARD_MARGIN * 2f)); val panelWidth = width - PANEL_X * 2f
        val cardTop = (screenHeight - CARD_HEIGHT) / 2f
        val ime = WindowInsets.ime.getBottom(density) / density.density
        val covered = max(0f, ime - max(0f, fullHeight - screenHeight))
        val room = screenHeight - covered - KEYBOARD_GAP - (cardTop + CARD_HEIGHT + HALO)
        val keyboardLift = if (ime > 0f) min(0f, room) else 0f
        LaunchedEffect(keyboardLift) { lift.tween(keyboardLift, KEYBOARD_MS, CubicOut, still = reduced) }
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) { surface(0.dp) { _, _, w, h -> drawRect(Brush.radialGradient(0f to Sky[0], 0.5f to Sky[1], 1f to Sky[2], center = Offset(w / 2f, h * 0.52f), radius = h * 0.62f), size = Size(w, h)) } }
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { latestDismiss() } })
        val t = turn.value; val s = shownSwap(swap.value, t)
        val gone = smooth(t, 0f, OUT_FOR); val back = smooth(t, OUT_FOR + EMPTY_FOR, TURN_END)
        val cardScale = if (t < TURN_HALF) 1f - (1f - OUT_SCALE) * gone else IN_SCALE + (1f - IN_SCALE) * back
        val cardOpacity = if (t <= 0f) 1f else if (t < TURN_HALF) 1f - gone else smooth(t, OUT_FOR + EMPTY_FOR, OUT_FOR + EMPTY_FOR + IN_FOR * 0.6f)
        val contentOpacity = if (s >= 0f) 1f - smooth(s, 0f, CONTENT_OUT) else if (t > 0f) smooth(t, OUT_FOR + EMPTY_FOR + IN_FOR * 0.35f, TURN_END) else 1f
        val inForm = phase == Phase.Form
        // This layer includes the literal canvas spill so opacity never crops its halo or return blur.
        Box(Modifier.offset(x = ((screenWidth - width) / 2f - SPILL).dp, y = (cardTop - SPILL).dp).wrapContentSize(Alignment.TopStart, unbounded = true).size((width + SPILL * 2f).dp, (CARD_HEIGHT + SPILL * 2f).dp).graphicsLayer {
            alpha = cardOpacity; translationY = lift.value * density.density; scaleX = if (still) 1f else cardScale; scaleY = scaleX
        }) {
            Box(Modifier.offset(x = SPILL.dp, y = SPILL.dp).size(width.dp, CARD_HEIGHT.dp)) {
                Chrome(width, swap, turn, press, still)
                val titleProgress = smooth(s, TITLE_AT, TITLE_AT + TITLE_FOR)
                val lineOne = smooth(s, LINES_AT, LINES_AT + LINE_FOR)
                val lineTwo = smooth(s, LINES_AT + LINE_GAP, LINES_AT + LINE_GAP + LINE_FOR)
                Box(Modifier.offset(y = DONE_TOP.dp).fillMaxWidth().height(DONE_HEIGHT.dp)
                    .then(if (phase == Phase.Done) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { touched(); leave() }.semantics { contentDescription = "$successTitle. $successLine $receipt" } else Modifier.clearAndSetSemantics {})) {
                    BasicText(successTitle, style = textStyle(19.5f, 26f, FontWeight.SemiBold, Ink, spacing = -0.2f).copy(textAlign = TextAlign.Center), maxLines = 1, autoSize = TitleFit,
                        modifier = Modifier.offset(y = (SUCCESS_TITLE_Y - DONE_TOP).dp).padding(horizontal = 24.dp).fillMaxWidth().graphicsLayer {
                            alpha = titleProgress; translationY = if (still) 0f else TITLE_RISE * (1f - titleProgress) * density.density
                            val sigma = TITLE_BLUR * (1f - titleProgress)
                            renderEffect = if (Build.VERSION.SDK_INT >= 31 && sigma > 0.5f) BlurEffect(sigmaToRadius(sigma * density.density), sigmaToRadius(sigma * density.density), TileMode.Decal) else null
                        })
                    BasicText(successLine, style = textStyle(10.5f, 14f, FontWeight.Normal, Mute).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.offset(y = (SUCCESS_TITLE_Y - DONE_TOP + 26f + 8f).dp).fillMaxWidth().graphicsLayer { alpha = lineOne; translationY = LINE_RISE * (1f - lineOne) * density.density })
                    BasicText(receipt, style = textStyle(10.5f, 14f, FontWeight.Normal, Ink, true).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.offset(y = (SUCCESS_TITLE_Y - DONE_TOP + 26f + 8f + 14f + 1f).dp).fillMaxWidth().graphicsLayer { alpha = lineTwo; translationY = LINE_RISE * (1f - lineTwo) * density.density })
                }
                Box(Modifier.offset(y = (RING_Y - RING_CANVAS / 2f).dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) { Ring(swap, turn, still) }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = contentOpacity; compositingStrategy = CompositingStrategy.ModulateAlpha }.then(if (inForm) Modifier else Modifier.clearAndSetSemantics {})) {
                    BasicText(title, style = textStyle(14.5f, 19f, FontWeight.Medium, Ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(x = 13.dp, y = 16.dp))
                    Row(Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = 13.dp).height(27.dp).background(Panel, RoundedCornerShape(5.dp)).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        for ((index, name) in modes.withIndex()) Box {
                            BasicText(name, style = textStyle(12f, 16f, FontWeight.Normal, if (mode == index) Ink else Faint))
                            Box(Modifier.matchParentSize().bleed(6.dp).then(if (inForm) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { touched(); tick(); mode = index } else Modifier).semantics { contentDescription = name; selected = mode == index })
                        }
                    }
                    Box(Modifier.offset(x = PANEL_X.dp, y = PANEL_Y.dp).size(panelWidth.dp, PANEL_HEIGHT.dp)) {
                        BasicText(fromLabel, style = textStyle(12.5f, 17f, FontWeight.Normal, Ink), modifier = Modifier.offset(x = TEXT_LEFT.dp, y = 8.dp))
                        BasicText(toLabel, style = textStyle(12.5f, 17f, FontWeight.Normal, Ink), modifier = Modifier.offset(x = TEXT_LEFT.dp, y = (WELL_Y + 7f).dp))
                        for (index in 0..1) key(pair[index].symbol) { TokenRow(pair[index], if (index == 0) flip.value else 1f - flip.value, index == 1, (index == 0 && !flipped || index == 1 && flipped) && inForm,
                            amounts[index], (index == 0 && !flipped || index == 1 && flipped) && inForm && (focused || autoCaret), time, balanceLabel, if (index == 0) firstInput else secondInput, inForm,
                            { touched(); draft = cleaned(it) }, { next -> if (next) { touched(); focused = true } else focused = false }, { touched(); tick(); onPickToken?.invoke(pair[index]) }) }
                        val maxInteraction = remember { MutableInteractionSource() }; val maxPressed by maxInteraction.collectIsPressedAsState()
                        Box(Modifier.align(Alignment.TopEnd).offset(x = -(TEXT_LEFT + 6f).dp, y = (ROW_TOP + 44f).dp)) {
                            BasicText(maxLabel, style = textStyle(10f, 14f, FontWeight.Normal, Ink), modifier = Modifier.graphicsLayer { alpha = if (maxPressed) 0.6f else 1f })
                            Box(Modifier.matchParentSize().bleed(10.dp).then(if (inForm) Modifier.clickable(maxInteraction, indication = null, role = Role.Button) { touched(); tick(); draft = number(from.balance) } else Modifier).semantics { contentDescription = maxLabel })
                        }
                        BasicText("1 ${from.symbol} = ${grouped(fixed(price, if (price >= 1.0) 2 else 4))} ${to.symbol}", style = textStyle(11f, 15f, FontWeight.Normal, Mute, true).copy(textAlign = TextAlign.End), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = -TEXT_LEFT.dp, y = (ROW_TOP + SLOT + 45f).dp).width(max(0f, panelWidth - TEXT_LEFT).dp))
                        SwitchButton(panelWidth, flip, inForm) { touched(); switchTokens(true) }
                    }
                    Box(Modifier.offset(x = BUTTON_X.dp, y = BUTTON_Y.dp).width((width - BUTTON_X * 2f).dp).height(BUTTON_HEIGHT.dp)
                        .then(if (inForm) Modifier.pointerInput(Unit) { detectTapGestures(onPress = { latestHold(true); try { tryAwaitRelease() } finally { latestHold(false) } }, onTap = { latestTouched(); latestExchange(true) }) }.semantics { role = Role.Button; contentDescription = swapLabel; onClick { touched(); exchange(true); true } } else Modifier), contentAlignment = Alignment.Center) {
                        BasicText(swapLabel, style = textStyle(14.5f, 19f, FontWeight.Medium, ButtonInk), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.graphicsLayer { scaleX = 1f - 0.02f * press.value; scaleY = scaleX })
                    }
                }
            }
        }
    }
}

/** Fixed view shadows use the shared Gaussian helper, with bounded falloffs on older hardware. */
@Composable
private fun GlowBox(color: Color, sigma: Float, dy: Float, radius: Float, modifier: Modifier, content: @Composable () -> Unit) {
    val shadow = remember(color, sigma) { SoftRoundRect(color, sigma) }
    Box(modifier) {
        Canvas(Modifier.matchParentSize().bleed(20.dp).clearAndSetSemantics {}) { surface(20.dp) { x, y, w, h ->
            if (Build.VERSION.SDK_INT >= 28) drawIntoCanvas { shadow.draw(it.nativeCanvas, x, y + dy, w, h, radius) }
            else for (layer in 6 downTo 1) {
                val outer = layer * 0.5f; val inner = (layer - 1) * 0.5f; val spread = outer * sigma
                val weight = 0.5f * (exp(-inner * inner / 2f) - exp(-outer * outer / 2f))
                drawRoundRect(color.copy(alpha = color.alpha * weight), Offset(x - spread, y + dy - spread), Size(w + spread * 2f, h + spread * 2f), CornerRadius(radius + spread))
            }
        } }
        content()
    }
}


/** Native maxLength limits the replacement, preserving the text after the insertion point. */
private fun limitedEdit(old: TextFieldValue, proposed: TextFieldValue): TextFieldValue {
    if (old.text == proposed.text) return proposed
    var start = old.selection.min
    var end = old.selection.max
    val prefix = old.text.substring(0, start)
    val suffix = old.text.substring(end)
    if (!(proposed.text.startsWith(prefix) && proposed.text.endsWith(suffix) && proposed.text.length >= prefix.length + suffix.length)) {
        start = 0
        while (start < old.text.length && start < proposed.text.length && old.text[start] == proposed.text[start]) start++
        var common = 0
        while (common < old.text.length - start && common < proposed.text.length - start && old.text[old.text.lastIndex - common] == proposed.text[proposed.text.lastIndex - common]) common++
        end = old.text.length - common
    }
    val newEnd = proposed.text.length - (old.text.length - end)
    val inserted = proposed.text.substring(start, newEnd)
    val capacity = max(0, INPUT_LIMIT - (old.text.length - (end - start)))
    var keep = min(inserted.length, capacity)
    if (keep > 0 && keep < inserted.length && inserted[keep - 1].isHighSurrogate()) keep--
    val accepted = old.text.substring(0, start) + inserted.take(keep) + old.text.substring(end)
    val delta = keep - inserted.length
    fun position(at: Int): Int = (if (at <= start) at else if (at >= newEnd) at + delta else min(at, start + keep)).coerceIn(0, accepted.length)
    val selection = TextRange(position(proposed.selection.start), position(proposed.selection.end))
    val composition = proposed.composition?.let { TextRange(position(it.start), position(it.end)).takeUnless { range -> range.collapsed } }
    return TextFieldValue(accepted, selection, composition)
}

/** A row moves with its token; its native decimal field stays transparent behind the amount line. */
@Composable
private fun TokenRow(token: Token, slot: Float, front: Boolean, editable: Boolean, amount: String, caret: Boolean, time: State<Float>, balanceLabel: String,
    input: FocusRequester, inForm: Boolean, onChange: (String) -> Unit, onFocus: (Boolean) -> Unit, onPick: () -> Unit) {
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val mid = sin(PI.toFloat() * slot.coerceIn(0f, 1f))
    var field by remember { mutableStateOf(TextFieldValue(amount)) }
    if (field.text != amount) field = field.copy(text = amount, selection = TextRange(field.selection.start.coerceIn(0, amount.length), field.selection.end.coerceIn(0, amount.length)), composition = null)
    Box(Modifier.offset(y = ROW_TOP.dp).fillMaxWidth().height(ROW_HEIGHT.dp).graphicsLayer {
        alpha = if (front) 1f else 1f - CROSS_DIM * mid
        translationY = SLOT * slot * density.density
        val scale = 1f + (if (front) CROSS_GROW else -CROSS_SHRINK) * mid; scaleX = scale; scaleY = scale
    }) {
        Box(Modifier.padding(start = TEXT_LEFT.dp, end = 112.dp).fillMaxWidth().height(40.dp)) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(grouped(amount), style = textStyle(27f, 34f, FontWeight.Light, Ink, true, -0.3f), maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = AmountFit,
                    modifier = Modifier.weight(1f, fill = false))
                if (caret) Box(Modifier.padding(start = 1.5.dp).size(1.5.dp, 28.dp).graphicsLayer { alpha = if (time.value % BLINK < BLINK * 0.55f) 1f else 0f }.background(Mint, RoundedCornerShape(1.dp)))
            }
            if (editable) CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(Color.Transparent, Color.Transparent)) {
                BasicTextField(field, { proposed ->
                    val accepted = limitedEdit(field, proposed)
                    val changed = accepted.text != field.text
                    field = accepted
                    if (changed) onChange(accepted.text)
                }, textStyle = TextStyle(color = Color.Transparent, fontSize = 27.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }), cursorBrush = SolidColor(Color.Transparent),
                    modifier = Modifier.fillMaxSize().focusRequester(input).onFocusChanged { onFocus(it.isFocused) }.semantics { contentDescription = "${token.symbol} amount" })
            }
        }
        val interaction = remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState()
        GlowBox(Color.Black.copy(alpha = 0.5f), 3f, 2f, 9f,
            Modifier.align(Alignment.TopEnd).offset(x = -TEXT_LEFT.dp, y = 7.dp).height(28.dp)) {
            Row(Modifier.height(28.dp).background(Color(0xFF171817), RoundedCornerShape(9.dp)).border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(9.dp))
                .then(if (inForm) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onPick) else Modifier).semantics { contentDescription = "${token.symbol}, choose a token" }
                .padding(1.dp).padding(start = 8.dp, end = 9.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(15.dp).graphicsLayer { alpha = if (pressed) 0.6f else 1f }.background(token.disc, RoundedCornerShape(7.5.dp)), contentAlignment = Alignment.Center) { Box(Modifier.size(5.5.dp).background(token.dot, RoundedCornerShape(2.75.dp))) }
                BasicText(token.symbol, style = textStyle(12.5f, 16f, FontWeight.Medium, Ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.graphicsLayer { alpha = if (pressed) 0.6f else 1f })
                Canvas(Modifier.size(11.dp).clearAndSetSemantics {}) { surface(0.dp) { _, _, _, _ -> drawLine(Mute, Offset(2.5f, 4f), Offset(5.5f, 7f), 1.4f, StrokeCap.Round); drawLine(Mute, Offset(5.5f, 7f), Offset(8.5f, 4f), 1.4f, StrokeCap.Round) } }
            }
        }
        BasicText("$balanceLabel : ${grouped(fixed(token.balance, 2))}", style = textStyle(11f, 15f, FontWeight.Normal, Mute, true), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(x = TEXT_LEFT.dp, y = 45.dp).fillMaxWidth().padding(end = TEXT_LEFT.dp))
    }
}
@Composable
private fun SwitchButton(panelWidth: Float, flip: State<Float>, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState()
    val scale by cssTween(if (pressed) 0.9f else 1f, SWITCH_PRESS_MS)
    Box(Modifier.offset(x = (panelWidth / 2f - SWITCH / 2f - 5f).dp, y = (SWITCH_Y - SWITCH / 2f - 5f).dp).size((SWITCH + 10f).dp).background(Panel, RoundedCornerShape(((SWITCH + 10f) / 2f).dp)), contentAlignment = Alignment.Center) {
        GlowBox(rgba(30, 227, 154, 0.55f), 6f, 0f, SWITCH / 2f, Modifier.size(SWITCH.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            Box(Modifier.fillMaxSize().background(Mint, RoundedCornerShape((SWITCH / 2f).dp)), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(17.dp).graphicsLayer { rotationZ = 180f * flip.value }.clearAndSetSemantics {}) { surface(0.dp) { _, _, _, _ ->
                    drawLine(ButtonInk, Offset(5f, 13.5f), Offset(5f, 3.5f), 1.6f, StrokeCap.Round)
                    drawLine(ButtonInk, Offset(2.5f, 6f), Offset(5f, 3.5f), 1.6f, StrokeCap.Round); drawLine(ButtonInk, Offset(5f, 3.5f), Offset(7.5f, 6f), 1.6f, StrokeCap.Round)
                    drawLine(ButtonInk, Offset(12f, 3.5f), Offset(12f, 13.5f), 1.6f, StrokeCap.Round)
                    drawLine(ButtonInk, Offset(9.5f, 11f), Offset(12f, 13.5f), 1.6f, StrokeCap.Round); drawLine(ButtonInk, Offset(12f, 13.5f), Offset(14.5f, 11f), 1.6f, StrokeCap.Round)
                } }
            }
        }
        Box(Modifier.matchParentSize().bleed(8.dp).then(if (enabled) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier).semantics { contentDescription = "Switch the two tokens" })
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
