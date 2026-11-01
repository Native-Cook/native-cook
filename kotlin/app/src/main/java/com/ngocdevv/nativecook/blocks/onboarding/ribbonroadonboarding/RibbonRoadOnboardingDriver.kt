package com.ngocdevv.nativecook.blocks.onboarding.ribbonroadonboarding

import kotlin.math.*

internal class RibbonRoadOnboardingDriver(w: Double,h: Double) {
    val lines=arrayOf(RibbonRoadOnboardingMotion.line(RibbonRoadOnboardingMotion.blue,w,h),RibbonRoadOnboardingMotion.line(RibbonRoadOnboardingMotion.white,w,h))
    val field=RibbonRoadOnboardingField()
    var time=0.0; var clock=0.0; var last= -10.0
    var e=0.0; var z= -1.0; var phase=0.0; var flow=1.0; var button=0.0
    private var touching=false
    var on=false; var x=0.0; var y=0.0; var press=0.0; var grab= -1; var gi=0; var gx=0.0; var gy=0.0
    var pressed= -1
    fun advance(t: Double,dt: Double,scripted: Boolean,reduced: Boolean) {
        if(scripted) {
            e=t; z=if(t>=3.8+0.15)t-3.8-0.15 else -1.0
            val k=RibbonRoadOnboardingMotion.smooth(RibbonRoadOnboardingMotion.seg(t,1.75,1.75+0.55))
            on=t>=1.75 && t<2.5
            if(on) {
                if(grab!=0) { grab=0; gi=100; gx=lines[0][100][0]; gy=lines[0][100][1] }
                x=lines[0][100][0]+70*k; y=lines[0][100][1]+44*k
            }
            button=if(t>=3.8 && t<3.8+0.18)1.0 else 0.0
        } else {
            e+=dt
            if(z>=0) { z+=dt; if(z>2.2) { z= -1.0; e=0.0 } }
            button*=exp(-dt*14)
        }
        press+=((if(on)1.0 else 0.0)-press)*(1-exp(-dt*(if(on)18 else 30)))
        val boost=if(z>=0 && !reduced)1+5*RibbonRoadOnboardingMotion.smooth(RibbonRoadOnboardingMotion.seg(z,0.1,0.7)) else 1.0
        val want=(if(on && grab>=0)0.35 else 1.0)*boost
        flow+=(want-flow)*(1-exp(-dt*4))
        if(!reduced)phase+=64*flow*dt
        field.step(grab,gi,x-gx,y-gy,press,dt)
        if(!on && press<0.01)grab= -1
    }
    fun step(dt: Double,auto: Boolean,reduced: Boolean) {
        clock+=dt
        // The scripted finger must not pause its own autoplay.
        val touched=touching || clock-last<2
        if(auto && !reduced && !touched) {
            time+=dt
            if(time>=RibbonRoadOnboardingMotion.LOOP) { time-=RibbonRoadOnboardingMotion.LOOP; phase=0.0; flow=1.0; press=0.0; grab= -1; on=false; field.reset() }
            advance(time,dt,true,reduced)
        } else advance(time,dt,false,reduced)
    }
    fun begin(px: Double,py: Double): Boolean {
        last=clock
        var best=44.0; var hit= -1; var index=0
        for(i in 0 until 340) {
            val r=if(i<170)0 else 1; val j=i%170; val pt=lines[r][j]
            val distance=hypot(pt[0]+field.ox[i]-px,pt[1]+field.oy[i]-py)-(if(i>=170)2 else 0)
            if(distance<best) { best=distance; hit=r; index=j }
        }
        touching=true; on=true; x=px; y=py; grab=hit
        if(hit>=0) { gi=index; gx=px; gy=py }
        return hit>=0
    }
    fun move(px: Double,py: Double) { last=clock; x=px; y=py }
    fun end(): Boolean { last=clock; touching=false; on=false; return grab>=0 }
    fun start(): Boolean {
        last=clock
        if(z>=0)return false
        z=0.0; button=1.0; return true
    }
}
