package com.ngocdevv.nativecook.blocks.onboarding.ramenbowlbuilder

import kotlin.math.*

internal object RamenBowlBuilderMotion {
    const val LOOP = 9.0
    val names = listOf("Soft egg", "Fish cake", "Nori", "Shrimp", "Sushi roll")
    fun smoother(v: Double): Double { val x = v.coerceIn(0.0,1.0); return x*x*x*(x*(x*6-15)+10) }
    fun mix(a: Double,b: Double,k: Double) = a+(b-a)*k
    fun scriptK(t: Double,i: Int): Double {
        val at = 1+i*1.1
        if(t<at) return 0.0
        if(t<7.6) return smoother((t-at)/0.7)
        return 1-smoother((t-7.6-(4-i)*0.12)/0.7)
    }
    data class Point(val x: Double,val y: Double)
    class Geo(w: Double,h: Double) {
        val cx = w/2; val cy = floor(h*0.6+0.5); val s = floor(w*0.29+0.5)
        val homes = listOf(Point(cx-s*1.15,cy-s*1.45),Point(cx+s*0.25,cy-s*1.85),Point(cx-s*1.25,cy-s*0.55),Point(cx+s*1.25,cy-s*0.5),Point(cx-s*0.45,cy-s*2.15))
        val slots = listOf(Point(cx-s*0.45,cy-s*0.12),Point(cx+s*0.12,cy-s*0.18),Point(cx-s*0.08,cy-s*0.02),Point(cx+s*0.55,cy-s*0.05),Point(cx+s*0.32,cy+s*0.04))
    }
    class Frame {
        val k = DoubleArray(5); val back = DoubleArray(5); val bx = DoubleArray(5); val by = DoubleArray(5)
        var dragI = -1; var dx = 0.0; var dy = 0.0; var t = 0.0
        val count: Int get() = k.count { it>0.95 }
    }
}

internal class RamenBowlBuilderDriver {
    val frame = RamenBowlBuilderMotion.Frame()
    private val placed = IntArray(5)
    private var scripted = false
    private var loopT = 0.0
    var now = 0.0
    private var lastTouch = -100.0
    val moving: Boolean get() = frame.dragI>=0 || (0..4).any { (placed[it]==1 && frame.k[it]<1) || (frame.back[it]>0 && frame.back[it]<1) }
    fun step(wall: Double,auto: Boolean,reduced: Boolean,buzz: (Int)->Unit) {
        now += wall
        val dt = min(wall,0.05)
        val none = (0..4).all { placed[it]==0 && frame.k[it]<=0 }
        if(auto && !reduced && frame.dragI<0 && now-lastTouch>3.5 && (scripted || none)) {
            if(!scripted) { scripted=true; loopT=0.0 }
            loopT=(loopT+wall)%RamenBowlBuilderMotion.LOOP
            for(i in 0..4) { frame.k[i]=RamenBowlBuilderMotion.scriptK(loopT,i); placed[i]=if(frame.k[i]>0.5) 1 else 0; frame.back[i]=0.0 }
        } else {
            scripted=false
            for(i in 0..4) {
                if(placed[i]==1 && frame.k[i]<1) {
                    frame.k[i]=min(1.0,frame.k[i]+dt/(if(reduced) 0.05 else 0.55))
                    if(frame.k[i]>=1) buzz(1)
                }
                if(frame.back[i]>0 && frame.back[i]<1) frame.back[i]=min(1.0,frame.back[i]+dt/0.45)
            }
        }
        frame.t=now
    }
    fun begin(x: Double,y: Double,g: RamenBowlBuilderMotion.Geo,buzz: (Int)->Unit) {
        scripted=false; lastTouch=now
        var best = -1; var bd = g.s*0.55
        for(i in 0..4) {
            if(placed[i]==1) continue
            val d=hypot(x-g.homes[i].x,y-g.homes[i].y)
            if(d<bd) { bd=d; best=i }
        }
        frame.dragI=best; frame.dx=x; frame.dy=y
        if(best>=0) buzz(0)
    }
    fun update(x: Double,y: Double) { frame.dx=x; frame.dy=y; lastTouch=now }
    fun end(g: RamenBowlBuilderMotion.Geo) {
        val i=frame.dragI; frame.dragI = -1
        if(i<0) return
        frame.bx[i]=frame.dx; frame.by[i]=frame.dy
        if(hypot(frame.dx-g.cx,(frame.dy-g.cy)*1.6)<g.s*1.3) { placed[i]=1; frame.k[i]=0.0; frame.back[i] = -1.0 }
        else frame.back[i]=0.001
        lastTouch=now
    }
    fun next(g: RamenBowlBuilderMotion.Geo,buzz: (Int)->Unit) {
        buzz(0)
        if(frame.count<5) return
        for(i in 0..4) if(placed[i]==1) {
            placed[i]=0; frame.k[i]=0.0; frame.bx[i]=g.slots[i].x; frame.by[i]=g.slots[i].y; frame.back[i]=0.001
        }
        scripted=false; lastTouch=now
    }
}
