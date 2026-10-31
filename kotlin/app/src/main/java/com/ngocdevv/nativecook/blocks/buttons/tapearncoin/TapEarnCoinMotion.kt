package com.ngocdevv.nativecook.blocks.buttons.tapearncoin

import kotlin.math.*

/** Pure transcription of tec-motion.ts; seconds and logical points throughout. */
internal object TapEarnCoinMotion {
    const val LOOP = 10.0
    const val BASE = 438.0
    const val BURST_LIFE = 1.7
    const val LAND = 0.62
    const val RESUME_AFTER = 2.0
    val TAP_AT = doubleArrayOf(0.9, 1.65, 2.4)
    val TAP_PAY = doubleArrayOf(7.0, 6.0, 5.0)
    private val TAP_LEAN = arrayOf(doubleArrayOf(0.05, -0.04), doubleArrayOf(-0.06, 0.03), doubleArrayOf(0.02, 0.05))
    fun clamp(v: Double) = v.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp((t-a)/(b-a))
    fun out(x: Double) = 1-(1-x).pow(3)
    fun inside(x: Double) = x*x*x
    fun inOut(x: Double) = if (x<0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun mix(a: Double,b: Double,t: Double) = a+(b-a)*t
    fun spring(t: Double,response: Double,zeta: Double): Double {
        if(t<=0) return 0.0
        val w=2*PI/response; val wd=w*sqrt(1-zeta*zeta)
        return 1-exp(-zeta*w*t)*(cos(wd*t)+zeta*w/wd*sin(wd*t))
    }
    fun ring(t: Double,freq: Double,decay: Double) = if(t<=0) 0.0 else sin(2*PI*freq*t)*exp(-decay*t)
    fun hash(n: Double): Double { val s=sin(n*12.9898+78.233)*43758.5453;return s-floor(s) }
    // Reanimated 4 duration springs: same stiffness bisection, mass and relative energy gate.
    data class SpringSample(val position: Double,val velocity: Double,val finished: Boolean)
    fun durationStiffness(x0: Double,v0: Double,duration: Double,zeta: Double): Double {
        if(x0==0.0&&v0==0.0)return 8000.0
        fun diff(k: Double): Double {
            val t=duration*1.5;val w=sqrt(k/4)*zeta;val e=exp(-w*t)
            val x=(x0+(v0+x0*w)*t)*e
            val v=(x0+(v0+x0*w)*t)*e*(-w)+(v0+x0*w)*e
            return (0.5*k*x*x+2*v*v)/(0.5*k*x0*x0+2*v0*v0)-6e-9
        }
        var lo=2.220446049250313e-16;var hi=8000.0
        val direction=if(diff(hi)>=diff(lo))1 else -1
        var current=(lo+hi)/2
        repeat(100){if(abs(diff(current))<=6e-12)return current;if(diff(current)*direction<0)lo=current else hi=current;current=(lo+hi)/2}
        return current
    }
    fun durationSpring(from: Double,to: Double,v0: Double,t: Double,zeta: Double,stiffness: Double): SpringSample {
        val w=sqrt(stiffness/4);val x0=from-to
        val x: Double;val v: Double
        if(zeta<1){
            val wd=w*sqrt(1-zeta*zeta);val a=exp(-zeta*w*t)
            val frag=a*(sin(wd*t)*(v0+zeta*w*x0)/wd+x0*cos(wd*t))
            x=frag;v=-zeta*w*frag+a*(cos(wd*t)*(v0+zeta*w*x0)-wd*x0*sin(wd*t))
        }else{val a=exp(-w*t);x=a*(x0+(v0+w*x0)*t);v=a*(-w)*(x0+(v0+w*x0)*t)+a*(v0+w*x0)}
        val e0=0.5*stiffness*x0*x0+2*v0*v0
        val done=e0==0.0||(0.5*stiffness*x*x+2*v*v)/e0<=6e-9
        return SpringSample(if(done)to else to+x,if(done)0.0 else v,done)
    }
    data class Scene(
        var press: Double=0.0,var hop: Double=0.0,var morph: Double=0.0,var lift: Double=0.0,
        var travel: Double=0.0,var dim: Double=0.0,var sheet: Double=0.0,var swing: Double=0.0,
        var amount: Double=BASE,var later: Double=0.0,var label: Int=3,var leanX: Double=0.0,var leanZ: Double=0.0,
    )
    data class Burst(val age: Double,val seed: Double,val pay: Double,val power: Double)
    fun sceneAt(t: Double,base: Double=BASE): Scene {
        var press=0.0;var leanX=0.0;var leanZ=0.0;var paid=0.0;var label=3
        for(i in 0..2){
            val a=TAP_AT[i];val r=a+0.11
            if(t>=a&&t<a+0.09) press=out(seg(t,a,a+0.09))
            else if(t>=a+0.09&&t<r) press=1.0
            else if(t>=r&&t<r+0.7) press=1-spring(t-r,0.32,0.42)
            val lean=if(t>=a&&t<r) out(seg(t,a,a+0.09)) else if(t>=r&&t<r+0.7) 1-spring(t-r,0.3,0.45) else 0.0
            if(t>=a&&t<r+0.7){leanX=TAP_LEAN[i][0]*lean;leanZ=TAP_LEAN[i][1]*lean}
            paid+=TAP_PAY[i]*out(seg(t,r+0.12,r+0.62))
            if(t>=r)label=2-i
        }
        if(t>=7.8)label=4
        if(t>=9.3)label=3
        val up=spring(t-4.78,0.62,0.82)
        return Scene(press,
            sin(PI*seg(t,3.0,3.16))*0.55+ring(t-8.95,2.6,7.0)*0.9,
            inOut(seg(t,3.3,4.42))*(1-inOut(seg(t,7.72,8.62))),
            out(seg(t,3.16,3.62))*(1-inOut(seg(t,8.5,8.95))),
            inOut(seg(t,4.78,5.52))*(1-inOut(seg(t,7.26,7.9))),
            out(seg(t,4.62,5.1))*(1-inOut(seg(t,7.2,7.7))),
            if(t<7.2)up else (1-inOut(seg(t,7.2,7.62)))*min(1.0,up),
            ring(t-4.42,2.2,3.6)*0.32,
            base+paid*(1-inOut(seg(t,5.7,6.4))),
            out(seg(t,7.0,7.08))*(1-out(seg(t,7.14,7.34))),label,leanX,leanZ)
    }
    fun gleam(t: Double): Double {var g=-1.0;for(a in TAP_AT){val r=a+0.11;if(t>=r&&t<r+0.75)g=-1+2*out(seg(t,r,r+0.75))};return g}
    fun bursts(t: Double)=TAP_AT.indices.mapNotNull {i->val age=t-TAP_AT[i]-0.11;if(age>=0&&age<BURST_LIFE) Burst(age,11.0+i*7,TAP_PAY[i],if(i==2)1.0 else 0.0) else null}
    fun glow(bursts: List<Burst>)=bursts.filter {it.age>=0}.maxOfOrNull {(1-exp(-it.age/0.04))*exp(-it.age/0.42)*(0.8+0.2*it.power)}?:0.0
    fun bump(bursts: List<Burst>,k: Int): Double {var v=0.0;for(b in bursts){val age=b.age-LAND;if(age<0||age>0.8)continue;for(i in 0..1)if((i+floor(b.seed).toInt())%3==k)v+=sin(min(age/0.09,1.0)*PI*0.5)*exp(-age/0.16)*(1+ring(age,3.2,9.0)*0.4)};return v}
    fun column(amount: Double,k: Int): Double {val unit=10.0.pow(k);val whole=floor(amount/unit);return if(k==0)amount else whole+clamp(amount-whole*unit-(unit-1))}
    data class Coin(val x: Double,val y: Double,val r: Double,val tilt: Double,val spin: Double,val alpha: Double,val lit: Double,val roll: Double)
    fun coin(age: Double,seed: Double,i: Int,power: Double,ox: Double,oy: Double,radius: Double,tx: Double,ty: Double,floorY: Double): Coin {
        val h1=hash(seed*17+i*3.1);val h2=hash(seed*5.3+i*11.7);val h3=hash(seed*9.1+i*1.9);val h4=hash(seed*2.7+i*23.3)
        val r=radius*(0.085+0.035*h3);val sx=ox+(h1-0.5)*radius*0.55;val sy=oy-radius*0.2
        val emerge=out(seg(age,0.0,0.08));val roll=age*(7+11*h2)+h4*6.28
        if(i<2){
            val u=inside(seg(age,0.0,LAND));val cx=mix(sx,tx,0.5)+(if(i==0)-1 else 1)*radius*0.5;val cy=min(sy,ty)-radius*(0.95+0.25*h1);val a=1-u
            val shrink=1-inside(seg(age,LAND-0.12,LAND+0.02))
            return Coin(a*a*sx+2*a*u*cx+u*u*tx,a*a*sy+2*a*u*cy+u*u*ty,r*emerge*(0.75+0.25*shrink),cos(roll),roll*0.3,shrink,0.5+0.5*sin(roll*1.3),roll)
        }
        val boost=1+0.18*power;val vx=(h1-0.5)*radius*4.2*boost;val vy=-radius*(3.6+2.6*h2)*boost;val g=radius*13.5
        val disc=vy*vy-2*g*(sy-floorY);val hit=if(disc>0)(-vy+sqrt(disc))/g else 99.0
        val x: Double;var y: Double;var tilt=cos(roll);var landed=0.0
        if(age<hit){x=sx+vx*age;y=sy+vy*age+0.5*g*age*age}
        else {val a=age-hit;val up=-(vy+g*hit)*0.3;val rest=-2*up/g;x=sx+vx*hit+vx*0.5*min(a,rest+0.15);y=if(a<rest)floorY+up*a+0.5*g*a*a else floorY;landed=seg(a,rest,rest+0.12);tilt=mix(cos(roll),0.22,landed)}
        y-=r*0.25*landed
        return Coin(x,y,r*emerge,tilt,mix((h3-0.5)*1.4+age*(h1-0.5)*3,0.0,landed),1-seg(age,min(BURST_LIFE-0.25,hit+0.55),min(BURST_LIFE,hit+0.8)),0.5+0.5*sin(roll*1.3+h3*4),if(landed>0.99)PI*0.5 else roll)
    }
}
