package com.ngocdevv.nativecook.blocks.cards.pixeltrophyunlock

internal class PixelTrophyUnlockDriver {
    var t = 0.0;var now = 0.0;var last = -10.0;var a0 = -100.0;var b0 = -100.0
    var shake = -1;var shakeAt = -10.0;var landed = false;var flying = false
    var channels = PixelTrophyUnlockMotion.channels(-1.0,-1.0)
    fun step(dt: Double,auto: Boolean,reduced: Boolean,cue: (String)->Unit,buzz: (Int)->Unit) {
        now += dt
        var a = -1.0;var b = -1.0
        if(auto && now-last>=2 && a0 < -50) {
            t = (t+dt)%PixelTrophyUnlockMotion.LOOP;a = if(t>=1)t-1 else -1.0;b = if(t>=6.6)t-6.6 else -1.0
        } else {
            a = if(a0 > -50)now-a0 else -1.0;b = if(b0 > -50)now-b0 else -1.0
            if(b>1.4) { a0 = -100.0;b0 = -100.0 }
        }
        channels = PixelTrophyUnlockMotion.channels(a,b,reduced)
        val c = channels
        if(c.earned && !landed && a0 > -50) { cue("land");buzz(2) }
        val flight = c.fly>0.05 && c.fly<0.95
        if(flight && !flying && a0 > -50)cue("whoosh")
        landed = c.earned;flying = flight
    }
    fun tap(index: Int?,cue: (String)->Unit,buzz: (Int)->Unit) {
        last = now
        when(index) {
            1 -> if(!channels.earned && a0 < -50) { a0 = now;b0 = -100.0;cue("unlock");buzz(1) }
                 else if(channels.earned) { if(a0 < -50)a0 = now-3;b0 = now;buzz(0) }
            null,0 -> Unit
            else -> { shake = index;shakeAt = now;buzz(3) }
        }
    }
    fun moving() = a0 > -50 && (now-a0<2.35 || b0 > -50 && now-b0<1.45) || now-shakeAt<0.45 || now-last<2
}
