package com.ngocdevv.nativecook.blocks.progress.setupdomeloader

import kotlin.math.*

internal object SetupDomeLoaderMotion {
    const val LOOP = 9.0
    val ORBIT_W = 4 * PI / LOOP
    fun clamp(x: Double) = x.coerceIn(0.0,1.0)
    fun ramp(t: Double,a: Double,b: Double) = clamp((t-a)/(b-a))
    fun mix(a: Double,b: Double,k: Double) = a+(b-a)*k
    fun easeOut(k: Double) = 1-(1-clamp(k)).pow(3)
    fun easeIn(k: Double) = clamp(k).pow(3)
    fun easeInOut(k: Double): Double {
        val x=clamp(k)
        return if(x<0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    }
    fun backOut(k: Double): Double { val x=clamp(k)-1; return 1+2.9*x.pow(3)+1.9*x*x }
    data class Phase(val c: Double,val b: Double,val k: Double,val burst: Double,val chk: Double,val tick: Double,val swap: Double,val prog: Double)
    fun phase(t: Double) = Phase(
        if(t<8.75) easeInOut(ramp(t,6.4,7.0)) else 1-easeOut(ramp(t,8.75,9.0)),
        if(t<8.6) easeIn(ramp(t,7.0,7.35)) else 1-easeOut(ramp(t,8.6,8.8)),
        if(t<8.5) ramp(t,7.3,7.42) else 1-ramp(t,8.5,8.65),
        if(t>=7.35 && t<7.95) (t-7.35)/0.6 else 0.0,
        if(t<8.45) backOut(ramp(t,7.45,7.95)) else 1-easeIn(ramp(t,8.45,8.65)),
        if(t<8.45) easeOut(ramp(t,7.75,8.05)) else 1-ramp(t,8.45,8.55),
        if(t<8.5) ramp(t,7.4,7.9) else 1-ramp(t,8.5,8.9),
        if(t<7) clamp(t/7) else if(t<8.5) 1.0 else 1-ramp(t,8.5,8.9))
    data class Item(val kind: Int,val a: Double,val h: Double,val s: Double,val spin: Double,val delay: Double)
    val items = listOf(Item(0,0.0,0.62,1.0,1.0,0.0),Item(1,1.3,0.45,1.0,-0.5,0.06),Item(2,2.6,0.78,1.0,1.5,0.12),Item(3,3.7,0.3,1.0,2.0,0.03),Item(3,5.0,0.56,0.82,-2.5,0.09))
}

internal class SetupDomeLoaderDriver {
    var t=0.0
    var clock=0.0
    var off=0.0
    var velocity=0.0
    val hop=DoubleArray(5)
    private val hopV=DoubleArray(5)
    private val delay=DoubleArray(5){-1.0}
    var doneBuzz=false
    var ctaPressed=false
    fun step(wall: Double,auto: Boolean): Boolean {
        val dt=min(wall,0.05)
        clock+=wall
        t=if(auto) (t+wall)%SetupDomeLoaderMotion.LOOP else min(t+wall,8.3)
        val success=!auto && t>=7.35 && !doneBuzz
        if(success) doneBuzz=true
        velocity*=exp(-1.4*dt); off+=velocity*dt
        for(i in 0..4) {
            if(delay[i]>=0) {
                delay[i]-=dt
                if(delay[i]<0) { hopV[i]=260.0; delay[i]=-1.0 }
            }
            hopV[i]-=1400*dt; hop[i]+=hopV[i]*dt
            if(hop[i]<0) { hop[i]=0.0; hopV[i]=if(abs(hopV[i])>60) -hopV[i]*0.35 else 0.0 }
        }
        return success
    }
    fun tap() { for(i in 0..4) delay[i]=SetupDomeLoaderMotion.items[i].delay }
    fun pan(v: Double,end: Boolean) { velocity=(if(end) v*0.006 else velocity+v*0.00012).coerceIn(-9.0,9.0) }
    fun restart() { t=0.0; doneBuzz=false }
}
