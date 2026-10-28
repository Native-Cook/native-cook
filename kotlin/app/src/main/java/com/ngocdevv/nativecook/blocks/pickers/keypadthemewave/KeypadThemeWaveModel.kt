package com.ngocdevv.nativecook.blocks.pickers.keypadthemewave

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Keypad Theme Wave — the pure part (a port of KeypadThemeWaveModel.swift): palettes, the keypad, the
 strip's layout, the timing of a theme change and the autoPlay timeline. Every value the screen draws is a
 function of these and of the time.
*/

/** One calculator theme: the card, three key roles (digit, function, operator), the display ink and the ring colour. */
internal class KtwTheme(
    val name: String,
    val bg: Long,
    val digit: Long,
    val digitInk: Long,
    val fn: Long,
    val fnInk: Long,
    val op: Long,
    val opInk: Long,
    val display: Long,
    val ring: Long,
) {
    fun fill(role: Int) = if (role == 1) fn else if (role == 2) op else digit
    fun ink(role: Int) = if (role == 1) fnInk else if (role == 2) opInk else digitInk
}

/** A theme change: when, to which theme, and from which side (+1 the new swatch came from the right). */
internal data class KtwChange(val at: Double, val to: Int, val dir: Int)

/** What one frame shows. */
internal class KtwScene(
    val now: Double = 0.0,
    val base: Int = KtwModel.HOME,
    val changes: List<KtwChange> = emptyList(),
    /** The strip's position in swatches (wraps every theme count). */
    val scroll: Double = KtwModel.HOME.toDouble(),
    val display: String = "0",
    /** The time each key was last pressed (index row * 5 + column). */
    val presses: DoubleArray = DoubleArray(20) { -1e9 },
    val reduced: Boolean = false,
)

/** What a key draws at progress u of its change. */
internal class KtwLook {
    var oldR = 0.0
    var oldLabel = 0.0
    var ring = 0.0
    var ringLabel = 0.0
    var newR = 0.0
    var newLabel = 0.0
    var scale = 1.0
    var mix = 1.0
}

internal class KtwKeyState(val from: Int, val to: Int, val u: Double)

internal object KtwModel {
    const val LOOP = 10.0

    val THEMES = listOf(
        KtwTheme("Paper", 0xE8E7EA, 0xBEBDC3, 0x2C2C31, 0xBEBDC3, 0x2C2C31, 0x8F8E95, 0xFFFFFF, 0x2C2C31, 0x75747B),
        KtwTheme("Classic", 0xDCD8D0, 0xA9A49B, 0x26241F, 0xF0532F, 0xFFFFFF, 0x383532, 0xFFFFFF, 0x1F1D1A, 0x5E5A53),
        KtwTheme("Garden", 0x23A05A, 0xF5F2E6, 0x1F5F38, 0xF26A2A, 0xFFFFFF, 0xE3392F, 0xFFFFFF, 0xFFFFFF, 0xE6F6EA),
        KtwTheme("Marigold", 0xF8B33C, 0xFFF8EA, 0x3A2A12, 0xE2432D, 0xFFFFFF, 0x2B6FBE, 0xFFFFFF, 0xD93A22, 0x7A4E0E),
        KtwTheme("Lilac", 0xD0C2F9, 0x9B7CF1, 0xFFFFFF, 0x9B7CF1, 0xFFFFFF, 0xCFF24B, 0x3D3470, 0x5B46C2, 0x6A55C9),
        KtwTheme("Sky", 0x6CCAF4, 0x2F9AD5, 0xF2FAFF, 0x2F9AD5, 0xF2FAFF, 0xF26C22, 0xFFFFFF, 0x145C88, 0x1F6F9C),
        KtwTheme("Mint", 0x7DE8D6, 0x1DA388, 0xF2FFFB, 0x137E69, 0xFFFFFF, 0x55D3B9, 0x0F5A4B, 0x0F5A4B, 0x167D6A),
        KtwTheme("Meadow", 0x68D877, 0x41BC55, 0xF3FFF4, 0x2E9C43, 0xF3FFF4, 0xA6EC7A, 0x1F5A28, 0x1F5A28, 0x2B8A3C),
        KtwTheme("Lemon", 0xFADA5E, 0xF6A623, 0xFFFFFF, 0xF6A623, 0xFFFFFF, 0xE8562A, 0xFFFFFF, 0x8A5A00, 0xA8740E),
        KtwTheme("Night", 0x1D1C1B, 0x3A3936, 0xF3F1EC, 0x5A5854, 0xF3F1EC, 0x8E8B85, 0x1D1C1B, 0xF3F1EC, 0x8A8782),
    )
    val COUNT = THEMES.size

    /** The resting theme: Classic. */
    const val HOME = 1

    // Keypad

    /** Labels, row by row; "" is the wordmark's cell. */
    val LABELS = listOf(
        listOf("AC", "7", "8", "9", "÷"),
        listOf("±", "4", "5", "6", "×"),
        listOf("%", "1", "2", "3", "−"),
        listOf("", "0", ".", "=", "+"),
    )

    /** 0 digit, 1 function (AC ± %), 2 operator (÷ × − + =). */
    fun role(r: Int, c: Int): Int = when {
        c == 0 -> 1
        c == 4 || (r == 3 && c == 3) -> 2
        else -> 0
    }

    fun isKey(r: Int, c: Int) = !(r == 3 && c == 0)

    const val KEY_RADIUS = 25.0
    const val PITCH = 59.0

    // Timing of a change

    const val KEY_DUR = 0.64
    const val KEY_LEAD = 0.08
    const val KEY_STAGGER = 0.06
    const val REVEAL_DUR = 0.62
    const val SETTLE = 1.3
    const val REDUCED_DUR = 0.25

    /** The diagonal a key sits on, counted from the corner the colour comes from (0 … 7). */
    fun diagonal(r: Int, c: Int, dir: Int) = (3 - r) + (if (dir > 0) 4 - c else c)

    fun keyDelay(r: Int, c: Int, dir: Int) = KEY_LEAD + KEY_STAGGER * diagonal(r, c, dir)

    /** One key's transition: the theme it leaves, the one it goes to, and its progress (1 = done). */
    fun keyState(s: KtwScene, r: Int, c: Int): KtwKeyState {
        var from = s.base
        var to = s.base
        var u = 1.0
        var prev = s.base
        for (ch in s.changes) {
            val start = ch.at + (if (s.reduced) 0.0 else keyDelay(r, c, ch.dir))
            if (s.now >= start) {
                from = prev
                to = ch.to
                u = min(1.0, (s.now - start) / (if (s.reduced) REDUCED_DUR else KEY_DUR))
            }
            prev = ch.to
        }
        return KtwKeyState(from, to, u)
    }

    fun look(u: Double, reduced: Boolean): KtwLook {
        val l = KtwLook()
        val r = KEY_RADIUS
        if (reduced) {
            l.mix = easeInOutCubic(u)
            l.newR = r
            l.newLabel = 1.0
            return l
        }
        if (u >= 1) {
            l.newR = r
            l.newLabel = 1.0
            return l
        }
        val drain = easeInCubic(seg(u, 0.0, 0.28))
        l.oldR = r * (1 - drain)
        l.oldLabel = 1 - seg(u, 0.0, 0.15)
        val v = seg(u, 0.46, 1.0)
        val grow = easeOutBack(v, 1.9)
        l.newR = r * grow
        l.ring = seg(u, 0.06, 0.2) * (1 - seg(grow, 0.85, 1.0))
        val swap = seg(v, 0.25, 0.6)
        l.ringLabel = seg(u, 0.12, 0.26) * (1 - swap)
        l.newLabel = swap
        val dip = easeOutCubic(seg(u, 0.0, 0.28))
        l.scale = 1 - 0.08 * dip * (1 - grow)
        return l
    }

    /** The press dip of a key tapped tau seconds ago: 0 at rest, 1 fully pressed. */
    fun press(tau: Double): Double {
        if (tau < 0 || tau > 0.5) return 0.0
        if (tau < 0.07) return easeOutCubic(tau / 0.07)
        return 1 - easeOutBack(seg(tau, 0.07, 0.4), 1.6)
    }

    /** The reveal radius of a change tau seconds old, as a fraction of the card's diagonal. */
    fun reveal(tau: Double): Double {
        if (tau <= 0) return 0.0
        val x = min(1.0, tau / REVEAL_DUR)
        return (1 - cos(Math.PI * x)) / 2
    }

    // The strip

    const val SWATCH_PITCH = 34.0

    /** Distance of a swatch from the centre, in swatches, wrapped into [−count/2, count/2). */
    fun wrap(d: Double): Double {
        val n = COUNT.toDouble()
        var x = (d + n / 2) % n
        if (x < 0) x += n
        return x - n / 2
    }

    fun mod(i: Int) = ((i % COUNT) + COUNT) % COUNT

    /** Horizontal offset of a swatch d swatches from the centre (the centred one is wider and pushes the others). */
    fun swatchX(d: Double): Double {
        val a = min(abs(d), 1.0)
        return d * SWATCH_PITCH + (if (d < 0) -1 else 1) * a * 8
    }

    // Springs

    /** An underdamped spring from x0 with speed v0 towards 0, after tau seconds. */
    fun spring(x0: Double, v0: Double, tau: Double, response: Double = 0.5, damping: Double = 0.8): Double {
        if (tau <= 0) return x0
        val w = 2 * Math.PI / response
        val z = damping
        val wd = w * sqrt(1 - z * z)
        val e = exp(-z * w * tau)
        return e * (x0 * cos(wd * tau) + ((v0 + z * w * x0) / wd) * sin(wd * tau))
    }

    fun springVelocity(x0: Double, v0: Double, tau: Double): Double {
        val h = 0.001
        val a = max(0.0, tau - h)
        return (spring(x0, v0, tau + h) - spring(x0, v0, a)) / (tau + h - a)
    }

    // autoPlay

    /** The strip's moves: start time, from, to (unwrapped swatch positions). */
    class Move(val at: Double, val from: Double, val to: Double)

    val AUTO_MOVES = listOf(
        Move(1.0, 1.0, 3.0), // Classic → Marigold
        Move(3.2, 3.0, 4.0), // → Lilac
        Move(5.2, 4.0, 6.0), // → Mint
        Move(7.2, 6.0, 1.0), // back to Classic
    )
    const val WAVE_AFTER_MOVE = 0.15

    val AUTO_CHANGES = AUTO_MOVES.map { m ->
        KtwChange(m.at + WAVE_AFTER_MOVE, mod(m.to.toInt()), if (m.to > m.from) 1 else -1)
    }

    class Scroll(val s: Double, val v: Double, val target: Double)

    /** Strip position and speed at loop time t. */
    fun autoScroll(t: Double): Scroll {
        var s = AUTO_MOVES[0].from
        var v = 0.0
        var target = s
        for (m in AUTO_MOVES) {
            if (t < m.at) continue
            val x0 = m.from - m.to
            s = m.to + spring(x0, 0.0, t - m.at)
            v = springVelocity(x0, 0.0, t - m.at)
            target = m.to
        }
        return Scroll(s, v, target)
    }

    fun autoScene(t: Double) = KtwScene(
        now = t,
        base = HOME,
        changes = AUTO_CHANGES.filter { it.at <= t },
        scroll = autoScroll(t).s,
    )

    // Curves

    fun seg(t: Double, a: Double, b: Double) = min(max((t - a) / (b - a), 0.0), 1.0)
    fun easeInCubic(x: Double) = x * x * x
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeOutBack(x: Double, s: Double): Double {
        val y = x - 1
        return 1 + (s + 1) * y * y * y + s * y * y
    }
    fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    /** Rounds half away from zero, like Swift's rounded(). */
    fun round(x: Double): Double = if (x >= 0) floor(x + 0.5) else -floor(-x + 0.5)
}

/** The tiny calculator behind the preview's keys. */
internal class KtwCalc {
    var display = "0"
        private set
    private var acc: Double? = null
    private var op: String? = null
    private var fresh = true

    private val value: Double get() = display.replace("−", "-").toDoubleOrNull() ?: 0.0
    private val digits: Int get() = display.count { it.isDigit() }

    fun clear() {
        display = "0"
        acc = null
        op = null
        fresh = true
    }

    fun press(key: String) {
        if (display == "Error" && key != "AC") clear()
        when (key) {
            "AC" -> clear()
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> {
                if (fresh || display == "0") {
                    display = key
                    fresh = false
                } else if (digits < MAX_DIGITS) {
                    display += key
                }
            }
            "." -> {
                if (fresh) {
                    display = "0."
                    fresh = false
                } else if (!display.contains(".") && digits < MAX_DIGITS) {
                    display += "."
                }
            }
            "±" -> {
                if (display == "0") return
                display = if (display.startsWith("−")) display.drop(1) else "−$display"
            }
            "%" -> display = format(value / 100)
            "÷", "×", "−", "+" -> {
                val o = op
                val a = acc
                if (o != null && a != null && !fresh) display = format(apply(o, a, value))
                acc = value
                op = key
                fresh = true
            }
            "=" -> {
                val o = op
                val a = acc
                if (o != null && a != null) {
                    display = format(apply(o, a, value))
                    acc = null
                    op = null
                }
                fresh = true
            }
        }
    }

    /** The delete tag: drops the last character. */
    fun backspace() {
        if (fresh || display == "Error") return
        display = display.dropLast(1)
        if (display.isEmpty() || display == "−") display = "0"
    }

    companion object {
        const val MAX_DIGITS = 9

        fun apply(op: String, a: Double, b: Double): Double = when (op) {
            "÷" -> if (b == 0.0) Double.NaN else a / b
            "×" -> a * b
            "−" -> a - b
            else -> a + b
        }

        /** At most nine characters, the minus sign written as "−". */
        fun format(x: Double): String {
            if (!x.isFinite()) return "Error"
            var s: String? = null
            if (x == KtwModel.round(x) && abs(x) < 1e9) {
                s = x.toLong().toString()
            } else {
                for (d in 8 downTo 0) {
                    var t = String.format(Locale.US, "%.${d}f", x)
                    if (t.contains(".")) {
                        t = t.trimEnd('0').trimEnd('.')
                    }
                    if (t.length <= 9) {
                        s = t
                        break
                    }
                }
            }
            var out = s ?: return "Error"
            if (out == "-0") out = "0"
            return out.replace("-", "−")
        }
    }
}
