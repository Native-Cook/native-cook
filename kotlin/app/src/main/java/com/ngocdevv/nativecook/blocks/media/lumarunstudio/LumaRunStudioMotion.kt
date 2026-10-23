package com.ngocdevv.nativecook.blocks.media.lumarunstudio

import kotlin.math.*

internal data class LumaRunStudioParams(val q:Double=0.0,val angle:Double=0.0,val low:Double=.44,val high:Double=.58,val roiX:Double=0.0,val roiY:Double=0.0) {
    companion object { val Rest=LumaRunStudioParams();val Reduced=LumaRunStudioParams(1.0,0.0,.28,.78);val Zero=LumaRunStudioParams(0.0,0.0,0.0,0.0) }
}
internal data class LumaRunStudioKnot(val time:Double,val params:LumaRunStudioParams)
internal data class LumaRunStudioAppearance(val bracketAlpha:Double=1.0,val handleScale:Double=1.0,val anticipation:Double=0.0,val lowOffset:Double=0.0,val highOffset:Double=0.0)
internal object LumaRunStudioMath {
    const val LOOP=8.0
    fun clamp(x:Double,a:Double=0.0,b:Double=1.0)=x.coerceIn(a,b)
    fun smooth(x:Double):Double {val u=clamp(x);return u*u*(3-2*u)}
    fun cubic(x:Double):Double {val u=clamp(x);return if(u<.5)4*u*u*u else 1-(-2*u+2).pow(3)/2}
    fun mix(a:LumaRunStudioParams,b:LumaRunStudioParams,u:Double)=LumaRunStudioParams(a.q+(b.q-a.q)*u,a.angle+(b.angle-a.angle)*u,a.low+(b.low-a.low)*u,a.high+(b.high-a.high)*u,a.roiX+(b.roiX-a.roiX)*u,a.roiY+(b.roiY-a.roiY)*u)
    fun bounded(p:LumaRunStudioParams):LumaRunStudioParams {val lo=clamp(p.low,.08,.84);return LumaRunStudioParams(clamp(p.q),clamp(p.angle,-90.0,90.0),lo,clamp(p.high,lo+.08,.92),clamp(p.roiX,-30.0,30.0),clamp(p.roiY,-42.0,42.0))}
    fun quantized(p:LumaRunStudioParams):LumaRunStudioParams {
        fun nearest(x:Double,s:Double)=floor(x/s+.5)*s
        val lo=floor(p.low*200+.5).coerceIn(16.0,168.0);val hi=floor(p.high*200+.5).coerceIn(lo+16,184.0)
        return LumaRunStudioParams(clamp(nearest(p.q,1.0/64)),clamp(nearest(p.angle,1.0),-90.0,90.0),lo/200,hi/200,clamp(nearest(p.roiX,1.0),-30.0,30.0),clamp(nearest(p.roiY,1.0),-42.0,42.0))
    }
    fun authoredParams(time:Double):LumaRunStudioParams {
        val t=max(0.0,time)%LOOP;val window=cubic((t-1.30)/1.35)
        var p=LumaRunStudioParams(1-(1-clamp((t-1.30)/1.88)).pow(3),90*cubic((t-2.85)/2),.44-.16*window,.58+.20*window)
        if(t>=5.55){val back=cubic((t-5.55)/1.60);p=LumaRunStudioParams(1-cubic((t-5.55)/1.40),90*(1-back),.28+.16*back,.78-.20*back)}
        if(t>=7.15||t<=1.30)return LumaRunStudioParams.Rest
        return if(t>=6.95)p.copy(q=0.0) else p
    }
    val knots:List<LumaRunStudioKnot> = buildList {
        val times=mutableListOf(0.0)
        for(k in 0..15)times+=if(k==15)2.65 else 1.30+1.35*k/15
        for(k in 1..3)times+=if(k==3)2.85 else 2.65+.20*k/3
        for(k in 1..4)times+=if(k==4)3.18 else 2.85+.33*k/4
        for(k in 1..21)times+=if(k==21)4.85 else 3.18+1.67*k/21
        times+=5.55
        for(k in 1..17)times+=if(k==17)6.95 else 5.55+1.40*k/17
        times+=7.15
        times.forEach {add(LumaRunStudioKnot(it,authoredParams(it)))}
    }
    fun spring(start:Double,velocity:Double,target:Double,elapsed:Double,response:Double=.42,damping:Double=.82,end:Double=.60,tail:Double=.42):Double {
        if(elapsed<=0)return start;if(elapsed>=end)return target
        val w=2*PI/response;val c=damping*w;val wd=w*sqrt(1-damping*damping);val a=start-target;val b=(velocity+c*a)/wd
        return target+exp(-c*elapsed)*(a*cos(wd*elapsed)+b*sin(wd*elapsed))*(1-smooth((elapsed-tail)/(end-tail)))
    }
    fun appearance(time:Double):LumaRunStudioAppearance {
        val t=max(0.0,time)%LOOP
        if(t<=1.10||t>=7.40)return LumaRunStudioAppearance()
        if(t<1.30)return LumaRunStudioAppearance(anticipation=2*sin(PI*(t-1.10)/.20))
        if(t<7.15){val u=1-(1-clamp((t-1.30)/1.88)).pow(3);return LumaRunStudioAppearance(1-.78*u,1+.02*u)}
        return LumaRunStudioAppearance(clamp(spring(.22,0.0,1.0,t-7.15,.32,.85,.25,.18)),spring(1.02,0.0,1.0,t-7.15,.32,.85,.25,.18))
    }
    fun settledTarget(p:LumaRunStudioParams,velocityY:Double)=bounded(p.copy(q=if(p.q>.5||velocityY< -600)1.0 else 0.0,angle=floor(p.angle/15+.5)*15,low=floor(p.low*100+.5)/100,high=floor(p.high*100+.5)/100))
    fun settlement(a:LumaRunStudioParams,v:LumaRunStudioParams,b:LumaRunStudioParams,t:Double):LumaRunStudioParams {
        if(t<=0)return a;if(t>=.60)return b
        return bounded(LumaRunStudioParams(spring(a.q,v.q,b.q,t),spring(a.angle,v.angle,b.angle,t),spring(a.low,v.low,b.low,t),spring(a.high,v.high,b.high,t),spring(a.roiX,v.roiX,b.roiX,t),spring(a.roiY,v.roiY,b.roiY,t)))
    }
    fun recovery(p:LumaRunStudioParams,t:Double)=if(t<=0)p else if(t>=.60)LumaRunStudioParams.Rest else mix(p,LumaRunStudioParams.Rest,cubic(t/.60))
    fun rubber(over:Double)=over/(1+abs(over)/80)*.55
}
