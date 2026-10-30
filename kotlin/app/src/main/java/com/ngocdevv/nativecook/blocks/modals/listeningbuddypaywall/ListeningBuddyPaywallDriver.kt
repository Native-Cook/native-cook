package com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall

import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.REST_PLAN
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.LOOP
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.RESUME
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.Frame
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.frameAt
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.smooth

/** All touch timestamps share the clamped, lifecycle-paused frame clock. */
internal class ListeningBuddyPaywallDriver {
    var t = 0.0
    var mode = 0
    var last = -10.0
    var amb = 0.0
    var plan = REST_PLAN
    var from = REST_PLAN
    var selAt = -10.0
    var goAt = -10.0
    var pokeAt = -10.0
    var pressing = 0
    var pressAt = -10.0
    var releaseAt = -10.0
    var released = 0
    fun takeOver(now: Double) {
        last = now
        if (mode == 2) return
        val f = frameAt(t)
        mode = 2
        plan = f.plan
        from = f.from
        selAt = now - f.selAt
        goAt = if (f.go >= 0) now - f.go else -10.0
    }
    fun select(target: Int, now: Double): Boolean {
        if (target == plan) return false
        from = plan
        plan = target
        selAt = now
        return true
    }
    fun step(dt: Double, now: Double, auto: Boolean) {
        amb = (amb + dt) % LOOP
        if (mode == 0) {
            if (auto) t = (t + dt) % LOOP
            return
        }
        if (!auto || pressing > 0 || now - last < RESUME) return
        if (plan != REST_PLAN) {
            select(REST_PLAN, now)
            last = now - RESUME + .9
            return
        }
        if (now - selAt > 1 && now - goAt > 2.5 && now - pokeAt > 1) {
            mode = 0
            t = 0.0
        }
    }
    fun frame(now: Double): Frame {
        if (mode == 0) return frameAt(t)
        val press = if (pressing == 1) smooth((now - pressAt) / .15) else if (released == 1) 1 - smooth((now - releaseAt) / .15) else 0.0
        val close = if (pressing == 2) smooth((now - pressAt) / .15) else if (released == 2) 1 - smooth((now - releaseAt) / .15) else 0.0
        return Frame(amb, plan, from, now - selAt, if (now - goAt < 2.5) now - goAt else -1.0, if (now - pokeAt < 1) now - pokeAt else -1.0, press, close)
    }
}
