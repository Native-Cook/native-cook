package com.ngocdevv.nativecook.blocks.cards.goldemblemmembership

import kotlin.math.*

internal object GoldEmblemMembershipMotion {
    const val LOOP = 10.0
    const val CAST = 1.7
    val times = doubleArrayOf(0.8, 3.8, 7.3)
    data class Frame(var from: Int = 0, var to: Int = 0, var cast: Double = 0.0,
                     var side: Double = 1.0, var drag: Double = 0.0, var finger: Double = 0.0,
                     var fingerX: Double = 0.0, var press: Double = 0.0) {
        val shown get() = if(cast >= 0.5) to else from
        val words get() = when { cast <= 0 -> 1.0; cast < 0.3 -> 1-cast/0.3; cast < 0.82 -> 0.0; else -> (cast-0.82)/0.18 }
        val wordsY get() = if(cast > 0 && cast < 0.5) cast/0.3*4 else 0.0
    }
    fun seg(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun easeInOut(x: Double) = if(x < 0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeOut(x: Double) = 1-(1-x).pow(3)
    fun timing(x: Double) = if(x < 0.5) 2*x*x else 1-(-2*x+2).pow(2)/2
    fun frameAt(t: Double): Frame {
        val f = Frame()
        times.forEachIndexed { i, at ->
            if(t >= at-0.5) {
                f.from=i; f.to=if(i==2)0 else i+1; f.side=if(i==2)-1.0 else 1.0
                f.cast=seg(t,at,at+CAST)
                val sw=seg(t,at-0.45,at+0.05)
                val finger=easeOut(seg(t,at-0.5,at-0.4))*(1-easeOut(seg(t,at+0.02,at+0.16)))
                if(finger>0) { f.finger=finger; f.fingerX=f.side*(90-200*easeInOut(sw)) }
                f.drag=-f.side*26*sin(PI*easeInOut(sw))
            }
        }
        if(f.cast>=1) { f.from=f.to; f.cast=0.0 }
        f.press=f.finger
        return f
    }
}
