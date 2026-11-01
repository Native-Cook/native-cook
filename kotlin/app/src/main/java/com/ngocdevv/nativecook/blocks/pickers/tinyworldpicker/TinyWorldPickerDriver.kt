package com.ngocdevv.nativecook.blocks.pickers.tinyworldpicker

import kotlin.math.*

internal class TinyWorldPickerDriver(private val buzz: (Int)->Unit, private val cue: (Int)->Unit) {
    val f = TinyWorldPickerMotion.Frame()
    private var now = 0.0
    private var mV = 0.0; private var target = -1.0; private var yawV = 0.0; private var sqV = 0.0
    private var drag = false; private var edge = 0.0; private var p0 = 0.0; private var yaw0 = 0.0
    private var lastTouch = -100.0; private var scripted = false; private var loopT = 0.0; private var yawOff = 0.0; private var armed = false
    fun step(wallTime: Double, auto: Boolean, reduced: Boolean) {
        val wall = wallTime.coerceIn(0.0,0.5); val dt = min(wall,0.05); now+=wall; f.tm=(f.tm+wall)%9
        val canScript = auto && !reduced && !drag && now-lastTouch>3.5 && (scripted || (target<0 && f.m==0.0))
        if(canScript) {
            if(!scripted) { scripted=true; loopT=f.cur*2.25+0.6; yawOff=f.yaw-TinyWorldPickerMotion.at(loopT).yaw }
            loopT=(loopT+wall)%9; val s=TinyWorldPickerMotion.at(loopT)
            f.cur=if(s.m>=1)s.nxt else s.cur; f.nxt=if(s.m>0)s.nxt else s.cur; f.m=if(s.m>=1)0.0 else s.m
            f.yaw=s.yaw+yawOff; f.sq=s.sq; sqV=0.0; mV=0.0; yawV=-0.25
        } else {
            scripted=false
            if(!drag) {
                if(f.m>0 || target>=0) {
                    val tgt=if(target>=0)target else if(f.m>0.5)1.0 else 0.0
                    val k=if(reduced)700.0 else 160.0; val c=if(reduced)53.0 else 2*0.82*sqrt(k)
                    mV+=(k*(tgt-f.m)-c*mV)*dt; f.m+=mV*dt
                    if(f.m<0) { f.m=0.0; mV=0.0 }; if(f.m>1.03)f.m=1.03
                    val dir=if(f.nxt>f.cur)1 else -1
                    if(!reduced)f.yaw-=dir*mV*dt*1.9
                    if(tgt==1.0 && abs(1-f.m)<0.004 && abs(mV)<0.08) {
                        f.cur=f.nxt; f.m=0.0; mV=0.0; target=-1.0; if(!reduced)sqV-=2.4
                        buzz(2); if(!reduced)cue(1)
                    } else if(tgt==0.0 && f.m<0.004 && abs(mV)<0.08) {
                        f.m=0.0; mV=0.0; target=-1.0; f.nxt=f.cur
                    }
                }
                val idle=if(reduced)0.0 else -0.25
                yawV+=(idle-yawV)*(1-exp(-1.6*dt)); f.yaw+=yawV*dt
            }
            var sqT=if(reduced)0.0 else 0.12*sin(PI*TinyWorldPickerMotion.clamp(f.m))
            if(!reduced && drag && edge!=0.0)sqT=0.07*tanh(abs(edge)*3)
            sqV+=(260*(sqT-f.sq)-9*sqV)*dt; f.sq=(f.sq+sqV*dt).coerceIn(-0.25,0.3)
        }
        f.glow+=((if(drag)1.0 else 0.0)-f.glow)*(1-exp(-8*dt)); f.t=now
    }
    fun begin() { lastTouch=now }
    fun start() {
        val dir=if(f.m>0)if(f.nxt>f.cur)1 else -1 else 0
        drag=true; scripted=false; target=-1.0; mV=0.0; p0=dir*f.m; yaw0=f.yaw; armed=abs(p0)>0.42
    }
    fun update(dx: Double, width: Double, reduced: Boolean) {
        var p=p0-dx/(width*0.62)
        while(p>=1 && f.cur<3) { f.cur++; p0--; p--; buzz(2) }
        while(p<=-1 && f.cur>0) { f.cur--; p0++; p++; buzz(2) }
        edge=0.0
        if(p>0 && f.cur<3) { f.nxt=f.cur+1; f.m=min(p,1.0) }
        else if(p<0 && f.cur>0) { f.nxt=f.cur-1; f.m=min(-p,1.0) }
        else { f.nxt=f.cur; f.m=0.0; edge=p }
        f.yaw=yaw0+if(reduced)0.0 else dx*0.0105
        val next=f.m>0.42; if(next!=armed) {armed=next; buzz(0)}; lastTouch=now
    }
    fun end(velocity: Double, width: Double, reduced: Boolean) {
        if(!drag)return
        drag=false; edge=0.0
        val dir=if(f.nxt>f.cur)1 else if(f.nxt<f.cur)-1 else 0
        if(dir!=0) {
            val along=(-velocity/(width*0.62))*dir
            val commit=(f.m>0.42 && along > -0.9)||(along>0.9 && f.m>0.04)
            target=if(commit)1.0 else 0.0; mV=along
            if(commit && f.m<0.85 && !reduced)cue(0)
        }
        yawV=if(reduced)0.0 else (velocity*0.0105).coerceIn(-12.0,12.0); lastTouch=now
    }
    fun tap(reduced: Boolean) {
        lastTouch=now; scripted=false
        if(f.m>0 && target<0)target=if(f.m>0.5)1.0 else 0.0
        if(!reduced) {sqV-=2.6; yawV-=3}; buzz(1); if(!reduced)cue(2)
    }
    fun goTo(i: Int, reduced: Boolean) {
        lastTouch=now; scripted=false; if(i !in 0..3 || i==f.cur || drag)return
        if(f.m>0)f.cur=if(f.m>0.5)f.nxt else f.cur
        f.nxt=i; f.m=0.0; mV=0.0; target=1.0; if(!reduced)cue(0)
    }
}
