package com.ngocdevv.nativecook.blocks.animations.inkdropbloom

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/*
 Ink Drop Bloom — geometry, the autoPlay script and the touch simulation, ported from
 inkDropMotion.ts. Units: dp and seconds. LOOP = 9 s:
   0.0 – 1.4   rest: clear water, the swatches are empty outlines.
   1.15 – 1.4  drop 1 (indigo) falls into the glass; it blooms 1.4 – 4.
   2.55 – 2.8  drop 2 (orchid) falls; 3.95 – 4.2 drop 3 (lagoon).
   5.6 – 6.6   peak: the plumes fill the glass, every swatch is full.
   6.35 – 6.65 the press's mesh disc comes down to the water; 6.65 – 8.05 it pushes the ink to
               the floor (ease-in-out); 8.05 – 8.55 it lifts out.
   7.0 – 8.2   the swatches drain back to outlines.
   8.55 – 9.0  rest (the frame at 9 is the frame at 0).
*/

internal const val LOOP = 9f
/** The poster under reduced motion: the palette at its fullest. */
internal const val INK_REDUCED_AT = 6.1f
/** After the last touch the screen waits this long, then clears the water and restarts. */
private const val IDLE_BEFORE_BACK = 2f
/** How long clearing back to rest takes after a touch (s). */
private const val BACK_FOR = 0.9f

internal const val INK_MODE_AUTO = 0
internal const val INK_MODE_LIVE = 1
internal const val INK_MODE_BACK = 2

/** The three inks (sRGB 0–1, at full strength). */
internal val InkColors = arrayOf(
    floatArrayOf(0.17f, 0.2f, 0.62f),
    floatArrayOf(0.74f, 0.12f, 0.42f),
    floatArrayOf(0.04f, 0.5f, 0.52f),
)

internal class InkSwatch(val name: String, val hex: String, val color: Color, val deep: Color)

internal val InkSwatches = listOf(
    InkSwatch("Indigo", "2D3489", Color(0xFF2D3489), Color(0xFF1D2266)),
    InkSwatch("Orchid", "B0306E", Color(0xFFB0306E), Color(0xFF7E1B4E)),
    InkSwatch("Lagoon", "0F7C80", Color(0xFF0F7C80), Color(0xFF08585C)),
    InkSwatch("Plum", "4E2763", Color(0xFF4E2763), Color(0xFF331745)),
    InkSwatch("Deep Sea", "1C4A6B", Color(0xFF1C4A6B), Color(0xFF11324B)),
)

internal val InkPhases = listOf(
    "Clear water · ready to mix",
    "Dropping indigo…",
    "Dropping orchid…",
    "Dropping lagoon…",
    "Palette ready · 5 colours",
    "Filtering the glass…",
    "Clear water · ready to mix",
)

private val DROP_AT = floatArrayOf(1.4f, 2.8f, 4.2f)
/** Where each scripted drop enters, as a share of the water's half width. */
private val DROP_X = floatArrayOf(-0.32f, 0.3f, 0.02f)
private const val FALL_FOR = 0.25f
private const val FALL_FROM = 92f
private val PRESS_IN = floatArrayOf(6.35f, 6.65f)
private val PRESS_PUSH = floatArrayOf(6.65f, 8.05f)
private val PRESS_OUT = floatArrayOf(8.05f, 8.55f)
private val LABEL_AT = floatArrayOf(1.25f, 2.65f, 4.05f, 5.5f, 6.45f, 8.3f)
private const val LABEL_FOR = 0.42f

// Layout: header, glass, palette card and button, top to bottom.
internal const val INK_HEADER_H = 116f
internal const val INK_PALETTE_H = 140f
internal const val INK_BUTTON_H = 52f

internal class InkGeo(
    val w: Float,
    val h: Float,
    val cx: Float,
    val top: Float,
    val bottom: Float,
    val hwTop: Float,
    val hwBot: Float,
    val wall: Float,
    val surface: Float,
    val floor: Float,
    val paletteTop: Float,
    val buttonTop: Float,
)

internal fun makeInkGeo(w: Float, h: Float, insetTop: Float, insetBottom: Float): InkGeo {
    val buttonTop = h - max(insetBottom, 12f) - 12f - INK_BUTTON_H
    val paletteTop = buttonTop - 16f - INK_PALETTE_H
    val top = insetTop + INK_HEADER_H + 24f
    val bottom = paletteTop - 30f
    val tall = bottom - top
    val hwTop = minOf(w * 0.29f, 116f, tall * 0.31f)
    return InkGeo(
        w = w,
        h = h,
        cx = w / 2f,
        top = top,
        bottom = bottom,
        hwTop = hwTop,
        hwBot = hwTop * 0.88f,
        wall = 6f,
        surface = top + max(30f, tall * 0.11f),
        floor = bottom - 22f,
        paletteTop = paletteTop,
        buttonTop = buttonTop,
    )
}

private fun clamp01(x: Float) = min(1f, max(0f, x))
private fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
internal fun inkEaseInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)

/** Everything one frame needs, in the order the shader and the views read it. */
internal class InkFrame {
    /** 4 drops × (x, y, age, seed); age < 0 = none. */
    val drops = floatArrayOf(0f, 0f, -1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, -1f, 0f)
    /** Ink index of each drop slot. */
    val inks = intArrayOf(0, 1, 2, 0)
    val falling = floatArrayOf(0f, 0f, 0f, 0f)
    var fallInk = 0
    /** Disc y (< 0 none), rod alpha, squeeze, ink amount. */
    val press = floatArrayOf(-1f, 0f, 0f, 1f)
    val stir = floatArrayOf(0f, 0f, 0f, 60f)
    val swatches = floatArrayOf(0f, 0f, 0f, 0f, 0f)
    var label = 0f
    var bar = 0f

    fun copyTo(b: InkFrame) {
        drops.copyInto(b.drops)
        inks.copyInto(b.inks)
        press.copyInto(b.press)
        stir.copyInto(b.stir)
        falling.copyInto(b.falling)
        swatches.copyInto(b.swatches)
        b.fallInk = fallInk
        b.label = label
        b.bar = bar
    }
}

/** The autoPlay frame at loop time t: a pure function, so frame LOOP = frame 0. */
internal fun inkScriptFrame(t: Float, g: InkGeo, out: InkFrame) {
    val ihw = g.hwTop - g.wall
    val done = t >= PRESS_PUSH[1]
    for (i in 0 until 3) {
        val age = t - DROP_AT[i]
        out.drops[i * 4] = g.cx + DROP_X[i] * ihw
        out.drops[i * 4 + 1] = g.surface
        out.drops[i * 4 + 2] = if (age > 0 && !done) age else -1f
        out.drops[i * 4 + 3] = i * 0.37f + 0.11f
        out.inks[i] = i
    }
    out.drops[14] = -1f
    // The drop in the air.
    out.falling[3] = 0f
    for (i in 0 until 3) {
        val s = seg(t, DROP_AT[i] - FALL_FOR, DROP_AT[i])
        if (s > 0 && s < 1) {
            val y0 = g.top - FALL_FROM
            out.falling[0] = g.cx + DROP_X[i] * ihw
            // Falling is gravity: the drop speeds up (y ∝ s²).
            out.falling[1] = y0 + (g.surface - y0) * s * s
            out.falling[2] = 5.2f
            out.falling[3] = clamp01(s / 0.25f)
            out.fallInk = i
        }
    }
    // The press.
    var disc = -1f
    var rod = 0f
    val above = g.top - 26f
    if (t >= PRESS_IN[0] && t < PRESS_IN[1]) {
        disc = above + (g.surface - above) * easeOut(seg(t, PRESS_IN[0], PRESS_IN[1]))
        rod = easeOut(seg(t, PRESS_IN[0], PRESS_IN[0] + 0.18f))
    } else if (t >= PRESS_PUSH[0] && t < PRESS_PUSH[1]) {
        disc = g.surface + (g.floor - 4f - g.surface) * inkEaseInOut(seg(t, PRESS_PUSH[0], PRESS_PUSH[1]))
        rod = 1f
    } else if (t >= PRESS_OUT[0] && t < PRESS_OUT[1]) {
        disc = g.floor - 4f + (above - (g.floor - 4f)) * inkEaseInOut(seg(t, PRESS_OUT[0], PRESS_OUT[1]))
        rod = 1f - seg(t, PRESS_OUT[1] - 0.2f, PRESS_OUT[1])
    }
    out.press[0] = disc
    out.press[1] = rod
    out.press[2] = seg(t, PRESS_PUSH[0], PRESS_PUSH[1])
    out.press[3] = 1f
    out.stir[2] = 0f
    // Swatches fill as their colour blooms, the two mixes once the colours meet; all drain together.
    val drain = 1f - inkEaseInOut(seg(t, 7.0f, 8.2f))
    for (i in 0 until 3) out.swatches[i] = easeOut(seg(t, DROP_AT[i] + 0.25f, DROP_AT[i] + 1.35f)) * drain
    out.swatches[3] = easeOut(seg(t, 4.9f, 5.8f)) * drain
    out.swatches[4] = easeOut(seg(t, 5.15f, 6.05f)) * drain
    var label = 0f
    for (a in LABEL_AT) label += inkEaseInOut(seg(t, a, a + LABEL_FOR))
    out.label = label
    out.bar = inkEaseInOut(seg(t, 1.3f, 6.0f)) * (1f - inkEaseInOut(seg(t, 6.65f, 8.05f)))
}

/** The touch simulation. Times in seconds of the frame clock. */
internal class InkSim {
    var mode = INK_MODE_AUTO
    /** Loop time (auto), the frame clock, the last touch, the back progress. */
    var t = 0f
    var now = 0f
    var last = 0f
    var back = 0f
    var flow = 0f
    val frame = InkFrame()
    /** Values at the start of "back", eased toward rest from there. */
    val from = InkFrame()
    var slot = 3
    var nextInk = 0
    var touching = false
    var lastX = 0f
    var lastY = 0f

    /** A finger lands: the script stops where it is and the water is the finger's. */
    fun touchDown(x: Float, y: Float) {
        if (mode != INK_MODE_LIVE) {
            val f = frame
            // A press under way: what it already cleared stays clear, the disc goes away.
            if (f.press[0] > 0) f.press[3] = f.press[3] * (1f - f.press[2] * f.press[2])
            f.press[0] = -1f
            f.press[1] = 0f
            f.falling[3] = 0f
            f.label = round(f.label)
            if (f.label >= 5) f.label = 0f
            mode = INK_MODE_LIVE
        }
        touching = true
        last = now
        lastX = x
        lastY = y
        frame.stir[0] = x
        frame.stir[1] = y
    }

    /** Drops ink where the finger tapped; returns the ink index used. */
    fun drop(x: Float, y: Float): Int {
        val f = frame
        slot = (slot + 1) % 4
        val ink = nextInk
        nextInk = (nextInk + 1) % 3
        f.drops[slot * 4] = x
        f.drops[slot * 4 + 1] = y
        f.drops[slot * 4 + 2] = 0.001f
        f.drops[slot * 4 + 3] = (now * 0.731f) % 1f
        f.inks[slot] = ink
        // Older ink that was half filtered fades with the rest: keep it, but lift the amount.
        if (f.press[3] < 0.999f) f.press[3] = 1f
        f.label = 1f + ink
        return ink
    }

    /** A drag stirs: the twist follows the finger and grows with how far it moves sideways. */
    fun stir(x: Float, y: Float) {
        val f = frame
        val dx = (x - lastX).coerceIn(-28f, 28f)
        f.stir[0] += (x - f.stir[0]) * 0.35f
        f.stir[1] += (y - f.stir[1]) * 0.35f
        f.stir[2] = (f.stir[2] + dx * 0.0042f).coerceIn(-5f, 5f)
        f.stir[3] = 74f
        lastX = x
        lastY = y
        last = now
    }

    fun touchUp() {
        touching = false
        last = now
    }

    /** One frame of the simulation. */
    fun step(dt: Float, g: InkGeo, auto: Boolean, reduced: Boolean) {
        now += dt
        val f = frame
        if (mode == INK_MODE_AUTO) {
            if (auto) t = (t + dt) % LOOP
            flow = if (reduced) INK_REDUCED_AT else t
            inkScriptFrame(if (reduced) INK_REDUCED_AT else t, g, f)
            return
        }
        flow += dt
        if (mode == INK_MODE_LIVE) {
            for (i in 0 until 4) {
                val a = f.drops[i * 4 + 2]
                if (a >= 0) f.drops[i * 4 + 2] = min(a + dt, 9f)
            }
            // Swatches of the inks in the water fill; the mixes when two meet.
            for (i in 0 until 4) {
                if (f.drops[i * 4 + 2] > 0.3f) {
                    val k = f.inks[i]
                    f.swatches[k] = min(1f, f.swatches[k] + dt * 1.2f)
                }
            }
            if (f.swatches[0] > 0.6f && f.swatches[1] > 0.6f) f.swatches[3] = min(1f, f.swatches[3] + dt)
            if (f.swatches[1] > 0.6f && f.swatches[2] > 0.6f) f.swatches[4] = min(1f, f.swatches[4] + dt)
            var full = 0f
            for (i in 0 until 5) full += f.swatches[i] / 5f
            f.bar += (full - f.bar) * min(1f, dt * 4f)
            if (!touching) f.stir[2] *= exp(-dt / 3f)
            if (auto && !touching && now - last > IDLE_BEFORE_BACK) {
                mode = INK_MODE_BACK
                back = 0f
                f.copyTo(from)
            }
            return
        }
        // Back to rest: the ink fades out, the twist unwinds, the swatches drain, the label rolls home.
        back = min(1f, back + dt / BACK_FOR)
        val k = inkEaseInOut(back)
        val a = from
        f.press[3] = a.press[3] * (1f - k)
        f.stir[2] = a.stir[2] * (1f - k)
        for (i in 0 until 5) f.swatches[i] = a.swatches[i] * (1f - k)
        f.bar = a.bar * (1f - k)
        val home = if (a.label <= 3) 0f else 6f
        f.label = a.label + (home - a.label) * k
        if (back >= 1f) {
            mode = INK_MODE_AUTO
            t = 0f
            flow = 0f
            inkScriptFrame(0f, g, f)
        }
    }

    /** Whether the frame loop has anything to do: false at rest outside autoPlay. */
    fun idle(auto: Boolean) = mode == INK_MODE_AUTO && !auto
}

/** Whether a point is inside the water (where a tap drops ink). */
internal fun inkInWater(g: InkGeo, x: Float, y: Float) =
    y > g.surface + 4 && y < g.floor - 4 && abs(x - g.cx) < g.hwBot - g.wall - 4

/** The light the water throws on the table takes the mean colour of the ink in it. */
internal fun inkTint(f: InkFrame, out: FloatArray) {
    var r = 0f
    var gg = 0f
    var b = 0f
    var w = 0f
    for (i in 0 until 4) {
        val a = f.drops[i * 4 + 2]
        if (a <= 0) continue
        val k = min(1f, a / 1.5f) * f.press[3] * (1f - f.press[2])
        val c = InkColors[f.inks[i]]
        r += c[0] * k
        gg += c[1] * k
        b += c[2] * k
        w += k
    }
    if (w <= 0.0001f) {
        out[0] = 1f; out[1] = 1f; out[2] = 1f
        return
    }
    val m = min(1f, w)
    out[0] = 1f - m + (r / w) * m
    out[1] = 1f - m + (gg / w) * m
    out[2] = 1f - m + (b / w) * m
}
