package com.ngocdevv.nativecook.blocks.gestures.cabinclimatecar

import com.ngocdevv.nativecook.blocks.gestures.cabinclimatecar.CabinClimateCarMotion as M

/** Same two-mode driver, including the degree-by-degree homing and hold cadence. */
internal class CabinClimateCarDriver {
    var t = 0.0; var last = -10.0; var amb = 0.0; var mode = 0
    var temp = 70.0; var from = 70.0; var tempAt = -10.0; var heatFrom = 70.0
    var leg = false; var legAt = -10.0; var legFrom = 0.0
    var fan = 0; var fanAt = -10.0; var pressing = 0
    var pressAt = -10.0; var releaseAt = -10.0; var repeatAt = -10.0
    var released = 0; var homing = 0
    fun takeOver(now: Double) {
        last = now; homing = 0
        if (mode == 2) return
        val f = M.frameAt(t)
        mode = 2; temp = f.temp; from = f.from; tempAt = now-f.tempAt; heatFrom = f.from
        leg = f.legOn; legAt = now-f.legAt; legFrom = if (f.legOn) 0.0 else 1.0; fan = 0
    }
    fun heatOf(now: Double) = M.mix(heatFrom,temp,M.easeOut((now-tempAt)/0.9))
    fun setTemp(value: Double,now: Double): Boolean {
        val next = value.coerceIn(60.0,80.0)
        if (next == temp) return false
        heatFrom = heatOf(now); from = temp; temp = next; tempAt = now
        return true
    }
    fun toggleLeg(now: Double) { leg = !leg; legAt = now }
    fun step(dt: Double,now: Double,auto: Boolean): Boolean {
        amb = (amb+dt)%M.LOOP
        if (mode == 0) { if (auto) t = (t+dt)%M.LOOP; return false }
        if ((pressing == 1 || pressing == 2) && now-pressAt > 0.45 && now-repeatAt > 0.16) {
            repeatAt = now
            return setTemp(temp+(if (pressing == 1) 1 else -1),now)
        }
        if (!auto || pressing > 0 || now-last < M.RESUME) return false
        homing = 1
        if (leg) toggleLeg(now)
        if (fan != 0) { fan = 0; fanAt = now }
        if (temp != 70.0 && now-tempAt > 0.22) {
            setTemp(temp+(if (temp > 70) -1 else 1),now)
            return false
        }
        if (temp == 70.0 && now-tempAt > 1 && now-legAt > 0.8 && now-fanAt > 0.5) {
            mode = 0; t = 0.0; homing = 0
        }
        return false
    }
    fun frame(now: Double): M.Frame {
        if (mode == 0) return M.frameAt(t)
        fun pk(id: Int) = if (pressing == id) M.smooth((now-pressAt)/0.15)
            else if (released == id) 1-M.smooth((now-releaseAt)/0.15) else 0.0
        val lk = M.spring(now-legAt)
        val card = if (pressing in 3..5) pressing else released
        return M.Frame(amb,temp,from,now-tempAt,heatOf(now),pk(1),pk(2),
            if (leg) lk else 1-lk,leg,now-legAt,fan,now-fanAt,
            if (card in 3..5) pk(card) else 0.0,card)
    }
}
