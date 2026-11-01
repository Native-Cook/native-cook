package com.ngocdevv.nativecook.blocks.modals.clawprizedrop

import kotlin.math.*

internal object ClawPrizeDropMotion {
    const val LOOP = 9.0
    val names = listOf("Plush bear", "Bunny ball", "Sleepy star", "Mint capsule", "Coin sack", "Gift coupon")
    val rewards = listOf("15% off toys", "Free delivery", "20% off snacks", "Mystery gift", "+200 coins", "10% off your basket")
    fun clamp(v: Double) = v.coerceIn(0.0, 1.0)
    fun smoother(v: Double): Double { val x = clamp(v); return x*x*x*(x*(x*6-15)+10) }
    fun lerp(a: Double, b: Double, k: Double) = a+(b-a)*k
    class Geo(val width: Double, val height: Double, val top: Double) {
        val x = 28.0; val y = top+150; val w = width-56; val h = floor(height*0.42+0.5)
        val railY = y+22; val floorY = y+h-40; val chuteX = x+50; val homeX = x+w*0.5
        val ps = floor(w*0.24+0.5)
        val px = doubleArrayOf(0.36,0.58,0.78,0.47,0.68,0.88).map { x+w*it }
        val py = doubleArrayOf(0.35,0.3,0.42,0.85,0.82,0.7).map { floorY-ps*it }
    }
    data class Grab(val at: Double = -100.0, val fromX: Double = 0.0, val prize: Int = -1)
    data class Frame(var x: Double, var drop: Double = 0.0, var close: Double = 0.0,
        var carry: Double = 0.0, var fall: Double = -1.0, var prize: Int = -1,
        var coupon: Double = 0.0, var respawn: Double = 1.0, var swing: Double = 0.0)
    fun grabAt(g: Geo, gr: Grab, u: Double): Frame {
        val r = Frame(gr.fromX, prize=gr.prize)
        if (u >= 0) {
            val down = smoother(u/0.7); val shut = smoother((u-0.7)/0.25)
            val up = smoother((u-0.95)/0.7); val move = smoother((u-1.65)/0.65)
            val open = if (u>=2.3) smoother((u-2.3)/0.2) else 0.0
            r.drop=down*(1-up); r.close=shut*(1-open)
            if (gr.prize>=0) {
                r.x=lerp(gr.fromX,g.chuteX,move); r.carry=if(u>=0.9 && u<2.3) 1.0 else 0.0
                r.fall=if(u>=2.3) u-2.3 else -1.0
            }
        }
        return r
    }
    fun scriptAt(g: Geo, t: Double): Frame {
        val target=g.px[2]
        if(t<1.2) return Frame(lerp(g.homeX,target,smoother((t-0.4)/0.8)),prize=2)
        val r=grabAt(g,Grab(1.2,target,2),t-1.2)
        if(t>=5.6) r.x=lerp(g.chuteX,g.homeX,smoother(t-5.6))
        r.coupon=if(t>=4 && t<5.6) smoother((t-4)/0.3)*(1-smoother((t-5.2)/0.4)) else 0.0
        r.respawn=if(t>=7.6) smoother((t-7.6)/0.8) else 0.0
        return r
    }
}

internal class ClawPrizeDropDriver {
    private var now=0.0; private var loopT=0.0
    private var x=0.0; private var sw=0.0; private var swV=0.0
    private var lastTouch=-100.0; private var grab=ClawPrizeDropMotion.Grab()
    private var dragging=false; private var scripted=false; private var initialized=false
    private var x0=0.0
    var won=2; private set
    private var visible=false
    var success=false; private set
    var pressed=false
    fun step(wall: Double, g: ClawPrizeDropMotion.Geo, auto: Boolean, reduced: Boolean): ClawPrizeDropMotion.Frame {
        val m=ClawPrizeDropMotion
        if(!initialized) { x=g.homeX; initialized=true }
        now+=wall; val dt=min(wall,0.05); val busy=now-grab.at<3.2
        val r: ClawPrizeDropMotion.Frame
        if(auto && !reduced && !dragging && !busy && now-lastTouch>3.5 && (scripted || abs(x-g.homeX)<1)) {
            if(!scripted) { scripted=true; loopT=0.0 }
            val prev=x; loopT=(loopT+wall)%m.LOOP; r=m.scriptAt(g,loopT); x=r.x
            integrate((x-prev)/max(dt,0.001),dt)
        } else {
            scripted=false; val u=now-grab.at; r=m.grabAt(g,grab,u); val prev=x
            if(u>=0 && u<3.2) x=r.x else if(u>=3.2 && u<4.2) x=m.lerp(g.chuteX,g.homeX,m.smoother(u-3.2))
            integrate((x-prev)/max(dt,0.001),dt); r.x=x
            r.respawn=if(grab.prize>=0) m.smoother((u-4.6)/0.8) else 1.0
            r.coupon=if(grab.prize>=0 && u>=2.8 && u<4.6) m.smoother((u-2.8)/0.3)*(1-m.smoother((u-4.2)/0.4)) else 0.0
        }
        r.swing=if(reduced) 0.0 else sw
        val v=r.coupon>0.5 && r.prize>=0; success=v && !visible
        if(success) won=r.prize
        visible=v; return r
    }
    private fun integrate(vx: Double, dt: Double) { swV+=(-90*sw-7*swV-vx*0.9)*dt; sw+=swV*dt }
    fun pan(dx: Double,g: ClawPrizeDropMotion.Geo) {
        if(!dragging) {
            if(now-grab.at<4.2)return
            dragging=true; scripted=false; x0=if(initialized)x else g.homeX
        }
        x=(x0+dx).coerceIn(g.x+40,g.x+g.w-30); lastTouch=now
    }
    fun endPan() { dragging=false; lastTouch=now }
    fun grab(g: ClawPrizeDropMotion.Geo) {
        if(now-grab.at<4.2)return
        if(!initialized) { x=g.homeX; initialized=true }
        var best=-1; var bd=g.ps*0.45
        for(i in 0..5) { val d=abs(x-g.px[i]); if(d<bd) { bd=d; best=i } }
        grab=ClawPrizeDropMotion.Grab(now,x,best); scripted=false; lastTouch=now
    }
    fun idle()= !dragging && now-lastTouch>5.6 && now-grab.at>5.4 && abs(sw)<0.001 && abs(swV)<0.001
}
