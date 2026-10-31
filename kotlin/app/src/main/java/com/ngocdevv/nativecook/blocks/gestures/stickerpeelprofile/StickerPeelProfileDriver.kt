package com.ngocdevv.nativecook.blocks.gestures.stickerpeelprofile

import kotlin.math.*

internal class StickerPeelProfileDriver(val cue:(String)->Unit,val buzz:(Int)->Unit) {
    private val m=StickerPeelProfileMotion
    var ss=m.home(402.0,0.0)
    var order=mutableListOf(0,1,2,3)
    var width=402.0; var top=0.0
    var time=0.0; var now=0.0; var last=-10.0
    var drag=-1; var peeling=false
    var gx=0.0; var gy=0.0; var ox=0.0; var oy=0.0; var px=0.0
    var twist=-1; var k0=1.0; var r0=0.0
    fun layout(w:Double,t:Double) { if(w!=width || t!=top) { width=w; top=t; ss=m.home(w,t) } }
    fun step(delta:Double,auto:Boolean,reduced:Boolean) {
        val dt=delta.coerceIn(0.0,.5); now+=dt
        if(auto && !reduced && drag<0 && now-last>=2) {
            time=(time+dt)%m.LOOP; ss=m.frame(time,width,top); order=mutableListOf(0,1,3,2)
        } else {
            val h=min(dt,.05)
            ss.forEachIndexed { i,s ->
                if(!(drag==i && peeling)) { s.vf+=(-300*s.f-2*sqrt(300.0)*.35*s.vf)*h; s.f=max(0.0,s.f+s.vf*h) }
                if(!(drag==i && s.lift>.5))s.lift=max(0.0,s.lift-h*10)
                s.vsq+=(-500*s.sq-2*sqrt(500.0)*.3*s.vsq)*h; s.sq+=s.vsq*h; s.vx*=exp(-h*8)
            }
        }
    }
    fun begin(x:Double,y:Double,reduced:Boolean) {
        last=now
        for(i in order.reversed()) {
            val s=ss[i]; val a=-s.rot*PI/180; val dx=x-s.x; val dy=y-s.y
            val lx=dx*cos(a)-dy*sin(a); val ly=dx*sin(a)+dy*cos(a); val h=m.half[i]
            if(abs(lx)<h.first+8 && abs(ly)<h.second+8) {
                s.corner=if(ly<0) { if(lx<0)0 else 1 } else if(lx<0)3 else 2
                drag=i; gx=x; gy=y; ox=s.x-x; oy=s.y-y; peeling=!reduced; px=x
                order.remove(i); order.add(i); buzz(0); return
            }
        }
    }
    fun update(x:Double,y:Double) {
        if(drag<0)return
        last=now; val s=ss[drag]; val dist=hypot(x-gx,y-gy)
        if(peeling) {
            val h=m.half[drag]; s.f=min(.62,dist/(hypot(h.first,h.second)*1.6))
            if(dist>52) { peeling=false; s.lift=1.0; s.f=0.0; cue("peel"); buzz(1) }
        } else { s.lift=1.0; s.vx=(x-px)/.016; px=x; s.x=x+ox; s.y=y+oy }
    }
    fun end() {
        if(drag<0)return
        last=now; val s=ss[drag]
        if(!peeling && s.lift>.5) { s.lift=0.0; s.sq=.1; s.vsq=0.0; cue("slap"); buzz(1) }
        drag=-1; peeling=false
    }
    fun pick(x:Double,y:Double):Int=if(drag>=0)drag else order.reversed().firstOrNull { i -> val s=ss[i]; val h=m.half[i]; abs(x-s.x)<h.first*s.k+30 && abs(y-s.y)<h.second*s.k+30 }?:-1
}
