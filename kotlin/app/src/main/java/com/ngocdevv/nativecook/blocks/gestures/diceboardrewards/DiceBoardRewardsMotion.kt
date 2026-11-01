package com.ngocdevv.nativecook.blocks.gestures.diceboardrewards

import kotlin.math.*

internal object DiceBoardRewardsMotion {
    const val LOOP = 9.0
    const val HOP = 0.3
    val path = arrayOf(3 to 3,3 to 2,3 to 1,3 to 0,2 to 0,1 to 0,0 to 0,0 to 1,0 to 2,0 to 3,1 to 3,2 to 3)
    val colors = arrayOf("FFD95A","BFE7FF","FFC7D9","C9F2C7","E3D4FF","BFE7FF","FFE2B8","FFC7D9","C9F2C7","E3D4FF","FFE2B8","BFE7FF")
    val kinds = arrayOf("start","coin","gift","coin","star","coin","coupon","gift","coin","star","coupon","coin")
    val labels = arrayOf("START","+20","","+50","","+20","10%","","+80","","5%","+30")
    val prizes = arrayOf("Lap bonus · +100 coins","+20 coins","Mystery gift","+50 coins","Lucky star · roll again","+20 coins","10% OFF your basket","Mystery gift","+80 coins","Lucky star · roll again","5% OFF your basket","+30 coins")
    val rotations = arrayOf(0.0 to 0.0,0.0 to 0.0,PI/2 to 0.0,0.0 to -PI/2,0.0 to PI/2,-PI/2 to 0.0,PI to 0.0)
    data class Throw(val at: Double = -10.0,val value: Int = 1,val steps: Int = 0,val from: Int = 0)
    data class Out(val th: Throw = Throw(),val u: Double = -1.0,val prev: Throw = Throw(),val uPrev: Double = -1.0)
    data class Roll(val fl: Double,val rx: Double,val rz: Double,val bounce: Double,val home: Double,val pawn: Double,val flip: Double,val toast: Double,val hopsDone: Double)
    fun clamp(x: Double) = x.coerceIn(0.0,1.0)
    fun easeOut(x: Double) = 1-(1-clamp(x)).pow(3)
    fun smoother(v: Double): Double { val x=clamp(v); return x*x*x*(x*(x*6-15)+10) }
    fun scriptAt(time: Double): Out {
        val t=time%LOOP; val first=Throw(0.6,6,6,0)
        if(t<first.at)return Out(first,-1.0)
        if(t>=4.6) {
            val second=Throw(4.6,6,6,6)
            return Out(second,t-second.at,first,t-first.at)
        }
        return Out(first,t-first.at)
    }
    fun toastIndex(o: Out): Int {
        if(rollAt(o.th,o.u).toast>0.01)return (o.th.from+o.th.steps)%12
        if(o.uPrev>0 && rollAt(o.prev,o.uPrev).toast>0.01)return (o.prev.from+o.prev.steps)%12
        return -1
    }
    fun rollAt(th: Throw,u: Double): Roll {
        val fl=clamp(u/0.95); val end=1.15+th.steps*HOP
        val home=clamp((u-end-0.5)/0.45); val spin=1-easeOut(fl)
        val rot=rotations[th.value]
        val bounce=if(u>0.95 && u<1.25)sin((u-0.95)/0.3*PI)*0.18 else 0.0
        val hopU=(u-1.15)/HOP
        val done=if(th.steps==0)0.0 else clamp(hopU/th.steps)*th.steps
        val pawn=if(hopU<=0)th.from.toDouble() else if(hopU>=th.steps)(th.from+th.steps).toDouble() else th.from+floor(hopU)+smoother(hopU-floor(hopU))
        val toast=if(u>end+0.2)clamp((u-end-0.2)/0.3)*(1-clamp((u-end-0.9)/0.2)) else 0.0
        return Roll(fl,rot.first+spin*PI*6,rot.second+spin*PI*4,bounce,home,pawn,clamp((u-end)/0.35)+clamp((u-end-1.1)/0.35),toast,done)
    }
}
