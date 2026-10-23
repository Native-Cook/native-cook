package com.ngocdevv.nativecook.blocks.forms.expandingfilterrows

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt


/** A friend disc uses either initials or the source's drawn head and shoulders. */
data class Friend(val color: String, val initials: String? = null)
sealed interface FilterTime { data class Single(val half: Int) : FilterTime; data class Range(val start: Int, val end: Int) : FilterTime }
data class Filters(val date: String, val friends: Boolean, val time: FilterTime, val genres: List<String>, val price: String?)
private val DATES = listOf("18 Dec 2024", "19 Dec 2024", "20 Dec 2024", "21 Dec 2024")
private val FRIENDS = listOf(Friend("#4d8df0", "MT"), Friend("#2fa58f"), Friend("#d98a4e"))
private val GENRES = listOf("Afrobeat", "Alternative", "Bluegrass", "Blues", "Chant", "Classical", "Country", "Dancehall", "Disco", "EDM", "Electronic", "Experimental", "Folk", "Funk", "Gospel", "Hip-Hop", "House", "Indie", "Jazz", "Latin", "Metal", "Pop", "Reggae", "Soul")
private val CHOSEN = listOf("Afrobeat", "Bluegrass", "Chant", "EDM", "Gospel", "Jazz", "Latin", "Soul")
private val PRICES = listOf("All price", "Free", "Under $30", "Under $60")
private const val BACKDROP = "#000000"
private const val SHEET = "#121214"
private const val ROW_FILL = "#1c1c1f"
private const val ROW_PRESSED = "#26262a"
private const val WELL = "#161618"
private const val INK = "#ffffff"
private const val MUTE = "#8e8e93"
private const val BLUE = "#1f6bff"
private const val SIDE = 20f
private const val ROW = 60f
private const val ROW_GAP = 16f
private const val ROW_RADIUS = 16f
private const val WELL_INSET = 6f
private const val WELL_RADIUS = ROW_RADIUS - 4f
private const val TIME_EXTRA = TIME_PANEL + WELL_INSET
private const val GENRE_EXTRA = GENRE_PANEL + WELL_INSET
private const val AVATAR = 26f
private const val AVATAR_PITCH = 34f
private const val BUTTON = 52f
private const val BUTTON_WIDTH = 158f
private const val FADE = 44f
private const val CLEAR = 8f
private const val RIGHT_START = 3
private const val OPEN_MASS = 1.0
private const val OPEN_STIFFNESS = 115.0
private const val OPEN_DAMPING = 14.5
private const val RANGE_MS = 380.0
private const val TICKER_MASS = 1.0
private const val TICKER_STIFFNESS = 150.0
private const val TICKER_DAMPING = 16.0
private const val PRESS_MS = 160L
const val LOOP = 10.5
private const val OPEN_TIME_AT = .8
private const val RANGE_AT = 1.9
private const val TURN_TIME_AT = 2.6
private const val TURN_TIME_BY = -5
private const val TURN_TIME_FOR = 1.2
private const val OPEN_GENRES_AT = 4.4
private const val TURN_GENRES_AT = 5.4
private const val TURN_GENRES_BY = 5
private const val TURN_GENRES_FOR = 1.0
private const val PICK_AT = 6.9
private const val CLOSE_AT = 7.9
private const val RESET_AT = 8.9
private const val TICK = LOOP / 3.0
private const val USER_HOLD = 3.0
private const val RESUME_MS = 900L
private const val OPEN_REDUCED_MS = 160.0
private const val ROW_PRESS_IN = 60.0
private const val ROW_PRESS_OUT = 260.0
private const val VALUE_ENTER = 220
private const val VALUE_EXIT = 140
private const val COUNT_ENTER = 220
private const val COUNT_EXIT = 160
private const val BUTTON_PRESS_MS = 120.0
private const val SCREEN_TOP = 8f
private const val SHEET_RADIUS = 30f
private const val GRABBER_TOP = 8f
private const val GRABBER_WIDTH = 36f
private const val GRABBER_HEIGHT = 5f
private const val GRABBER_INK = "#3a3a3e"
private const val HEADER_TOP = 9f
private const val HEADER_HEIGHT = 44f
private const val TITLE_SIZE = 17f
private const val TITLE_LINE = 22f
private const val CLOSE = 30f
private const val CLOSE_TOP = 7f
private const val CLOSE_SLOP = 8f
private const val CLOSE_ICON = 15f
private const val CLOSE_FILL = "#2a2a2d"
private const val CLOSE_INK = "#b8b8be"
private const val LIST_TOP = 30f
private const val HEAD_PAD = 18f
private const val ROW_ICON = 18f
private const val LABEL_LEFT = 10f
private const val LABEL_SIZE = 16f
private const val LABEL_LINE = 21f
private const val VALUE_LEFT = .4f
private const val FRIENDS_WIDTH = 96f
private const val AVATAR_LEFT = 14f
private const val INITIALS_SIZE = 10f
private const val INITIALS_LINE = 13f
private const val HEAD_TOP = 6f
private const val HEAD_SIZE = 9f
private const val SHOULDER_TOP = 17f
private const val SHOULDER_SIZE = 18f
private const val FIGURE_ALPHA = .9f
private const val WELL_DROP = 12f
private const val BOTTOM_MIN = 12f
private const val BOTTOM_EXTRA = 8f
private const val RESET_SLOP = 12f
private const val COUNTER_WIDTH = 11f
private const val COUNTER_LINE = 21f
private const val COUNTER_TRAVEL = 25f
private const val BUTTON_SCALE = .97
private const val WITH_ALPHA = .62f
private const val PRESS_RETENTION = 20f
private const val PRESS_RETENTION_BOTTOM = 30f
private const val MIN_PRESS_DURATION_MS = 130L
private const val SPRING_FRAME_CAP = .064
private const val SPRING_ENERGY_THRESHOLD = 6e-9
internal val ExpandingFilterRowsQuad = Easing { if (it < .5f) 2f * it * it else 1f - (-2f * it + 2f).pow(2) / 2f }
private val CubicOut = Easing { 1f - (1f - it).pow(3) }
private val CubicInOut = Easing { if (it < .5f) 4f * it * it * it else 1f - (-2f * it + 2f).pow(3) / 2f }
internal val ExpandingFilterRowsCss = CubicBezierEasing(.25f, .1f, .25f, 1f)
internal fun expandingFilterRowsRound(value: Double): Double { if (!value.isFinite() || value == 0.0) return value; val lower = floor(value); return if (value - lower < .5) lower else lower + 1.0 }
internal fun expandingFilterRowsColor(value: String): Color = ExpandingFilterRowsCSS.color(value)

private object ExpandingFilterRowsCSS {
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

internal object ExpandingFilterRowsFrame { var now: Long? = null }

private const val FILTER_ICON_UNITS = 512f
private const val FILTER_ICON_CENTER = 194f
private val FILTER_ICON_PATHS = mapOf(
    "calendar" to "M43 320Q43 2 43 -5Q47 -36 74 -41Q82 -43 107 -43H405Q430 -43 438 -41Q465 -36 469 -5Q469 2 469 21V320Q469 344 468 353Q462 380 432 383Q425 384 405 384Q87 384 80 383Q50 380 44 353Q43 344 43 320ZM86 0Q85 18 85.0 177.0Q85 336 86 341Q94 341 405 341Q419 341 426 341Q427 333 427 21Q427 7 426 1Q418 0 107 0Q93 0 86 0ZM363 405Q363 414 356.5 420.5Q350 427 341.5 427.0Q333 427 326.5 420.5Q320 414 320 405V320Q320 311 326.5 305.0Q333 299 341.5 299.0Q350 299 356.5 305.0Q363 311 363 320ZM192 405Q192 414 185.5 420.5Q179 427 170.5 427.0Q162 427 155.5 420.5Q149 414 149 405V320Q149 311 155.5 305.0Q162 299 170.5 299.0Q179 299 185.5 305.0Q192 311 192 320ZM64 256Q55 256 49.0 249.5Q43 243 43.0 234.5Q43 226 49.0 219.5Q55 213 64 213H448Q457 213 463.0 219.5Q469 226 469.0 234.5Q469 243 463.0 249.5Q457 256 448 256Z",
    "users" to "M341 0Q341 -9 347.5 -15.0Q354 -21 362.5 -21.0Q371 -21 377.5 -15.0Q384 -9 384 0V43Q384 87 352.5 118.0Q321 149 277 149H107Q63 149 31.5 118.0Q0 87 0 43V0Q0 -9 6.5 -15.0Q13 -21 21.5 -21.0Q30 -21 36.5 -15.0Q43 -9 43 0V43Q43 69 61.5 88.0Q80 107 107 107H277Q304 107 322.5 88.0Q341 69 341 43ZM146.5 253.5Q128 272 128.0 298.5Q128 325 146.5 344.0Q165 363 192.0 363.0Q219 363 237.5 344.0Q256 325 256.0 298.5Q256 272 237.5 253.5Q219 235 192.0 235.0Q165 235 146.5 253.5ZM116.5 374.0Q85 343 85.0 298.5Q85 254 116.5 223.0Q148 192 192.0 192.0Q236 192 267.5 223.0Q299 254 299.0 298.5Q299 343 267.5 374.0Q236 405 192.0 405.0Q148 405 116.5 374.0ZM512 0V43Q512 80 489.5 108.5Q467 137 432 146Q422 148 413.5 142.0Q405 136 405 125Q405 109 421 105Q442 99 455.5 82.0Q469 65 469 43V0Q469 -9 475.5 -15.0Q482 -21 490.5 -21.0Q499 -21 505.5 -15.0Q512 -9 512 0ZM347 402Q337 404 328.5 398.0Q320 392 320 381Q320 365 336 361Q357 355 370.5 338.0Q384 321 384.0 298.5Q384 276 370.5 259.0Q357 242 336 237Q320 232 320 216Q320 205 328.5 199.0Q337 193 347 195Q382 204 404.5 233.0Q427 262 427.0 298.5Q427 335 404.5 364.0Q382 393 347 402Z",
    "clock" to "M120.0 56.0Q64 112 64.0 192.0Q64 272 120.0 328.0Q176 384 256.0 384.0Q336 384 392.0 328.0Q448 272 448.0 192.0Q448 112 392.0 56.0Q336 0 256.0 0.0Q176 0 120.0 56.0ZM90.0 358.0Q21 289 21.0 192.0Q21 95 90.0 26.0Q159 -43 256.0 -43.0Q353 -43 422.0 26.0Q491 95 491.0 192.0Q491 289 422.0 358.0Q353 427 256.0 427.0Q159 427 90.0 358.0ZM277 320Q277 329 271.0 335.0Q265 341 256.0 341.0Q247 341 241.0 335.0Q235 329 235 320V192Q235 179 246 173L332 130Q342 125 352.5 131.0Q363 137 363 149Q363 163 351 168L277 205Z",
    "music" to "M171 64Q171 55 177.0 49.0Q183 43 192.0 43.0Q201 43 207.0 49.0Q213 55 213 64V323L427 359V107Q427 98 433.0 91.5Q439 85 448.0 85.0Q457 85 463.0 91.5Q469 98 469 107V384Q469 394 461.5 400.5Q454 407 444 405L188 362Q181 361 176.0 355.0Q171 349 171 341ZM97.5 33.5Q85 46 85.0 64.0Q85 82 97.5 94.5Q110 107 128.0 107.0Q146 107 158.5 94.5Q171 82 171.0 64.0Q171 46 158.5 33.5Q146 21 128.0 21.0Q110 21 97.5 33.5ZM68.0 124.0Q43 99 43.0 64.0Q43 29 68.0 4.0Q93 -21 128.0 -21.0Q163 -21 188.0 4.0Q213 29 213.0 64.0Q213 99 188.0 124.0Q163 149 128.0 149.0Q93 149 68.0 124.0ZM353.5 76.5Q341 89 341.0 106.5Q341 124 353.5 136.5Q366 149 384.0 149.0Q402 149 414.5 136.5Q427 124 427.0 106.5Q427 89 414.5 76.5Q402 64 384.0 64.0Q366 64 353.5 76.5ZM324.0 167.0Q299 142 299.0 106.5Q299 71 324.0 46.0Q349 21 384.0 21.0Q419 21 444.0 46.0Q469 71 469.0 106.5Q469 142 444.0 167.0Q419 192 384.0 192.0Q349 192 324.0 167.0Z",
    "dollar-sign" to "M277 427Q277 435 271.0 441.5Q265 448 256.0 448.0Q247 448 241.0 441.5Q235 435 235 427V-43Q235 -51 241.0 -57.5Q247 -64 256.0 -64.0Q265 -64 271.0 -57.5Q277 -51 277 -43ZM363 320Q371 320 377.5 326.5Q384 333 384.0 341.5Q384 350 377.5 356.5Q371 363 363 363H203Q163 363 135.0 334.5Q107 306 107.0 266.5Q107 227 135.0 199.0Q163 171 203 171H309Q331 171 347.0 155.0Q363 139 363.0 117.0Q363 95 347.0 79.5Q331 64 309 64H128Q119 64 113.0 57.5Q107 51 107.0 42.5Q107 34 113.0 27.5Q119 21 128 21H309Q349 21 377.0 49.5Q405 78 405.0 117.5Q405 157 377.0 185.0Q349 213 309 213H203Q181 213 165.0 229.0Q149 245 149.0 267.0Q149 289 165.0 304.5Q181 320 203 320Z",
    "x" to "M399 305Q409 315 403.5 328.0Q398 341 384 341Q375 341 369 335L113 79Q103 69 108.5 56.0Q114 43 128 43Q137 43 143 49ZM143 335Q133 345 120.0 339.5Q107 334 107 320Q107 311 113 305L369 49Q379 39 392.0 44.5Q405 50 405 64Q405 73 399 79Z"
)
private val FILTER_ICON_CACHE = mutableMapOf<String, Path>()

@Composable
private fun FilterIcon(name: String, size: Float, ink: Color) {
    val source = FILTER_ICON_PATHS[name] ?: return
    val path = remember(name) {
        FILTER_ICON_CACHE.getOrPut(name) { androidx.compose.ui.graphics.vector.PathParser().parsePathString(source).toPath() }
    }
    Canvas(Modifier.size(size.dp).clearAndSetSemantics {}) {
        surface(0.dp) { _, _, _, _ ->
            withTransform({
                translate(0f, size / 2f + FILTER_ICON_CENTER * size / FILTER_ICON_UNITS)
                scale(size / FILTER_ICON_UNITS, -size / FILTER_ICON_UNITS, Offset.Zero)
            }) { drawPath(path, ink) }
        }
    }
}


/** Mutable picker values retain the source's Never-reduced frame clocks. */
class ExpandingFilterRowsValue(initial: Double) : State<Double> {
    private var current by mutableStateOf(initial)
    override val value get() = current
    var ownershipEpoch by mutableIntStateOf(0)
        private set
    private val observers = mutableListOf<(Double) -> Unit>()
    private var motion: Motion? = null
    fun resetOwnership() { ownershipEpoch++ }
    fun observe(listener: (Double) -> Unit): () -> Unit { observers += listener; return { observers -= listener } }
    private fun publish(next: Double) { if (current != next) { current = next; observers.toList().forEach { it(next) } } }
    fun cancel() { motion = null }
    fun set(next: Double) { cancel(); publish(next) }
    fun time(to: Double, durationMs: Double, easing: Easing = ExpandingFilterRowsQuad) {
        if (current == to) { set(to); return }
        val now = ExpandingFilterRowsFrame.now ?: System.nanoTime()
        val old = motion as? Timing
        val origin = if (old?.to == to && old.began != 0L) old.began else now
        val from = if (old?.to == to && old.began != 0L) old.from else current
        motion = Timing(from, to, durationMs, easing, origin)
        advance(now)
    }
    fun spring(to: Double, mass: Double = 1.0, stiffness: Double = 190.0, damping: Double = 24.0, velocity: Double = 0.0) {
        if (current == to) { set(to); return }
        val now = ExpandingFilterRowsFrame.now ?: System.nanoTime()
        val old = motion as? SpringMotion
        motion = SpringMotion(current, to, mass, stiffness, damping, velocity, old, now)
        advance(now)
    }
    fun advance(nowNanos: Long) {
        val animation = motion ?: return
        animation.advance(nowNanos); publish(animation.value)
        if (motion === animation && animation.done) motion = null
    }
    private interface Motion { val value: Double; val done: Boolean; fun advance(now: Long) }
    private class Timing(val from: Double, val to: Double, private val duration: Double, private val ease: Easing, var began: Long) : Motion {
        override var value = from
        override var done = false
        override fun advance(now: Long) {
            if (done) return
            val u = if (duration <= 0.0) 1.0 else ((now - began) / (duration * 1e6)).coerceIn(0.0, 1.0)
            value = from + (to - from) * ease.transform(u.toFloat()).toDouble()
            if (u >= 1.0) { value = to; done = true; began = 0L }
        }
    }
    private class SpringMotion(initial: Double, val to: Double, mass: Double, stiffness: Double, damping: Double, initialVelocity: Double, previous: SpringMotion?, now: Long) : Motion {
        private val omega = sqrt(stiffness / mass)
        private val zeta = damping / (2.0 * sqrt(stiffness * mass))
        private val decay = damping / (2.0 * mass)
        private val same = previous?.to == to
        private val originDisplacement: Double = if (same) previous!!.originDisplacement else initial - to
        private val energy = max(1e-20, stiffness * originDisplacement * originDisplacement + mass * initialVelocity.pow(2))
        private val k = stiffness; private val m = mass
        private var displacement: Double = initial - to
        private var last: Long = previous?.last ?: now
        private var velocity: Double = (initialVelocity + (previous?.velocity ?: 0.0)).let { if ((to - initial) * it < 0.0) 0.0 else it }
        override var value = initial
        override var done = false
        override fun advance(now: Long) {
            if (done) return
            val dt = ((now - last).coerceAtLeast(0L) / 1e9).coerceAtMost(SPRING_FRAME_CAP); last = now
            val x = displacement; val v = velocity
            val fall = exp(-if (zeta < 1.0) decay * dt else omega * dt)
            if (zeta < 1.0) {
                val wd = omega * sqrt(1.0 - zeta * zeta); val c = cos(wd * dt); val s = sin(wd * dt)
                val b = (v + decay * x) / wd
                displacement = fall * (x * c + b * s)
                velocity = fall * (v * c - (decay * v + omega * omega * x) / wd * s)
            } else {
                displacement = fall * (x + (v + omega * x) * dt)
                velocity = fall * (v - omega * (v + omega * x) * dt)
            }
            value = to + displacement
            if ((k * displacement * displacement + m * velocity * velocity) / energy < SPRING_ENERGY_THRESHOLD) { value = to; velocity = 0.0; done = true }
        }
    }
}

private enum class Expanded { Time, Genres }
private enum class Pressed { Time, Genres, Reset }
private data class ValueCopy(val text: String, val dim: Boolean = false, val tail: String? = null, val friends: Boolean = false, val friendsData: List<Friend> = emptyList()) {
    val key get() = if (friends) "friends" else "text:$text${tail.orEmpty()}"
}
private class ValueFade(initial: ValueCopy, initialEnter: Boolean = false, private val count: Boolean = false) {
    class Entry(val id: Int, initial: ValueCopy, val began: Long, val duration: Int, val leaving: Boolean) { var copy by mutableStateOf(initial); var progress by mutableStateOf(if (duration == 0) 1f else 0f) }
    private var serial = 1
    var entries by mutableStateOf(listOf(Entry(0, initial, System.nanoTime(), if (initialEnter) COUNT_ENTER else 0, false)))
        private set
    fun update(copy: ValueCopy, still: Boolean, now: Long) {
        val old = entries.lastOrNull { !it.leaving } ?: return
        if (old.copy.key == copy.key) { old.copy = copy; return }
        val outgoing = if (still) emptyList() else entries.filter { it.leaving } + Entry(old.id, old.copy, now, if (count) COUNT_EXIT else VALUE_EXIT, true)
        entries = outgoing + Entry(serial++, copy, now, if (still) 0 else if (count) COUNT_ENTER else VALUE_ENTER, false)
    }
    fun advance(now: Long) {
        entries.forEach { it.progress = if (it.duration == 0) 1f else ExpandingFilterRowsQuad.transform(((now - it.began) / (it.duration * 1e6)).coerceIn(0.0, 1.0).toFloat()) }
        if (entries.any { it.leaving && it.progress >= 1f }) entries = entries.filterNot { it.leaving && it.progress >= 1f }
    }
}

/** Rows unfold their pickers in place; the same paths drive hand input and the tour. */
@Composable
fun ExpandingFilterRows(
    title: String = "Filters", dates: List<String> = DATES, friends: List<Friend> = FRIENDS, friendsCount: String = "12+", time: Double = 38.0,
    genres: List<String> = GENRES, chosenGenres: List<String> = CHOSEN, prices: List<String> = PRICES,
    datesLabel: String = "Dates", friendsLabel: String = "Friends attend", anyFriendsLabel: String = "Anyone", timeLabel: String = "Time",
    singleTimeLabel: String = "Single time", timeRangeLabel: String = "Time range", genresLabel: String = "Genres", allGenresLabel: String = "All genres",
    noGenresLabel: String = "Tap a genre", priceLabel: String = "Price", resetLabel: String = "Reset all", continueLabel: String = "Continue", withLabel: String = "with",
    autoPlay: Boolean = false, onChange: ((Filters) -> Unit)? = null, onContinue: ((Filters) -> Unit)? = null, onClose: (() -> Unit)? = null, modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion(); val reduced by rememberUpdatedState(still)
    val view = LocalView.current; val density = LocalDensity.current; val measurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope(); val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val firstTime = expandingFilterRowsRound(time).coerceIn(0.0, (TIMES - 1).toDouble()).toInt()
    val currentFirst by rememberUpdatedState(firstTime); val originalChosen by rememberUpdatedState(chosenGenres)
    val currentDates by rememberUpdatedState(dates); val currentPrices by rememberUpdatedState(prices)
    val changeCallback by rememberUpdatedState(onChange); val continueCallback by rememberUpdatedState(onContinue); val closeCallback by rememberUpdatedState(onClose)
    var expanded by remember { mutableStateOf<Expanded?>(null) }
    var dateIndex by remember { mutableIntStateOf(0) }; var withFriends by remember { mutableStateOf(true) }; var priceIndex by remember { mutableIntStateOf(0) }
    var ranged by remember { mutableStateOf(false) }; var startTime by remember { mutableIntStateOf(firstTime) }; var endTime by remember { mutableIntStateOf(firstTime) }
    var chosen by remember { mutableStateOf(chosenGenres, referentialEqualityPolicy()) }; var chosenVersion by remember { mutableIntStateOf(0) }
    var moving by remember { mutableStateOf<String?>(null) }; var fresh by remember { mutableStateOf<String?>(null) }
    var pressed by remember { mutableStateOf<Pressed?>(null) }; var touches by remember { mutableIntStateOf(0) }
    val timeShown = remember { ExpandingFilterRowsValue(0.0) }; val genresShown = remember { ExpandingFilterRowsValue(0.0) }
    val start = remember { ExpandingFilterRowsValue(firstTime.toDouble()) }; val end = remember { ExpandingFilterRowsValue(firstTime.toDouble()) }
    val range = remember { ExpandingFilterRowsValue(0.0) }; val leftPosition = remember { ExpandingFilterRowsValue(0.0) }; val rightPosition = remember { ExpandingFilterRowsValue(RIGHT_START.toDouble()) }
    val turn = remember { ExpandingFilterRowsValue(0.0) }; val buttonPress = remember { ExpandingFilterRowsValue(0.0) }
    val others = genres.filter { !chosen.contains(it) }
    val price = if (priceIndex == 0) null else prices.getOrNull(priceIndex)
    val count = 2 + (if (withFriends) 1 else 0) + (if (chosen.isNotEmpty()) 1 else 0) + (if (!price.isNullOrEmpty()) 1 else 0)
    val filters = Filters(dates.getOrNull(dateIndex).orEmpty(), withFriends, if (ranged) FilterTime.Range(startTime, endTime) else FilterTime.Single(startTime), chosen, price)
    val currentFilters by rememberUpdatedState(filters)
    LaunchedEffect(dateIndex, withFriends, priceIndex, ranged, startTime, endTime, chosenVersion) { changeCallback?.invoke(currentFilters) }
    fun touched() { touches++ }
    fun light() { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    fun select() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun setChosen(next: List<String>) { if (chosen !== next) { chosen = next; chosenVersion++ } }
    fun expand(next: Expanded?) {
        expanded = next
        listOf(timeShown to (next == Expanded.Time), genresShown to (next == Expanded.Genres)).forEach { (value, show) ->
            if (reduced) value.time(if (show) 1.0 else 0.0, OPEN_REDUCED_MS) else value.spring(if (show) 1.0 else 0.0, OPEN_MASS, OPEN_STIFFNESS, OPEN_DAMPING)
        }
    }
    fun toggle(row: Expanded) { expand(if (expanded == row) null else row) }
    fun silenceWheels() { start.resetOwnership(); end.resetOwnership() }
    fun setMode(next: Boolean, automatic: Boolean = false) {
        if (automatic) silenceWheels()
        ranged = next
        if (next) { end.set(start.value); endTime = expandingFilterRowsRound(start.value).toInt() }
        range.time(if (next) 1.0 else 0.0, RANGE_MS, CubicOut)
    }
    fun onStart(half: Int, byHand: Boolean) { startTime = half; if (byHand) select(); if (ranged && half > expandingFilterRowsRound(end.value)) end.spring(half.toDouble()) }
    fun onEnd(half: Int, byHand: Boolean) { endTime = half; if (byHand) select(); if (ranged && half < expandingFilterRowsRound(start.value)) start.spring(half.toDouble()) }
    fun move(name: String, pick: Boolean) {
        if (moving != null) return
        moving = name
        val captured = chosen
        // Reset and touch leave this transfer alive, as in the source.
        scope.launch {
            delay(PICK_MS)
            val at = min(captured.size, expandingFilterRowsRound(leftPosition.value).toInt() + 1)
            val slice = if (at < 0) max(0, captured.size + at) else at
            setChosen(if (pick) captured.take(slice) + name + captured.drop(slice) else captured.filter { it != name })
            fresh = name; moving = null
        }
    }
    fun reset(automatic: Boolean = false) {
        if (automatic) silenceWheels()
        expand(null); dateIndex = 0; withFriends = true; priceIndex = 0; ranged = false
        startTime = currentFirst; endTime = currentFirst; setChosen(originalChosen); fresh = null
        range.time(0.0, RANGE_MS); start.set(currentFirst.toDouble()); end.set(currentFirst.toDouble()); leftPosition.set(0.0); rightPosition.set(RIGHT_START.toDouble())
    }
    val latestTouch by rememberUpdatedState(::touched); val latestToggle by rememberUpdatedState(::toggle); val latestMode by rememberUpdatedState(::setMode)
    val latestStart by rememberUpdatedState(::onStart); val latestEnd by rememberUpdatedState(::onEnd); val latestMove by rememberUpdatedState(::move); val latestReset by rememberUpdatedState(::reset)
    val latestOthers by rememberUpdatedState(others); val latestLight by rememberUpdatedState(::light); val latestSelect by rememberUpdatedState(::select)
    val timeValue = if (ranged && endTime != startTime) "${com.ngocdevv.nativecook.blocks.forms.expandingfilterrows.timeLabel(startTime)} – ${com.ngocdevv.nativecook.blocks.forms.expandingfilterrows.timeLabel(endTime)}" else com.ngocdevv.nativecook.blocks.forms.expandingfilterrows.timeLabel(startTime)
    val genreValue = if (chosen.isEmpty()) allGenresLabel else chosen.take(2).joinToString(", ")
    val genreTail = if (chosen.size > 2) " +${chosen.size - 2}" else null
    val copies = listOf(ValueCopy(dates.getOrNull(dateIndex).orEmpty()), ValueCopy(if (withFriends) friendsCount else anyFriendsLabel, !withFriends, friends = withFriends, friendsData = friends), ValueCopy(timeValue), ValueCopy(genreValue, chosen.isEmpty(), genreTail), ValueCopy(prices.getOrNull(priceIndex).orEmpty(), priceIndex == 0))
    val fades = remember { copies.map { ValueFade(it) } }
    val countFade = remember { ValueFade(ValueCopy(count.toString()), !still, true) }
    LaunchedEffect(copies, count, still) { val now = System.nanoTime(); copies.forEachIndexed { i, copy -> fades[i].update(copy, reduced, now) }; countFade.update(ValueCopy(count.toString()), reduced, now) }
    DisposableEffect(view) {
        val controller = view.context.filterActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }; val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    LaunchedEffect(autoPlay, touches, firstTime) {
        if (!autoPlay) return@LaunchedEffect
        try {
            if (touches > 0) { delay((USER_HOLD * 1000.0).toLong()); latestReset(true); delay(RESUME_MS) }
            var origin = System.nanoTime()
            while (true) {
                val events = listOf<Pair<Double, () -> Unit>>(
                    OPEN_TIME_AT to { pressed = Pressed.Time }, (OPEN_TIME_AT + PRESS_MS / 1000.0) to { pressed = null; latestToggle(Expanded.Time) },
                    RANGE_AT to { latestMode(true, true) }, TURN_TIME_AT to { silenceWheels(); start.time(firstTime + TURN_TIME_BY.toDouble(), TURN_TIME_FOR * 1000.0, CubicInOut) },
                    OPEN_GENRES_AT to { pressed = Pressed.Genres }, (OPEN_GENRES_AT + PRESS_MS / 1000.0) to { pressed = null; latestToggle(Expanded.Genres) },
                    TURN_GENRES_AT to { rightPosition.time(RIGHT_START + TURN_GENRES_BY.toDouble(), TURN_GENRES_FOR * 1000.0, CubicInOut) },
                    PICK_AT to { latestOthers.getOrNull(RIGHT_START + TURN_GENRES_BY)?.let { latestMove(it, true) } },
                    CLOSE_AT to { pressed = Pressed.Genres }, (CLOSE_AT + PRESS_MS / 1000.0) to { pressed = null; latestToggle(Expanded.Genres) },
                    RESET_AT to { pressed = Pressed.Reset }, (RESET_AT + PRESS_MS / 1000.0) to { pressed = null; latestReset(true) },
                )
                for ((at, run) in events) { delay(max(0L, (origin + (at * 1e9).toLong() - System.nanoTime()) / 1_000_000)); run() }
                delay(max(0L, (origin + (LOOP * 1e9).toLong() - System.nanoTime()) / 1_000_000)); origin += (LOOP * 1e9).toLong()
            }
        } finally { pressed = null }
    }
    LaunchedEffect(still, friends.size) {
        if (still || friends.size < 2) return@LaunchedEffect
        val origin = System.nanoTime(); var number = 0
        while (true) { number++; delay(max(0L, (origin + (number * TICK * 1e9).toLong() - System.nanoTime()) / 1_000_000)); turn.spring(number.toDouble(), TICKER_MASS, TICKER_STIFFNESS, TICKER_DAMPING) }
    }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { now ->
            ExpandingFilterRowsFrame.now = now
            try { listOf(timeShown, genresShown, start, end, range, leftPosition, rightPosition, turn, buttonPress).forEach { it.advance(now) }; fades.forEach { it.advance(now) }; countFade.advance(now) }
            finally { ExpandingFilterRowsFrame.now = null }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(expandingFilterRowsColor(BACKDROP)).pointerInput(Unit) {
        awaitPointerEventScope { while (true) { val event = awaitPointerEvent(PointerEventPass.Initial); if (event.changes.any { it.pressed && !it.previousPressed }) latestTouch() } }
    }) {
        val width = maxWidth.value; val height = maxHeight.value; val sheetTop = safe.calculateTopPadding().value + SCREEN_TOP
        val sheetHeight = max(0f, height - sheetTop)
        val headerTop = GRABBER_TOP + GRABBER_HEIGHT + HEADER_TOP
        val listTop = headerTop + HEADER_HEIGHT + LIST_TOP
        val actualRoom = max(0f, sheetHeight - listTop)
        var room by remember { mutableStateOf(0f) }
        LaunchedEffect(actualRoom) { room = actualRoom }
        val bottom = max(safe.calculateBottomPadding().value, BOTTOM_MIN) + BOTTOM_EXTRA
        val limit = room - bottom - BUTTON - FADE - CLEAR
        val timeHeight = ROW + TIME_EXTRA * max(0.0, timeShown.value).toFloat()
        val genreHeight = ROW + GENRE_EXTRA * max(0.0, genresShown.value).toFloat()
        val timeEnd = 2f * (ROW + ROW_GAP) + timeHeight
        val genresEnd = timeEnd + ROW_GAP + genreHeight
        val listShift = if (room > 0f) -max(0f, max(timeEnd - limit, genresEnd - limit)) else 0f
        val rowWidth = max(1f, width - SIDE * 2f)
        val titleStyle = filterStyle(TITLE_SIZE, TITLE_LINE, 600, expandingFilterRowsColor(INK))
        fun measure(text: String, style: TextStyle, available: Float) = measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = max(1, (available * density.density).roundToInt()))).size
        Box(filterFrame(0f, sheetTop, width, sheetHeight).background(expandingFilterRowsColor(SHEET), RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp)).clip(RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp))) {
            Box(filterFrame((width - GRABBER_WIDTH) / 2f, GRABBER_TOP, GRABBER_WIDTH, GRABBER_HEIGHT).background(expandingFilterRowsColor(GRABBER_INK), RoundedCornerShape((GRABBER_HEIGHT / 2f).dp)))
            Box(filterFrame(0f, headerTop, width, HEADER_HEIGHT), contentAlignment = Alignment.Center) { BasicText(title, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() }) }
            FilterControl("Close", filterFrame(width - SIDE - CLOSE - CLOSE_SLOP, headerTop + CLOSE_TOP - CLOSE_SLOP, CLOSE + CLOSE_SLOP * 2f, CLOSE + CLOSE_SLOP * 2f), scope, { latestTouch(); closeCallback?.invoke() }) {
                Box(filterFrame(CLOSE_SLOP, CLOSE_SLOP, CLOSE, CLOSE).background(expandingFilterRowsColor(CLOSE_FILL), RoundedCornerShape((CLOSE / 2f).dp)), contentAlignment = Alignment.Center) { FilterIcon("x", CLOSE_ICON, expandingFilterRowsColor(CLOSE_INK)) }
            }
            Box(filterFrame(0f, listTop, width, actualRoom).clipToBounds()) {
                Box(filterFrame(SIDE, listShift, rowWidth, genresEnd + ROW_GAP + ROW)) {
                    FilterRow("calendar", datesLabel, "Shows the next date", rowWidth, fades[0], turn, scope, { latestTouch(); latestLight(); if (currentDates.isNotEmpty()) dateIndex = (dateIndex + 1) % currentDates.size }, filterFrame(0f, 0f, rowWidth, ROW))
                    FilterRow("users", friendsLabel, "Turns the filter on or off", rowWidth, fades[1], turn, scope, { latestTouch(); latestLight(); withFriends = !withFriends }, filterFrame(0f, ROW + ROW_GAP, rowWidth, ROW))
                    FilterRow("clock", timeLabel, null, rowWidth, fades[2], turn, scope, { latestTouch(); latestLight(); latestToggle(Expanded.Time) }, filterFrame(0f, (ROW + ROW_GAP) * 2f, rowWidth, timeHeight), pressed == Pressed.Time, timeShown.value, expanded == Expanded.Time, TIME_PANEL) {
                        TimeDial(start, end, range, ranged, singleTimeLabel, timeRangeLabel, { latestMode(it, false) }, { half, hand -> latestStart(half, hand) }, { half, hand -> latestEnd(half, hand) }, { latestTouch() }, timeShown, Modifier.fillMaxSize(), enabled = expanded == Expanded.Time)
                    }
                    FilterRow("music", genresLabel, null, rowWidth, fades[3], turn, scope, { latestTouch(); latestLight(); latestToggle(Expanded.Genres) }, filterFrame(0f, timeEnd + ROW_GAP, rowWidth, genreHeight), pressed == Pressed.Genres, genresShown.value, expanded == Expanded.Genres, GENRE_PANEL) {
                        GenreDrum(chosen, others, leftPosition, rightPosition, genresShown, moving, fresh, noGenresLabel, { name -> latestTouch(); latestSelect(); latestMove(name, true) }, { name -> latestTouch(); latestSelect(); latestMove(name, false) }, { latestTouch() }, Modifier.fillMaxSize(), enabled = expanded == Expanded.Genres)
                    }
                    FilterRow("dollar-sign", priceLabel, "Shows the next price", rowWidth, fades[4], turn, scope, { latestTouch(); latestLight(); if (currentPrices.isNotEmpty()) priceIndex = (priceIndex + 1) % currentPrices.size }, filterFrame(0f, genresEnd + ROW_GAP, rowWidth, ROW))
                }
            }
            val barTop = sheetHeight - bottom - BUTTON
            Box(filterFrame(0f, barTop - FADE, width, FADE).background(Brush.verticalGradient(listOf(expandingFilterRowsColor(SHEET).copy(alpha = 0f), expandingFilterRowsColor(SHEET)))))
            Box(filterFrame(0f, barTop, width, BUTTON + bottom).background(expandingFilterRowsColor(SHEET))) {
                val resetStyle = filterStyle(LABEL_SIZE, LABEL_LINE, 400, expandingFilterRowsColor(MUTE))
                val resetSize = measure(resetLabel, resetStyle, width - SIDE * 2f)
                val resetWidth = resetSize.width / density.density; val resetHeight = resetSize.height / density.density
                FilterControl(resetLabel, filterFrame(SIDE - RESET_SLOP, (BUTTON - resetHeight) / 2f - RESET_SLOP, resetWidth + RESET_SLOP * 2f, resetHeight + RESET_SLOP * 2f), scope, { latestTouch(); latestLight(); latestReset(false) }) { held ->
                    BasicText(resetLabel, filterFrame(RESET_SLOP, RESET_SLOP, resetWidth, resetHeight), resetStyle.copy(color = expandingFilterRowsColor(if (held || pressed == Pressed.Reset) INK else MUTE)))
                }
                FilterControl("$continueLabel $withLabel $count", filterFrame(width - SIDE - BUTTON_WIDTH, 0f, BUTTON_WIDTH, BUTTON), scope, { latestTouch(); latestLight(); continueCallback?.invoke(currentFilters) }) { held ->
                    LaunchedEffect(held) { buttonPress.time(if (held) 1.0 else 0.0, BUTTON_PRESS_MS, ExpandingFilterRowsCss) }
                    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = (1.0 - (1.0 - BUTTON_SCALE) * buttonPress.value).toFloat(); scaleY = scaleX }.background(expandingFilterRowsColor(BLUE), RoundedCornerShape((BUTTON / 2f).dp))) {
                        val labelStyle = filterStyle(LABEL_SIZE, LABEL_LINE, 600, expandingFilterRowsColor(INK))
                        val withStyle = filterStyle(LABEL_SIZE, LABEL_LINE, 500, expandingFilterRowsColor(INK).copy(alpha = WITH_ALPHA))
                        val labelSize = measure(continueLabel, labelStyle, BUTTON_WIDTH); val withSize = measure(" $withLabel ", withStyle, BUTTON_WIDTH)
                        val labelWidth = labelSize.width / density.density; val withWidth = withSize.width / density.density
                        val left = (BUTTON_WIDTH - labelWidth - withWidth - COUNTER_WIDTH) / 2f
                        BasicText(continueLabel, filterFrame(left, (BUTTON - labelSize.height / density.density) / 2f, labelWidth, labelSize.height / density.density), labelStyle)
                        BasicText(" $withLabel ", filterFrame(left + labelWidth, (BUTTON - withSize.height / density.density) / 2f, withWidth, withSize.height / density.density), withStyle)
                        Box(filterFrame(left + labelWidth + withWidth, (BUTTON - COUNTER_LINE) / 2f, COUNTER_WIDTH, COUNTER_LINE)) {
                            countFade.entries.forEach { entry -> key(entry.id) {
                                BasicText(entry.copy.text, Modifier.fillMaxSize().graphicsLayer { alpha = if (entry.leaving) 1f - entry.progress else entry.progress; translationY = (if (entry.leaving) -COUNTER_TRAVEL * entry.progress else COUNTER_TRAVEL * (1f - entry.progress)) * density.density; compositingStrategy = CompositingStrategy.ModulateAlpha }.then(if (entry.leaving) Modifier.clearAndSetSemantics {} else Modifier), withStyle.copy(textAlign = TextAlign.Center, fontFeatureSettings = "tnum"))
                            } }
                        }
                    }
                }
            }
        }
    }
}

private fun Context.filterActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.filterActivity(); else -> null }
private fun filterFrame(x: Float, y: Float, width: Float, height: Float) = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(max(0f, width).dp, max(0f, height).dp).graphicsLayer { translationX = x * density; translationY = y * density }
private fun filterStyle(size: Float, line: Float, weight: Int, ink: Color, align: TextAlign = TextAlign.Start) = TextStyle(
    fontSize = size.sp, lineHeight = line.sp, color = ink, fontWeight = FontWeight(weight), fontFamily = FontFamily.SansSerif, textAlign = align,
    platformStyle = PlatformTextStyle(includeFontPadding = true), lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)
@Composable
private fun FilterRow(icon: String, label: String, hint: String?, width: Float, fade: ValueFade, turn: ExpandingFilterRowsValue, scope: CoroutineScope, action: () -> Unit, modifier: Modifier, automaticHeld: Boolean = false, shown: Double = 0.0, expanded: Boolean = false, panelHeight: Float = 0f, content: (@Composable () -> Unit)? = null) {
    val tint = remember { ExpandingFilterRowsValue(0.0) }
    val density = LocalDensity.current; val measurer = rememberTextMeasurer()
    LaunchedEffect(Unit) { while (true) withFrameNanos { tint.advance(it) } }
    Box(modifier.background(expandingFilterRowsColor(ROW_FILL), RoundedCornerShape((ROW_RADIUS).dp)).then(Modifier.clip(RoundedCornerShape((ROW_RADIUS).dp)))) {
        FilterControl(label, filterFrame(0f, 0f, width, ROW), scope, action, hint, if (content != null) expanded else null) { held ->
            val down = held || automaticHeld
            LaunchedEffect(down) { tint.time(if (down) 1.0 else 0.0, if (down) ROW_PRESS_IN else ROW_PRESS_OUT, ExpandingFilterRowsCss) }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = tint.value.toFloat() }.background(expandingFilterRowsColor(ROW_PRESSED)))
            Box(filterFrame(HEAD_PAD, (ROW - ROW_ICON) / 2f, ROW_ICON, ROW_ICON), contentAlignment = Alignment.Center) { FilterIcon(icon, ROW_ICON, expandingFilterRowsColor(MUTE)) }
            val labelStyle = filterStyle(LABEL_SIZE, LABEL_LINE, 400, expandingFilterRowsColor(MUTE))
            val size = measurer.measure(AnnotatedString(label), labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = max(1, ((width - HEAD_PAD * 2f - LABEL_LEFT) * density.density).roundToInt()))).size
            BasicText(label, filterFrame(HEAD_PAD + ROW_ICON + LABEL_LEFT, (ROW - size.height / density.density) / 2f, size.width / density.density, size.height / density.density), labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            FilterValue(fade, turn, filterFrame(width * VALUE_LEFT, 0f, max(0f, width * (1f - VALUE_LEFT) - HEAD_PAD), ROW))
        }
        if (content != null) {
            Box(filterFrame(WELL_INSET, ROW + (1.0 - min(1.0, shown)).toFloat() * -WELL_DROP, max(0f, width - WELL_INSET * 2f), panelHeight).graphicsLayer { alpha = ((shown - .1) / .6).coerceIn(0.0, 1.0).toFloat() }.background(expandingFilterRowsColor(WELL), RoundedCornerShape((WELL_RADIUS).dp)).then(Modifier.clip(RoundedCornerShape((WELL_RADIUS).dp))).then(if (!expanded) Modifier.clearAndSetSemantics {} else Modifier)) {
                content()
            }
        }
    }
}
@Composable
private fun FilterValue(fade: ValueFade, turn: ExpandingFilterRowsValue, modifier: Modifier) {
    val density = LocalDensity.current; val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val width = maxWidth.value
        fade.entries.forEach { entry -> key(entry.id) {
            val copy = entry.copy
            val alpha = if (entry.leaving) 1f - entry.progress else entry.progress
            if (copy.friends) {
                Box(filterFrame(width - FRIENDS_WIDTH, 0f, FRIENDS_WIDTH, ROW).graphicsLayer { this.alpha = alpha }.then(if (entry.leaving) Modifier.clearAndSetSemantics {} else Modifier)) {
                    Box(filterFrame(AVATAR_LEFT, 0f, AVATAR, ROW)) {
                        copy.friendsData.forEachIndexed { index, friend ->
                            val count = copy.friendsData.size.toDouble()
                            val away = if (count == 0.0) 0.0 else ((index - turn.value + count / 2.0) % count + count) % count - count / 2.0
                            Box(filterFrame(0f, (ROW - AVATAR) / 2f + (away * AVATAR_PITCH).toFloat(), AVATAR, AVATAR).background(expandingFilterRowsColor(friend.color), RoundedCornerShape((AVATAR / 2f).dp)).then(Modifier.clip(RoundedCornerShape((AVATAR / 2f).dp))), contentAlignment = Alignment.Center) {
                                if (!friend.initials.isNullOrEmpty()) BasicText(friend.initials, style = filterStyle(INITIALS_SIZE, INITIALS_LINE, 700, expandingFilterRowsColor(INK)))
                                else {
                                    Box(filterFrame((AVATAR - HEAD_SIZE) / 2f, HEAD_TOP, HEAD_SIZE, HEAD_SIZE).background(expandingFilterRowsColor(INK).copy(alpha = FIGURE_ALPHA), RoundedCornerShape((HEAD_SIZE / 2f).dp)))
                                    Box(filterFrame((AVATAR - SHOULDER_SIZE) / 2f, SHOULDER_TOP, SHOULDER_SIZE, SHOULDER_SIZE).background(expandingFilterRowsColor(INK).copy(alpha = FIGURE_ALPHA), RoundedCornerShape((SHOULDER_SIZE / 2f).dp)))
                                }
                            }
                        }
                    }
                    val style = filterStyle(LABEL_SIZE, LABEL_LINE, 600, expandingFilterRowsColor(INK), TextAlign.End).copy(fontFeatureSettings = "tnum")
                    val measured = measurer.measure(AnnotatedString(copy.text), style, constraints = Constraints(maxWidth = max(1, (FRIENDS_WIDTH * density.density).roundToInt()))).size
                    BasicText(copy.text, filterFrame(FRIENDS_WIDTH - measured.width / density.density, (ROW - measured.height / density.density) / 2f, measured.width / density.density, measured.height / density.density), style)
                }
            } else {
                val style = filterStyle(LABEL_SIZE, LABEL_LINE, if (copy.dim) 400 else 600, expandingFilterRowsColor(if (copy.dim) MUTE else INK), TextAlign.End).copy(fontFeatureSettings = "tnum")
                val text = buildAnnotatedString { append(copy.text); if (copy.tail != null) withStyle(SpanStyle(color = expandingFilterRowsColor(MUTE), fontWeight = FontWeight.Medium)) { append(copy.tail) } }
                val measured = measurer.measure(text, style, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = max(1, (width * density.density).roundToInt()))).size
                BasicText(text, filterFrame(width - measured.width / density.density, (ROW - measured.height / density.density) / 2f, measured.width / density.density, measured.height / density.density).graphicsLayer { this.alpha = alpha; compositingStrategy = CompositingStrategy.ModulateAlpha }.then(if (entry.leaving) Modifier.clearAndSetSemantics {} else Modifier), style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } }
    }
}
private class PressState { var alive = true; var latestOut: Job? = null }
@Composable
private fun FilterControl(label: String, modifier: Modifier, scope: CoroutineScope, action: () -> Unit, hint: String? = null, expanded: Boolean? = null, content: @Composable (Boolean) -> Unit) {
    val state = remember { PressState() }; var pressed by remember { mutableStateOf(false) }; val currentAction by rememberUpdatedState(action)
    val view = LocalView.current
    DisposableEffect(state) { state.alive = true; onDispose { state.alive = false; state.latestOut?.cancel() } }
    Box(modifier.semantics(mergeDescendants = true) { role = Role.Button; contentDescription = label; if (hint != null) stateDescription = hint; if (expanded != null) stateDescription = if (expanded) "Expanded" else "Collapsed"; onClick { currentAction(); true } }.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x < 0f || down.position.x > size.width || down.position.y < 0f || down.position.y > size.height) return@awaitEachGesture
            state.latestOut?.cancel(); state.latestOut = null
            var active = true; var activatedAt: Long? = System.nanoTime()
            fun pressIn() { activatedAt = System.nanoTime(); pressed = true }
            fun pressOut() {
                val remaining = max(0L, MIN_PRESS_DURATION_MS * 1_000_000L - (System.nanoTime() - (activatedAt ?: 0L)))
                activatedAt = null
                if (remaining == 0L) pressed = false else state.latestOut = scope.launch { delay((remaining + 999_999L) / 1_000_000L); pressed = false }
            }
            pressIn(); down.consume()
            try {
                while (true) {
                    val event = awaitPointerEvent(); val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (finger.isConsumed) break
                    val inside = finger.position.x > -PRESS_RETENTION * density && finger.position.x < size.width + PRESS_RETENTION * density && finger.position.y > -PRESS_RETENTION * density && finger.position.y < size.height + PRESS_RETENTION_BOTTOM * density
                    if (inside != active) { active = inside; if (inside) pressIn() else pressOut() }
                    finger.consume()
                    if (!finger.pressed) { if (active) { pressOut(); active = false; view.playSoundEffect(SoundEffectConstants.CLICK); currentAction() }; break }
                }
                if (active) { active = false; pressOut() }
            } catch (cancelled: CancellationException) { if (active) scope.launch { yield(); if (state.alive) pressOut() }; throw cancelled }
        }
    }) { content(pressed) }
}
