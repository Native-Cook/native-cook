package com.ngocdevv.nativecook.blocks.cards.filamentbulbdimmer
import kotlin.math.*

internal object FilamentBulbDimmerMotion {
    const val LOOP=10.0
    val SWING_W=2*PI/1.6;const val SWING_Z=.14;val TWIST_W=2*PI/2.4;const val TWIST_Z=.12
    const val PULL_W=22.0;const val PULL_Z=.32;const val GRAV=3400.0;const val WARM=.45;const val COOL=.22
    val off=8.12+.3*(1-Math.cbrt(1-16.0/24));val colours=arrayOf(4.8 to 1.0,5.8 to 4.0,6.8 to 2.0)
    data class Frame(var th:Double=0.0,var s:Double=0.0,var tw:Double=0.0,var on:Double=1.0,var heat:Double=.78,var level:Double=.78,var lead:Double=2.0,var trail:Double=2.0,var finger:Double=0.0,var fingerAt:Int=0,var fingerDot:Double=0.0,var fingerPress:Double=0.0,var time:Double=0.0,var tog:Double=1.0,var shownPct:Double=78.0,var knobPress:Double=0.0) { val colour get()=(lead+trail)/2 }
    fun clamp01(x:Double)=x.coerceIn(0.0,1.0)
    fun seg(t:Double,a:Double,b:Double)=clamp01((t-a)/(b-a))
    fun easeInOut(x:Double)=if(x<.5)4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeOut(x:Double)=1-(1-x).pow(3)
    fun smooth(x:Double)=x*x*(3-2*x)
    fun warmUp(x:Double)=easeOut(x)*(1-.14*sin(x*38)*(1-x))
    fun lum(h:Double)=max(0.0,h).pow(2.1)
    fun osc(t:Double,x:Double,v:Double,w:Double,z:Double):Double {val wd=w*sqrt(1-z*z);return exp(-z*w*t)*(x*cos(wd*t)+(v+z*w*x)/wd*sin(wd*t))}
    fun levelAt(t:Double)=.78-.6*easeInOut(seg(t,2.2,3.1))+.82*easeInOut(seg(t,3.35,4.25))-.22*easeInOut(seg(t,7.35,7.95))
    fun frameAt(t:Double):Frame {
        val f=Frame(time=t);val fade=1-smooth(seg(t,8.8,9.8))
        if(t>=.6 && t<1.3) {val x=easeInOut(seg(t,.6,1.3));f.th=.32*x;f.tw=.55*x;f.s = -8*x}
        else if(t>=1.3) {val tau=t-1.3;val t0=sqrt(16/GRAV);f.th=osc(tau,.32,0.0,SWING_W,SWING_Z);f.tw=osc(tau,.55,0.0,TWIST_W,TWIST_Z);f.s=if(tau<t0)-8+.5*GRAV*tau*tau else osc(tau-t0,0.0,GRAV*t0,PULL_W,PULL_Z)}
        if(t>=8.12 && t<8.42)f.s+=24*easeOut(seg(t,8.12,8.42)) else if(t>=8.42)f.s+=osc(t-8.42,24.0,0.0,PULL_W,PULL_Z*1.1)
        f.th=f.th*fade+.004*sin(2*PI*t/LOOP)+.002*sin(4*PI*t/LOOP+1);f.tw=f.tw*fade+.16*sin(2*PI*t/LOOP);f.s*=fade
        f.level=levelAt(t);var from=2.0
        for((at,to)in colours) {f.lead+=(to-from)*easeOut(seg(t,at+.03,at+.36));f.trail+=(to-from)*easeInOut(seg(t,at+.08,at+.62));from=to}
        f.on=if(t>=off && t<9.15)0.0 else 1.0;f.heat=levelAt(t-.06)
        if(t>=off && t<9.15)f.heat=levelAt(off)*exp(-(t-off)/COOL) else if(t>=9.15)f.heat=levelAt(t)*warmUp(seg(t,9.15,9.6))
        val grab=easeOut(seg(t,.42,.58))*(1-easeOut(seg(t,1.3,1.46)))
        if(grab>f.finger) {f.finger=grab;f.fingerAt=0;f.fingerPress=grab}
        for((a,b)in arrayOf(2.2 to 3.1,3.35 to 4.25,7.35 to 7.95)) {val p=easeOut(seg(t,a-.22,a-.06))*(1-easeOut(seg(t,b+.05,b+.22)));if(p>f.finger){f.finger=p;f.fingerAt=1;f.fingerPress=p}}
        for((at,to)in colours) {val p=easeOut(seg(t,at-.18,at-.04))*(1-easeOut(seg(t,at+.14,at+.3)));if(p>f.finger){f.finger=p;f.fingerAt=2;f.fingerDot=to;f.fingerPress=easeOut(seg(t,at-.04,at+.05))*(1-easeOut(seg(t,at+.1,at+.22)))}}
        val pull=easeOut(seg(t,7.94,8.09))*(1-easeOut(seg(t,8.42,8.58)));if(pull>f.finger){f.finger=pull;f.fingerAt=4;f.fingerPress=pull}
        val tg=easeOut(seg(t,8.97,9.11))*(1-easeOut(seg(t,9.29,9.45)));if(tg>f.finger){f.finger=tg;f.fingerAt=3;f.fingerPress=easeOut(seg(t,9.11,9.2))*(1-easeOut(seg(t,9.25,9.37)))}
        f.tog=f.on;f.shownPct=(f.level*100).roundToInt().toDouble();return f
    }
    fun bezier(x:Double,x1:Double,y1:Double,x2:Double,y2:Double):Double {fun b(t:Double,a:Double,c:Double)=3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*c+t*t*t
        var lo=0.0;var hi=1.0;repeat(30){val t=(lo+hi)/2;if(b(t,x1,x2)<clamp01(x))lo=t else hi=t};return b((lo+hi)/2,y1,y2)}
}
