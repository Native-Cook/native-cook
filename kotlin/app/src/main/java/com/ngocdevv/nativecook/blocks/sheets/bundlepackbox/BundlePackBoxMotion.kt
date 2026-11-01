package com.ngocdevv.nativecook.blocks.sheets.bundlepackbox

import kotlin.math.*

internal object BundlePackBoxMotion {
    const val LOOP = 9.0
    fun smooth(v: Double): Double { val x=v.coerceIn(0.0,1.0); return x*x*x*(x*(x*6-15)+10) }
    fun mix(a: Double,b: Double,k: Double)=a+(b-a)*k
    fun progress(t: Double)=when { t<1.2 -> 0.0; t<3.4 -> smooth((t-1.2)/2.2); t<5.6 -> 1.0; t<7.2 -> 1-smooth((t-5.6)/1.6); else -> 0.0 }
    data class Frame(val p: Double=0.0,val t: Double=0.0,val burst: Double=0.0) {
        val tape get()=smooth((p-0.93)/0.07)
        val stamp get()=((p-0.96)/0.04).coerceIn(0.0,1.0)
        val packed get()=p>0.97
    }
}
internal class BundlePackBoxDriver {
    var p=0.0; var v=0.0; var target=0.0; var p0=0.0
    var lastTouch=-100.0; var loopT=0.0; var burstAt=-10.0; var now=0.0
    var pinch=false; var scripted=false; var armed=false
    var success:(()->Unit)?=null
    fun step(wall: Double,auto: Boolean,reduced: Boolean): BundlePackBoxMotion.Frame {
        now+=wall
        if(auto && !pinch && now-lastTouch>3.5 && (scripted || (p<0.001 && target==0.0))) {
            if(!scripted) { scripted=true; loopT=7.4 }
            loopT=(loopT+wall)%9; p=BundlePackBoxMotion.progress(loopT); v=0.0; target=if(p>0.5)1.0 else 0.0
            return BundlePackBoxMotion.Frame(p,now,if(loopT>=5.6 && loopT<6.4)(loopT-5.6)/0.8 else 0.0)
        }
        scripted=false
        if(!pinch) { val dt=min(wall,0.05); val kk=if(reduced)500.0 else 60.0; v+=(kk*(target-p)-2*sqrt(kk)*v)*dt; p=(p+v*dt).coerceIn(0.0,1.0) }
        val next=p>0.97
        if(next!=armed) { armed=next; if(armed)success?.invoke() else burstAt=now }
        return BundlePackBoxMotion.Frame(p,now,if(now-burstAt<0.8)(now-burstAt)/0.8 else 0.0)
    }
    fun toggle() { target=if(target>0.5)0.0 else 1.0; scripted=false; lastTouch=now }
    fun begin() { pinch=true; scripted=false; p0=p }
    fun update(scale: Double) { p=(p0+(1-scale)*1.6).coerceIn(0.0,1.0); lastTouch=now }
    fun end(velocity: Double) { if(!pinch)return; pinch=false; target=when {velocity< -0.4 -> 1.0; velocity>0.4 -> 0.0; p>0.5 -> 1.0; else -> 0.0}; lastTouch=now }
}
