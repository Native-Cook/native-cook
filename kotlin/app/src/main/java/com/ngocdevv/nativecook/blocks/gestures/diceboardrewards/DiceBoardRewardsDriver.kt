package com.ngocdevv.nativecook.blocks.gestures.diceboardrewards

import kotlin.math.*

internal class DiceBoardRewardsDriver {
    private val m=DiceBoardRewardsMotion
    private val started=System.nanoTime()
    var now=0.0
    private var last=0.0
    var th=DiceBoardRewardsMotion.Throw()
    var prev=DiceBoardRewardsMotion.Throw()
    var pos=0
    var lastTouch=-100.0
    var scripted=false
    var loopT=0.0
    var lastHop=0
    var rolls=3
    var out=DiceBoardRewardsMotion.Out()
    fun sample(auto: Boolean,buzz: (Int)->Unit) {
        val wallNow=(System.nanoTime()-started)/1e9
        val dt=(wallNow-last).coerceIn(0.0,0.5);last=wallNow;now=wallNow
        val busy=now-th.at<1.15+th.steps*m.HOP+2
        if(auto && !busy && now-lastTouch>3.5 && (scripted || pos%12==0)) {
            if(!scripted) { scripted=true;loopT=0.0 }
            loopT=(loopT+dt)%m.LOOP;out=m.scriptAt(loopT)
            pos=if(out.u<0)0 else (out.th.from+min(out.th.steps,max(0,floor((out.u-1.15)/m.HOP).toInt()+1)))%12
            return
        }
        scripted=false
        val u=now-th.at;val r=m.rollAt(th,u);val hop=floor(r.hopsDone).toInt()
        if(u>1.15 && hop!=lastHop && hop<=th.steps) { lastHop=hop;buzz(0);if(hop==th.steps)buzz(2) }
        out=DiceBoardRewardsMotion.Out(th,u,prev,now-prev.at)
    }
    fun throwDie(buzz: (Int)->Unit): Boolean {
        now=(System.nanoTime()-started)/1e9;last=now
        if(now-th.at<1.15+th.steps*m.HOP+0.6 && th.at>0)return false
        val rnd=sin(now*12.9898+pos*78.233)*43758.5453
        val value=1+floor((rnd-floor(rnd))*6).toInt()
        prev=th
        th=DiceBoardRewardsMotion.Throw(now,value,value,pos);pos=(pos+value)%12
        lastHop=0;scripted=false;lastTouch=now;rolls=if(rolls>0)rolls-1 else 3
        buzz(1);return true
    }
    fun moving(auto: Boolean)=auto && (scripted || pos%12==0) || now-th.at<1.15+th.steps*m.HOP+2
}
