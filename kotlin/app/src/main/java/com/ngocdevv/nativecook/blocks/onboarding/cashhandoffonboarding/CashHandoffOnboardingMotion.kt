package com.ngocdevv.nativecook.blocks.onboarding.cashhandoffonboarding

import kotlin.math.*

internal object CashHandoffOnboardingMotion {
    const val LOOP = 8.0
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k
    fun smooth(k: Double): Double { val x = clamp(k); return x * x * (3 - 2 * x) }
    fun easeIn(k: Double) = clamp(k).pow(3)
    fun easeOut(k: Double) = 1 - (1 - clamp(k)).pow(3)
    fun easeInOut(k: Double): Double { val x = clamp(k); return if (x < 0.5) 4*x*x*x else 1 - (-2*x+2).pow(3)/2 }
    fun closeSpring(s: Double) = if (s <= 0) 0.0 else 1 - exp(-s*9)*cos(s*14)
    data class Layout(val width: Double, val height: Double, val top: Double, val bottom: Double) {
        val panel get() = floor(height*0.64 + 0.5)
        val hx = 142.0; val hy get() = top+304
        val px get() = width-104; val py get() = top+342
    }
    data class Scene(var cx: Double = 142.0, var cy: Double = 304.0, var rot: Double = 0.0, var fan: Double = 0.0,
                     var gCurl: Double = 0.75, var rCurl: Double = 0.02, var gOff: Double = 0.0, var rOff: Double = 0.0, var caughtAt: Double = -10.0)
    fun at(t: Double, l: Layout): Scene {
        val k = easeInOut((t-1)/1.2)
        val fly = t >= 1 && t < 2.2
        val away = when { t >= 4.6 && t < 5.6 -> easeIn((t-4.6)/1.0); t >= 5.6 && t < 6.8 -> 1-easeOut((t-5.6)/1.2); else -> 0.0 }
        val fresh = t >= 5.6; val caught = t >= 2.2 && !fresh
        return Scene((if (fresh) l.hx else mix(l.hx,l.px,k)) + (if(fresh) -1 else if(caught) 1 else 0)*away*320,
            if(fresh) l.hy else mix(l.hy,l.py,k)-sin(k*PI)*80,
            if(fly) sin(k*PI*3)*12 else if(caught) -6.0 else 0.0, if(fly) sin(k*PI)*0.7 else 0.0,
            if(t < 1 || fresh) 0.75 else mix(0.75,0.15,smooth((t-1)/0.25)),
            if(caught) mix(0.02,0.85,closeSpring(t-2.2)) else 0.02, -away*320, away*320, if(caught) 2.2 else -10.0)
    }
}

internal class CashHandoffOnboardingDriver {
    private val m = CashHandoffOnboardingMotion
    var scene = CashHandoffOnboardingMotion.Scene()
    var time = 0.0; var now = 0.0; var last = -10.0
    var phase = 0; var at = 0.0
    var running = false
    private var initialized = false
    private var x = 0.0; private var y = 0.0; private var fx = 0.0; private var fy = 0.0; private var gx = 0.0; private var gy = 0.0
    fun step(elapsed: Double, l: CashHandoffOnboardingMotion.Layout, auto: Boolean) {
        if(!initialized) { scene = m.at(0.0,l); initialized = true }
        now += elapsed
        val dt = min(elapsed,0.05)
        if(auto && phase == 0 && now-last >= 2) { time = (time+elapsed)%m.LOOP; scene = m.at(time,l); return }
        val since = now-at; val sc = scene
        when(phase) {
            0 -> { sc.cx=l.hx; sc.cy=l.hy; sc.gCurl+=(0.75-sc.gCurl)*(1-exp(-dt*12)); sc.rCurl+=(0.02-sc.rCurl)*(1-exp(-dt*12)); sc.rot*=exp(-dt*10); sc.fan*=exp(-dt*10); sc.gOff=0.0; sc.rOff=0.0; sc.caughtAt=-10.0 }
            1 -> { val vx=(x-sc.cx)/max(dt,0.001); sc.cx=x; sc.cy=y; sc.rot+=((vx*0.02).coerceIn(-18.0,18.0)-sc.rot)*(1-exp(-dt*12)); sc.fan+=(0.5-sc.fan)*(1-exp(-dt*8)); sc.gCurl+=(0.15-sc.gCurl)*(1-exp(-dt*14)) }
            2 -> { val k=m.easeOut(since/0.35); sc.cx=m.mix(fx,l.hx,k); sc.cy=m.mix(fy,l.hy,k); sc.rot*=exp(-dt*10); sc.fan*=exp(-dt*10); if(since>0.3)sc.gCurl+=(0.75-sc.gCurl)*(1-exp(-dt*14)); if(since>0.6)phase=0 }
            3 -> { val k=m.easeOut(since/0.25); sc.cx=m.mix(fx,l.px,k); sc.cy=m.mix(fy,l.py,k); sc.rot+=(-6-sc.rot)*(1-exp(-dt*10)); sc.fan*=exp(-dt*10); sc.rCurl=m.mix(0.02,0.85,m.closeSpring(since)); sc.caughtAt=now-since }
            4 -> { val out=m.easeIn(since/0.9); val back=m.easeOut((since-1)/1.0)
                if(since<1) { sc.gOff=-out*320; sc.rOff=out*320; sc.cx=l.px+out*320 }
                else { sc.gOff=-(1-back)*320; sc.rOff=(1-back)*320; sc.cx=l.hx-(1-back)*320; sc.cy=l.hy; sc.rot=0.0; sc.gCurl=0.75; sc.rCurl=0.02; sc.caughtAt=-10.0 }
                if(since>2.05)phase=0
            }
        }
        if(!auto && ((phase==0 && now-last>2) || (phase==3 && since>2)))running=false
    }
    fun drawNow(auto: Boolean) = if(auto && phase==0 && now-last>=2) time else now
    fun begin(px: Double, py: Double): Int {
        last=now; running=true
        if(phase==3) { phase=4; at=now; return -1 }
        if(phase!=0 || hypot(px-scene.cx,py-scene.cy)>70)return -1
        phase=1; gx=px-scene.cx; gy=py-scene.cy; x=scene.cx; y=scene.cy
        return 0
    }
    fun move(px: Double, py: Double) { if(phase==1) { last=now; x=px-gx; y=py-gy } }
    fun end(l: CashHandoffOnboardingMotion.Layout): Int {
        if(phase!=1)return -1
        last=now; fx=x; fy=y; at=now
        return if(hypot(x-l.px,y-l.py)<90) { phase=3; 2 } else { phase=2; 1 }
    }
}
