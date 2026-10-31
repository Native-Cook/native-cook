package com.ngocdevv.nativecook.blocks.onboarding.haloringpairing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*
import kotlin.reflect.KMutableProperty1

private data class HaloRingPairingTrack(
    val from: Double, val to: Double, val start: Double, val duration: Double, val curve: Int = 0,
    val omega0: Double = 0.0, val velocity0: Double = 0.0,
) {
    fun spring(t: Double): Pair<Double,Double> {
        val x = max(0.0,t-start); val x0 = from-to; val zeta = 0.6; val wd = omega0*0.8
        if (wd == 0.0) return Pair(to,0.0)
        val envelope = exp(-zeta*omega0*x)
        val position = envelope*(sin(wd*x)*(velocity0+zeta*omega0*x0)/wd+x0*cos(wd*x))
        val velocity = -zeta*omega0*position+envelope*(cos(wd*x)*(velocity0+zeta*omega0*x0)-wd*x0*sin(wd*x))
        val energy = (omega0*omega0*position*position+velocity*velocity)/max(1e-30,omega0*omega0*x0*x0)
        return if (energy <= 6e-9) Pair(to,0.0) else Pair(to+position,velocity)
    }
    fun value(t: Double): Double {
        val u = HaloRingPairingMotion.seg(t,start,start+duration)
        val v = when (curve) {
            1 -> HaloRingPairingMotion.bezier(u,0.77,0.0,0.175,1.0)
            2 -> HaloRingPairingMotion.bezier(u,0.23,1.0,0.32,1.0)
            3 -> u
            4 -> return spring(t).first
            else -> if (u < 0.5) 2*u*u else 1-(-2*u+2).pow(2)/2
        }
        return from+(to-from)*v
    }
}

internal class HaloRingPairingDriver(val auto: Boolean, val reduced: Boolean) {
    var holding = false
    private var manual = !auto
    private var base = HaloRingPairingFrame()
    private val tracks = mutableMapOf<KMutableProperty1<HaloRingPairingFrame,Double>,HaloRingPairingTrack>()
    private var lastTouch = 0.0
    private var foundAt = 1.8
    private var phaseOffset = 0.0
    private var manualStep = 0
    private var dragYaw = 0.0
    private var spinning = false
    private var decayStart = 0.0
    private var decayVelocity = 0.0
    private var decayPosition = 0.0
    private var decayLast = 0.0
    private var decayDone = false
    private var hasDecay = false
    var foundHapticAt: Double? by mutableStateOf(if (auto) null else 1.8)
        private set
    init { if (manual) searching(0.0) }
    private fun animate(key: KMutableProperty1<HaloRingPairingFrame,Double>, to: Double, t: Double, duration: Double, curve: Int = 0, delay: Double = 0.0) {
        val old = tracks[key]?.value(t) ?: key.get(base)
        var v0 = if (tracks[key]?.curve == 4) tracks[key]!!.spring(t).second else 0.0
        if ((to > old && v0 < 0) || (to < old && v0 > 0)) v0 = 0.0
        tracks[key] = HaloRingPairingTrack(old,to,t+delay,duration,curve,
            if (curve == 4) HaloRingPairingMotion.springOmega(old-to,v0,duration) else 0.0,v0)
    }
    private fun searching(t: Double) {
        foundAt = t+1.8
        animate(HaloRingPairingFrame::condense,1.0,foundAt,if (reduced) 0.25 else 1.2,1)
        animate(HaloRingPairingFrame::search,0.0,foundAt,0.6,2)
        animate(HaloRingPairingFrame::found,1.0,foundAt,0.4,2,if (reduced) 0.0 else 0.7)
    }
    private fun manualFrame(t: Double): HaloRingPairingFrame {
        val f = base.copy()
        tracks.forEach { (key,track) -> key.set(f,track.value(t)) }
        if (hasDecay) {
            if (t > decayLast && !decayDone) {
                val dt = (t-decayLast).coerceIn(0.0,0.064)
                decayVelocity *= exp(-(1-0.991)*(t-decayStart)*1000*0.1)
                decayPosition += decayVelocity*dt
                decayLast = t
                decayDone = abs(decayVelocity) < 1
            }
            f.yaw = decayPosition
        }
        f.phase = if (reduced) 0.0 else phaseOffset+2*PI*t/11
        return f
    }
    fun frame(t: Double): HaloRingPairingFrame {
        if (!manual) return HaloRingPairingMotion.frameAt(t%11)
        val resume = max(lastTouch,if (manualStep == 0) foundAt else lastTouch)+2
        if (auto && !holding && !spinning && t >= resume) {
            val u = t-resume
            if (u >= 1.7) return HaloRingPairingMotion.frameAt((u-1.7)%11)
            val f = manualFrame(resume)
            f.fly *= 1-HaloRingPairingMotion.bezier(u/0.7,0.77,0.0,0.175,1.0)
            f.paired *= 1-HaloRingPairingMotion.defaultEase(u/0.3)
            f.found *= 1-HaloRingPairingMotion.defaultEase(u/0.3)
            val goalYaw = 0.3+2*PI*floor((f.yaw-0.3)/(2*PI)+0.5)
            f.yaw += (goalYaw-f.yaw)*HaloRingPairingMotion.bezier(u/0.9,0.77,0.0,0.175,1.0)
            f.scale += (1-f.scale)*HaloRingPairingMotion.defaultEase(u/0.5)
            f.size = 9
            f.condense *= 1-HaloRingPairingMotion.bezier((u-0.6)/0.8,0.77,0.0,0.175,1.0)
            f.search += (1-f.search)*HaloRingPairingMotion.defaultEase((u-1)/0.7)
            return f
        }
        return manualFrame(t)
    }
    fun takeOver(t: Double) {
        val f = frame(t)
        val resume = max(lastTouch,if (manualStep == 0) foundAt else lastTouch)+2
        if (manual && !(auto && !holding && !spinning && t >= resume)) {
            if (manualStep == 0 && t >= foundAt) manualStep = 1
            lastTouch = t; return
        }
        val wasManual = manual
        hasDecay = false
        phaseOffset = f.phase-2*PI*t/11
        base = f; tracks.clear(); decayVelocity = 0.0; manual = true
        manualStep = f.step; lastTouch = t
        animate(HaloRingPairingFrame::finger,0.0,t,0.15)
        animate(HaloRingPairingFrame::pressPair,0.0,t,0.15)
        if (manualStep == 0) {
            val delay = if (wasManual) max(0.0,foundAt-t) else 0.0
            foundAt = t+delay
            foundHapticAt = foundAt
            animate(HaloRingPairingFrame::condense,1.0,t,if (reduced) 0.25 else 1.2,1,delay)
            animate(HaloRingPairingFrame::search,0.0,t,0.6,2,delay)
            animate(HaloRingPairingFrame::found,1.0,t,0.4,2,delay+if (reduced) 0.0 else 0.7)
        } else {
            animate(HaloRingPairingFrame::condense,1.0,t,0.3)
            animate(HaloRingPairingFrame::search,0.0,t,0.3)
            animate(HaloRingPairingFrame::fly,if (manualStep == 2) 1.0 else 0.0,t,0.3)
            animate(HaloRingPairingFrame::paired,if (manualStep == 2) 1.0 else 0.0,t,0.3)
            if (manualStep == 1) animate(HaloRingPairingFrame::found,1.0,t,0.3)
        }
    }
    fun begin(t: Double) {
        takeOver(t); holding = true; dragYaw = frame(t).yaw; spinning = false
    }
    fun move(t: Double,dx: Double,dy: Double) {
        lastTouch = t
        if (manualStep != 1 && frame(t).step != 1) return
        if (!spinning && hypot(dx,dy) < 3) return
        if (!spinning) {
            dragYaw = frame(t).yaw
            base.yaw = dragYaw; tracks.remove(HaloRingPairingFrame::yaw); hasDecay = false; decayVelocity = 0.0
        }
        spinning = true; manualStep = 1; tracks.remove(HaloRingPairingFrame::yaw)
        base.yaw = dragYaw+dx*0.012
    }
    fun end(t: Double,velocityX: Double) {
        holding = false; lastTouch = t
        if (spinning && !reduced) {
            decayVelocity = (velocityX*0.012).coerceIn(-12.0,12.0); decayStart = t
            decayPosition = base.yaw; decayLast = t; decayDone = false; hasDecay = true
        }
        spinning = false
    }
    fun choose(size: Int,t: Double): Boolean {
        takeOver(t)
        if (manualStep != 1 || base.size == size) return false
        base.size = size
        animate(HaloRingPairingFrame::scale,HaloRingPairingMotion.scaleOf(size),t,if (reduced) 0.2 else 0.45,if (reduced) 0 else 4)
        if (!reduced) { tracks.remove(HaloRingPairingFrame::bumpProgress); base.bumpProgress = 0.0; animate(HaloRingPairingFrame::bumpProgress,1.0,t,0.7,3) }
        return true
    }
    fun pair(t: Double): Boolean {
        takeOver(t); if (manualStep != 1) return false
        manualStep = 2
        base.yaw = frame(t).yaw; tracks.remove(HaloRingPairingFrame::yaw); hasDecay = false; decayVelocity = 0.0
        animate(HaloRingPairingFrame::fly,1.0,t,if (reduced) 0.3 else 0.8,1)
        animate(HaloRingPairingFrame::paired,1.0,t,0.5,2,if (reduced) 0.0 else 0.3)
        animate(HaloRingPairingFrame::found,0.0,t,0.25)
        return true
    }
    fun unpair(t: Double) {
        takeOver(t); if (manualStep != 2) return
        manualStep = 1
        animate(HaloRingPairingFrame::fly,0.0,t,if (reduced) 0.3 else 0.7,1)
        animate(HaloRingPairingFrame::paired,0.0,t,0.3)
        animate(HaloRingPairingFrame::found,1.0,t,0.4,0,if (reduced) 0.0 else 0.4)
    }
    fun idleAt(t: Double) = reduced && !holding && t > max(foundAt+0.6,lastTouch+1.5)
}
