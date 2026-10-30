package com.ngocdevv.nativecook.blocks.charts.savingsfuturesim

import kotlin.math.*

/** Hit regions and finalize tolerances copied from SavingsFutureSim.tsx. */
internal class SavingsFutureSimDriver {
    val state = SavingsFutureSimMotion.Driver()
    var now = 0.0
    private var lastStep = 0.0
    fun frame(reduced: Boolean): SavingsFutureSimMotion.Frame {
        val f = state.frameOf(now)
        return if (reduced) f.copy(amb = 0.0,morph = if (f.open) 1.0 else 0.0) else f
    }
    private fun yearAt(x: Double) = (((x-92)/552)*30).coerceIn(1.0,30.0)
    fun begin(x: Double, y: Double) {
        state.takeOver(now); state.pressing = 0
        if (x > 80 && x < 656 && y > 1097 && y < 1204) {
            state.pressing = 1; state.pressAt = now
        } else if (state.open && y > 552 && y < 832) {
            state.pressing = 2; state.closeAt = -10.0; state.year = yearAt(x); lastStep = floor(state.year)
        } else if (!state.open) {
            val angle = PI+PI*SavingsFutureSimTheme.rateFrac(state.rate)
            if (hypot(x-(368+260*cos(angle)),y-(770+260*sin(angle))) < 70) {
                state.pressing = 3; lastStep = state.rate
            }
        }
    }
    fun move(x: Double, y: Double): Boolean {
        state.last = now
        if (state.pressing == 2) {
            state.year = yearAt(x)
            val year = floor(state.year)
            if (year != lastStep) { lastStep = year; return true }
        } else if (state.pressing == 3) {
            var angle = atan2(y-770,x-368)
            if (angle > PI/2) angle -= 2*PI
            val u = ((angle+PI)/PI).coerceIn(0.0,1.0)
            val rate = (floor((4+(u-0.1)/0.125)*4+0.5)/4).coerceIn(4.0,8.0)
            if (rate != lastStep) { lastStep = rate; state.rate = rate; return true }
        }
        return false
    }
    fun end(x: Double, y: Double, reduced: Boolean): String? {
        state.last = now
        val was = state.pressing; state.pressing = 0
        if (was == 1) {
            state.releaseAt = now
            if (x < 60 || x > 676 || y < 1075 || y > 1226) return null
            state.setOpen(!state.open,if (reduced) now-2 else now)
            if (state.open) state.year = 1.0
            return if (state.open) "unfurl" else "furl"
        } else if (was == 2) { state.closeAt = now+1.2 }
        return null
    }
}
