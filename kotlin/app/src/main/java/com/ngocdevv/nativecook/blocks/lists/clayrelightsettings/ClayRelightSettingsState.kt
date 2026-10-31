package com.ngocdevv.nativecook.blocks.lists.clayrelightsettings

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*

internal class ClayRelightSettingsState(val autoPlay: Boolean) {
    data class Tween(val from: Double, val to: Double, val start: Double, var duration: Double,
                     val curve: Int = 0, var velocity: Double = 0.0, var omega: Double = 0.0) {
        val end get() = start+duration
        fun configureSpring() {
            val x0 = from-to
            val requestedVelocity = velocity
            if ((to > from && velocity < 0) || (to < from && velocity > 0)) velocity = 0.0
            val settling = duration*1.5
            fun fraction(stiffness: Double, t: Double): Double {
                val w = sqrt(stiffness/4)
                val a = velocity+x0*w
                val decay = exp(-w*t)
                val x = (x0+a*t)*decay
                val v = decay*(a-w*(x0+a*t))
                val initial = 0.5*stiffness*x0*x0+2*velocity*velocity
                return if (initial == 0.0) 0.0 else (0.5*stiffness*x*x+2*v*v)/initial
            }
            var lo = Math.ulp(1.0)
            var hi = 8000.0
            var stiffness = 4000.0
            for (i in 0 until 100) {
                val diff = fraction(stiffness,settling)-6e-9
                if (abs(diff) <= 6e-12) break
                if (diff > 0) lo = stiffness else hi = stiffness
                stiffness = (lo+hi)/2
            }
            omega = sqrt(stiffness/4)
            val initialEnergy = 0.5*stiffness*x0*x0+2*requestedVelocity*requestedVelocity
            fun finished(t: Double): Boolean {
                val a = velocity+omega*x0
                val decay = exp(-omega*t)
                val x = (x0+a*t)*decay
                val v = decay*(a-omega*(x0+a*t))
                val position = to+x
                return position < min(from,to) || position > max(from,to) || initialEnergy == 0.0 ||
                    (0.5*stiffness*x*x+2*v*v)/initialEnergy <= 6e-9
            }
            lo = 0.0; hi = settling
            repeat(40) {
                val mid = (lo+hi)/2
                if (finished(mid)) hi = mid else lo = mid
            }
            duration = hi
        }
        val activityEnd: Double get() {
            if (abs(from-to) <= 0.002) return start
            var lo = start
            var hi = end
            repeat(30) {
                val mid = (lo+hi)/2
                if (abs(value(mid)-to) <= 0.002) hi = mid else lo = mid
            }
            return hi
        }
        fun value(now: Double): Double {
            val x = ClayRelightSettingsMotion.seg(now,start,start+max(0.0001,duration))
            if (x <= 0) return from
            if (x >= 1) return to
            val k = when (curve) {
                1 -> ClayRelightSettingsMotion.bezier(x,0.23,1.0,0.32,1.0)
                2 -> ClayRelightSettingsMotion.bezier(x,0.77,0.0,0.175,1.0)
                3 -> ClayRelightSettingsMotion.bezier(x,0.45,0.0,0.55,1.0)
                5 -> if (x < 0.5) 2*x*x else 1-(-2*x+2).pow(2)/2
                4 -> {
                    val t = now-start
                    return (to+(from-to+(velocity+omega*(from-to))*t)*exp(-omega*t)).coerceIn(min(from,to),max(from,to))
                }
                else -> x
            }
            return from+(to-from)*k
        }
        companion object { fun still(v: Double) = Tween(v,v,0.0,0.0) }
    }
    private val epoch = SystemClock.uptimeMillis()/1000.0
    private var pausedAt: Double? = null
    private var pausedDuration = 0.0
    fun time() = (pausedAt ?: (SystemClock.uptimeMillis()/1000.0))-epoch-pausedDuration
    fun setActive(active: Boolean) {
        val now = SystemClock.uptimeMillis()/1000.0
        val began = pausedAt
        if (!active && began == null) pausedAt = now
        else if (active && began != null) { pausedDuration += now-began; pausedAt = null }
    }
    var revision by mutableIntStateOf(0)
    var manual = false
    var dragging = false
    private var themeOn = false
    private var lastTouch = -100.0
    private var dragStart = 0.0
    private var on = 0.0
    private var fingerAt = 0.0
    private var fingerX = 0.0
    var light = Tween.still(0.0)
    var chrome = Tween.still(0.0)
    private var knob = Tween.still(0.0)
    private var press = Tween.still(0.0)
    private var grab = Tween.still(0.0)
    private var finger = Tween.still(0.0)
    fun isUserDriven(now: Double, reduced: Boolean): Boolean {
        if (!manual) return false
        val resume = max(lastTouch,light.activityEnd)+2
        val duration = if (on == 1.0 || light.to > 0.002) 0.5+2.5*light.to else 0.0
        return !(autoPlay && !reduced && !dragging && now >= resume+duration)
    }
    fun snapshot(now: Double, reduced: Boolean): ClayRelightSettingsMotion.Frame {
        if (!manual) {
            return if (autoPlay && !reduced) ClayRelightSettingsMotion.frameAt(now%10) else ClayRelightSettingsMotion.Frame()
        }
        var f = ClayRelightSettingsMotion.Frame(light.value(now),chrome.value(now),on,knob.value(now),press.value(now),finger.value(now),fingerAt,fingerX,grab.value(now))
        val resume = max(lastTouch,light.activityEnd)+2
        if (autoPlay && !reduced && !dragging && now > resume) {
            val duration = if (on == 1.0 || light.to > 0.002) 0.5+2.5*light.to else 0.0
            if (now >= resume+duration) return ClayRelightSettingsMotion.frameAt((now-resume-duration)%10)
            val back = Tween(light.to,0.0,resume,duration,3)
            f = f.copy(m = back.value(now),on = 0.0,knob = Tween(knob.to,0.0,resume,0.28,1).value(now),
                theme = themeDuring(back,chrome.to,themeOn,now))
        }
        return f
    }
    private fun themeDuring(tween: Tween, initial: Double, initialOn: Boolean, now: Double): Double {
        for (i in 0..300) {
            val t = tween.start+tween.duration*i/300
            val m = tween.value(t)
            if (if (initialOn) m < 0.54 else m > 0.62)
                return Tween(initial,if (initialOn) 0.0 else 1.0,t,0.3,2).value(now)
        }
        return initial
    }
    fun takeOver(now: Double, reduced: Boolean) {
        val f = snapshot(now,reduced)
        if (!manual || (autoPlay && now > max(lastTouch,light.activityEnd)+2 && !dragging)) {
            light = Tween.still(f.m); chrome = Tween.still(f.theme); knob = Tween.still(f.knob)
            on = f.on; themeOn = f.theme > 0.5; fingerAt = f.fingerAt; fingerX = f.fingerX
            finger = Tween(f.finger,0.0,now,0.15)
            press = Tween.still(0.0); grab = Tween.still(0.0)
        }
        manual = true; lastTouch = now
    }
    fun setTarget(dest: Double, velocity: Double? = null, reduced: Boolean) {
        val now = time()
        takeOver(now,reduced)
        val startM = light.value(now)
        on = dest
        knob = Tween(knob.value(now),dest,now,0.26,1)
        light = Tween(startM,dest,now,if (reduced) 0.4 else if (velocity == null) 0.5+2.5*abs(dest-startM) else 1.8,
            if (reduced) 5 else if (velocity == null) 3 else 4,velocity ?: 0.0)
        if (light.curve == 4) light.configureSpring()
        val initial = chrome.value(now)
        val state = themeOn
        for (i in 0..300) {
            val t = now+light.duration*i/300
            val m = light.value(t)
            if (if (state) m < 0.54 else m > 0.62) {
                chrome = Tween(initial,if (state) 0.0 else 1.0,t,0.3,2)
                themeOn = !state
                break
            }
        }
        revision++
    }
    fun switchPress(down: Boolean, reduced: Boolean) {
        val now = time()
        takeOver(now,reduced)
        press = Tween(press.value(now),if (down) 1.0 else 0.0,now,if (down) 0.1 else 0.15,1)
        revision++
    }
    fun beginDrag(reduced: Boolean) {
        val now = time()
        takeOver(now,reduced); dragging = true
        dragStart = light.value(now); light = Tween.still(dragStart)
        grab = Tween(grab.value(now),1.0,now,0.15)
        revision++
    }
    fun drag(dx: Double, span: Double) {
        val now = time()
        var v = dragStart+dx/span
        if (v < 0) v = -0.06*(1-exp(v/0.06))
        if (v > 1) v = 1+0.06*(1-exp(-(v-1)/0.06))
        light = Tween.still(v); lastTouch = now
        if ((!themeOn && v > 0.62) || (themeOn && v < 0.54)) {
            themeOn = !themeOn
            chrome = Tween(chrome.value(now),if (themeOn) 1.0 else 0.0,now,0.3,2)
        }
        revision++
    }
    fun endDrag(velocity: Double, span: Double, reduced: Boolean) {
        val now = time()
        val v = velocity/span
        setTarget(if (light.value(now)+0.25*v > 0.5) 1.0 else 0.0,v,reduced)
        dragging = false; lastTouch = now
        grab = Tween(grab.value(now),0.0,now,0.25)
        revision++
    }
}
