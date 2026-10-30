package com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive

import kotlin.math.*

/** Every event and analytic curve is copied from cpf-motion.ts; clocks are seconds. */
internal object CashbackPickFiveMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val FLY = 0.5
    const val NEVER = -100.0
    const val MAX_SCROLL = 420.0
    const val SPARKLE_AT = 5.0

    data class Event(val t: Double, val k: Int, val row: Int, val v: Double = 0.0)
    val LOOP_EVENTS = listOf(
        Event(0.6, 0, 0),
        Event(1.25, 0, 1),
        Event(1.85, 0, 3),
        Event(2.45, 0, 2),
        Event(3.05, 0, 4),
        Event(3.55, 2, -1, 240.0),
        Event(4.45, 0, 6),
        Event(5.45, 2, -1, 0.0),
        Event(6.1, 1, 4),
        Event(6.35, 1, 2),
        Event(6.6, 1, 3),
        Event(6.85, 1, 1),
        Event(7.1, 1, 0)
    )

    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)

    fun mix(a: Double, b: Double, p: Double) = a + (b - a) * p

    fun smooth(x: Double): Double {
        val p = clamp01(x)
        return p * p * (3 - 2 * p)
    }

    fun easeOut(x: Double) = 1 - (1 - clamp01(x)).pow(3)

    fun easeInOut(x: Double): Double {
        val p = clamp01(x)
        return if (p < 0.5) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2
    }

    fun spring(tau: Double, response: Double, damping: Double): Double {
        if (tau <= 0) {
            return 0.0
        }
        val w = 2 * PI / response
        val zw = damping * w
        val wd = w * sqrt(1 - damping * damping)
        return 1 - exp(-zw * tau) * (cos(wd * tau) + (zw / wd) * sin(wd * tau))
    }

    data class Flight(val row: Int, val from: Int, val to: Int, val at: Double, val dir: Int)

    class State {
        val picks = mutableListOf<Int>()
        val slotOf = IntArray(9) { -1 }
        val slotAt = DoubleArray(9) { NEVER }
        val slotFrom = IntArray(9) { -1 }
        val checkAt = DoubleArray(9) { NEVER }
        val checked = BooleanArray(9)
        var flights = mutableListOf<Flight>()
        var nopeAt = NEVER
        var nopeRow = -1
        var countAt = NEVER
        var countFrom = 0
        var panelAt = NEVER
        var panelUp = false
        var scrollFrom = 0.0
        var scrollTo = 0.0
        var scrollAt = NEVER
    }

    fun scrollOf(s: State, now: Double) = mix(s.scrollFrom, s.scrollTo, easeInOut((now - s.scrollAt) / 0.7))

    fun replay(events: List<Event>, now: Double): State {
        val s = State()
        for (e in events) {
            if (e.t > now) {
                break
            }
            if (e.k == 2) {
                s.scrollFrom = scrollOf(s, e.t)
                s.scrollTo = e.v
                s.scrollAt = e.t
                continue
            }
            val r = e.row
            if (e.k == 0) {
                if (s.slotOf[r] >= 0) {
                    continue
                }
                if (s.picks.size >= 5) {
                    s.nopeAt = e.t
                    s.nopeRow = r
                    continue
                }
                val slot = s.picks.size
                s.picks.add(r)
                s.slotOf[r] = slot
                s.slotFrom[r] = slot
                s.slotAt[r] = NEVER
                s.checked[r] = true
                s.checkAt[r] = e.t
                s.flights.add(Flight(r, -1, slot, e.t, 1))
                s.countFrom = s.picks.size - 1
                s.countAt = e.t
                if (s.picks.size == 1) {
                    s.panelUp = true
                    s.panelAt = e.t
                }
            } else {
                val slot = s.slotOf[r]
                if (slot < 0) {
                    continue
                }
                s.picks.removeAt(slot)
                s.slotOf[r] = -1
                s.checked[r] = false
                s.checkAt[r] = e.t
                s.flights.add(Flight(r, slot, -1, e.t, -1))
                for (k in slot until s.picks.size) {
                    val q = s.picks[k]
                    s.slotFrom[q] = k + 1
                    s.slotOf[q] = k
                    s.slotAt[q] = e.t
                }
                s.countFrom = s.picks.size + 1
                s.countAt = e.t
                if (s.picks.isEmpty()) {
                    s.panelUp = false
                    s.panelAt = e.t + FLY * 0.6
                }
            }
        }
        s.flights = s.flights.filter {
            now - it.at < FLY + 0.35
        }.toMutableList()
        return s
    }

    data class Frame(val amb: Double, val now: Double, val s: State, val scroll: Double, val sparkle: Double, val press: Double = 0.0)

    fun frameAt(t: Double): Frame {
        val s = replay(LOOP_EVENTS, t)
        return Frame(t, t, s, scrollOf(s, t), if (t >= SPARKLE_AT)
            t - SPARKLE_AT else -1.0)
    }

    fun panelK(f: Frame): Double {
        if (f.s.panelAt <= NEVER) {
            return 0.0
        }
        val k = spring(f.now - f.s.panelAt, 0.5, 0.86)
        return if (f.s.panelUp) k else 1 - k
    }

    fun slotPos(i: Int, k: Double): Pair<Double, Double> {
        val u = CashbackPickFiveTheme.UI
        return Pair(u.buttonX + u.buttonW - 48 - (4 - i) * 52, mix(u.disabledY + u.disabledH / 2, u.buttonY + u.buttonH / 2, k))
    }
}
