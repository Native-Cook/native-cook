package com.ngocdevv.nativecook.blocks.gestures.liquidartdeck

import kotlin.math.*

internal object LiquidArtDeckMotion {
    const val LOOP = 9.0
    val titles = listOf("Mercury Bloom", "Quiet Chrome", "Dusk Liquor", "Soft Alloy", "Pearl Static", "Night Syrup")
    val authors = listOf("@ines.form", "@tomas.k", "@mira.studio", "@juno.labs", "@ade.works", "@koa.prints")
    val prices = listOf("$240", "$180", "$320", "$95", "$410", "$150")
    val seeds = doubleArrayOf(.1, .37, .62, .83, .21, .5)
    fun mix(a: Double, b: Double, k: Double) = a+(b-a)*k
    fun smooth(k: Double): Double { val x = k.coerceIn(0.0,1.0); return x*x*(3-2*x) }
    fun ease(k: Double): Double { val x = k.coerceIn(0.0,1.0); return if (x<.5)4*x*x*x else 1-(-2*x+2).pow(3)/2 }
    class Deck {
        var cur = 0; var held = 0; var fly = -1; var kind = 0; var back = 0; var count = 12
        var dx = 0.0; var dy = 0.0; var vx = 0.0; var vy = 0.0
        var sx = 0.0; var sy = 0.0; var svx = 0.0; var svy = 0.0
        var start = 0.0; var fx = 0.0; var fy = 0.0; var beat = -10.0
        val ds = DoubleArray(6) { it.toDouble() }; val vds = DoubleArray(6)
        val hist = mutableListOf<Int>()
        fun swipe(k: Int, now: Double): Boolean {
            if (fly>=0 || cur>=6)return false
            fly=cur;kind=k;back=0;start=now;fx=dx;fy=dy;hist.add(k);cur++
            dx=0.0;dy=0.0;vx=0.0;vy=0.0;return true
        }
        fun undo(now: Double): Boolean {
            if(fly>=0 || hist.isEmpty())return false
            kind=hist.removeAt(hist.lastIndex);cur--;fly=cur;back=1;start=now;fx=0.0;fy=0.0
            if(kind==1)count--;return true
        }
        fun step(dt: Double, now: Double) {
            val n=max(1,ceil(dt*240).toInt());val h=dt/n;val px=dx;val py=dy
            repeat(n) {
                if(held==0) {
                    vx+=(-230*dx-2*sqrt(230.0)*.72*vx)*h;vy+=(-230*dy-2*sqrt(230.0)*.72*vy)*h
                    dx+=vx*h;dy+=vy*h
                }
                repeat(6) { i ->
                    vds[i]+=(190*(i-cur-ds[i])-2*sqrt(190.0)*.86*vds[i])*h
                    ds[i]+=vds[i]*h
                }
            }
            val tx=(if(dt>0)(dx-px)/dt*.1 else 0.0).coerceIn(-64.0,64.0)
            val ty=(if(dt>0)(dy-py)/dt*.1 else 0.0).coerceIn(-64.0,64.0)
            repeat(n) {
                svx+=(260*(tx-sx)-2*sqrt(260.0)*.28*svx)*h;svy+=(260*(ty-sy)-2*sqrt(260.0)*.28*svy)*h
                sx+=svx*h;sy+=svy*h
            }
            if(fly>=0 && now-start >= if(kind==1).6 else .42) {
                if(back==0 && kind==1) { count++;beat=now }
                if(back==1)ds[fly]=0.0
                fly=-1
            }
        }
        fun script(t0: Double,t: Double) {
            if(t>=1 && t<1.6) {
                val k=ease((t-1)/.6);held=1;dx=150*k;dy=-30*sin(k*PI)-10*k
            } else if(t>=3.4 && t<3.9) {
                val k=ease((t-3.4)/.5);held=1;dx=-140*k;dy=20*sin(k*PI)
            } else if(held==1) { held=0;swipe(if(dx>0)1 else 2,t) }
            if(t0<5.6 && t>=5.6)undo(t)
            if(t0<7.2 && t>=7.2)undo(t)
            step(t-t0,t)
        }
    }
    class Driver {
        var deck=Deck();var t=0.0;var now=0.0;var phase=0.0;var last=-10.0
        fun step(dt: Double,auto: Boolean,reduced: Boolean) {
            now+=dt
            val d=deck
            // Scripted holds keep the script running; touches reset t to zero.
            if(auto && !reduced && (t>0 || d.held==0) && now-last>=2 && (t>0 || (d.cur==0 && d.fly<0 && d.hist.isEmpty() && abs(d.dx)<1 && abs(d.ds[0])<.01))) {
                val end=t+dt
                if(end>=LOOP) { deck=Deck();t=end-LOOP }
                else { var st=t;while(st<end) { val next=min(end,st+1.0/120);d.script(st,next);st=next };t=end }
            } else d.step(min(dt,.05),now)
            phase=(phase+dt)%LOOP
        }
        fun drawingNow(auto: Boolean) = if(auto && deck.held==0 && now-last>=2)t else now
        fun touch() { last=now;t=0.0 }
        fun act(kind: Int): Boolean { touch();return if(kind==3)deck.undo(now) else deck.swipe(kind,now) }
    }
}
