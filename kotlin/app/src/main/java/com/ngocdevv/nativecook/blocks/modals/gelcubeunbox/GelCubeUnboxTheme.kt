package com.ngocdevv.nativecook.blocks.modals.gelcubeunbox

import android.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The texts of one state of the sheet: the action's label, the chip, the sheet's title and body (RN `LightBoxCopy`). */
class GelCubeUnboxCopy(val action: String, val chip: String? = null, val sheetTitle: String? = null, val sheetBody: String? = null)

/** Measurements of the RN block (`gcu-theme.ts`): reference points on the 402 x 874 canvas (source height 804). */
internal object GelCubeUnboxTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SAFE_TOP = 62.0
    const val SOURCE_H = 804.0

    private fun rgb(hex: Int): Int = 0xFF000000.toInt() or hex

    val tint = rgb(0x2F63D8)
    val page = rgb(0x3C5180)
    val floor = rgb(0x8FA6DA)
    val ink = rgb(0xF7F9FD)
    val mute = rgb(0xD6DEEF)
    val litInk = rgb(0x24385D)
    val titleRest = rgb(0xE8EEFA)
    val flowGlass = Color.argb(36, 244, 250, 255) // rgba(244,250,255,0.14)
    val litMute = rgb(0x283D62)
    val edge = rgb(0x7FB2FF)
    val core = rgb(0xF4FAFF)
    val fan = rgb(0xDCEBFF)
    val underside = rgb(0x9CC4FF)

    // type: size, line height, tracking (design pt, scaled by S)
    const val DISPLAY_SIZE = 25.0
    const val DISPLAY_LINE = 31.0
    const val DISPLAY_TRACK = -0.5
    const val BODY_SIZE = 17.0
    const val BODY_LINE = 24.0
    const val ACTION_SIZE = 21.0
    const val CHIP_SIZE = 15.0
    const val RADIUS_SHEET = 48.0

    const val GLASS_FILL = 0.1
    const val GLASS_STROKE = 0.35
    const val GRAIN = 0.03

    class Box(val x: Double, val y: Double, val w: Double, val h: Double)

    val CUBE = Box(86.0, 129.0, 220.0, 211.0)
    const val CUBE_SIZE = 215.0
    val CLOSE = Box(340.0, 44.0, 44.0, 44.0)
    val TITLE_BOX = Box(24.0, 380.0, 354.0, 62.0)
    val SUBTITLE_BOX = Box(20.0, 451.0, 362.0, 26.0)
    val PILL = Box(16.0, 497.0, 370.0, 58.0)
    val SHEET = Box(0.0, 596.0, 402.0, 278.0)
    val CHIP = Box(149.0, 629.0, 104.0, 34.0)
    val SHEET_TITLE = Box(16.0, 680.0, 370.0, 36.0)
    val SHEET_BODY = Box(24.0, 720.0, 354.0, 52.0)
    val TOUCH = Box(86.0, 96.0, 220.0, 252.0)

    // the large-type flow (system font scale above 1.1): real text in a scrolling column over the shader
    const val FLOW_PADDING = 16.0
    const val FLOW_GAP = 16.0
    const val FLOW_TITLE = 25.0
    const val FLOW_TITLE_LINE = 32.0
    const val FLOW_BODY = 17.0
    const val FLOW_BODY_LINE = 25.0
    const val FLOW_CHIP = 15.0
    const val FLOW_CHIP_LINE = 24.0
    const val FLOW_ACTION = 21.0
    const val FLOW_ACTION_HEIGHT = 58.0
    const val FLOW_ACTION_RADIUS = 29.0
    const val FLOW_ALPHA = 0.96

    const val TITLE_SHADOW_RADIUS = 6.0
    const val TITLE_SHADOW_Y = 3.0
    const val TITLE_SHADOW_ALPHA = 0.45
    val TITLE_SHADOW_RGB = intArrayOf(20, 35, 71)
    const val LARGE_TYPE_THRESHOLD = 1.1
    const val PRESS = 0.97
    const val MIN_HIT = 44.0

    const val TITLE = "Congratulations!\nYou got a Lumen Box"
    const val SUBTITLE = "Deals are waiting for you inside"
    val SEALED = GelCubeUnboxCopy("Unbox", "54 days", "Next one in \$38.56", "Save up the required amount before\nyour Lumen Box turns into a pumpkin")
    val OPENED = GelCubeUnboxCopy(
        "See deals", "7 days", // the chip carries a clock: it shows how long the unlocked deals last (was "3 deals")
        "Unlocked today", "\$8 off your next order · Free delivery\nA little extra with your next coffee",
    )
    const val A11Y_OPEN = "Open your Lumen Box"
    const val A11Y_CLOSE_BOX = "Close your Lumen Box"
    const val A11Y_HINT = "Drag upward to lift the lid, or activate to open."
    const val A11Y_CLOSE_REWARD = "Close reward"

    /** the reference canvas fitted to the window: scale, offsets and the two mappings (RN `makeLayout`) */
    class Layout(val w: Double, val h: Double, top: Double, bottom: Double) {
        val s = min(w / REF_W, (h - top - bottom) / (SOURCE_H - SAFE_TOP))
        val ox = (w - REF_W * s) / 2
        val oy = top - SAFE_TOP * s
        fun x(v: Double) = ox + v * s
        fun y(v: Double) = oy + v * s
    }

    /** the colours of the shader, linear 0..1 sRGB triples (`paletteOf`) */
    class Palette(
        val tint: FloatArray,
        val page: FloatArray,
        val floor: FloatArray,
        val edge: FloatArray,
        val core: FloatArray,
        val fan: FloatArray,
        val underside: FloatArray,
    )

    private fun triple(c: Int) = floatArrayOf(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f)

    private fun linear(x: Double) = if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
    private fun encoded(x: Double) = max(0.0, min(1.0, if (x <= 0.0031308) 12.92 * x else 1.055 * max(0.0, x).pow(1 / 2.4) - 0.055))

    private fun lab(c: FloatArray): DoubleArray {
        val r = linear(c[0].toDouble())
        val g = linear(c[1].toDouble())
        val b = linear(c[2].toDouble())
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
        )
    }

    private fun fromLab(lab: DoubleArray): FloatArray {
        val l = (lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2]).pow(3)
        val m = (lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2]).pow(3)
        val s = (lab[0] - 0.0894841775 * lab[1] - 1.291485548 * lab[2]).pow(3)
        return floatArrayOf(
            encoded(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s).toFloat(),
            encoded(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s).toFloat(),
            encoded(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s).toFloat(),
        )
    }

    /** Change hue and chroma in OKLCH, keeping the measured lightness ladder of the source (a custom `tint`). */
    fun paletteOf(tintColor: Int = tint): Palette {
        val chosenTint = triple(tintColor)
        if (tintColor == tint) {
            return Palette(chosenTint, triple(page), triple(floor), triple(edge), triple(core), triple(fan), triple(underside))
        }
        val original = lab(triple(tint))
        val chosen = lab(chosenTint)
        val delta = atan2(chosen[2], chosen[1]) - atan2(original[2], original[1])
        val ratio = min(1.4, hypot(chosen[1], chosen[2]) / max(0.001, hypot(original[1], original[2])))
        fun recolor(hex: Int): FloatArray {
            val v = lab(triple(hex))
            val c = hypot(v[1], v[2]) * ratio
            val h = atan2(v[2], v[1]) + delta
            return fromLab(doubleArrayOf(v[0], c * cos(h), c * sin(h)))
        }
        return Palette(chosenTint, recolor(page), recolor(floor), recolor(edge), recolor(core), recolor(fan), recolor(underside))
    }
}
