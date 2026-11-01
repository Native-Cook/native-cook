package com.ngocdevv.nativecook.blocks.pickers.tinyworldpicker

import kotlin.math.*

internal object TinyWorldPickerMotion {
    const val LOOP = 9.0
    const val SEG = 2.25
    const val HOLD = 1.35
    const val MORPH = 0.75
    const val IDLE = 0.25
    val SPIN = (4 * PI - IDLE * LOOP) / 4
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun smooth(a: Double, b: Double, v: Double): Double { val x = clamp((v-a)/(b-a)); return x*x*(3-2*x) }
    fun smoother(v: Double): Double { val x = clamp(v); return x*x*x*(x*(x*6-15)+10) }
    data class Frame(var cur: Int = 0, var nxt: Int = 0, var m: Double = 0.0, var yaw: Double = 0.0,
                     var sq: Double = 0.0, var glow: Double = 0.0, var t: Double = 0.0, var tm: Double = 0.0)
    fun at(time: Double): Frame {
        val t = time % LOOP; val k = floor(t/SEG).toInt(); val u = t-k*SEG
        val mm = clamp((u-HOLD)/MORPH); val m = smoother(mm); val done = HOLD+MORPH
        val tau = if(u>=done)u-done else u+SEG-done
        val sq = (if(mm>0 && mm<1)0.12*sin(PI*mm) else 0.0) - 0.07*exp(-7*tau)*sin(17*tau)
        return Frame(k%4,(k+1)%4,m,-(IDLE*t+SPIN*(k+m)),sq,t=t,tm=t)
    }
    fun vis(i: Int, f: Frame): Double { val s = smooth(0.35,0.65,f.m); return (if(i==f.cur)if(f.cur==f.nxt)1.0 else 1-s else 0.0)+(if(i==f.nxt && f.nxt!=f.cur)s else 0.0) }
    fun labelVis(i: Int, f: Frame): Double = when { f.cur==f.nxt -> if(i==f.cur)1.0 else 0.0; i==f.cur -> 1-smooth(0.14,0.46,f.m); i==f.nxt -> smooth(0.48,0.82,f.m); else -> 0.0 }
}

internal data class TinyWorldPickerWorld(val name: String, val status: String, val cta: String, val halo: Int, val glyph: Int, val stats: List<String>) {
    val done get() = status=="COMPLETED"
    companion object {
        val all = listOf(
            TinyWorldPickerWorld("Cloud Nest","COMPLETED","Replay world",0x2FB9D3,984464,listOf("2:41","24/24","3/3")),
            TinyWorldPickerWorld("Kart Loop","COMPLETED","Replay world",0x7BD13A,983612,listOf("1:58","18/18","2/2")),
            TinyWorldPickerWorld("Bun Summit","3 / 5 STARS","Continue",0xF29A3D,984709,listOf("4:12","11/20","1/3")),
            TinyWorldPickerWorld("Old Arena","NEW WORLD","Start world",0xE8BE84,984834,listOf("--:--","0/16","0/4")))
    }
}
