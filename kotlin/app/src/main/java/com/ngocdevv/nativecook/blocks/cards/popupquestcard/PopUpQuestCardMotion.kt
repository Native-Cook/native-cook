package com.ngocdevv.nativecook.blocks.cards.popupquestcard

import kotlin.math.*

internal object PopUpQuestCardMotion {
    const val LOOP = 9.0
    data class Pose(val o: Double = 0.0, val yaw: Double = 0.0)
    fun clamp(v: Double) = v.coerceIn(0.0, 1.0)
    fun smooth(a: Double, b: Double, v: Double): Double { val x = clamp((v-a)/(b-a)); return x*x*(3-2*x) }
    fun smoother(v: Double): Double { val x = clamp(v); return x*x*x*(x*(x*6-15)+10) }
    fun mix(a: Double, b: Double, k: Double) = a+(b-a)*k
    fun at(time: Double): Pose {
        val t = time % LOOP
        val o = when { t >= 1 && t < 2 -> smoother(t-1); t >= 2 && t < 5.8 -> 1.0; t >= 5.8 && t < 6.8 -> 1-smoother(t-5.8); else -> 0.0 }
        return Pose(o, if(t >= 2 && t < 5.8) 0.22*sin((t-2)/3.8*2*PI) else 0.0)
    }
    data class Box(val x: Double, val y: Double, val w: Double, val h: Double) {
        fun contains(px: Double, py: Double) = px >= x && px <= x+w && py >= y && py <= y+h
    }
    data class Layout(val w: Double, val h: Double, val top: Double, val bottom: Double) {
        val sheetTop = floor(h*0.46+0.5)
        val heroH = floor(h*0.5+0.5)
        fun card(i: Int): Box { val cw=(w-44)/2; return Box(16+(i%2)*(cw+12),top+176+(i/2)*226,cw,214.0) }
        fun illustration(i: Int): Box { val c=card(i); return Box(c.x+8,c.y+8,c.w-16,138.0) }
        fun frame(p: Pose, i: Int, reduced: Boolean): Box {
            val a=illustration(i); val e=if(reduced)1.0 else p.o
            return Box(mix(a.x,26.0,e),mix(a.y,top+70,e),mix(a.w,w-52,e),mix(a.h,sheetTop-top-86,e))
        }
        fun clip(p: Pose,i: Int,reduced: Boolean): Box { val a=illustration(i); val e=if(reduced)1.0 else p.o; return Box(mix(a.x,0.0,e),mix(a.y,0.0,e),mix(a.w,w,e),mix(a.h,sheetTop+40,e)) }
    }
}
