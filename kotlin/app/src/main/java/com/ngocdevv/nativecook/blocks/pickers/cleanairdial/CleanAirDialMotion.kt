package com.ngocdevv.nativecook.blocks.pickers.cleanairdial

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialTheme as Theme

/** RN's time functions, including the midpoint-integrated, whole-turn phase table. */
internal object CleanAirDialMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val F_HIGH = 0.62
    const val TAU = PI * 2
    object T {
        val up = doubleArrayOf(1.0, 1.7)
        val pull = doubleArrayOf(1.8, 4.4)
        val down = doubleArrayOf(5.0, 5.6)
        val back = doubleArrayOf(5.8, 8.4)
    }

    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)

    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))

    fun mix(a: Double, b: Double, p: Double) = a + (b - a) * p

    fun smooth(x: Double): Double {
        val p = clamp01(x)
        return p * p * (3 - 2 * p)
    }

    fun easeInOut(x: Double): Double {
        val p = clamp01(x)
        return if (p < 0.5) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2
    }

    fun easeOutBack(x: Double): Double {
        val p = clamp01(x)
        return 1 + 2.4 * (p - 1).pow(3) + 1.4 * (p - 1).pow(2)
    }

    fun omegaAt(g: Double): Double {
        if (g <= Theme.ticks[0]) return Theme.omega[0] * max(0.0, g) / Theme.ticks[0]
        for (i in 0..2) if (g <= Theme.ticks[i + 1]) return mix(Theme.omega[i], Theme.omega[i + 1], (g - Theme.ticks[i]) / (Theme.ticks[i + 1] - Theme.ticks[i]))
        return Theme.omega.last()
    }

    fun levelAt(g: Double): Int {
        var best = 0
        for (i in 1..3) if (abs(g - Theme.ticks[i]) < abs(g - Theme.ticks[best])) best = i
        return best
    }

    fun loopG(t: Double): Double {
        if (t < T.down[0]) return mix(12.0, 21.0, easeOutBack(seg(t, T.up[0], T.up[1])))
        return mix(21.0, 12.0, easeInOut(seg(t, T.down[0], T.down[1])))
    }

    fun loopF(t: Double): Double {
        if (t < T.back[0]) return F_HIGH * easeInOut(seg(t, T.pull[0], T.pull[1]))
        return F_HIGH * (1 - easeInOut(seg(t, T.back[0], T.back[1])))
    }

    private const val STEP = 0.01
    private val phiTable = DoubleArray(901).also { table ->
        var acc = 0.0
        for (i in 1..900) {
            acc += omegaAt(loopG((i - 0.5) * STEP)) * STEP
            table[i] = acc
        }
        val turns = max(1.0, floor(acc / TAU + 0.5))
        for (i in table.indices) table[i] = table[i] * turns * TAU / acc
    }

    fun loopPhi(t: Double): Double {
        val x = t.coerceIn(0.0, LOOP) / STEP
        val i = min(phiTable.size - 2, floor(x).toInt())
        return mix(phiTable[i], phiTable[i + 1], x - i)
    }

    fun pmOf(f: Double) = max(0.0, 12 - 13 * f)
    data class Frame(
        val amb: Double, val g: Double, val phi: Double, val f: Double, val ret: Int,
        val power: Double = 1.0, val timer: Double = 0.0, val timerAt: Double = 10.0,
        val scenes: Double = 1.0, val auto: Double = 0.0, val drag: Double = -1.0,
        val press: Int = -1, val pressK: Double = 0.0, val hush: Double = 0.0,
    )

    fun frameAt(t: Double, phi0: Double = 0.0) = Frame(t, loopG(t), loopPhi(t) + phi0, loopF(t), if (t >= T.down[0]) 1 else 0)
}
