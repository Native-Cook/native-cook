package com.ngocdevv.nativecook.blocks.gestures.stickerpeelprofile

import kotlin.math.*

internal object StickerPeelProfileMotion {
    const val LOOP = 9.0
    val half = arrayOf(52.0 to 48.0,27.0 to 25.0,46.0 to 22.0,25.0 to 31.0)
    data class Sticker(var x:Double,var y:Double,var rot:Double=0.0,var f:Double=0.0,var vf:Double=0.0,var corner:Int=2,var lift:Double=0.0,var sq:Double=0.0,var vsq:Double=0.0,var vx:Double=0.0,var k:Double=1.0)
    fun mix(a:Double,b:Double,k:Double)=a+(b-a)*k
    fun smooth(k:Double):Double { val x=k.coerceIn(0.0,1.0); return x*x*(3-2*x) }
    fun ease(k:Double):Double { val x=k.coerceIn(0.0,1.0); return if(x<.5)4*x*x*x else 1-(-2*x+2).pow(3)/2 }
    fun home(w:Double,t:Double)=mutableListOf(Sticker(w/2,t+128),Sticker(w/2-56,t+86,-14.0),Sticker(w/2-50,t+170,-10.0),Sticker(w/2+52,t+168,10.0))
    fun frame(time:Double,w:Double,top:Double):MutableList<Sticker> {
        val ss=home(w,top); val t=time%LOOP; val a=if(t<5.2).8 else 5.2
        val f=if(t<a+.9).62*ease((t-a)/.85) else 0.0
        val lift=if(t>=a+.85)1-smooth((t-(a+1.75))/.08) else 0.0
        val k=ease((t-(a+.9))/.85); val land=t-(a+1.8)
        val sq=if(land>=0 && land<.5)sin(land*22)*exp(-land*9)*.1 else 0.0
        val h=ss[2]; val ax=w-74; val ay=top+324
        ss[2]=Sticker(mix(if(t<5.2)h.x else ax,if(t<5.2)ax else h.x,k),mix(if(t<5.2)h.y else ay,if(t<5.2)ay else h.y,k)-sin(k*PI)*40,mix(if(t<5.2)h.rot else 14.0,if(t<5.2)14.0 else h.rot,k)+sin(k*PI)*(if(t<5.2)-12 else 12),f=f,corner=if(t<5.2)2 else 0,lift=lift,sq=sq)
        return ss
    }
}
