package com.ngocdevv.nativecook.blocks.forms.starfieldpaymentsuccess

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
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private val BLACK = Color(0xFF000000)
private val TINT = Color(0xFF1A0C0C)
private val EMBER = intArrayOf(255, 122, 26)
private const val EMBER_Y = 1.09f
private const val EMBER_WIDTH = 1.25f
private const val EMBER_HEIGHT = .3f
private val TILE = Color(0xFF1C1C1E)
private val LABEL = Color(0xFF77777C)
private val PLACEHOLDER = Color(0xFF6B6B70)
private val UNIT = Color(0xFF8A8A8E)
private val CARD = listOf(Color(0xFF2B3139), Color(0xFF474A50))
private val CARD_NAME = Color(0xFFA4A7AD)
private val SHARE = Color(0xFF2A2A2E)
private const val PRESSED = .72
private const val CLOSE = 38f
private const val CLOSE_INSET = 36f
private const val TOP_GAP = 15f
private const val AMOUNT = 54f
private const val AMOUNT_ROW = 66f
private const val CARET = 60f
private const val CHIP = 32f
private const val CARD_HEIGHT = 72f
private const val CARD_MARGIN = 24f
private const val KEY = 64f
private const val KEY_FLASH = 72f
private const val PAD_MARGIN = 11f
private const val PAD_GAP = 59f
private const val PAD_GAP_CLOSED = 25f
private const val ROW_MIN = 50f
private const val BUTTON = 56f
private const val CONFIRM_MARGIN = 32f
private const val BAR_MARGIN = 28f
private const val BAR_GAP = 10f
private const val BUTTON_LIFT = 2f
private const val PAD_END = 8f
private const val CONFIRM_AFTER = .4
private const val CONFIRM_MASS = 1.0
private const val CONFIRM_STIFFNESS = 190.0
private const val CONFIRM_DAMPING = 17.0
private const val CONFIRM_OUT = 220.0
private const val FLASH_ON = 40.0
private const val FLASH_OFF = 350.0
private const val FLOAT = 140.0
private const val SINK = 100.0
private const val BACK_TRAVEL = 28.0
private const val ROW_STAGGER = .06
private const val TEXT_RISE = 26.0
private const val RESULT_RISE = 14.0
private const val RESULT_STAGGER = .08
private const val CLOCK_END = 3600.0
private val KEYS = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "back"))
private val FALLOFF_AT = floatArrayOf(0f, .2f, .4f, .6f, .8f, 1f)
private val FALLOFF = floatArrayOf(1f, .905f, .655f, .345f, .095f, 0f)
private const val HEAD_TOP = 26f
private const val TITLE_SIZE = 16f
private const val TITLE_LINE = 20f
private const val AMOUNT_TOP = 15f
private const val FIGURE_TRACKING = -1.2f
private const val UNIT_LEFT = 1f
private const val UNIT_APART = 12f
private const val SELECT_LEFT = 8f
private const val SELECT_SIZE = 22f
private const val CARET_WIDTH = 2.5f
private const val CARET_MARGIN = 2f
private const val CARET_RADIUS = 1.25f
private const val CHIP_TOP = 9f
private const val CHIP_PADDING = 13f
private const val CHIP_GAP = 7f
private const val CHIP_ICON = 13f
private const val CHIP_SIZE = 13f
private const val CHIP_LINE = 16f
private const val DIVIDER_TOP = 29f
private const val PAYING_TOP = 32f
private const val CARD_TOP = 17f
private const val CARD_RADIUS = 18f
private const val CARD_BORDER = 1f
private const val CARD_LEFT = 13f
private const val CARD_RIGHT = 16f
private const val CARD_ART_WIDTH = 50f
private const val CARD_ART_HEIGHT = 42f
private const val CARD_TEXT_LEFT = 16f
private const val CARD_NUMBER_SIZE = 17f
private const val CARD_NUMBER_LINE = 21f
private const val CARD_NAME_SIZE = 10.5f
private const val CARD_NAME_LINE = 13f
private const val CARD_NAME_TOP = 3f
private const val CARD_NAME_TRACKING = .4f
private const val CARD_CHEVRON = 20f
private const val CARD_PRESS_MS = 120.0
private const val DIGIT_SIZE = 28f
private const val DIGIT_LINE = 34f
private const val DELETE_SIZE = 24f
private const val WIDE_SIZE = 15f
private const val WIDE_LINE = 20f
private const val WIDE_GAP = 8f
private const val WIDE_PRESS_MS = 110.0
private const val WIDE_SCALE = .97
private const val WAIT_Y = .514f
private const val WAIT_UP = 15f
private const val WAIT_TITLE_SIZE = 24f
private const val WAIT_TITLE_LINE = 30f
private const val WAIT_TRACKING = -.4f
private const val WAIT_HINT_TOP = 19f
private const val WAIT_HINT_SIZE = 15f
private const val WAIT_HINT_LINE = 20f
private const val RESULT_Y = .517f
private const val RESULT_UP = 14f
private const val DONE_SIZE = 22f
private const val DONE_LINE = 28f
private const val DONE_TRACKING = -.3f
private const val SUM = 60f
private const val SUM_TOP = 22f
private const val SUM_BOTTOM = 6f
private const val SUM_SIGN_SIZE = 40f
private const val SUM_SIGN_RIGHT = 4f
private const val SUM_WHOLE_SIZE = 50f
private const val SUM_CENTS_SIZE = 28f
private const val SUM_TRACKING = -1f
private const val ARROW_SIZE = 17f
private const val SHARE_ICON = 19f
private const val SHARE_AFTER = .07
private const val CLOSE_RADIUS = 11f
private const val CLOSE_SLOP = 8f
private const val CLOSE_ICON = 18f
private const val DIGIT_ENTER = 140.0
private const val REST_POLL_MS = 100L
private const val PRESS_RETENTION = 20f
private const val PRESS_RETENTION_BOTTOM = 30f
private const val MIN_PRESS_DURATION_MS = 130L
private const val SPRING_FRAME_CAP = .064
private const val SPRING_ENERGY_THRESHOLD = 6e-9
private const val ICON_CLOSE = "M18 6L6 18M6 6l12 12"
private const val ICON_SELECT = "M7 15l5 5 5-5M7 9l5-5 5 5"
private const val ICON_SWAP = "M17 1l4 4-4 4M3 11V9a4 4 0 0 1 4-4h14M7 23l-4-4 4-4M21 13v2a4 4 0 0 1-4 4H3"
private const val ICON_CHEVRON = "M9 18l6-6-6-6"
private const val ICON_DELETE = "M21 4H8l-7 8 7 8h13a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2zM18 9l-6 6M12 9l6 6"
private const val ICON_ARROW = "M5 12h14M12 5l7 7-7 7"
private const val ICON_SHARE = "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8M16 6l-4-4-4 4M12 2v13"
private const val CARD_PLANET_ARC = "M10.2 9.2A8 8 0 0 1 24.9 11.6"
private val Linear = Easing { it }
private val Quad = Easing { if (it < .5f) 2f * it * it else 1f - (-2f * it + 2f).pow(2) / 2f }
private val CssEase = CubicBezierEasing(.25f, .1f, .25f, 1f)
private fun easeIn(t: Double): Double = SpaceMath.clamp01(t).let { it * it }
private fun rounded(value: Double): Double { if (!value.isFinite()) return value; val lower = floor(value); return if (value - lower < .5) lower else lower + 1.0 }
private fun jsTrim(text: String): String = text.trim { it == '\u0009' || it == '\u000A' || it == '\u000B' || it == '\u000C' || it == '\u000D' || it == '\u0020' || it == '\u00A0' || it == '\u1680' || it in '\u2000'..'\u200A' || it == '\u2028' || it == '\u2029' || it == '\u202F' || it == '\u205F' || it == '\u3000' || it == '\uFEFF' }
private fun jsNumber(text: String): Double {
    val raw = jsTrim(text)
    if (raw.isEmpty()) return 0.0
    if (raw == "Infinity" || raw == "+Infinity") return Double.POSITIVE_INFINITY
    if (raw == "-Infinity") return Double.NEGATIVE_INFINITY
    if (raw.matches(Regex("0[xX][0-9a-fA-F]+"))) return raw.drop(2).toBigInteger(16).toDouble()
    if (raw.matches(Regex("0[bB][01]+"))) return raw.drop(2).toBigInteger(2).toDouble()
    if (raw.matches(Regex("0[oO][0-7]+"))) return raw.drop(2).toBigInteger(8).toDouble()
    if (!raw.matches(Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))) return 0.0
    return raw.toDoubleOrNull()?.takeUnless { it.isNaN() } ?: 0.0
}
private fun number(value: Double): String {
    if (value.isNaN()) return "NaN"
    if (value == Double.POSITIVE_INFINITY) return "Infinity"
    if (value == Double.NEGATIVE_INFINITY) return "-Infinity"
    if (value == 0.0) return "0"
    if (abs(value) >= 1e-6 && abs(value) < 1e21) return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    val parts = value.toString().lowercase().split('e')
    val exponent = parts.getOrNull(1)?.toIntOrNull()
    return if (exponent == null) parts[0] else parts[0].removeSuffix(".0") + "e" + (if (exponent >= 0) "+" else "") + exponent
}
private fun money(value: Double): Pair<String, String> {
    val cents = rounded(value * 100.0)
    val whole = number(floor(cents / 100.0)).replace(Regex("\\B(?=(\\d{3})+(?!\\d))"), ",")
    return whole to number(cents % 100.0).padStart(2, '0')
}
private enum class Phase { Form, Waiting, Done, Leaving }
private class FrameTime { var now: Long? = null }

/** Explicit clocks keep Never motion independent of the system animator scale. */
private class Scalar(initial: Double, private val clock: FrameTime) {
    var value by mutableStateOf(initial)
        private set
    private var motion: Motion? = null
    fun set(next: Double) { motion = null; value = next }
    fun time(to: Double, ms: Double, easing: Easing = Quad, still: Boolean = false): Boolean {
        if (value == to || still || ms <= 0.0) { set(to); return true }
        val now = clock.now ?: System.nanoTime(); val old = motion as? Timing
        motion = Timing(if (old?.to == to) old.from else value, to, ms, easing, if (old?.to == to) old.began else now)
        return advance(now)
    }
    fun spring(to: Double, still: Boolean) {
        if (value == to || still) { set(to); return }
        val now = clock.now ?: System.nanoTime()
        motion = Spring(value, to, motion as? Spring, now); advance(now)
    }
    fun advance(now: Long): Boolean {
        val animation = motion ?: return false
        animation.advance(now); value = animation.value
        if (motion === animation && animation.done) { motion = null; return true }
        return false
    }
    private interface Motion { val value: Double; val done: Boolean; fun advance(now: Long) }
    private class Timing(val from: Double, val to: Double, val duration: Double, val curve: Easing, val began: Long) : Motion {
        override var value = from; override var done = false
        override fun advance(now: Long) {
            val u = ((now - began) / (duration * 1e6)).coerceIn(0.0, 1.0)
            value = from + (to - from) * if (curve === Linear) u else curve.transform(u.toFloat()).toDouble()
            if (u >= 1.0) { value = to; done = true }
        }
    }
    private class Spring(initial: Double, val to: Double, previous: Spring?, now: Long) : Motion {
        private val omega = sqrt(CONFIRM_STIFFNESS / CONFIRM_MASS)
        private val zeta = CONFIRM_DAMPING / (2.0 * sqrt(CONFIRM_STIFFNESS * CONFIRM_MASS))
        private val decay = CONFIRM_DAMPING / (2.0 * CONFIRM_MASS)
        private val origin: Double = if (previous?.to == to) previous.origin else initial - to
        private val energy = max(1e-20, CONFIRM_STIFFNESS * origin * origin)
        private var displacement = initial - to
        private var velocity: Double = (previous?.velocity ?: 0.0).let { if ((to - initial) * it < 0.0) 0.0 else it }
        private var last: Long = previous?.last ?: now
        override var value = initial; override var done = false
        override fun advance(now: Long) {
            val dt = ((now - last).coerceAtLeast(0L) / 1e9).coerceAtMost(SPRING_FRAME_CAP); last = now
            val x = displacement; val v = velocity; val fall = exp(-if (zeta < 1.0) decay * dt else omega * dt)
            if (zeta < 1.0) {
                val wd = omega * sqrt(1.0 - zeta * zeta); val c = cos(wd * dt); val s = sin(wd * dt)
                displacement = fall * (x * c + (v + decay * x) / wd * s)
                velocity = fall * (v * c - (decay * v + omega * omega * x) / wd * s)
            } else {
                displacement = fall * (x + (v + omega * x) * dt)
                velocity = fall * (v - omega * (v + omega * x) * dt)
            }
            value = to + displacement
            if ((CONFIRM_STIFFNESS * displacement * displacement + CONFIRM_MASS * velocity * velocity) / energy < SPRING_ENERGY_THRESHOLD) { value = to; velocity = 0.0; done = true }
        }
    }
}
private data class AutoEvent(val at: Long, val order: Long, val action: () -> Unit)


private object StarfieldPaymentSuccessCSS {
    private val names: Map<String, Long> = mapOf(
        "transparent" to 0x00000000L,
        "aliceblue" to 0xf0f8ffffL,
        "antiquewhite" to 0xfaebd7ffL,
        "aqua" to 0x00ffffffL,
        "aquamarine" to 0x7fffd4ffL,
        "azure" to 0xf0ffffffL,
        "beige" to 0xf5f5dcffL,
        "bisque" to 0xffe4c4ffL,
        "black" to 0x000000ffL,
        "blanchedalmond" to 0xffebcdffL,
        "blue" to 0x0000ffffL,
        "blueviolet" to 0x8a2be2ffL,
        "brown" to 0xa52a2affL,
        "burlywood" to 0xdeb887ffL,
        "burntsienna" to 0xea7e5dffL,
        "cadetblue" to 0x5f9ea0ffL,
        "chartreuse" to 0x7fff00ffL,
        "chocolate" to 0xd2691effL,
        "coral" to 0xff7f50ffL,
        "cornflowerblue" to 0x6495edffL,
        "cornsilk" to 0xfff8dcffL,
        "crimson" to 0xdc143cffL,
        "cyan" to 0x00ffffffL,
        "darkblue" to 0x00008bffL,
        "darkcyan" to 0x008b8bffL,
        "darkgoldenrod" to 0xb8860bffL,
        "darkgray" to 0xa9a9a9ffL,
        "darkgreen" to 0x006400ffL,
        "darkgrey" to 0xa9a9a9ffL,
        "darkkhaki" to 0xbdb76bffL,
        "darkmagenta" to 0x8b008bffL,
        "darkolivegreen" to 0x556b2fffL,
        "darkorange" to 0xff8c00ffL,
        "darkorchid" to 0x9932ccffL,
        "darkred" to 0x8b0000ffL,
        "darksalmon" to 0xe9967affL,
        "darkseagreen" to 0x8fbc8fffL,
        "darkslateblue" to 0x483d8bffL,
        "darkslategray" to 0x2f4f4fffL,
        "darkslategrey" to 0x2f4f4fffL,
        "darkturquoise" to 0x00ced1ffL,
        "darkviolet" to 0x9400d3ffL,
        "deeppink" to 0xff1493ffL,
        "deepskyblue" to 0x00bfffffL,
        "dimgray" to 0x696969ffL,
        "dimgrey" to 0x696969ffL,
        "dodgerblue" to 0x1e90ffffL,
        "firebrick" to 0xb22222ffL,
        "floralwhite" to 0xfffaf0ffL,
        "forestgreen" to 0x228b22ffL,
        "fuchsia" to 0xff00ffffL,
        "gainsboro" to 0xdcdcdcffL,
        "ghostwhite" to 0xf8f8ffffL,
        "gold" to 0xffd700ffL,
        "goldenrod" to 0xdaa520ffL,
        "gray" to 0x808080ffL,
        "green" to 0x008000ffL,
        "greenyellow" to 0xadff2fffL,
        "grey" to 0x808080ffL,
        "honeydew" to 0xf0fff0ffL,
        "hotpink" to 0xff69b4ffL,
        "indianred" to 0xcd5c5cffL,
        "indigo" to 0x4b0082ffL,
        "ivory" to 0xfffff0ffL,
        "khaki" to 0xf0e68cffL,
        "lavender" to 0xe6e6faffL,
        "lavenderblush" to 0xfff0f5ffL,
        "lawngreen" to 0x7cfc00ffL,
        "lemonchiffon" to 0xfffacdffL,
        "lightblue" to 0xadd8e6ffL,
        "lightcoral" to 0xf08080ffL,
        "lightcyan" to 0xe0ffffffL,
        "lightgoldenrodyellow" to 0xfafad2ffL,
        "lightgray" to 0xd3d3d3ffL,
        "lightgreen" to 0x90ee90ffL,
        "lightgrey" to 0xd3d3d3ffL,
        "lightpink" to 0xffb6c1ffL,
        "lightsalmon" to 0xffa07affL,
        "lightseagreen" to 0x20b2aaffL,
        "lightskyblue" to 0x87cefaffL,
        "lightslategray" to 0x778899ffL,
        "lightslategrey" to 0x778899ffL,
        "lightsteelblue" to 0xb0c4deffL,
        "lightyellow" to 0xffffe0ffL,
        "lime" to 0x00ff00ffL,
        "limegreen" to 0x32cd32ffL,
        "linen" to 0xfaf0e6ffL,
        "magenta" to 0xff00ffffL,
        "maroon" to 0x800000ffL,
        "mediumaquamarine" to 0x66cdaaffL,
        "mediumblue" to 0x0000cdffL,
        "mediumorchid" to 0xba55d3ffL,
        "mediumpurple" to 0x9370dbffL,
        "mediumseagreen" to 0x3cb371ffL,
        "mediumslateblue" to 0x7b68eeffL,
        "mediumspringgreen" to 0x00fa9affL,
        "mediumturquoise" to 0x48d1ccffL,
        "mediumvioletred" to 0xc71585ffL,
        "midnightblue" to 0x191970ffL,
        "mintcream" to 0xf5fffaffL,
        "mistyrose" to 0xffe4e1ffL,
        "moccasin" to 0xffe4b5ffL,
        "navajowhite" to 0xffdeadffL,
        "navy" to 0x000080ffL,
        "oldlace" to 0xfdf5e6ffL,
        "olive" to 0x808000ffL,
        "olivedrab" to 0x6b8e23ffL,
        "orange" to 0xffa500ffL,
        "orangered" to 0xff4500ffL,
        "orchid" to 0xda70d6ffL,
        "palegoldenrod" to 0xeee8aaffL,
        "palegreen" to 0x98fb98ffL,
        "paleturquoise" to 0xafeeeeffL,
        "palevioletred" to 0xdb7093ffL,
        "papayawhip" to 0xffefd5ffL,
        "peachpuff" to 0xffdab9ffL,
        "peru" to 0xcd853fffL,
        "pink" to 0xffc0cbffL,
        "plum" to 0xdda0ddffL,
        "powderblue" to 0xb0e0e6ffL,
        "purple" to 0x800080ffL,
        "rebeccapurple" to 0x663399ffL,
        "red" to 0xff0000ffL,
        "rosybrown" to 0xbc8f8fffL,
        "royalblue" to 0x4169e1ffL,
        "saddlebrown" to 0x8b4513ffL,
        "salmon" to 0xfa8072ffL,
        "sandybrown" to 0xf4a460ffL,
        "seagreen" to 0x2e8b57ffL,
        "seashell" to 0xfff5eeffL,
        "sienna" to 0xa0522dffL,
        "silver" to 0xc0c0c0ffL,
        "skyblue" to 0x87ceebffL,
        "slateblue" to 0x6a5acdffL,
        "slategray" to 0x708090ffL,
        "slategrey" to 0x708090ffL,
        "snow" to 0xfffafaffL,
        "springgreen" to 0x00ff7fffL,
        "steelblue" to 0x4682b4ffL,
        "tan" to 0xd2b48cffL,
        "teal" to 0x008080ffL,
        "thistle" to 0xd8bfd8ffL,
        "tomato" to 0xff6347ffL,
        "turquoise" to 0x40e0d0ffL,
        "violet" to 0xee82eeffL,
        "wheat" to 0xf5deb3ffL,
        "white" to 0xffffffffL,
        "whitesmoke" to 0xf5f5f5ffL,
        "yellow" to 0xffff00ffL,
        "yellowgreen" to 0x9acd32ffL
    )
    private val hex = Regex("""^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$""")
    private val formats = listOf(
        Triple("rgb", false, Regex("""rgba?\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("rgb", true, Regex("""rgba?\(\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("rgb", true, Regex("""rgba?\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*/\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("hsl", false, Regex("""hsl\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*\)""")),
        Triple("hsl", true, Regex("""hsla\(\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+%)\s*,\s*([-+]?\d*\.?\d+%)\s*,\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("hsl", true, Regex("""hsla\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*/\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("hwb", false, Regex("""hwb\(\s*([-+]?\d*\.?\d+)\s*([-+]?\d*\.?\d+%)\s*([-+]?\d*\.?\d+%)\s*\)""")),
        Triple("hwb", true, Regex("""hwb\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*/\s*([-+]?\d*\.?\d+)\s*\)"""))
    )
    private fun packed(value: Long) = rgba((value shr 24).toInt() and 255, (value shr 16).toInt() and 255, (value shr 8).toInt() and 255, (value and 255) / 255f)
    private fun byte(value: Double): Int {
        if (!value.isFinite()) return 0
        val low = floor(value)
        return (if (value - low < .5) low else low + 1).toInt()
    }
    private fun hue(p: Double, q: Double, input: Double): Double {
        var t = input
        if (t < 0) t += 1
        if (t > 1) t -= 1
        return when {
            t < 1.0 / 6 -> p + (q - p) * 6 * t
            t < .5 -> q
            t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
            else -> p
        }
    }
    fun color(source: String): Color {
        names[source]?.let { return packed(it) }
        if (hex.matches(source)) {
            var digits = source.drop(1)
            if (digits.length == 3 || digits.length == 4) digits = digits.map { "$it$it" }.joinToString("")
            if (digits.length == 6) digits += "ff"
            digits.toLongOrNull(16)?.let { return packed(it) }
        }
        for ((kind, alpha, pattern) in formats) {
            val match = pattern.find(source) ?: continue
            val values = match.groupValues.drop(1).map { it.removeSuffix("%").toDoubleOrNull() ?: 0.0 }
            val opacity = if (alpha) byte(values[3].coerceIn(0.0, 1.0) * 255) / 255f else 1f
            if (kind == "rgb") return rgba(values[0].coerceIn(0.0, 255.0).toInt(), values[1].coerceIn(0.0, 255.0).toInt(), values[2].coerceIn(0.0, 255.0).toInt(), opacity)
            val h = ((values[0] % 360 + 360) % 360) / 360
            val second = (values[1] / 100).coerceIn(0.0, 1.0)
            val third = (values[2] / 100).coerceIn(0.0, 1.0)
            val channels = if (kind == "hwb") {
                if (second + third >= 1) List(3) { second / (second + third) }
                else listOf(h + 1.0 / 3, h, h - 1.0 / 3).map { hue(0.0, 1.0, it) * (1 - second - third) + second }
            } else {
                val q = if (third < .5) third * (1 + second) else third + second - third * second
                val p = 2 * third - q
                listOf(h + 1.0 / 3, h, h - 1.0 / 3).map { hue(p, q, it) }
            }
            return rgba(byte(channels[0] * 255), byte(channels[1] * 255), byte(channels[2] * 255), opacity)
        }
        return Color.Transparent
    }
}



@Composable
private fun StarfieldIcon(path: String, size: Float, ink: Color, stroke: Float = 2f) {
    val outline = remember(path) { androidx.compose.ui.graphics.vector.PathParser().parsePathString(path).toPath() }
    Canvas(Modifier.size(size.dp)) {
        drawContext.canvas.save()
        drawContext.canvas.scale(this.size.width / 24, this.size.height / 24)
        drawPath(outline, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round,
        ))
        drawContext.canvas.restore()
    }
}

@Composable
private fun StarfieldCardArt() {
    val arc = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString(CARD_PLANET_ARC).toPath() }
    Canvas(Modifier.size(50.dp, 42.dp)) {
        drawRoundRect(Color.White, cornerRadius = androidx.compose.ui.geometry.CornerRadius(11.dp.toPx()))
        drawContext.canvas.save()
        drawContext.canvas.translate(8.dp.toPx(), 8.dp.toPx())
        drawContext.canvas.scale(density, density)
        drawCircle(Color(0xFFFF8A1E), radius = 8f, center = androidx.compose.ui.geometry.Offset(17f, 13f))
        drawContext.canvas.save()
        drawContext.canvas.translate(17f, 13f)
        drawContext.canvas.rotate(-20f)
        drawOval(Color(0xFF2A2C30), topLeft = androidx.compose.ui.geometry.Offset(-14.5f, -4.2f),
            size = androidx.compose.ui.geometry.Size(29f, 8.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.8f))
        drawContext.canvas.restore()
        drawPath(arc, Color(0xFFFF8A1E), style = androidx.compose.ui.graphics.drawscope.Stroke(3.4f))
        drawContext.canvas.restore()
    }
}



private class AnimationBank(val clock: FrameTime) {
    val values = mutableListOf<Scalar>()
    fun advance(now: Long) { values.toList().forEach { it.advance(now) } }
}
@Composable
private fun rememberScalar(initial: Double, bank: AnimationBank): Scalar {
    val value = remember { Scalar(initial, bank.clock) }
    DisposableEffect(value, bank) { bank.values += value; onDispose { bank.values -= value } }
    return value
}
private class DigitLayers(private val bank: AnimationBank) {
    val opacity = mutableListOf<Scalar>()
    fun update(length: Int, still: Boolean) {
        while (opacity.size > length) bank.values.remove(opacity.removeAt(opacity.lastIndex))
        while (opacity.size < length) {
            val value = Scalar(0.0, bank.clock); opacity += value; bank.values += value
            value.time(1.0, DIGIT_ENTER, Quad, still)
        }
    }
}

/** A keypad payment; the callbacks are the integration points for a real transaction. */
@Composable
fun StarfieldPaymentSuccess(
    autoPlay: Boolean = false,
    autoPlayAmount: String = "100",
    currency: String = "USD",
    symbol: String = "$",
    quoteSymbol: String = "€",
    rate: Double = 1.087,
    maxDigits: Double = 6.0,
    cardNumber: String = "**** 3256",
    cardName: String = "ORBIT DEBIT",
    processingDuration: Double = 2.7,
    accent: String = "#5b2eff",
    title: String = "Enter Amount",
    payingFromLabel: String = "Paying from",
    confirmLabel: String = "Confirm",
    waitingTitle: String = "Starting your transaction",
    waitingHint: String = "Please hold on..",
    doneLabel: String = "Successfully sent",
    reviewLabel: String = "Review",
    onConfirm: ((Double) -> Unit)? = null,
    onSuccess: ((Double) -> Unit)? = null,
    onReview: ((Double) -> Unit)? = null,
    onShare: ((Double) -> Unit)? = null,
    onCurrencyPress: (() -> Unit)? = null,
    onCardPress: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val still = rememberReducedMotion(); val view = LocalView.current
    val scope = rememberCoroutineScope(); val clock = remember { FrameTime() }; val bank = remember { AnimationBank(clock) }
    val elapsed = remember { Scalar(-1.0, clock) }; val leaving = remember { Scalar(0.0, clock) }; val confirm = remember { Scalar(0.0, clock) }
    val layers = remember { DigitLayers(bank) }
    var digits by remember { mutableStateOf("") }; var confirmUp by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(Phase.Form) }; var sent by remember { mutableStateOf(0.0) }
    var autoKey by remember { mutableStateOf<String?>(null) }; var autoPressed by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }; var timer by remember { mutableStateOf<Job?>(null) }
    var automatic by remember { mutableStateOf(false) }; var arrivedAt by remember { mutableStateOf(0L) }
    var frame by remember { mutableStateOf(System.nanoTime()) }
    val busy = phase != Phase.Form; val arrived = phase == Phase.Done; val amount = jsNumber(digits)
    val check = processingDuration + SpaceMotion.CHECK_AFTER
    val currentSuccess by rememberUpdatedState(onSuccess)
    val currentConfirm by rememberUpdatedState(onConfirm)
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    fun showConfirm(up: Boolean) {
        confirmUp = up
        if (up) confirm.spring(1.0, still) else confirm.time(0.0, CONFIRM_OUT, Quad, still)
    }
    fun type(key: String) {
        val next = if (key == "back") digits.dropLast(1) else if (digits.isEmpty() && key == "0") "" else if (digits.length < maxDigits) digits + key else digits
        digits = next; layers.update(next.length, still); timer?.cancel(); timer = null
        if (next.isEmpty()) showConfirm(false)
        else if (!confirmUp) timer = scope.launch { delay((CONFIRM_AFTER * 1000).toLong()); showConfirm(true) }
    }
    fun clear() { timer?.cancel(); timer = null; digits = ""; layers.update(0, still); showConfirm(false) }
    fun start(byItself: Boolean) {
        val value = jsNumber(digits)
        if (phase != Phase.Form || value <= 0.0) return
        automatic = byItself
        if (!byItself) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        sent = value; phase = Phase.Waiting; currentConfirm?.invoke(value)
        leaving.set(0.0); elapsed.set(0.0); elapsed.time(CLOCK_END, CLOCK_END * 1000.0, Linear)
    }
    fun leave() {
        if (phase != Phase.Done) return
        phase = Phase.Leaving; clear(); leaving.time(1.0, SpaceMotion.RETURN * 1000.0, Linear)
    }
    fun touched() { autoKey = null; autoPressed = false; touches++ }
    val currentCheck by rememberUpdatedState(check)
    val arrive by rememberUpdatedState<() -> Unit> {
        phase = Phase.Done; arrivedAt = System.currentTimeMillis()
        if (!automatic) view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        currentSuccess?.invoke(sent)
    }
    LaunchedEffect(Unit) {
        var previous: Boolean? = null
        while (true) withFrameNanos { now ->
            clock.now = now
            elapsed.advance(now); val returned = leaving.advance(now); confirm.advance(now); bank.advance(now)
            if (returned) { elapsed.set(-1.0); leaving.set(0.0); phase = Phase.Form }
            val nowArrived = elapsed.value >= currentCheck
            if (nowArrived && previous == false) arrive()
            previous = nowArrived
            frame = now; clock.now = null
        }
    }
    val latestType by rememberUpdatedState<(String) -> Unit>(::type)
    val latestStart by rememberUpdatedState<(Boolean) -> Unit>(::start)
    val latestLeave by rememberUpdatedState<() -> Unit>(::leave)
    val latestClear by rememberUpdatedState<() -> Unit>(::clear)
    val currentBusy by rememberUpdatedState(busy)
    DisposableEffect(Unit) { onDispose { timer?.cancel() } }
    LaunchedEffect(autoPlay, autoPlayAmount, check, touches) {
        if (!autoPlay) return@LaunchedEffect
        val events = mutableListOf<AutoEvent>(); var serial = 0L
        fun at(origin: Long, seconds: Double, action: () -> Unit) { events += AutoEvent(origin + (seconds * 1e9).toLong(), serial++, action) }
        fun cycle(origin: Long) {
            autoPlayAmount.forEachIndexed { index, character ->
                val key = character.toString(); val time = SpaceMotion.FIRST_KEY_AT + index * SpaceMotion.KEY_EVERY
                at(origin, time) { autoKey = key }
                at(origin, time + SpaceMotion.KEY_FOR) { autoKey = null; latestType(key) }
            }
            at(origin, SpaceMotion.PRESS_AT) { autoPressed = true }
            at(origin, SpaceMotion.PRESS_AT + SpaceMotion.PRESS_FOR) { autoPressed = false; latestStart(true) }
            at(origin, SpaceMotion.PRESS_AT + SpaceMotion.PRESS_FOR + check + SpaceMotion.LEAVE_AFTER) { latestLeave() }
            at(origin, SpaceMotion.LOOP) { cycle(origin + (SpaceMotion.LOOP * 1e9).toLong()) }
        }
        if (touches == 0) cycle(System.nanoTime()) else {
            delay((SpaceMotion.USER_HOLD * 1000.0).toLong())
            while (currentBusy) {
                if (System.currentTimeMillis() - arrivedAt >= SpaceMotion.LEAVE_AFTER * 1000.0) latestLeave()
                delay(REST_POLL_MS)
            }
            latestClear(); cycle(System.nanoTime())
        }
        while (true) {
            val next = events.minWithOrNull(compareBy<AutoEvent> { it.at }.thenBy { it.order }) ?: break
            val wait = next.at - System.nanoTime()
            if (wait > 0L) { delay(max(1L, wait / 1_000_000L)); continue }
            events.remove(next); next.action()
        }
    }
    DisposableEffect(view) {
        val window = view.context.starfieldActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val old = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (old != null) controller.isAppearanceLightStatusBars = old }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(BLACK)) {
        val density = LocalDensity.current; val measurer = rememberTextMeasurer()
        val width = maxWidth.value; val height = maxHeight.value
        val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
        val safeTop = safe.calculateTopPadding().value; val safeBottom = safe.calculateBottomPadding().value
        val hairline = 1f / density.density; val bottom = max(safeBottom, 12f) + BUTTON_LIFT
        val rise = BUTTON + bottom + 16f
        val ink = remember(accent) { StarfieldPaymentSuccessCSS.color(accent) }
        fun measured(text: String, style: TextStyle, maxLines: Int = Int.MAX_VALUE, proposal: Float = width): TextLayoutResult = measurer.measure(AnnotatedString(text), style, maxLines = maxLines, overflow = if (maxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip, constraints = Constraints(maxWidth = max(0, (proposal * density.density).toInt())))
        val titleStyle = starfieldStyle(TITLE_SIZE, TITLE_LINE, 400, LABEL, align = TextAlign.Center)
        val titleText = measured(title, titleStyle, 1); val payingText = measured(payingFromLabel, titleStyle, 1)
        val titleHeight = titleText.size.height / density.density; val payingHeight = payingText.size.height / density.density
        val headStart = safeTop + TOP_GAP + CLOSE
        val amountY = HEAD_TOP + titleHeight + AMOUNT_TOP
        val chipY = amountY + AMOUNT_ROW + CHIP_TOP
        val headHeight = chipY + CHIP
        val dividerY = headStart + headHeight + DIVIDER_TOP
        val payingY = dividerY + hairline + PAYING_TOP
        val cardHitY = payingY + payingHeight
        val formEnd = cardHitY + CARD_TOP + CARD_HEIGHT
        val padEnd = height - max(safeBottom, 12f) - PAD_END
        val row = max(ROW_MIN, (padEnd - formEnd - PAD_GAP) / KEYS.size)
        val rowClosed = max(ROW_MIN - 6f, (padEnd - BUTTON - formEnd - PAD_GAP_CLOSED) / KEYS.size)
        val e = elapsed.value; val b = leaving.value; val c = confirm.value
        val out = SpaceMath.clamp01((e - SpaceMotion.DISSOLVE_AT) / SpaceMotion.DISSOLVE_FOR)
        val back = SpaceMath.smooth(b, .3, .8)
        val backdropAlpha = if (e < 0) 1.0 else if (b > 0) SpaceMath.smooth(b, .3, 1.0) else 1.0 - SpaceMath.smooth(e, SpaceMotion.DISSOLVE_AT, SpaceMotion.DISSOLVE_AT + SpaceMotion.DISSOLVE_FOR)
        StarfieldBackdrop(width, height, Modifier.fillMaxSize().graphicsLayer { alpha = backdropAlpha.toFloat() })
        if (busy) Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1.0 - SpaceMath.smooth(b, 0.0, .6)).toFloat() }.clearAndSetSemantics {}) { Space(e, processingDuration, still, width, height) }
        val headAlpha = if (e < 0) 1.0 else if (b > 0) back else 1.0 - SpaceMath.smooth(out, 0.0, .8)
        val headTravel = if (still || e < 0) 0.0 else if (b > 0) -BACK_TRAVEL * (1 - back) else -FLOAT * out * out
        val cardAlpha = if (e < 0) 1.0 else if (b > 0) back else 1.0 - SpaceMath.smooth(out, 0.0, .5)
        val cardTravel = if (still || e < 0) 0.0 else if (b > 0) BACK_TRAVEL * (1 - back) else SINK * out * out
        Box(Modifier.fillMaxSize().then(if (busy) Modifier.clearAndSetSemantics {} else Modifier)) {
            Box(Modifier.starfieldFrame(0f, headStart + headTravel.toFloat(), width, headHeight).graphicsLayer { alpha = headAlpha.toFloat(); compositingStrategy = CompositingStrategy.ModulateAlpha }) {
                StarfieldText(title, titleText, titleStyle, (width - titleText.size.width / density.density) / 2f, HEAD_TOP)
                StarfieldAmount(digits, busy, currency, title, amount, width, amountY, ink, frame, layers, scope, !busy) { touched(); onCurrencyPress?.invoke() }
                val quote = money(amount / rate).let { "${it.first}.${it.second}" }
                StarfieldChip("$quoteSymbol$quote ($quoteSymbol" + "1 = $symbol${number(rate)})", width, chipY)
            }
            Box(Modifier.starfieldFrame(0f, cardTravel.toFloat(), width, formEnd).graphicsLayer { alpha = cardAlpha.toFloat(); compositingStrategy = CompositingStrategy.ModulateAlpha }) {
                Box(Modifier.starfieldFrame(0f, dividerY, width, hairline).background(Color.White.copy(alpha = .14f)))
                StarfieldText(payingFromLabel, payingText, titleStyle, (width - payingText.size.width / density.density) / 2f, payingY)
                StarfieldControl("$payingFromLabel $cardName $cardNumber", true, !busy, Modifier.starfieldFrame(0f, cardHitY, width, CARD_TOP + CARD_HEIGHT), scope, { touched(); tick(); onCardPress?.invoke() }) { pressed ->
                    val fade = rememberScalar(1.0, bank)
                    LaunchedEffect(pressed) { fade.time(if (pressed) PRESSED else 1.0, CARD_PRESS_MS, CssEase) }
                    StarfieldCard(cardNumber, cardName, width - CARD_MARGIN * 2f, CARD_TOP, CARD_MARGIN, fade.value.toFloat())
                }
            }
            KEYS.forEachIndexed { index, keys ->
                val pitch = row + (rowClosed - row) * c
                var y = formEnd + PAD_GAP + (PAD_GAP_CLOSED - PAD_GAP) * c + pitch * (index + .5) - KEY / 2f
                val opacity = if (e < 0) 1.0 else if (b > 0) {
                    val p = SpaceMath.smooth(b, .3 + index * ROW_STAGGER, .8 + index * ROW_STAGGER)
                    if (!still) y += BACK_TRAVEL * (1 - p)
                    p
                } else {
                    if (!still) y += SINK * out * out
                    1.0 - SpaceMath.smooth(out, 0.0, .85)
                }
                Box(Modifier.starfieldFrame(PAD_MARGIN, y.toFloat(), width - PAD_MARGIN * 2f, KEY).graphicsLayer { alpha = opacity.toFloat(); compositingStrategy = CompositingStrategy.ModulateAlpha }) {
                    val columnWidth = (width - PAD_MARGIN * 2f) / 3f
                    keys.forEachIndexed { column, key ->
                        if (key.isNotEmpty()) StarfieldKey(key, autoKey == key, columnWidth, column * columnWidth, !busy, bank, scope) { touched(); tick(); type(key) }
                    }
                }
            }
            val confirmTravel = rise * (1.0 - c) + if (e < 0) 0.0 else rise * easeIn(e / SpaceMotion.DROP)
            StarfieldControl(confirmLabel, confirmUp, !busy, Modifier.starfieldFrame(CONFIRM_MARGIN, height - bottom - BUTTON + confirmTravel.toFloat(), width - CONFIRM_MARGIN * 2f, BUTTON).then(if (!confirmUp) Modifier.clearAndSetSemantics {} else Modifier), scope, { touched(); start(false) }, accessible = confirmUp) { pressed ->
                StarfieldWide(confirmLabel, null, width - CONFIRM_MARGIN * 2f, ink, pressed || autoPressed, true, bank)
            }
        }
        val waitProgress = SpaceMath.smooth(e, SpaceMotion.TEXT_AT, SpaceMotion.TEXT_AT + SpaceMotion.TEXT_IN)
        val waitOpacity = waitProgress * (1.0 - SpaceMath.smooth(e, processingDuration, processingDuration + SpaceMotion.WAIT_OUT))
        Box(Modifier.starfieldFrame(0f, height * WAIT_Y - WAIT_UP + if (still) 0f else (TEXT_RISE * (1 - waitProgress)).toFloat(), width, WAIT_TITLE_LINE + WAIT_HINT_TOP + WAIT_HINT_LINE).graphicsLayer { alpha = waitOpacity.toFloat(); compositingStrategy = CompositingStrategy.ModulateAlpha }.clearAndSetSemantics { if (phase == Phase.Waiting) contentDescription = "$waitingTitle. $waitingHint" }) {
            val waitStyle = starfieldStyle(WAIT_TITLE_SIZE, WAIT_TITLE_LINE, 500, Color.White, WAIT_TRACKING)
            val waitTitleText = measured(waitingTitle, waitStyle, 1)
            StarfieldText(waitingTitle, waitTitleText, waitStyle, (width - waitTitleText.size.width / density.density) / 2f, 0f)
            val hintStyle = starfieldStyle(WAIT_HINT_SIZE, WAIT_HINT_LINE, 400, LABEL)
            val hintText = measured(waitingHint, hintStyle, 1)
            StarfieldText(waitingHint, hintText, hintStyle, (width - hintText.size.width / density.density) / 2f, waitTitleText.size.height / density.density + WAIT_HINT_TOP)
        }
        val sentParts = money(sent)
        val doneStyle = starfieldStyle(DONE_SIZE, DONE_LINE, 500, Color.White, DONE_TRACKING)
        val doneText = measured(doneLabel, doneStyle, 1)
        val resultTop = height * RESULT_Y - RESULT_UP; val doneHeight = doneText.size.height / density.density
        fun result(index: Int): Pair<Float, Float> {
            val from = check + SpaceMotion.RESULT_AFTER + RESULT_STAGGER * index
            val p = SpaceMath.smooth(e, from, from + SpaceMotion.RESULT_IN)
            return (p * (1 - SpaceMath.smooth(b, 0.0, .5))).toFloat() to if (still) 0f else (RESULT_RISE * (1 - p)).toFloat()
        }
        Box(Modifier.starfieldFrame(0f, resultTop, width, doneHeight + SUM_TOP + SUM + SUM_BOTTOM + CHIP_TOP + CHIP).clearAndSetSemantics { if (arrived) contentDescription = "$doneLabel: $symbol${sentParts.first}.${sentParts.second}" }) {
            val doneMotion = result(0)
            Box(Modifier.starfieldFrame(0f, doneMotion.second, width, doneHeight).graphicsLayer { alpha = doneMotion.first; compositingStrategy = CompositingStrategy.ModulateAlpha }) { StarfieldText(doneLabel, doneText, doneStyle, (width - doneText.size.width / density.density) / 2f, 0f) }
            val sumMotion = result(1)
            StarfieldSum(symbol, sentParts, width, doneHeight + SUM_TOP + sumMotion.second, sumMotion.first)
            val noteMotion = result(2)
            val sentQuote = money(sent / rate).let { "${it.first}.${it.second}" }
            Box(Modifier.starfieldFrame(0f, doneHeight + SUM_TOP + SUM + SUM_BOTTOM + noteMotion.second, width, CHIP_TOP + CHIP).graphicsLayer { alpha = noteMotion.first; compositingStrategy = CompositingStrategy.ModulateAlpha }) { StarfieldChip("$quoteSymbol$sentQuote ($quoteSymbol" + "1 = $symbol${number(rate)})", width, CHIP_TOP) }
        }
        val barWidth = width - BAR_MARGIN * 2f; val reviewWidth = barWidth - BAR_GAP - BUTTON
        val reviewP = SpaceMath.backOut((e - check - SpaceMotion.BAR_AFTER) / SpaceMotion.BAR_IN)
        val shareP = SpaceMath.backOut((e - check - SpaceMotion.BAR_AFTER - SHARE_AFTER) / SpaceMotion.BAR_IN)
        val returning = rise * easeIn(b / .5)
        StarfieldControl(reviewLabel, true, arrived, Modifier.starfieldFrame(BAR_MARGIN, height - bottom - BUTTON + (rise * (1 - reviewP) + returning).toFloat(), reviewWidth, BUTTON), scope, { touched(); tick(); onReview?.invoke(sent); leave() }) { pressed -> StarfieldWide(reviewLabel, ICON_ARROW, reviewWidth, ink, pressed, false, bank) }
        StarfieldControl("Share", true, arrived, Modifier.starfieldFrame(width - BAR_MARGIN - BUTTON, height - bottom - BUTTON + (rise * (1 - shareP) + returning).toFloat(), BUTTON, BUTTON), scope, { touched(); tick(); onShare?.invoke(sent); leave() }) { pressed ->
            val fade = rememberScalar(1.0, bank)
            LaunchedEffect(pressed) { fade.time(if (pressed) PRESSED else 1.0, WIDE_PRESS_MS, CssEase) }
            Box(Modifier.size(BUTTON.dp).graphicsLayer { alpha = fade.value.toFloat() }.background(SHARE, RoundedCornerShape((BUTTON / 2f).dp)).border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape((BUTTON / 2f).dp)), contentAlignment = Alignment.Center) { StarfieldIcon(ICON_SHARE, SHARE_ICON, Color.White, 1.8f) }
        }
        StarfieldControl("Close", true, true, Modifier.starfieldFrame(width - CLOSE_INSET - CLOSE - CLOSE_SLOP, safeTop + TOP_GAP - CLOSE_SLOP, CLOSE + CLOSE_SLOP * 2f, CLOSE + CLOSE_SLOP * 2f), scope, { touched(); tick(); onClose?.invoke(); if (phase != Phase.Form) leave() else clear() }) {
            Box(Modifier.starfieldFrame(CLOSE_SLOP, CLOSE_SLOP, CLOSE, CLOSE).background(TILE, RoundedCornerShape(CLOSE_RADIUS.dp)), contentAlignment = Alignment.Center) { StarfieldIcon(ICON_CLOSE, CLOSE_ICON, Color.White, 1.8f) }
        }
    }
}

private fun Context.starfieldActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.starfieldActivity(); else -> null }
private fun Modifier.starfieldFrame(x: Float, y: Float, width: Float, height: Float): Modifier = wrapContentSize(Alignment.TopStart, unbounded = true).size(max(0f, width).dp, max(0f, height).dp).graphicsLayer { translationX = x * density; translationY = y * density }
private fun starfieldStyle(size: Float, line: Float, weight: Int, ink: Color, tracking: Float = 0f, tabular: Boolean = false, align: TextAlign = TextAlign.Start) = TextStyle(
    fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight), color = ink, fontFamily = FontFamily.SansSerif, letterSpacing = tracking.sp, textAlign = align,
    fontFeatureSettings = if (tabular) "tnum" else null, platformStyle = PlatformTextStyle(includeFontPadding = true), lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)
@Composable
private fun StarfieldText(text: String, layout: TextLayoutResult, style: TextStyle, x: Float, y: Float) {
    val density = LocalDensity.current
    BasicText(text, Modifier.starfieldFrame(x, y, layout.size.width / density.density, layout.size.height / density.density), style = style, maxLines = layout.lineCount.coerceAtLeast(1), overflow = TextOverflow.Ellipsis)
}
@Composable
private fun StarfieldBackdrop(width: Float, height: Float, modifier: Modifier) {
    val glow = remember { Brush.radialGradient(*FALLOFF_AT.indices.map { FALLOFF_AT[it] to rgba(EMBER[0], EMBER[1], EMBER[2], .9f * FALLOFF[it]) }.toTypedArray(), center = Offset.Zero, radius = 1f) }
    val tint = remember(height) { Brush.verticalGradient(listOf(TINT, BLACK), startY = 0f, endY = height * .36f) }
    Canvas(modifier.clearAndSetSemantics {}) {
        surface(0.dp) { _, _, _, _ ->
            drawRect(tint, size = Size(width, height * .36f))
            withTransform({ translate(width / 2f, height * EMBER_Y); scale(width * EMBER_WIDTH, height * EMBER_HEIGHT, Offset.Zero) }) { drawCircle(glow, 1f, Offset.Zero) }
        }
    }
}
@Composable
private fun StarfieldChip(label: String, parentWidth: Float, top: Float) {
    val density = LocalDensity.current; val measurer = rememberTextMeasurer()
    val style = starfieldStyle(CHIP_SIZE, CHIP_LINE, 500, UNIT, tabular = true)
    val text = measurer.measure(AnnotatedString(label), style, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = max(0, ((parentWidth - CHIP_PADDING * 2f) * density.density).toInt())))
    val textWidth = text.size.width / density.density; val width = CHIP_PADDING * 2f + CHIP_ICON + CHIP_GAP + textWidth
    Box(Modifier.starfieldFrame((parentWidth - width) / 2f, top, width, CHIP).background(TILE, RoundedCornerShape((CHIP / 2f).dp)).clearAndSetSemantics {}) {
        Box(Modifier.starfieldFrame(CHIP_PADDING, (CHIP - CHIP_ICON) / 2f, CHIP_ICON, CHIP_ICON)) { StarfieldIcon(ICON_SWAP, CHIP_ICON, UNIT) }
        StarfieldText(label, text, style, CHIP_PADDING + CHIP_ICON + CHIP_GAP, (CHIP - text.size.height / density.density) / 2f)
    }
}
@Composable
private fun StarfieldCard(number: String, name: String, width: Float, top: Float, left: Float, opacity: Float) {
    val density = LocalDensity.current; val measurer = rememberTextMeasurer()
    val numberStyle = starfieldStyle(CARD_NUMBER_SIZE, CARD_NUMBER_LINE, 500, Color.White)
    val nameStyle = starfieldStyle(CARD_NAME_SIZE, CARD_NAME_LINE, 500, CARD_NAME, CARD_NAME_TRACKING)
    val textLeft = CARD_BORDER + CARD_LEFT + CARD_ART_WIDTH + CARD_TEXT_LEFT
    val textWidth = max(0f, width - textLeft - CARD_BORDER - CARD_RIGHT - CARD_CHEVRON)
    fun text(raw: String, style: TextStyle) = measurer.measure(AnnotatedString(raw), style, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = max(0, (textWidth * density.density).toInt())))
    val numberText = text(number, numberStyle); val nameText = text(name, nameStyle)
    val linesHeight = (numberText.size.height + nameText.size.height) / density.density + CARD_NAME_TOP
    val textY = (CARD_HEIGHT - linesHeight) / 2f
    Box(Modifier.starfieldFrame(left, top, width, CARD_HEIGHT).graphicsLayer { alpha = opacity; compositingStrategy = CompositingStrategy.ModulateAlpha }.background(Brush.horizontalGradient(CARD), RoundedCornerShape(CARD_RADIUS.dp)).border(CARD_BORDER.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(CARD_RADIUS.dp))) {
        Box(Modifier.starfieldFrame(CARD_BORDER + CARD_LEFT, (CARD_HEIGHT - CARD_ART_HEIGHT) / 2f, CARD_ART_WIDTH, CARD_ART_HEIGHT)) { StarfieldCardArt() }
        StarfieldText(number, numberText, numberStyle, textLeft, textY)
        StarfieldText(name, nameText, nameStyle, textLeft, textY + numberText.size.height / density.density + CARD_NAME_TOP)
        Box(Modifier.starfieldFrame(width - CARD_BORDER - CARD_RIGHT - CARD_CHEVRON, (CARD_HEIGHT - CARD_CHEVRON) / 2f, CARD_CHEVRON, CARD_CHEVRON)) { StarfieldIcon(ICON_CHEVRON, CARD_CHEVRON, CARD_NAME) }
    }
}
@Composable
private fun StarfieldAmount(digits: String, busy: Boolean, currency: String, title: String, amount: Double, parentWidth: Float, top: Float, ink: Color, frame: Long, layers: DigitLayers, scope: CoroutineScope, pointers: Boolean, action: () -> Unit) {
    val density = LocalDensity.current; val measurer = rememberTextMeasurer()
    val figureStyle = starfieldStyle(AMOUNT, AMOUNT_ROW, 300, Color.White, FIGURE_TRACKING, true)
    val unitStyle = figureStyle.copy(color = UNIT)
    val placeholderStyle = figureStyle.copy(color = PLACEHOLDER)
    fun measure(text: String, style: TextStyle) = measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = max(0, (parentWidth * density.density).toInt())))
    val shown = if (digits.isEmpty()) listOf("0.00") else digits.map { it.toString() }
    val digitText = shown.map { measure(it, if (digits.isEmpty()) placeholderStyle else figureStyle) }
    val unit = measure(currency, unitStyle)
    val caretSlot = CARET_WIDTH + CARET_MARGIN * 2f
    val unitLeft = if (digits.isEmpty()) UNIT_APART else UNIT_LEFT
    val width = digitText.sumOf { (it.size.width / density.density).toDouble() }.toFloat() + caretSlot + unitLeft + unit.size.width / density.density + SELECT_LEFT + SELECT_SIZE
    val key = if (digits.isEmpty()) "empty-$busy" else digits
    val began = remember(key) { System.nanoTime() }
    val u = ((frame - began).coerceAtLeast(0L) / 1e9) % 1.0
    val caretAlpha = when { u <= .5 -> 1.0; u < .56 -> 1.0 - (u - .5) / .06; u <= .94 -> 0.0; else -> (u - .94) / .06 }
    StarfieldControl("$title: ${number(amount)} $currency", true, pointers, Modifier.starfieldFrame((parentWidth - width) / 2f, top, width, AMOUNT_ROW), scope, action) {
        var x = 0f
        @Composable fun caret(at: Float) { Box(Modifier.starfieldFrame(at + CARET_MARGIN, (AMOUNT_ROW - CARET) / 2f, CARET_WIDTH, CARET).graphicsLayer { alpha = caretAlpha.toFloat() }.background(ink, RoundedCornerShape(CARET_RADIUS.dp))) }
        if (digits.isEmpty()) { caret(x); x += caretSlot }
        shown.forEachIndexed { index, text ->
            val layout = digitText[index]; val at = x; val alpha = if (digits.isEmpty()) 1f else layers.opacity.getOrNull(index)?.value?.toFloat() ?: 1f
            Box(Modifier.starfieldFrame(at, 0f, layout.size.width / density.density, AMOUNT_ROW).graphicsLayer { this.alpha = alpha; compositingStrategy = CompositingStrategy.ModulateAlpha }) { StarfieldText(text, layout, if (digits.isEmpty()) placeholderStyle else figureStyle, 0f, (AMOUNT_ROW - layout.size.height / density.density) / 2f) }
            x += layout.size.width / density.density
        }
        if (digits.isNotEmpty()) { caret(x); x += caretSlot }
        x += unitLeft
        StarfieldText(currency, unit, unitStyle, x, (AMOUNT_ROW - unit.size.height / density.density) / 2f); x += unit.size.width / density.density + SELECT_LEFT
        Box(Modifier.starfieldFrame(x, (AMOUNT_ROW - SELECT_SIZE) / 2f, SELECT_SIZE, SELECT_SIZE)) { StarfieldIcon(ICON_SELECT, SELECT_SIZE, UNIT, 1.8f) }
    }
}
@Composable
private fun StarfieldSum(symbol: String, sent: Pair<String, String>, parentWidth: Float, top: Float, opacity: Float) {
    val density = LocalDensity.current; val measurer = rememberTextMeasurer()
    val signStyle = starfieldStyle(SUM_SIGN_SIZE, SUM, 200, PLACEHOLDER)
    val wholeStyle = starfieldStyle(SUM_WHOLE_SIZE, SUM, 400, Color.White, SUM_TRACKING, true)
    val centsStyle = starfieldStyle(SUM_CENTS_SIZE, SUM, 400, PLACEHOLDER, tabular = true)
    val values = listOf("−", symbol + sent.first, ".${sent.second}"); val styles = listOf(signStyle, wholeStyle, centsStyle)
    val layouts = values.mapIndexed { index, value -> measurer.measure(AnnotatedString(value), styles[index], constraints = Constraints(maxWidth = max(0, (parentWidth * density.density).toInt()))) }
    val width = layouts.sumOf { (it.size.width / density.density).toDouble() }.toFloat() + SUM_SIGN_RIGHT
    val baseline = layouts.maxOf { it.firstBaseline } / density.density
    Box(Modifier.starfieldFrame((parentWidth - width) / 2f, top, width, SUM).graphicsLayer { alpha = opacity; compositingStrategy = CompositingStrategy.ModulateAlpha }) {
        var x = 0f
        values.forEachIndexed { index, value ->
            StarfieldText(value, layouts[index], styles[index], x, baseline - layouts[index].firstBaseline / density.density)
            x += layouts[index].size.width / density.density + if (index == 0) SUM_SIGN_RIGHT else 0f
        }
    }
}
@Composable
private fun StarfieldWide(label: String, icon: String?, width: Float, ink: Color, pressed: Boolean, scaled: Boolean, bank: AnimationBank) {
    val fade = rememberScalar(1.0, bank); val scale = rememberScalar(1.0, bank)
    LaunchedEffect(pressed) { fade.time(if (pressed) PRESSED else 1.0, WIDE_PRESS_MS, CssEase); scale.time(if (pressed && scaled) WIDE_SCALE else 1.0, WIDE_PRESS_MS, CssEase) }
    val density = LocalDensity.current; val measurer = rememberTextMeasurer()
    val style = starfieldStyle(WIDE_SIZE, WIDE_LINE, 600, Color.White)
    val text = measurer.measure(AnnotatedString(label), style, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = max(0, (width * density.density).toInt())))
    val textWidth = text.size.width / density.density; val contentWidth = textWidth + if (icon == null) 0f else WIDE_GAP + ARROW_SIZE
    Box(Modifier.size(max(0f, width).dp, BUTTON.dp).graphicsLayer { alpha = fade.value.toFloat(); scaleX = scale.value.toFloat(); scaleY = scale.value.toFloat(); compositingStrategy = CompositingStrategy.ModulateAlpha }.background(ink, RoundedCornerShape((BUTTON / 2f).dp))) {
        StarfieldText(label, text, style, (width - contentWidth) / 2f, (BUTTON - text.size.height / density.density) / 2f)
        if (icon != null) Box(Modifier.starfieldFrame((width - contentWidth) / 2f + textWidth + WIDE_GAP, (BUTTON - ARROW_SIZE) / 2f, ARROW_SIZE, ARROW_SIZE)) { StarfieldIcon(icon, ARROW_SIZE, Color.White) }
    }
}
@Composable
private fun StarfieldKey(label: String, held: Boolean, width: Float, left: Float, pointers: Boolean, bank: AnimationBank, scope: CoroutineScope, action: () -> Unit) {
    // Fabric includes the overflowing flash in initial hits; retention still follows the 64-point key.
    val hitWidth = max(width, KEY_FLASH); val extra = (KEY_FLASH - KEY) / 2f
    val base = HitFrame((hitWidth - width) / 2f, extra, width, KEY)
    StarfieldControl(if (label == "back") "Delete" else label, true, pointers, Modifier.starfieldFrame(left - (hitWidth - width) / 2f, -extra, hitWidth, KEY_FLASH), scope, action, base = base, flashUnion = true) { pressed ->
        val flash = rememberScalar(0.0, bank)
        LaunchedEffect(pressed || held) { flash.time(if (pressed || held) 1.0 else 0.0, if (pressed || held) FLASH_ON else FLASH_OFF, CssEase) }
        Box(Modifier.starfieldFrame((hitWidth - KEY_FLASH) / 2f, 0f, KEY_FLASH, KEY_FLASH).graphicsLayer { alpha = flash.value.toFloat() }.background(Color.White.copy(alpha = .1f), RoundedCornerShape((KEY_FLASH / 2f).dp)))
        Box(Modifier.starfieldFrame(base.left, base.top, width, KEY), contentAlignment = Alignment.Center) {
            if (label == "back") StarfieldIcon(ICON_DELETE, DELETE_SIZE, Color.White, 1.8f)
            else BasicText(label, style = starfieldStyle(DIGIT_SIZE, DIGIT_LINE, 500, Color.White))
        }
    }
}
private data class HitFrame(val left: Float, val top: Float, val width: Float, val height: Float)
private class PressState { var alive = true; var latestOut: Job? = null }

/** Source Pressability keeps a grant during leave/re-entry, and delays its visual release. */
@Composable
private fun StarfieldControl(label: String, enabled: Boolean, pointers: Boolean, modifier: Modifier, scope: CoroutineScope, action: () -> Unit, accessible: Boolean = true, base: HitFrame? = null, flashUnion: Boolean = false, content: @Composable (Boolean) -> Unit) {
    val state = remember { PressState() }; var pressed by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var retainingGrant by remember { mutableStateOf(false) }
    val currentPointers by rememberUpdatedState(pointers)
    val granted by rememberUpdatedState(enabled); val currentAction by rememberUpdatedState(action); val view = LocalView.current
    DisposableEffect(state) { state.alive = true; onDispose { state.alive = false; state.latestOut?.cancel() } }
    val semantics = if (!accessible) Modifier else Modifier.semantics(mergeDescendants = true) { role = Role.Button; contentDescription = label; if (!enabled) disabled(); onClick { if (granted) { currentAction(); true } else false } }
    val gesture = if (pointers || retainingGrant) Modifier.pointerInput(state, base, flashUnion) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val rect = base ?: HitFrame(0f, 0f, size.width / density, size.height / density)
            fun inBase(x: Float, y: Float): Boolean = x >= rect.left * density && x <= (rect.left + rect.width) * density && y >= rect.top * density && y <= (rect.top + rect.height) * density
            val flashLeft = (size.width / density - KEY_FLASH) / 2f
            val inFlash = flashUnion && down.position.x >= flashLeft * density && down.position.x <= (flashLeft + KEY_FLASH) * density && down.position.y >= 0f && down.position.y <= KEY_FLASH * density
            if (!currentPointers || !granted || !(inBase(down.position.x, down.position.y) || inFlash)) return@awaitEachGesture
            retainingGrant = true
            state.latestOut?.cancel(); state.latestOut = null
            var active = true; var activatedAt: Long? = System.nanoTime()
            fun pressIn() { activatedAt = System.nanoTime(); pressed = true }
            fun pressOut() {
                val remaining = max(0L, MIN_PRESS_DURATION_MS * 1_000_000L - (System.nanoTime() - (activatedAt ?: 0L)))
                activatedAt = null
                if (remaining == 0L) pressed = false else state.latestOut = scope.launch { delay((remaining + 999_999L) / 1_000_000L); pressed = false }
            }
            val grantedOrigin = coordinates?.takeIf { it.isAttached }?.localToRoot(Offset.Zero)
            pressIn(); down.consume()
            try {
                while (true) {
                    val event = awaitPointerEvent(); val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (finger.isConsumed) break
                    val root = coordinates?.takeIf { it.isAttached }?.localToRoot(finger.position)
                    val point = if (root != null && grantedOrigin != null) root - grantedOrigin else finger.position
                    val x = point.x / density; val y = point.y / density
                    val inside = x > rect.left - PRESS_RETENTION && x < rect.left + rect.width + PRESS_RETENTION && y > rect.top - PRESS_RETENTION && y < rect.top + rect.height + PRESS_RETENTION_BOTTOM
                    if (inside != active) { active = inside; if (inside) pressIn() else pressOut() }
                    finger.consume()
                    if (!finger.pressed) { if (active) { pressOut(); active = false; view.playSoundEffect(SoundEffectConstants.CLICK); currentAction() }; break }
                }
                if (active) { active = false; pressOut() }
            } catch (cancelled: CancellationException) { if (active) scope.launch { yield(); if (state.alive) pressOut() }; throw cancelled }
            finally { retainingGrant = false }
        }
    } else Modifier
    Box(modifier.onGloballyPositioned { coordinates = it }.then(semantics).then(gesture)) { content(pressed) }
}
