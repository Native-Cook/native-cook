package com.ngocdevv.nativecook.blocks.lists.fridgedoorgrocery

import kotlin.math.*

internal object FridgeDoorGroceryMotion {
    const val LOOP = 9.0
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun smoother(k: Double): Double { val x = clamp(k); return x*x*x*(x*(x*6-15)+10) }
    fun backOut(k: Double): Double { val x = clamp(k)-1; return 1+2.4*x.pow(3)+1.4*x.pow(2) }
    data class Frame(val a: Double = 0.0, val fly: List<Double> = List(8) { 0.0 }, val t: Double = 0.0, val bag: Int = 0) {
        val bagScale: Double get() = 1+0.18*fly.maxOf { if (it > 0.85 && it < 1) sin((it-0.85)/0.15*PI) else 0.0 }
    }
    fun at(time: Double): Frame {
        val t = time%LOOP
        val a = when {
            t >= 1 && t < 1.9 -> backOut((t-1)/0.9)
            t >= 1.9 && t < 6.2 -> 1.0
            t >= 6.2 && t < 7 -> { val k = (t-6.2)/0.8; 1-smoother(k)+(if (k > 0.85) sin((k-0.85)/0.15*PI)*0.03 else 0.0) }
            else -> 0.0
        }
        val f0 = if (t >= 2.5 && t < 8.2) clamp((t-2.5)/0.7) else 0.0
        val f2 = if (t >= 3.2 && t < 8.2) clamp((t-3.2)/0.7) else 0.0
        return Frame(a,listOf(f0,0.0,f2,0.0,0.0,0.0,0.0,0.0),time,(if(f0>=1)1 else 0)+(if(f2>=1)1 else 0))
    }
    data class Item(val shelf: Int, val x: Double, val low: Boolean = false)
    val items = listOf(Item(0,0.18,true),Item(0,0.55),Item(1,0.22,true),Item(1,0.66),Item(2,0.2),Item(2,0.6),Item(3,0.3),Item(3,0.7))
    class Geo(width: Double, height: Double, top: Double) {
        val w = floor(width*0.62+0.5)
        val x = floor((width-w)/2+0.5)-10
        val y = top+120
        val h = floor(height*0.56+0.5)
        val bagX = width-52
        val bagY = top+30
    }
}

internal class FridgeDoorGroceryDriver {
    var a = 0.0; var v = 0.0; var target = 0.0; var a0 = 0.0
    var drag = false; var scripted = false; var snapped = true
    var now = 0.0; var loopT = 0.0; var lastTouch = -100.0
    val fly = MutableList(8) { 0.0 }; val flyOn = BooleanArray(8)
    var bag = 0
    fun step(wall: Double, auto: Boolean, buzz: (Int) -> Unit): FridgeDoorGroceryMotion.Frame {
        val dt = min(wall,0.05); now += wall
        if (auto && !drag && now-lastTouch > 3.5 && (scripted || (a<0.001 && target==0.0))) {
            if (!scripted) { scripted=true; loopT=0.0 }
            loopT=(loopT+wall)%9
            val f=FridgeDoorGroceryMotion.at(loopT); a=f.a; target=if(a>0.5)1.0 else 0.0
            return f.copy(t=now)
        }
        scripted=false
        if(!drag) {
            v+=(120*(target-a)-2*0.62*sqrt(120.0)*v)*dt; a+=v*dt
            if(a<0) { a=0.0; if(!snapped) { snapped=true; v=abs(v)*0.2; buzz(1) } }
            if(a>0.05)snapped=false
        }
        for(i in 0..7) if(flyOn[i] && fly[i]<1) {
            fly[i]=min(1.0,fly[i]+dt/0.7)
            if(fly[i]>=1) { bag++; buzz(2) }
        }
        return FridgeDoorGroceryMotion.Frame(a,fly.toList(),now,bag)
    }
    fun tap(x: Double,y: Double,g: FridgeDoorGroceryMotion.Geo,buzz: (Int)->Unit) {
        lastTouch=now; scripted=false
        if(a<0.6) { target=1.0; return }
        val sh=(g.h-28)/4
        FridgeDoorGroceryMotion.items.forEachIndexed { i,it ->
            val bx=g.x+14+(g.w-28)*it.x
            val by=g.y+14+sh*(it.shelf+1)-10-sh*0.3
            if(abs(x-bx)<sh*0.45 && abs(y-by)<sh*0.45 && !flyOn[i]) { flyOn[i]=true; buzz(0) }
        }
    }
}
