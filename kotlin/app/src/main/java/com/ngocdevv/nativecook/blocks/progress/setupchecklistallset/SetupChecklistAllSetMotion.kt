package com.ngocdevv.nativecook.blocks.progress.setupchecklistallset

import kotlin.math.*

internal object SetupChecklistAllSetMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val N = 6
    const val NEVER = -100.0
    object D {
        const val tick = 0.42
        const val morph = 0.55
        const val bloom = 0.6
        const val leave = 0.5
        const val enter = 0.7
    }
    data class State(
        val done: BooleanArray = booleanArrayOf(false, false, false, false, false, true),
        val doneAt: DoubleArray = DoubleArray(N) { NEVER },
        val open: BooleanArray = booleanArrayOf(true, false, false, false, false, false),
        val openAt: DoubleArray = DoubleArray(N) { NEVER },
        var pressAt: Double = NEVER, var pressRow: Int = -1,
        var allSetAt: Double = NEVER, var leaveAt: Double = NEVER,
        var backAt: Double = NEVER, var reset: Boolean = false,
        var ctaAt: Double = NEVER, var laterAt: Double = NEVER,
    ) {
        fun snapshot() = copy(done = done.copyOf(), doneAt = doneAt.copyOf(), open = open.copyOf(), openAt = openAt.copyOf())
    }
    data class Live(val now: Double, val amb: Double, val s: State)
    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t-a)/(b-a))
    fun mix(a: Double, b: Double, p: Double) = a+(b-a)*p
    fun easeOut(x: Double) = 1-(1-clamp01(x)).pow(3)
    fun easeInOut(x: Double): Double {
        val p = clamp01(x)
        return if (p < 0.5) 4*p*p*p else 1-(-2*p+2).pow(3)/2
    }
    fun smooth(x: Double): Double { val p = clamp01(x); return p*p*(3-2*p) }
    fun spring(tau: Double, response: Double, damping: Double): Double {
        if (tau <= 0) return 0.0
        val w = 2*PI/response
        val zw = damping*w
        val wd = w*sqrt(1-damping*damping)
        return 1-exp(-zw*tau)*(cos(wd*tau)+(zw/wd)*sin(wd*tau))
    }
    fun loopState(t: Double): State {
        val s = State()
        if (t >= 7.9) { s.backAt = 7.9; s.leaveAt = 7.2; return s }
        val done = doubleArrayOf(0.7, 1.45, 2.15, 2.85, 3.55)
        val press = doubleArrayOf(0.52, 1.27, 1.97, 2.67, 3.37)
        for (i in 0 until 5) {
            val dn = done[i]
            if (t >= dn) { s.done[i] = true; s.doneAt[i] = dn }
            if (t >= press[i]) { s.pressAt = press[i]; s.pressRow = i }
            val openT = if (i == 0) NEVER else done[i-1]+0.24
            val closeT = dn+0.16
            if (t >= closeT) { s.open[i] = false; s.openAt[i] = closeT }
            else if (t >= openT) { s.open[i] = true; s.openAt[i] = openT }
            else { s.open[i] = false; s.openAt[i] = NEVER }
        }
        if (t >= 3.95) s.allSetAt = 3.95
        if (t >= 7) s.laterAt = 7.0
        if (t >= 7.2) { s.leaveAt = 7.2; s.backAt = 7.9; s.reset = true }
        return s
    }
    fun openness(s: State, i: Int, now: Double): Double {
        val k = if (s.openAt[i] <= NEVER) 1.0 else if (s.open[i]) spring(now-s.openAt[i], 0.42, 0.86) else spring(now-s.openAt[i], 0.36, 0.92)
        return if (s.open[i]) k else 1-k
    }
    fun doneness(s: State, i: Int, now: Double): Double {
        if (s.doneAt[i] <= NEVER) return if (s.done[i]) 1.0 else 0.0
        return if (s.done[i]) clamp01((now-s.doneAt[i])/D.tick) else 1-clamp01((now-s.doneAt[i])/0.22)
    }
    fun countRolled(s: State, now: Double): Double {
        var value = 0.0
        for (i in 0 until N) {
            val k = if (s.doneAt[i] <= NEVER) 1.0 else smooth((now-s.doneAt[i]-0.08)/0.3)
            value += if (s.done[i]) k else 1-k
        }
        return value
    }
    class Driver {
        var t = 0.0
        var mode = 0
        var last = -10.0
        var amb = 0.0
        var s = State()
        fun step(dt: Double, now: Double, auto: Boolean) {
            amb = (amb+dt)%LOOP
            if (mode == 0) { if (auto) t = (t+dt)%LOOP; return }
            if (s.reset && s.backAt > NEVER && now >= s.backAt) s = State(leaveAt = s.leaveAt, backAt = s.backAt)
            if (!auto || now-last < RESUME) return
            if (s.leaveAt > NEVER && now < s.backAt+D.enter) return
            val fresh = s.allSetAt <= NEVER && (0 until N).all { s.done[it] == (it == 5) && s.open[it] == (it == 0) }
            if (fresh && openness(s, 0, now) > 0.999) { mode = 0; t = 0.0 }
            else if (now-last >= RESUME+2) { s.leaveAt = now; s.backAt = now+D.leave+0.2; s.reset = true }
        }
        fun takeOver(now: Double) {
            last = now
            if (mode == 2) return
            val state = loopState(t)
            val k = now-t
            fun shift(x: Double) = if (x <= NEVER) x else x+k
            for (i in 0 until N) { state.doneAt[i] = shift(state.doneAt[i]); state.openAt[i] = shift(state.openAt[i]) }
            state.pressAt = shift(state.pressAt); state.allSetAt = shift(state.allSetAt)
            state.laterAt = shift(state.laterAt); state.leaveAt = shift(state.leaveAt); state.backAt = shift(state.backAt)
            if (state.backAt > NEVER && now >= state.backAt) state.reset = false
            if (state.allSetAt <= NEVER && state.leaveAt <= NEVER) {
                var next = -1
                var anyOpen = false
                for (i in 0 until N) {
                    if (state.open[i] && state.done[i]) { state.open[i] = false; state.openAt[i] = max(now, state.doneAt[i]+0.16) }
                    if (state.open[i]) anyOpen = true
                    if (next < 0 && !state.done[i]) next = i
                }
                if (next < 0) state.allSetAt = now+0.3
                else if (!anyOpen) { state.open[next] = true; state.openAt[next] = now+0.08 }
            }
            s = state; mode = 2
        }
        fun toggle(i: Int, now: Double): String {
            if (s.done[i]) { s.done[i] = false; s.doneAt[i] = now; return "uncheck" }
            s.done[i] = true; s.doneAt[i] = now; s.pressAt = now; s.pressRow = i
            if (s.open[i]) { s.open[i] = false; s.openAt[i] = now+0.16 }
            var next = -1
            for (k in 1..N) { val j = (i+k)%N; if (!s.done[j]) { next = j; break } }
            if (next < 0) { s.allSetAt = now+0.4; return "complete" }
            if (!s.open[next]) { s.open[next] = true; s.openAt[next] = now+0.24 }
            return "check"
        }
        fun toggleRow(i: Int, now: Double) {
            if (s.open[i]) { s.open[i] = false; s.openAt[i] = now; return }
            for (j in 0 until N) if (j != i && s.open[j]) { s.open[j] = false; s.openAt[j] = now }
            s.open[i] = true; s.openAt[i] = now
        }
        fun skip(i: Int, now: Double) {
            s.open[i] = false; s.openAt[i] = now
            for (k in 1 until N) { val j = (i+k)%N; if (!s.done[j]) { s.open[j] = true; s.openAt[j] = now+0.12; return } }
        }
        fun dismiss(now: Double, fresh: Boolean): Boolean {
            if (s.leaveAt > NEVER && now < s.backAt+D.enter) return false
            s.leaveAt = now; s.backAt = now+D.leave+0.35; s.reset = fresh
            return true
        }
        fun hit(x: Double, y: Double, now: Double): Pair<Int, Int> {
            if (s.leaveAt > NEVER && now < s.backAt+D.enter) return 0 to -1
            if (s.allSetAt > NEVER) {
                if (now-s.allSetAt < D.morph+D.bloom) return 0 to -1
                if (x > 16 && x < 386 && y > 683 && y < 746) return 6 to -1
                if (abs(y-775.5) < 24 && abs(x-201) < 80) return 7 to -1
                return 0 to -1
            }
            if (hypot(x-371,y-338) < 24) return 5 to -1
            var top = 396.5
            for (i in 0 until N) {
                val e = openness(s,i,now)
                val height = 54.3+115.5*e
                if (y >= top && y < top+height) {
                    if (y < top+54.3) return (if (x < 46) 1 else 2) to i
                    if (e > 0.6 && abs(y-(top+54.3/2+100.5)) < 22) {
                        val width = 26+SetupChecklistAllSetTheme.items[i].action.length*8.4
                        if (x > 49.4-6 && x < 49.4+width+6) return 3 to i
                        if (x > 49.4+width+10 && x < 49.4+width+90) return 4 to i
                    }
                    return 0 to i
                }
                top += height
            }
            return 0 to -1
        }
    }
}
