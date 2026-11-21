package com.ngocdevv.nativecook.blocks.pickers.wavefieldpad

import kotlin.math.*

/** Pure nine-second autoplay model, translated from wfp-motion.ts. Coordinates are pad UV. */
internal object WaveFieldPadMotion {
    const val LOOP = 9.0
    const val RANGE = 266.0
    const val MAX_LIVE_OVERSHOOT_PT = 14.0 // Live fling policy is pending the shared GPU fix.
    private const val STEP = .005
    private const val N = 1800
    data class P(val u: Double, val v: Double)
    data class Frame(val puck: P, val settle: Double, val ripple: Double, val time: Double,
                     val dent: P, val beat: Double, val hz: Double, val lock: Double, val ring: Double,
                     val scale: Double, val halo: Double, val depth: Double,
                     val rip0: Ripple, val rip1: Ripple, val wall: P, val scatter: P, val velocity: P, val jitter: Double, val flash: Double)
    data class Ripple(val x: Double, val y: Double, val age: Double, val amplitude: Double)
    private fun seg(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0,1.0)
    private fun mix(a: Double,b: Double,k: Double) = a+(b-a)*k
    private fun curve(x1: Double,y1: Double,x2: Double,y2: Double,k: Double): Double {
        if (k <= 0) return 0.0
        if (k >= 1) return 1.0
        var s=k
        repeat(6) {
            val x=3*(1-s)*(1-s)*s*x1+3*(1-s)*s*s*x2+s*s*s-k
            val dx=3*(1-s)*(1-s)*x1+6*(1-s)*s*(x2-x1)+3*s*s*(1-x2)
            if (abs(dx)<1e-6) return@repeat
            s=(s-x/dx).coerceIn(0.0,1.0)
        }
        return 3*(1-s)*(1-s)*s*y1+3*(1-s)*s*s*y2+s*s*s
    }
    private fun out(k: Double)=curve(.2,.8,.2,1.0,k)
    private fun inside(k: Double)=curve(.32,0.0,.67,0.0,k)
    private fun inout(k: Double)=curve(.65,0.0,.35,1.0,k)
    private fun easeSettle(k: Double)=curve(.3,.35,.25,1.0,k)
    private fun quad(a:Double,c:Double,b:Double,s:Double)=(1-s)*(1-s)*a+2*(1-s)*s*c+s*s*b
    private fun spring(t:Double, from:Double, to:Double, d:Double,k:Double):Double {
        if(t<=0) return from
        val w=sqrt(k); val z=d/(2*w); val wd=w*sqrt(1-z*z); val x=from-to
        return to+exp(-z*w*t)*(x*cos(wd*t)+(z*w*x/wd)*sin(wd*t))
    }
    private fun band(x:Double):Double = when { x>1 -> 1+MAX_LIVE_OVERSHOOT_PT*tanh((x-1)*RANGE/MAX_LIVE_OVERSHOOT_PT)/RANGE; x<0 -> -MAX_LIVE_OVERSHOOT_PT*tanh(-x*RANGE/MAX_LIVE_OVERSHOOT_PT)/RANGE; else -> x }
    fun puckAt(input:Double):P {
        val t=((input%LOOP)+LOOP)%LOOP
        if(t>8.2) return P(.5,.5)
        val p=when {
            t<.42 -> P(.5,.5)
            t<1.9 -> { val s=inout(seg(t,.42,1.9)); P(quad(.5,.58,.8,s),quad(.5,.8,.78,s)) }
            t<3.3 -> P(.8,.78)
            t<4.6 -> { val s=inout(seg(t,3.3,4.6)); P(quad(.8,.6,.22,s),quad(.78,.36,.24,s)) }
            t<5.4 -> P(.22,.24)
            t<5.75 -> { val s=1-(1-seg(t,5.4,5.75)).pow(3); P(mix(.22,1.06,s),mix(.24,.55,s)) }
            t<6.8 -> P(spring(t-5.75,1.06,.9,15.1,93.4),spring(t-5.75,.55,.56,15.1,93.4))
            else -> P(spring(t-6.8,spring(1.05,1.06,.9,15.1,93.4),.5,19.0,195.0),spring(t-6.8,spring(1.05,.55,.56,15.1,93.4),.5,19.0,195.0))
        }
        return P(band(p.u),band(p.v))
    }
    fun hzAt(u:Double,v:Double)=(1+6.9*((u.coerceIn(0.0,1.0)+v.coerceIn(0.0,1.0))/2-.5)).coerceIn(.5,4.2)
    fun settleAt(t:Double):Double=when {
        t<1.9 -> 0.0; t<2.8 -> easeSettle(seg(t,1.9,2.8)); t<3.3 -> 1.0
        t<3.6 -> 1-inside(seg(t,3.3,3.6)); t<4.6 -> 0.0
        t<5.3 -> easeSettle(seg(t,4.6,5.3)); t<5.4 -> 1.0
        t<5.55 -> 1-inside(seg(t,5.4,5.55)); t<7.7 -> 0.0
        t<8.4 -> easeSettle(seg(t,7.7,8.4)); t<8.7 -> 1-inside(seg(t,8.4,8.7)); else -> 0.0
    }
    private val tables: Triple<DoubleArray,DoubleArray,DoubleArray> by lazy {
        val u=DoubleArray(N+1); val v=DoubleArray(N+1); val beat=DoubleArray(N+1)
        var x=.5; var y=.5; var vx=0.0; var vy=0.0
        repeat(2) { loop -> for(i in 0..N) { if(loop==1){u[i]=x;v[i]=y}; if(i<N) repeat(5){ j ->
            val p=puckAt(i*STEP+j*.001); vx+=(-110*(x-p.u)-11*vx)*.001; vy+=(-110*(y-p.v)-11*vy)*.001; x+=vx*.001; y+=vy*.001
        } } }
        for(i in 0 until N){val p=puckAt((i+.5)*STEP);beat[i+1]=beat[i]+hzAt(p.u,p.v)*STEP}
        val whole=2*floor(beat[N]/2+.5); for(i in 0..N) beat[i]=beat[i]*whole/beat[N]
        Triple(u,v,beat)
    }
    private fun sample(a:DoubleArray,t:Double):Double { val f=t/STEP; val i=floor(f).toInt().coerceIn(0,N-1); return mix(a[i],a[i+1],f-i) }
    private fun press(t:Double):Double=when { t<.3 -> 0.0; t<.42 -> out(seg(t,.3,.42)); t<1.9 -> 1.0; t<2.2 -> 1-out(seg(t,1.9,2.2)); t<3.18 -> 0.0; t<3.3 -> out(seg(t,3.18,3.3)); t<4.6 -> 1.0; t<4.9 -> 1-out(seg(t,4.6,4.9)); t<5.28 -> 0.0; t<5.4 -> out(seg(t,5.28,5.4)); t<5.52 -> 1-out(seg(t,5.4,5.52)); else -> 0.0 }
    private val wallTime: Double by lazy {
        var lo=5.4; var hi=5.75
        repeat(30) { val m=(lo+hi)/2; val s=1-(1-seg(m,5.4,5.75)).pow(3); if(mix(.22,1.06,s)>=1) hi=m else lo=m }
        hi
    }
    fun frameAt(seconds:Double):Frame {
        val t=((seconds%LOOP)+LOOP)%LOOP; val p=puckAt(t); val press=press(t)
        val hum=maxOf(if(t>1.9&&t<3.3) sin(PI*seg(t,1.9,3.3)) else 0.0,if(t>4.6&&t<5.4) sin(PI*seg(t,4.6,5.4)) else 0.0,if(t>7.7&&t<8.6) sin(PI*seg(t,7.7,8.6)) else 0.0)
        val lock=when { t>=2.8&&t<3.6 -> out(seg(t,2.8,2.88))*(1-inside(seg(t,3.3,3.45))); t>=5.2&&t<5.6 -> out(seg(t,5.2,5.3))*(1-inside(seg(t,5.4,5.5))); t>=8.2&&t<8.7 -> out(seg(t,8.2,8.3))*(1-inside(seg(t,8.4,8.55))); else -> 0.0 }
        val ring=if(t>=2.8&&t<3.3) seg(t,2.8,3.3) else 0.0
        val blip=if(t>=6.8&&t<7.16) .08*sin(PI*seg(t,6.8,7.16)) else 0.0
        val ripple=when {t in 5.62..6.22 -> 1-(t-5.62)/.6; t in 6.8..7.4 -> 1-(t-6.8)/.6; t in 8.4..9.0 -> 1-(t-8.4)/.6; else -> 0.0}
        val none=Ripple(0.0,0.0,9.0,0.0)
        val wallV=mix(.24,.55,1-(1-seg(wallTime,5.4,5.75)).pow(3))
        val age=t-wallTime
        val r0=if(age>=0&&age<.6) Ripple(354.0,44+(1-wallV)*RANGE,age,10*(1-out(age/.6))) else none
        val home=t-6.8; val scatterAge=t-8.4
        val r1=when { home>=0&&home<.6 -> Ripple(44+.9*RANGE,44+(1-.56)*RANGE,home,8*(1-out(home/.6))); scatterAge>=0&&scatterAge<.6 -> Ripple(177.0,177.0,scatterAge,8*(1-out(scatterAge/.6))); else -> none }
        val pb=puckAt(t-1.0/60)
        return Frame(p,settleAt(t),ripple,t,P(sample(tables.first,t),sample(tables.second,t)),sample(tables.third,t),hzAt(p.u,p.v),lock,ring,1-.06*press+blip+.012*hum*sin(2*PI*3*t),.38+.17*press+.07*hum+.06*(1-cos(2*PI*6*t/LOOP))*(1-press),16+6*press,r0,r1,P((max(0.0,p.u-1)-max(0.0,-p.u))*RANGE*.8,(max(0.0,-p.v)-max(0.0,p.v-1))*RANGE*.8),P(-.3*(p.u-.5)*RANGE,.3*(p.v-.5)*RANGE),P((p.u-pb.u)*RANGE*60,-(p.v-pb.v)*RANGE*60),floor(t*30)%270,if(t>=2.8&&t<3.3) sin(PI*seg(t,2.8,3.3)) else 0.0)
    }
}
