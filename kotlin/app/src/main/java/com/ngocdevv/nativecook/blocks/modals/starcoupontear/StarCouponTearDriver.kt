package com.ngocdevv.nativecook.blocks.modals.starcoupontear

import androidx.compose.runtime.*
import kotlin.math.*

internal class StarCouponTearDriver {
    var clock by mutableDoubleStateOf(0.0)
    var manualStart by mutableStateOf<Double?>(null)
    var initial by mutableStateOf(StarCouponTearMotion.Frame())
    var held by mutableStateOf(StarCouponTearMotion.Frame())
    var returnDuration=0.45
    var returnDamping=0.55
    var action by mutableIntStateOf(0)
    var phaseOrigin=0.0
    var dragging=false
    var lastTouch = -100.0
    var reduced=false
    var autoPlay=false
    var hapticAt=Double.POSITIVE_INFINITY
    val settleTime: Double get() = (manualStart ?: 0.0) + when(action) {
        2 -> if (reduced) 1.35 else 2.65
        else -> 0.0
    }
    val busyUntil: Double get() = (manualStart ?: 0.0)+(if(reduced) 1.25 else 2.65)
    fun resumed(t: Double) = autoPlay && !reduced && action!=1 && t>=max(settleTime,lastTouch)+2
    fun frame(t: Double=clock): StarCouponTearMotion.Frame {
        val start=manualStart ?: return if (autoPlay && !reduced) StarCouponTearMotion.frameAt(t%9) else StarCouponTearMotion.Frame()
        if (resumed(t)) return StarCouponTearMotion.frameAt((t-max(settleTime,lastTouch)-2)%9)
        val dt=max(0.0,t-start)
        if (action==1) return held
        if (action==3) return initial.copy(pop=initial.pop+(1-initial.pop)*StarCouponTearMotion.defaultEase(StarCouponTearMotion.seg(dt,0.0,0.3)),finger=initial.finger*(1-StarCouponTearMotion.defaultEase(StarCouponTearMotion.seg(dt,0.0,0.15))))
        if (action==0) return initial.copy(pull=initial.pull*(1-(if(reduced) 1.0 else StarCouponTearMotion.spring(dt,returnDuration,returnDamping))),tear=initial.tear*(1-(if(reduced) 1.0 else StarCouponTearMotion.spring(dt,returnDuration,0.8))),finger=initial.finger*(1-StarCouponTearMotion.defaultEase(StarCouponTearMotion.seg(dt,0.0,0.15))))
        val morph=if (reduced) 0.25 else 0.4
        val wait=if (reduced) 0.0 else 0.45
        val flight=if (reduced) 0.3 else 0.7
        val done=morph+wait+flight
        if (dt<done) return initial.copy(
            pull=initial.pull*(1-(if(reduced) 1.0 else StarCouponTearMotion.spring(dt,0.4,0.6))),
            tear=initial.tear+(1-initial.tear)*StarCouponTearMotion.bezier(dt/morph,0.23,1.0,0.32,1.0),
            fly=initial.fly+(1-initial.fly)*StarCouponTearMotion.bezier((dt-morph-wait)/flight,0.77,0.0,0.175,1.0),
            finger=initial.finger*(1-StarCouponTearMotion.defaultEase(StarCouponTearMotion.seg(dt,0.0,0.15))))
        return StarCouponTearMotion.Frame(pop=StarCouponTearMotion.seg(dt,done+0.5,done+0.5+if(reduced) 0.2 else 0.6),chip=StarCouponTearMotion.seg(dt,done,done+0.8))
    }
    fun phase(t: Double=clock): Double {
        if (reduced) return phaseOrigin
        if (manualStart==null) return 2*PI*(t%9)/9
        if (resumed(t)) return 2*PI*(t-max(settleTime,lastTouch)-2)/9
        return phaseOrigin+2*PI*(t-(manualStart ?: t))/9
    }
    fun takeOver() {
        val t=clock; val wasAuto=manualStart==null || resumed(t)
        val f=frame(t); val p=phase(t); lastTouch=t
        if (wasAuto) {
            manualStart=t; initial=f; held=f; phaseOrigin=p; action=0
            returnDuration=0.4; returnDamping=0.6
            if (f.fly>0.001 || f.tear>0.95) claim() else if (f.pop<1) action=3
        }
    }
    fun claim() {
        val t=clock
        if (action==2 && t<busyUntil && frame(t).fly>0) return
        val f=frame(t); val p=phase(t)
        manualStart=t; initial=f; phaseOrigin=p; action=2; lastTouch=t
        hapticAt=t+if(reduced) 0.55 else 1.55
    }
    fun begin() { takeOver(); dragging=false }
    fun drag(dy: Double): Boolean {
        if (action==2 && clock<busyUntil) return false
        var grab=false
        if (!dragging) {
            if (abs(dy)<=6) return false
            dragging=true; val f=frame(); val p=phase(); manualStart=clock; initial=f; held=f.copy(finger=0.0); phaseOrigin=p; action=1; grab=true
        }
        lastTouch=clock
        var p=max(0.0,dy/150)
        if(p>1) p=1+0.15*(1-exp(-(p-1)/0.15))
        held=held.copy(pull=if(reduced) 0.0 else p,tear=StarCouponTearMotion.tearOf(min(1.0,p)))
        return grab
    }
    fun end(dy: Double,velocity: Double) {
        if (!dragging) return
        dragging=false; lastTouch=clock
        if(dy/150+velocity/150*0.15>0.6) claim()
        else { initial=held; phaseOrigin=phase(); manualStart=clock; returnDuration=0.45; returnDamping=0.55; action=0 }
    }
}
