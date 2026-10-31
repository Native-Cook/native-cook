package com.ngocdevv.nativecook.blocks.onboarding.coinlightlanding

import kotlin.math.*

internal object CoinLightLandingMotion {
    const val LOOP = 8.0
    const val BASE = 733000.0
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun mix(a: Double, b: Double, k: Double) = a + (b-a)*k
    fun easeInOut(k: Double): Double { val x=clamp(k); return if(x<0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2 }
    fun easeOut(k: Double) = 1-(1-clamp(k)).pow(3)
    fun easeOutBack(k: Double): Double { val x=clamp(k)-1; return 1+2.5*x*x*x+1.5*x*x }
    data class Frame(var x: Double=0.0, var y: Double=0.0, var a: Double=1.0, var s: Double=1.0, var yaw: Double=0.0, var tx: Double=0.0, var pay: Double=BASE, var button: Double=0.0, var t: Double=0.0) {
        val dip get() = if(t>=7.2 && t<7.7) sin((t-7.2)/0.5*PI) else 0.0
    }
    fun at(t: Double,w: Double,h: Double,top: Double): Frame {
        val rx=w/2; val ry=h*0.74; val row=top+168; val by=top+354
        val bob=sin(2*PI*2*t/LOOP)*4
        val f=Frame(x=rx,y=ry+bob,yaw=0.12*sin(2*PI*t/LOOP),t=t)
        if(t>=1 && t<3.3) {
            val p=arrayOf(doubleArrayOf(rx,ry),doubleArrayOf(w*0.3,row+104),doubleArrayOf(w*0.72,row+24),doubleArrayOf(w*0.55,row-10),doubleArrayOf(104.0,by))
            val k=easeInOut((t-1)/2.3)*4; val i=min(3,floor(k).toInt()); val u=k-i
            val p0=p[max(0,i-1)]; val p1=p[i]; val p2=p[i+1]; val p3=p[min(4,i+2)]
            fun cat(j: Int) = 0.5*(2*p1[j]+(-p0[j]+p2[j])*u+(2*p0[j]-5*p1[j]+4*p2[j]-p3[j])*u*u+(-p0[j]+3*p1[j]-3*p2[j]+p3[j])*u*u*u)
            f.x=cat(0); f.y=cat(1); f.tx=(p2[0]-p1[0])*0.002
        } else if(t>=3.3 && t<4.6) {
            val k=easeOut((t-3.3)/0.3); f.x=104.0; f.y=by; f.s=mix(1.0,0.3,k); f.a=1-k
        } else if(t>=4.6 && t<5.4) {
            f.y=mix(h+140,ry+bob,easeOutBack((t-4.6)/0.8))
        } else if(t>=5.8 && t<7.2) { f.yaw+=4*PI*easeOut((t-5.8)/1.4) }
        f.pay=if(t>=3.4 && t<7.45) BASE+48*easeInOut(t-3.4) else BASE
        f.button=if(t>=3.3) exp(-max(0.0,t-3.4)*1.6)*(if(t<3.4) (t-3.3)/0.1 else 1.0) else 0.0
        return f
    }
}
