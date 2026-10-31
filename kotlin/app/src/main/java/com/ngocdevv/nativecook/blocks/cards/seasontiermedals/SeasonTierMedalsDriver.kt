package com.ngocdevv.nativecook.blocks.cards.seasontiermedals

import com.ngocdevv.nativecook.blocks.cards.seasontiermedals.SeasonTierMedalsMotion as M
import kotlin.math.*

internal class SeasonTierMedalsDriver {
    val rail = M.Rail()
    var time = 0.0; var now = 0.0; var last = -10.0
    private var startP = 1.0; private var at = 0.0
    private var detent = 1; private var byHand = false
    var audio: ((String) -> Unit)? = null
    var buzz: ((Int) -> Unit)? = null
    val current get() = M.round(rail.p).coerceIn(0, 2)
    val moving get() = rail.mode == 1 || abs(rail.p - rail.target) > 0.000001 || abs(rail.v) > 0.000001 || rail.spins.any { now - it < M.SPIN + 1.2 }
    fun step(dt: Double, auto: Boolean, reduced: Boolean) {
        now += dt
        if (auto && !reduced && rail.mode != 1 && now - last >= 2) {
            time += dt; if (time >= M.LOOP) time -= M.LOOP
            rail.script(time, dt, now, reduced)
        } else rail.step(dt)
        if (byHand) for (i in 0..2) {
            val since = now - rail.spins[i]
            if (since >= M.SPIN * 0.78 && since < M.SPIN && rail.landed != i) {
                rail.landed = i; audio?.invoke("land"); buzz?.invoke(1)
            }
        }
    }
    private fun go(target: Int, spin: Boolean, reduced: Boolean): Boolean {
        val tg = target.coerceIn(0, 2); val changed = tg != rail.target
        rail.target = tg; rail.stiff = if (reduced) 260.0 else 150.0
        if ((changed || spin) && !reduced) rail.spin(tg, now)
        return changed
    }
    fun adjust(dir: Int, reduced: Boolean) { last = now; if (go(rail.target + dir, false, reduced)) buzz?.invoke(0) }
    fun begin() {
        last = now; startP = rail.p; at = now; detent = M.round(rail.p); byHand = true
        rail.mode = 1; rail.finger = rail.p
    }
    fun drag(dx: Double, sp: Double) {
        last = now; rail.finger = M.rubber(startP - dx / sp)
        val next = M.round(rail.finger.coerceIn(0.0, 2.0))
        if (next != detent) { detent = next; audio?.invoke("tick"); buzz?.invoke(0) }
    }
    fun end(x: Double, localY: Double, dx: Double, vx: Double, w: Double, my: Double, reduced: Boolean) {
        last = now; rail.mode = 0
        val sp = min(172.0, w * 0.43); val rc = min(84.0, w * 0.21)
        if (abs(dx) <= 8 && now - at < 0.35) {
            if (abs(localY - my) < rc * 1.2) {
                val pick = M.round(rail.p + M.round((x - w / 2) / sp).coerceIn(-1, 1))
                val changed = go(pick, true, reduced)
                audio?.invoke(if (changed) "slide" else "tick"); buzz?.invoke(if (changed) 0 else 2)
            }
            return
        }
        rail.v = -vx / sp
        if (go(M.round(rail.p + rail.v * 0.16), false, reduced)) audio?.invoke("slide")
    }
}
