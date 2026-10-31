package com.ngocdevv.nativecook.blocks.cards.glassorbshelf

import kotlin.math.*

internal object GlassOrbShelfMotion {
    const val LOOP = 9.6
    fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t-a)/(b-a))
    fun easeInOut(x: Double) = if (x < 0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun snap(x: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        val e = easeInOut(min(1.0,x/0.82))
        return e + if (x > 0.82) sin((x-0.82)/0.18*PI)*0.025 else 0.0
    }
    private val moves = arrayOf(doubleArrayOf(1.2,2.0,0.0,1.0),doubleArrayOf(3.4,4.2,1.0,2.0),doubleArrayOf(5.6,6.4,2.0,3.0),doubleArrayOf(7.6,8.9,3.0,0.0))
    fun offsetAt(t: Double): Double {
        var o=0.0
        moves.forEachIndexed { i,m -> if (t >= m[0]) {
            val x=seg(t,m[0],m[1]); o=m[2]+(m[3]-m[2])*(if (i==3) easeInOut(x) else snap(x))
        } }
        return o
    }
    fun rubber(v: Double): Double {
        if (v < 0) return -(-v/(1-v*1.6))*0.6
        if (v > 3) return 3+(v-3)/(1+(v-3)*1.6)*0.6
        return v
    }
    fun rounded(v: Double) = floor(v+0.5)
    fun bezier(x: Double,x1: Double,y1: Double,x2: Double,y2: Double): Double {
        fun b(t: Double,a: Double,c: Double) = 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*c+t*t*t
        var lo=0.0; var hi=1.0
        repeat(24) { val mid=(lo+hi)/2; if (b(mid,x1,x2)<x) lo=mid else hi=mid }
        return b((lo+hi)/2,y1,y2)
    }
    fun spring(t: Double,from: Double,to: Double,velocity: Double=0.0,duration: Double,damping: Double): Double {
        if (t<=0) return from
        val delta=from-to
        val v=if ((to>from && velocity<0) || (to<from && velocity>0)) 0.0 else velocity
        if (delta==0.0 && v==0.0) return to
        val mass=4.0; val threshold=6e-9; val settling=duration*1.5
        fun energyFraction(stiffness: Double): Double {
            val omega=sqrt(stiffness/mass)*damping
            val envelope=exp(-omega*settling)
            val x=(delta+(v+delta*omega)*settling)*envelope
            val vel=x * -omega+(v+delta*omega)*envelope
            return (stiffness*x*x+mass*vel*vel)/(stiffness*delta*delta+mass*v*v)-threshold
        }
        var lo=Math.ulp(1.0); var hi=8000.0; var stiffness=4000.0
        for (i in 0 until 100) {
            val f=energyFraction(stiffness)
            if (abs(f)<=threshold*1e-3) break
            if (f>0) lo=stiffness else hi=stiffness
            stiffness=(lo+hi)/2
        }
        val omega=sqrt(stiffness/mass); val wd=omega*sqrt(1-damping*damping)
        val e=exp(-damping*omega*t); val a=(v+damping*omega*delta)/wd
        val fragment=e*(delta*cos(wd*t)+a*sin(wd*t))
        val vel= -damping*omega*fragment+e*((v+damping*omega*delta)*cos(wd*t)-wd*delta*sin(wd*t))
        val ratio=(stiffness*fragment*fragment+mass*vel*vel)/(stiffness*delta*delta+mass*velocity*velocity)
        return if (ratio<=threshold) to else to+fragment
    }
}
