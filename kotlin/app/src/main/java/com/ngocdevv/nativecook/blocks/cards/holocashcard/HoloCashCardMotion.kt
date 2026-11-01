package com.ngocdevv.nativecook.blocks.cards.holocashcard

import kotlin.math.*

internal object HoloCashCardMotion {
    const val LOOP = 8.0
    const val START = 502020313L
    fun seg(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun smooth(k: Double): Double { val x=k.coerceIn(0.0,1.0); return x*x*(3-2*x) }
    fun mix(a: Double,b: Double,k: Double)=a+(b-a)*k
    fun scriptTilt(t: Double): DoubleArray {
        if(t<1.0 || t>=2.4) return doubleArrayOf(0.0,0.0,0.0)
        val k=seg(t,1.0,2.4); val a=smooth(seg(k,0.0,0.35)); val b=smooth(seg(k,0.35,0.9))
        return doubleArrayOf(1.0,mix(mix(0.0,9.0,a),-7.0,b),mix(mix(0.0,-15.0,a),15.0,b))
    }
    fun ticksAt(t: Double)=floor(min(t,3.2)/0.3+max(0.0,min(t,6.4)-3.2)/0.11+max(0.0,t-6.4)/0.3).toLong()
    class Odometer(value: Long) {
        val last=IntArray(9) { (value/10.0.pow(8-it).toLong()%10).toInt() }
        val disp=DoubleArray(9) { last[it].toDouble() }
        val target=disp.copyOf()
        val velocity=DoubleArray(9)
        fun step(value: Long,dt: Double) {
            val n=max(1,ceil(dt/(1.0/240)).toInt()); val h=dt/n
            for(k in 0..8) {
                val d=(value/10.0.pow(8-k).toLong()%10).toInt()
                if(d!=last[k]) { target[k]+=(d-last[k]+10)%10; last[k]=d }
                repeat(n) {
                    velocity[k]+=(420*(target[k]-disp[k])-2*sqrt(420.0)*0.9*velocity[k])*h
                    disp[k]+=velocity[k]*h
                }
                if(target[k]>1000) { target[k]-=1000; disp[k]-=1000 }
            }
        }
    }
    class State {
        var time=0.0; var clock=0.0; var last = -10.0
        var rx=0.0; var ry=0.0; var vx=0.0; var vy=0.0; var tx=0.0; var ty=0.0
        var on=false; var son=false
        var value=START; var extra=0L; var acc=0.0; var boost=false; var boostK=0.0; var dip=0.0
        var odo=Odometer(START); var apy=3.0; var apyVelocity=0.0; var popAt = -10.0
        var hide=0.0; var hideTarget=0.0
        fun changeBoost(enabled: Boolean) { if(boost==enabled)return; boost=enabled; popAt=clock }
        fun step(dt: Double,auto: Boolean,reduced: Boolean) {
            clock+=dt
            if(auto && !reduced && !(on || clock-last<2)) {
                time=(time+dt)%LOOP
                val st=scriptTilt(time); son=st[0]==1.0; tx=st[1]; ty=st[2]
                changeBoost(time>=3.2 && time<6.4)
                val target=START+extra+ticksAt(time)
                dip=sin(seg(time,7.25,7.75)*PI)
                if(time>=7.5 || time<7.25) {
                    val v=if(time>=7.5) START+extra else target
                    if(time>=7.5 && value!=v)odo=Odometer(v)
                    value=v
                } else value=target
            } else {
                dip=0.0; acc+=dt
                val tick=if(boost)0.11 else 0.3
                while(acc>=tick) { acc-=tick; value++ }
            }
            val held=on || son; val k=if(held)320.0 else 170.0; val z=if(held)0.9 else 0.32
            val c=2*sqrt(k)*z; val n=max(1,ceil(dt/(1.0/240)).toInt()); val h=dt/n
            repeat(n) {
                vx+=(k*((if(held)tx else 0.0)-rx)-c*vx)*h; rx+=vx*h
                vy+=(k*((if(held)ty else 0.0)-ry)-c*vy)*h; ry+=vy*h
            }
            boostK+=((if(boost)1 else 0)-boostK)*(1-exp(-dt*6))
            val ad=if(boost)8.0 else 3.0
            if(reduced) { apy=ad; odo=Odometer(value) }
            else { apyVelocity+=(380*(ad-apy)-2*sqrt(380.0)*0.8*apyVelocity)*dt; apy+=apyVelocity*dt; odo.step(value,dt) }
            hide+=(hideTarget-hide)*(1-exp(-dt*14))
        }
        fun stickerScale(): Double {
            val s=clock-popAt
            return if(s>=0 && s<0.6) 1+0.16*sin(s*22)*exp(-s*7)-0.08*exp(-s*30) else 1.0
        }
    }
}
