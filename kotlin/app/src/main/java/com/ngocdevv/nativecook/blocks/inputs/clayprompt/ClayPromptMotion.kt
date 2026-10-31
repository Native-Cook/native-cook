package com.ngocdevv.nativecook.blocks.inputs.clayprompt

import kotlin.math.*

internal object ClayPromptMotion {
    const val LOOP = 11.0
    const val CHAR = 0.085
    const val MEET = 40.0
    fun seg(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun smooth(a: Double,b: Double,x: Double): Double { val u=seg(x,a,b);return u*u*(3-2*u) }
    fun ease(x: Double) = if(x<0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun out(x: Double) = 1-(1-x).pow(3)
    fun spring(x: Double) = if(x<=0) 0.0 else 1-exp(-6*x)*(cos(10*x)+0.6*sin(10*x))
    private fun back(x: Double): Double {val u=x-1;return 1+2.45*u*u*u+1.45*u*u}
    data class Look(val lift:Double,val spread:Double,val k:Double,val pull:Double,val blobR:Double,val reveal:Double,val night:Double)
    fun look(g:Double)=Look(ease(seg(g,0.0,0.42)),if(g<=0.5)0.0 else back(seg(g,0.5,0.86)),
        2+30*smooth(0.12,0.4,g)*(1-smooth(0.52,0.8,g)),smooth(0.22,0.46,g)*(1-smooth(0.5,0.64,g)),
        62*smooth(0.25,0.46,g)*(1-smooth(0.5,0.6,g))*(1+0.1*sin(PI*seg(g,0.4,0.52))),out(seg(g,0.5,0.8)),smooth(0.55,0.9,g))
    data class Pose(val x:Double,val y:Double,val size:Double,val squash:Double)
    fun pose(g:Double,sx:Double,sy:Double,ss:Double,c:DoubleArray,cx:Double,cy:Double):Pose {
        val l=look(g)
        if(g<=0.5){val a=l.lift;return Pose(sx+(cx-sx)*a,sy+(cy-sy)*a-70*sin(PI*a),ss+(MEET-ss)*a,-0.12*sin(PI*a)*(1-a))}
        val b=l.spread;return Pose(cx+(c[0]-cx)*b,cy+(c[1]-cy)*b,MEET+(c[2]-MEET)*b,0.08*sin(PI*min(1.0,b))*(1-seg(g,0.75,1.0)))
    }
    fun composed(kind:Int,sofa:Boolean,x:Double,y:Double,w:Double,h:Double)=when(kind){
        1->doubleArrayOf(x+0.52*w,y+0.74*h,0.27*w)
        0->if(sofa)doubleArrayOf(x+0.42*w,y+0.6*h,0.14*w) else doubleArrayOf(x+0.46*w,y+0.72*h,0.16*w)
        2->doubleArrayOf(x+0.72*w,y+0.25*h,0.125*w)
        else->doubleArrayOf(x+0.14*w,y+0.74*h,0.115*w)
    }
    fun slot(i:Int,n:Double,w:Double)=w/2+(i-(n-1)/2)*88
    fun popTime(i:Int)=0.4+intArrayOf(5,14,30)[i]*CHAR+0.12
    data class Frame(val chars:Int,val pops:DoubleArray,val g:Double,val clear:Double,val press:Double,val finger:Double,val fingerAt:Int,val dragY:Double)
    fun frame(t:Double):Frame {
        val pops=DoubleArray(3){spring((t-popTime(it))/0.6)}
        var g=seg(t,3.42,5.82)-0.42*ease(seg(t,7.2,7.9))*(1-if(t>7.9)spring((t-7.9)/0.7)else 0.0)
        g*=1-smooth(10.5,10.95,t)
        val at=if(t<3.8)0 else 1
        val finger=if(at==0)out(seg(t,3.12,3.3))*(1-out(seg(t,3.44,3.62))) else out(seg(t,7.02,7.2))*(1-out(seg(t,7.9,8.08)))
        return Frame((floor((t-0.4)/CHAR)+1).toInt().coerceIn(0,30),pops,g,ease(seg(t,9.8,10.5)),out(seg(t,3.3,3.38))*(1-out(seg(t,3.42,3.6))),finger,at,130*ease(seg(t,7.2,7.9)))
    }
}
