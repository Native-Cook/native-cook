package com.ngocdevv.nativecook.blocks.forms.slingshotsendmoney

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.ViewConfiguration
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val STOPS = floatArrayOf(0f, .13f, .21f, .29f, .37f, .47f, .53f, .59f, .645f, .72f, .79f, .86f, .94f, 1f)
private val STOPS_PULLED = floatArrayOf(0f, .19f, .33f, .45f, .54f, .61f, .66f, .71f, .76f, .81f, .86f, .91f, .96f, 1f)
private val SATIN = listOf("#85625f", "#82605e", "#6b504e", "#453233", "#33282a", "#2f2526", "#402e30", "#694c4e", "#735553", "#594546", "#483436", "#4a2f31", "#5b3132", "#5e3030")
private val HEAT = listOf("#c9801f", "#c2781f", "#b0691d", "#8f561b", "#6e431c", "#5f3d2a", "#6a4a3e", "#5d423c", "#47302c", "#3a2622", "#3d2420", "#45241e", "#5a2c28", "#693229")
private const val GLOW = "#ffb347"
private const val GLOW_ALPHA = .3f
private val INK = Color.White
private const val HINT_ALPHA = .6f
private const val ARMED_ALPHA = .9f
private val ROUND_FILL = Color.White.copy(alpha = .13f)
private val ROUND_PRESSED = Color.White.copy(alpha = .26f)
private val PILL_FILL = Color(0xFF87595A)
private val PILL_PRESSED = Color(0xFF6C4648)
private const val AVATAR_FILL = "#f3cfcb"
private const val AVATAR_INK = "#6e4b4a"
private const val SUCCESS = "#7fe62c"
private const val AVATAR = 68f
private const val AVATAR_TOP = 6f
private const val AMOUNT_SIZE = 64f
private const val AMOUNT_LINE = 76f
private const val HINT_SIZE = 22f
private const val HINT_LINE = 28f
private const val HINT_TRACKING = 10f
private const val ARMED_TRACKING = 6f
private const val ROUND = 56f
private const val ROUND_MARGIN = 20f
private const val CHECK = 62f
private const val PILL_WIDTH = 184f
private const val PILL_HEIGHT = 52f
private const val AMOUNT_AT = .37f
private const val HINT_AT = .578f
private const val BOTTOM_ROW_AT = .885f
private const val SENT_GAP = 12f
private const val AMOUNT_TRAVEL = .245f
private const val FINGER_RATIO = .8f
private const val OVERPULL = 56f
private const val HINT_FOLLOW = .62f
private const val ARM_AT = .72f
private const val SIDE_RATIO = .32f
private const val SIDE_MAX = 46f
private const val TILT_ARM = 420f
private data class Spring(val mass: Float, val stiffness: Float, val damping: Float)
private val AMOUNT_SPRINGS = listOf(Spring(1f, 170f, 16f), Spring(1f, 118f, 13.5f), Spring(1f, 84f, 11.5f), Spring(1f, 60f, 10f))
private val HINT_SPRINGS = listOf(Spring(1f, 110f, 14f), Spring(1f, 80f, 12f), Spring(1f, 58f, 10.5f), Spring(1f, 42f, 9f))
private val GHOST_ALPHA = floatArrayOf(1f, .36f, .24f, .14f)
private const val GHOST_AT = 22f
private const val STRAIN_AT = 150f
private const val STRETCH = .24f
private const val SQUEEZE = .07f
private const val FLOWN_SCALE = .4f
private const val FADE_FROM = .3f
private const val FADE_TO = .72f
private val HEAT_SPRING = Spring(1f, 130f, 20f)
private const val WORDING = 140
private const val HINT_OUT = 220
private const val CATCH_AFTER = 150
private const val CATCH_IN = 90
private val CATCH_SPRING = Spring(1f, 260f, 12f)
private const val CATCH_SCALE = .1f
private const val CONFIRM_AFTER = 900
private val POP = Spring(1f, 260f, 15f)
private const val TICK_AFTER = 120
private const val TICK_FOR = 220
private const val FADE = 250
private val PILL_SPRING = Spring(1f, 190f, 19f)
private const val PILL_OUT = 260
private const val CHECK_OUT = 180
private const val HINT_BACK_AFTER = 320
private const val HINT_BACK = 350
private const val RETURN = 1f
private const val LOOP = 9f
private const val TEST_AT = .8f
private const val TEST_FOR = .5f
private const val TEST_UP = 1.45f
private const val TEST_DEPTH = .42f
private const val PULL_AT = 2.3f
private const val PULL_FOR = .95f
private const val PULL_UP = 4.3f
private const val TREMBLE = .02f
private const val TREMBLE_SPEED = 9f
private const val WANDER = 34f
private const val DONE_AT = 7f
private const val PRESS_FOR = .15f
private const val USER_HOLD = 3f
private const val SENDING_POLL_MS = 200L
private const val ROUND_TRANSITION_MS = 120
private const val PILL_TRANSITION_MS = 110
private const val ROUND_HIT = 8f
private const val AVATAR_SHADOW_Y = 6f
private const val AVATAR_SHADOW_SIGMA = 8f
private const val AVATAR_SHADOW_ALPHA = .22f
private const val SHADOW_SPILL = 32f
private const val SPRING_ENERGY_THRESHOLD = 6e-9f
private const val SPRING_FRAME_CAP = .064f
private val QuadInOut = Easing { if (it < .5f) 2f * it * it else 1f - (-2f * it + 2f).pow(2) / 2f }
private val QuadOut = Easing { 1f - (1f - it).pow(2) }
private val CubicOut = Easing { 1f - (1f - it).pow(3) }
private val CubicIn = Easing { it * it * it }
private val CssEase = CubicBezierEasing(.25f, .1f, .25f, 1f)
private val TickPath = Path().apply { moveTo(19.5f, 32.5f); lineTo(27.5f, 40f); lineTo(43f, 23.5f) }
private enum class Stage { Idle, Sending, Sent }
private fun smooth(t: Float, from: Float, to: Float): Float { val u = ((t - from) / (to - from)).coerceIn(0f, 1f); return u * u * (3f - 2f * u) }
private fun pullFor(dy: Float, travel: Float): Float { val y = max(0f, dy) * FINGER_RATIO; if (y <= travel) return y; val over = y - travel; return travel + OVERPULL * over / (over + OVERPULL) }
private fun color(value: String): Color = SlingshotSendMoneyCSS.color(value)
private fun channels(hex: String): Color {
    val value = hex.drop(1).takeWhile { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.toLongOrNull(16) ?: 0L
    return rgba(((value shr 16) and 255).toInt(), ((value shr 8) and 255).toInt(), (value and 255).toInt(), 1f)
}
private fun mix(a: Color, b: Color, t: Float) = Color(((a.red + (b.red - a.red) * t) * 255f).roundToInt() / 255f, ((a.green + (b.green - a.green) * t) * 255f).roundToInt() / 255f, ((a.blue + (b.blue - a.blue) * t) * 255f).roundToInt() / 255f, a.alpha + (b.alpha - a.alpha) * t)
private fun money(symbol: String, value: Double): String {
    val fixed = when { value.isNaN() -> "NaN"; value == Double.POSITIVE_INFINITY -> "Infinity"; value == Double.NEGATIVE_INFINITY -> "-Infinity"; abs(value) >= 1e21 -> value.toString().replace("E", "e").replace(".0e", "e").replace(Regex("e(-?)0+"), "e$1").replace(Regex("e(?=\\d)"), "e+"); else -> BigDecimal(abs(value)).setScale(2, RoundingMode.HALF_UP).toPlainString().let { if (value < 0.0) "-$it" else it } }
    return symbol + fixed.replace(Regex("\\B(?=(\\d{3})+(?!\\d))"), ",")
}
private fun textStyle(size: Float, line: Float, weight: FontWeight, spacing: Float = 0f, ink: Color = INK) = TextStyle(color = ink, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp, textAlign = TextAlign.Center, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None), platformStyle = PlatformTextStyle(includeFontPadding = false))
private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

/** Analytic Reanimated spring integration, with its critical branch and frame cap. */
private class Motion(initial: Float) : State<Float> {
    private val state = mutableFloatStateOf(initial)
    override val value get() = state.floatValue
    val active get() = timing || springing
    private var target = initial
    private var velocity = 0f
    private var config = Spring(1f, 1f, 1f)
    private var energy0 = 0f
    private var springing = false
    private var firstSpringFrame = false
    private var timing = false
    private var origin = 0L
    private var duration = 0f
    private var from = initial
    private var curve: Easing = QuadInOut
    fun direct(next: Float) { timing = false; springing = false; velocity = 0f; state.floatValue = next; target = next }
    fun spring(next: Float, spring: Spring, still: Boolean, higherOrder: Boolean = false) {
        if (still || (value == next && !higherOrder)) { direct(next); return }
        if (springing && target == next && config == spring) return
        if ((next > value && velocity < 0f) || (next < value && velocity > 0f)) velocity = 0f
        firstSpringFrame = !springing
        energy0 = .5f * spring.stiffness * (value - next).pow(2)
        target = next; config = spring; springing = true; timing = false
    }
    fun tween(next: Float, ms: Int, ease: Easing = QuadInOut, still: Boolean = false, higherOrder: Boolean = false, startingAt: Long = System.nanoTime()) {
        if (still || (value == next && !higherOrder)) { direct(next); return }
        if (timing && target == next) { duration = ms * 1e6f; curve = ease; return }
        from = value; target = next; origin = startingAt; duration = ms * 1e6f; curve = ease; timing = true; springing = false; velocity = 0f
    }
    fun advance(now: Long, elapsed: Float) {
        if (timing) { val t = ((now - origin) / duration).coerceIn(0f, 1f); state.floatValue = from + (target - from) * curve.transform(t); if (t >= 1f) timing = false }
        if (!springing) return
        if (firstSpringFrame) { firstSpringFrame = false; return }
        val dt = min(SPRING_FRAME_CAP, elapsed)
        val omega = sqrt(config.stiffness / config.mass); val decay = config.damping / (2f * config.mass); val e = value - target
        val next: Float
        if (decay < omega) { val w = sqrt(omega * omega - decay * decay); val s = sin(w * dt); val c = cos(w * dt); val envelope = exp(-decay * dt); next = envelope * (e * c + (velocity + decay * e) / w * s); velocity = envelope * (velocity * c - (decay * velocity + omega * omega * e) / w * s) }
        else { val envelope = exp(-omega * dt); val b = velocity + omega * e; next = envelope * (e + b * dt); velocity = envelope * (velocity - omega * b * dt) }
        state.floatValue = target + next
        val energy = .5f * config.stiffness * next * next + .5f * config.mass * velocity * velocity
        if (energy0 == 0f || energy / energy0 <= SPRING_ENERGY_THRESHOLD) { state.floatValue = target; velocity = 0f; springing = false }
    }
}

private object SlingshotSendMoneyCSS {
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
        val low = kotlin.math.floor(value)
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


private sealed interface AnimationSpec {
    data class Time(val target: Float, val ms: Int, val ease: Easing = QuadInOut, val still: Boolean = false) : AnimationSpec
    data class Bounce(val target: Float, val spring: Spring, val still: Boolean = false) : AnimationSpec
    data class Sequence(val steps: List<AnimationSpec>) : AnimationSpec
}
private interface Node { val value: Float; val finished: Boolean; fun advance(now: Long) }
private class DirectNode(initial: Float, spec: AnimationSpec, previous: Node?, higherOrder: Boolean, now: Long) : Node {
    val motion: Motion = (previous as? DirectNode)?.motion ?: Motion(initial)
    private var last: Long = (previous as? DirectNode)?.last ?: now
    override val value get() = motion.value
    override val finished get() = !motion.active
    init { when (spec) { is AnimationSpec.Time -> motion.tween(spec.target, spec.ms, spec.ease, spec.still, higherOrder, now); is AnimationSpec.Bounce -> motion.spring(spec.target, spec.spring, spec.still, higherOrder); is AnimationSpec.Sequence -> error("A sequence owns its children") } }
    override fun advance(now: Long) { motion.advance(now, ((now - last) / 1e9f).coerceAtLeast(0f)); last = now }
}
private fun node(initial: Float, spec: AnimationSpec, previous: Node?, higherOrder: Boolean = false, now: Long = System.nanoTime()): Node = if (spec is AnimationSpec.Sequence) SequenceNode(initial, spec.steps, previous, now) else DirectNode(initial, spec, previous, higherOrder, now)
private class SequenceNode(initial: Float, private val specs: List<AnimationSpec>, previous: Node?, now: Long) : Node {
    private var index = 0
    private var child = node(initial, specs[0], previous, true, now)
    override val value get() = child.value
    override val finished get() = index == specs.lastIndex && child.finished
    override fun advance(now: Long) { child.advance(now); while (child.finished && index < specs.lastIndex) { val previous = child; index++; child = node(previous.value, specs[index], previous, true, now); child.advance(now) } }
}
/** A replacement delay lets its preceding wrapper keep running until the new child begins. */
private class DelayNode(initial: Float, ms: Int, private val spec: AnimationSpec, previous: Node?, origin: Long) : Node {
    private val wait = ms > 0
    private var old = previous
    private var child: Node? = null
    private val at = origin + ms * 1_000_000L
    private var current = initial
    override val value get() = child?.value ?: current
    override val finished get() = child?.finished == true
    override fun advance(now: Long) {
        if (child == null && wait && now < at) { old?.advance(now); old?.let { current = it.value }; if (old?.finished == true) old = null; return }
        if (child == null) { child = node(current, spec, old, true, now); old = null }
        child?.advance(now)
    }
}
private class FrameTime { var now: Long? = null }
private class AnimatedValue(initial: Float, private val frameTime: FrameTime) : State<Float> {
    private val state = mutableFloatStateOf(initial)
    private var animation: Node? = null
    override val value get() = state.floatValue
    fun time(target: Float, ms: Int, ease: Easing = QuadInOut) { animation = node(value, AnimationSpec.Time(target, ms, ease), animation, now = frameTime.now ?: System.nanoTime()); state.floatValue = animation!!.value }
    fun delayed(ms: Int, spec: AnimationSpec) { animation = DelayNode(value, ms, spec, animation, frameTime.now ?: System.nanoTime()) }
    fun advance(now: Long) { animation?.let { it.advance(now); state.floatValue = it.value } }
}

@Composable
private fun Echo(x: Motion, y: Motion, aimY: Float, order: Int, rise: Float, shown: AnimatedValue, top: Float, line: Float, content: @Composable () -> Unit) {
    Box(Modifier.offset(y = top.dp).fillMaxWidth().height(line.dp).graphicsLayer {
        val lag = abs(aimY - y.value); val strain = min(1f, lag / STRAIN_AT); val up = if (rise > 0f) (-y.value / rise).coerceIn(0f, 1f) else 0f; val scale = 1f - (1f - FLOWN_SCALE) * up
        alpha = shown.value * GHOST_ALPHA[order] * (if (order == 0) 1f else min(1f, lag / GHOST_AT)) * (1f - smooth(up, FADE_FROM, FADE_TO))
        translationX = x.value * density; translationY = y.value * density; rotationZ = -x.value / TILT_ARM * 180f / PI.toFloat(); scaleX = scale * (1f - SQUEEZE * strain); scaleY = scale * (1f + STRETCH * strain); compositingStrategy = CompositingStrategy.ModulateAlpha
    }.clearAndSetSemantics {}, contentAlignment = Alignment.Center) { content() }
}

private class LoopClock {
    var origin: Long? = null
    var previous: Float? = null
    fun start() { origin = System.nanoTime(); previous = null }
    fun stop() { origin = null; previous = null }
}

/** Pull the amount down, then release it into the recipient's picture. */
@Composable
fun SlingshotSendMoney(
    amount: Double = 4.5,
    symbol: String = "$",
    recipient: String = "Dave",
    initials: String? = null,
    hint: String = "Swipe  down",
    armedHint: String = "Release to send",
    sentLabel: String = "Sent",
    doneLabel: String = "Done",
    autoPlay: Boolean = false,
    colors: List<String> = SATIN,
    heatColors: List<String> = HEAT,
    glowColor: String = GLOW,
    avatarColor: String = AVATAR_FILL,
    avatarInk: String = AVATAR_INK,
    successColor: String = SUCCESS,
    onSend: ((Double) -> Unit)? = null,
    onSent: ((Double) -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val reduced by rememberUpdatedState(still)
    val currentAmount by rememberUpdatedState(amount)
    val sendCallback by rememberUpdatedState(onSend)
    val sentCallback by rememberUpdatedState(onSent)
    val doneCallback by rememberUpdatedState(onDone)
    val view = LocalView.current
    val displayDensity = LocalDensity.current
    val touchSlop = remember(view) { ViewConfiguration.get(view.context).scaledTouchSlop.toFloat() }
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    var stage by remember { mutableStateOf(Stage.Idle) }
    var autoPressed by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var aimX by remember { mutableFloatStateOf(0f) }
    var aimY by remember { mutableFloatStateOf(0f) }
    var flown by remember { mutableStateOf(false) }
    var byHand by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    val frameTime = remember { FrameTime() }
    val amountShown = remember { AnimatedValue(1f, frameTime) }
    val hintShown = remember { AnimatedValue(1f, frameTime) }
    val avatarShown = remember { AnimatedValue(1f, frameTime) }
    val caught = remember { AnimatedValue(0f, frameTime) }
    val check = remember { AnimatedValue(0f, frameTime) }
    val tick = remember { AnimatedValue(0f, frameTime) }
    val sentShown = remember { AnimatedValue(0f, frameTime) }
    val pill = remember { AnimatedValue(0f, frameTime) }
    val corners = remember { AnimatedValue(1f, frameTime) }
    val wording = remember { Motion(0f) }
    val heat = remember { Motion(0f) }
    val amountX = remember { List(AMOUNT_SPRINGS.size) { Motion(0f) } }
    val amountY = remember { List(AMOUNT_SPRINGS.size) { Motion(0f) } }
    val hintX = remember { List(HINT_SPRINGS.size) { Motion(0f) } }
    val hintY = remember { List(HINT_SPRINGS.size) { Motion(0f) } }
    val clock = remember { LoopClock() }
    val shown = money(symbol, amount)
    val letters = initials ?: recipient.take(1).uppercase(Locale.ROOT)
    val rest = remember(colors) { (if (colors.size == STOPS.size) colors else SATIN).map(::channels) }
    val hot = remember(heatColors) { (if (heatColors.size == STOPS.size) heatColors else HEAT).map(::channels) }
    BoxWithConstraints(modifier.fillMaxSize().background(color(SATIN[5]))) {
        val height = maxHeight.value
        val avatarTop = safe.calculateTopPadding().value + AVATAR_TOP
        val amountAt = height * AMOUNT_AT
        val hintAt = height * HINT_AT
        val travel = height * AMOUNT_TRAVEL
        val reach = travel / FINGER_RATIO
        val rise = amountAt - (avatarTop + AVATAR / 2f)
        val rowBottom = max(safe.calculateBottomPadding().value + 12f, height * (1f - BOTTOM_ROW_AT) - ROUND / 2f)
        val latestTravel by rememberUpdatedState(travel)
        val latestReach by rememberUpdatedState(reach)
        val latestRise by rememberUpdatedState(rise)
        fun touched() { autoPressed = false; touches++ }
        fun haptic(kind: String) {
            view.performHapticFeedback(when (kind) { "sent" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY; "arm" -> HapticFeedbackConstants.LONG_PRESS; else -> HapticFeedbackConstants.VIRTUAL_KEY })
        }
        fun pullTo(dx: Float, dy: Float) {
            if (flown) return
            aimX = (dx * SIDE_RATIO).coerceIn(-SIDE_MAX, SIDE_MAX)
            aimY = pullFor(dy, latestTravel)
        }
        fun fire() {
            if (flown) return
            flown = true; aimX = 0f
            if (reduced) amountShown.time(0f, FADE)
            else {
                aimY = -latestRise
                caught.delayed(CATCH_AFTER, AnimationSpec.Sequence(listOf(AnimationSpec.Time(1f, CATCH_IN, QuadOut, reduced), AnimationSpec.Bounce(0f, CATCH_SPRING, reduced))))
            }
            hintShown.time(0f, HINT_OUT)
            // This wrapper is System; its Never-reduced child still fades when the delay is skipped.
            avatarShown.delayed(if (reduced) 0 else CONFIRM_AFTER, AnimationSpec.Time(0f, FADE))
            check.delayed(CONFIRM_AFTER, if (reduced) AnimationSpec.Time(1f, FADE) else AnimationSpec.Bounce(1f, POP))
            tick.delayed(CONFIRM_AFTER + TICK_AFTER, AnimationSpec.Time(1f, TICK_FOR, CubicOut))
            sentShown.delayed(CONFIRM_AFTER + TICK_AFTER, AnimationSpec.Time(1f, FADE))
            pill.delayed(CONFIRM_AFTER, if (reduced) AnimationSpec.Time(1f, FADE) else AnimationSpec.Bounce(1f, PILL_SPRING))
            corners.delayed(0, AnimationSpec.Time(0f, FADE))
            stage = Stage.Sending; sendCallback?.invoke(currentAmount)
        }
        fun letGoOf() { if (!flown) { aimX = 0f; aimY = 0f } }
        fun letGo() { if (aimY >= latestTravel * ARM_AT) fire() else letGoOf() }
        fun done() {
            stage = Stage.Idle; autoPressed = false
            check.time(0f, CHECK_OUT); tick.time(0f, CHECK_OUT); sentShown.time(0f, CHECK_OUT)
            pill.time(0f, PILL_OUT, CubicIn); avatarShown.time(1f, FADE)
            hintShown.delayed(HINT_BACK_AFTER, AnimationSpec.Time(1f, HINT_BACK))
            amountShown.delayed(CHECK_OUT, AnimationSpec.Time(1f, FADE))
            aimX = 0f; aimY = 0f; flown = false
            corners.delayed(PILL_OUT, AnimationSpec.Time(1f, FADE))
        }
        val latestDone by rememberUpdatedState(::done)
        val latestPull by rememberUpdatedState(::pullTo)
        val latestRelease by rememberUpdatedState(::letGo)
        val latestTouched by rememberUpdatedState(::touched)
        val latestFire by rememberUpdatedState(::fire)
        val latestLetGoOf by rememberUpdatedState(::letGoOf)
        LaunchedEffect(autoPlay, touches) {
            if (!autoPlay) return@LaunchedEffect
            fun start() { byHand = false; clock.start() }
            try {
                if (touches == 0) start()
                else {
                    delay((USER_HOLD * 1000).toLong())
                    if (!holding) {
                        while (stage == Stage.Sending) delay(SENDING_POLL_MS)
                        if (stage == Stage.Sent) { latestDone(); delay((RETURN * 1000).toLong()) }
                        start()
                    }
                }
                kotlinx.coroutines.awaitCancellation()
            } finally { clock.stop() }
        }
        LaunchedEffect(Unit) {
            var previousFrame = System.nanoTime()
            var previousArmed: Boolean? = null
            var previousArrived: Boolean? = null
            var lastX = Float.NaN
            var lastY = Float.NaN
            var lastHintY = Float.NaN
            var lastHeat = Float.NaN
            var lastWording = Float.NaN
            while (true) {
                withFrameNanos { now ->
                    frameTime.now = now
                    try {
                        val dt = ((now - previousFrame) / 1e9f).coerceAtLeast(0f); previousFrame = now
                        clock.origin?.let { origin ->
                            val t = ((now - origin) / 1e9f) % LOOP
                            val last = clock.previous
                            val was = if (last == null || last > t) -1f else last
                            if (t >= TEST_AT && t < TEST_UP) latestPull(0f, latestReach * TEST_DEPTH * CubicOut.transform(((t - TEST_AT) / TEST_FOR).coerceIn(0f, 1f)))
                            else if (t >= PULL_AT && t < PULL_UP) {
                                val held = t - PULL_AT - PULL_FOR
                                val depth = if (held < 0f) smooth(t, PULL_AT, PULL_AT + PULL_FOR) else 1f + TREMBLE * sin(held * TREMBLE_SPEED)
                                val side = if (held < 0f) WANDER * smooth(t, PULL_AT, PULL_AT + PULL_FOR) else WANDER * cos(PI.toFloat() * held / (PULL_UP - PULL_AT - PULL_FOR))
                                latestPull(side / SIDE_RATIO, latestReach * depth)
                            }
                            if ((was < TEST_UP && t >= TEST_UP) || (was < PULL_UP && t >= PULL_UP)) latestRelease()
                            if (was < DONE_AT && t >= DONE_AT) autoPressed = true
                            if (was < DONE_AT + PRESS_FOR && t >= DONE_AT + PRESS_FOR) { doneCallback?.invoke(); latestDone() }
                            clock.previous = t
                        }
                        val hintTarget = if (flown) 0f else aimY * HINT_FOLLOW
                        if (aimX != lastX) { amountX.forEachIndexed { i, it -> it.spring(aimX, AMOUNT_SPRINGS[i], reduced) }; hintX.forEachIndexed { i, it -> it.spring(aimX, HINT_SPRINGS[i], reduced) }; lastX = aimX }
                        if (aimY != lastY) { amountY.forEachIndexed { i, it -> it.spring(aimY, AMOUNT_SPRINGS[i], reduced) }; lastY = aimY }
                        if (hintTarget != lastHintY) { hintY.forEachIndexed { i, it -> it.spring(hintTarget, HINT_SPRINGS[i], reduced) }; lastHintY = hintTarget }
                        val armed = !flown && aimY >= latestTravel * ARM_AT
                        if (previousArmed != null && previousArmed != armed && byHand && !flown) haptic(if (armed) "arm" else "disarm")
                        previousArmed = armed
                        val wordingTarget = if (flown || armed) 1f else 0f
                        if (wordingTarget != lastWording) { wording.tween(wordingTarget, WORDING, startingAt = now); lastWording = wordingTarget }
                        val heatTarget = if (flown) 0f else max(0f, aimY) / latestTravel
                        if (heatTarget != lastHeat) { heat.spring(heatTarget, HEAT_SPRING, reduced); lastHeat = heatTarget }
                        (amountX + amountY + hintX + hintY + listOf(wording, heat)).forEach { it.advance(now, dt) }
                        listOf(amountShown, hintShown, avatarShown, caught, check, tick, sentShown, pill, corners).forEach { it.advance(now) }
                        val arrived = check.value > 0f && flown
                        if (arrived && previousArrived == false) { stage = Stage.Sent; if (byHand) haptic("sent"); sentCallback?.invoke(currentAmount) }
                        previousArrived = arrived
                    } finally { frameTime.now = null }
                }
            }
        }
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val h = heat.value.coerceIn(0f, 1f)
            val stops = Array(STOPS.size) { i -> STOPS[i] + (STOPS_PULLED[i] - STOPS[i]) * h to mix(rest[i], hot[i], h) }
            drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = size.height))
            val center = Offset(size.width / 2f, size.height * .1f)
            drawCircle(Brush.radialGradient(listOf(color(glowColor), color(glowColor).copy(alpha = 0f)), center, size.width * .9f), size.width * .9f, center, alpha = GLOW_ALPHA * h)
        }
        val idle = stage == Stage.Idle
        Box(Modifier.fillMaxSize().semantics {
            if (idle) { role = Role.Button; contentDescription = "Send $shown to $recipient. Swipe down and release to send"; onClick { latestTouched(); byHand = true; latestFire(); true } }
        }.pointerInput(idle) {
            if (!idle) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                clock.stop(); latestLetGoOf(); byHand = true; holding = true; latestTouched()
                var origin = down.position
                var active = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (pointer.isConsumed) break
                        if (!pointer.pressed) { if (active && pointer.previousPressed) latestRelease(); break }
                        val translation = pointer.position - origin
                        if (!active && translation.getDistance() >= touchSlop) { active = true; origin = pointer.position }
                        if (active) { val pull = pointer.position - origin; latestPull(pull.x / displayDensity.density, pull.y / displayDensity.density); pointer.consume() }
                    }
                } finally { holding = false; latestTouched() }
            }
        }) {
            val copies = if (still) 1 else AMOUNT_SPRINGS.size
            for (order in (0 until copies).reversed()) Echo(amountX[order], amountY[order], aimY, order, rise, amountShown, amountAt - AMOUNT_LINE / 2f, AMOUNT_LINE) {
                BasicText(shown, style = textStyle(AMOUNT_SIZE, AMOUNT_LINE, FontWeight.Bold, -.6f).copy(fontFeatureSettings = "tnum"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            for (order in (0 until copies).reversed()) Echo(hintX[order], hintY[order], if (flown) 0f else aimY * HINT_FOLLOW, order, 0f, hintShown, hintAt - HINT_LINE / 2f, HINT_LINE) {
                BasicText(hint.uppercase(Locale.ROOT), Modifier.padding(start = HINT_TRACKING.dp).graphicsLayer { alpha = HINT_ALPHA * (1f - wording.value); compositingStrategy = CompositingStrategy.ModulateAlpha }, style = textStyle(HINT_SIZE, HINT_LINE, FontWeight.SemiBold, HINT_TRACKING), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(armedHint.uppercase(Locale.ROOT), Modifier.padding(start = ARMED_TRACKING.dp).graphicsLayer { alpha = ARMED_ALPHA * wording.value; compositingStrategy = CompositingStrategy.ModulateAlpha }, style = textStyle(HINT_SIZE, HINT_LINE, FontWeight.SemiBold, ARMED_TRACKING), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.offset(y = avatarTop.dp).fillMaxWidth().graphicsLayer { alpha = avatarShown.value; scaleX = 1f + CATCH_SCALE * caught.value; scaleY = scaleX; compositingStrategy = CompositingStrategy.ModulateAlpha }.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(letters, color(avatarColor), color(avatarInk))
            BasicText(recipient, Modifier.padding(top = 8.dp), style = textStyle(14f, 18f, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.offset(y = (height - rowBottom - ROUND).dp).fillMaxWidth().height(ROUND.dp).graphicsLayer { alpha = corners.value * (1f - smooth(heat.value, .45f, .8f)) * (if (flown) 0f else 1f); compositingStrategy = CompositingStrategy.ModulateAlpha }) {
            RoundButton("Close", true, idle, Modifier.align(Alignment.CenterStart).offset(x = (ROUND_MARGIN - ROUND_HIT).dp)) { touched(); haptic("press"); onClose?.invoke() }
            RoundButton("Edit amount", false, idle, Modifier.align(Alignment.CenterEnd).offset(x = -(ROUND_MARGIN - ROUND_HIT).dp)) { touched(); haptic("press"); onEdit?.invoke() }
        }
        Column(Modifier.offset(y = (amountAt - CHECK / 2f).dp).fillMaxWidth().semantics { if (stage == Stage.Sent) contentDescription = sentLabel }, horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(CHECK.dp).graphicsLayer { alpha = min(1f, check.value * 4f); scaleX = if (still) 1f else check.value; scaleY = scaleX; compositingStrategy = CompositingStrategy.ModulateAlpha }) {
                surface(0.dp) { _, _, _, _ ->
                    drawCircle(color(successColor), CHECK / 2f, Offset(CHECK / 2f, CHECK / 2f))
                    val measure = PathMeasure().apply { setPath(TickPath, false) }
                    val partial = Path(); measure.getSegment(0f, measure.length * tick.value.coerceIn(0f, 1f), partial, true)
                    drawPath(partial, INK, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            BasicText(sentLabel, Modifier.padding(top = SENT_GAP.dp).graphicsLayer { alpha = sentShown.value; compositingStrategy = CompositingStrategy.ModulateAlpha }.clearAndSetSemantics {}, style = textStyle(14f, 18f, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val pillTop = height - rowBottom - (ROUND - PILL_HEIGHT) / 2f - PILL_HEIGHT
        Box(Modifier.offset(y = pillTop.dp).fillMaxWidth().height(PILL_HEIGHT.dp).graphicsLayer { alpha = min(1f, pill.value * 3f); translationY = if (still) 0f else (1f - pill.value) * (rowBottom + PILL_HEIGHT + 8f) * density; compositingStrategy = CompositingStrategy.ModulateAlpha }, contentAlignment = Alignment.Center) {
            DoneButton(doneLabel, autoPressed, stage == Stage.Sent) { touched(); haptic("press"); onDone?.invoke(); done() }
        }
    }
}

@Composable
private fun Avatar(letters: String, fill: Color, ink: Color) {
    val shadow = remember { SoftRoundRect(Color.Black.copy(alpha = AVATAR_SHADOW_ALPHA), AVATAR_SHADOW_SIGMA) }
    Box(Modifier.size(AVATAR.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().bleed(SHADOW_SPILL.dp)) {
            surface(SHADOW_SPILL.dp) { x, y, _, _ ->
                if (Build.VERSION.SDK_INT >= 28) drawIntoCanvas { shadow.draw(it.nativeCanvas, x, y + AVATAR_SHADOW_Y, AVATAR, AVATAR, AVATAR / 2f) }
                else {
                    // Fixed falloff keeps the older hardware canvas soft without a bitmap allocation.
                    for (i in 6 downTo 1) {
                        val spread = AVATAR_SHADOW_SIGMA * i / 3f
                        drawCircle(Color.Black.copy(alpha = AVATAR_SHADOW_ALPHA * exp(-.5f * (i / 2f).pow(2)) / 3f), AVATAR / 2f + spread, Offset(x + AVATAR / 2f, y + AVATAR / 2f + AVATAR_SHADOW_Y))
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(fill, CircleShape), contentAlignment = Alignment.Center) {
            BasicText(letters, style = textStyle(28f, 34f, FontWeight.Bold, ink = ink))
        }
    }
}

@Composable
private fun RoundButton(label: String, close: Boolean, enabled: Boolean, modifier: Modifier, onPress: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val press = remember { Motion(0f) }
    LaunchedEffect(pressed) { press.tween(if (pressed) 1f else 0f, ROUND_TRANSITION_MS, CssEase) }
    LaunchedEffect(Unit) { var previous = System.nanoTime(); while (true) withFrameNanos { now -> press.advance(now, (now - previous) / 1e9f); previous = now } }
    Box(modifier.wrapContentSize(Alignment.Center, unbounded = true).size((ROUND + ROUND_HIT * 2f).dp).then(if (enabled) Modifier.clickable(interactionSource = interactions, indication = null, role = Role.Button, onClickLabel = label, onClick = onPress).semantics { contentDescription = label } else Modifier.clearAndSetSemantics {}), contentAlignment = Alignment.Center) {
        Box(Modifier.size(ROUND.dp).graphicsLayer { scaleX = 1f - .08f * press.value; scaleY = scaleX }.background(mix(ROUND_FILL, ROUND_PRESSED, press.value), CircleShape), contentAlignment = Alignment.Center) {
            val icon = if (close) 30f else 22f
            Canvas(Modifier.size(icon.dp).clearAndSetSemantics {}) {
                surface(0.dp) { _, _, _, _ ->
                    val path = Path()
                    if (close) { path.moveTo(7f, 7f); path.lineTo(23f, 23f); path.moveTo(23f, 7f); path.lineTo(7f, 23f) }
                    else { path.moveTo(4f, 14f); path.lineTo(14f, 4f); path.lineTo(18f, 8f); path.lineTo(8f, 18f); path.lineTo(3f, 19f); path.close(); path.moveTo(12f, 6f); path.lineTo(16f, 10f) }
                    drawPath(path, INK, style = Stroke(if (close) 2.3f else 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

@Composable
private fun DoneButton(label: String, autoPressed: Boolean, enabled: Boolean, onPress: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val press = remember { Motion(0f) }
    LaunchedEffect(pressed, autoPressed) { press.tween(if (pressed || autoPressed) 1f else 0f, PILL_TRANSITION_MS, CssEase) }
    LaunchedEffect(Unit) { var previous = System.nanoTime(); while (true) withFrameNanos { now -> press.advance(now, (now - previous) / 1e9f); previous = now } }
    Box(Modifier.size(PILL_WIDTH.dp, PILL_HEIGHT.dp).then(if (enabled) Modifier.clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onPress) else Modifier.clearAndSetSemantics {}), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = 1f - .04f * press.value; scaleY = scaleX }.background(mix(PILL_FILL, PILL_PRESSED, press.value), RoundedCornerShape(PILL_HEIGHT.dp / 2f)), contentAlignment = Alignment.Center) {
            BasicText(label, style = textStyle(16f, 20f, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
