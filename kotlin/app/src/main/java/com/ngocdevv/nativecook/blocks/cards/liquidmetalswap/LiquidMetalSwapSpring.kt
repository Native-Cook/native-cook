package com.ngocdevv.nativecook.blocks.cards.liquidmetalswap

import kotlin.math.*

// Reanimated 4 duration solver, independent of the loop's scripted omega=9 return.
internal class LiquidMetalSwapSpring(private val displacement: Double,requestedVelocity: Double=0.0,duration: Double,private val damping: Double) {
    private val velocity=if(displacement*requestedVelocity>0) 0.0 else requestedVelocity
    private val omega: Double
    private val initialEnergy: Double
    init {
        val settling=duration*1.5
        fun energy(x: Double,v: Double,k: Double)=0.5*k*x*x+0.5*4*v*v
        fun difference(k: Double): Double {
            val w=sqrt(k/4)*damping
            val x=(displacement+(velocity+displacement*w)*settling)*exp(-w*settling)
            val v= -w*x+(velocity+displacement*w)*exp(-w*settling)
            val initial=energy(displacement,velocity,k)
            return if(initial==0.0) -6e-9 else energy(x,v,k)/initial-6e-9
        }
        var lo=2.220446049250313e-16; var hi=8000.0
        val direction=if(difference(hi)>=difference(lo)) 1.0 else -1.0
        var k=(lo+hi)/2; var iterations=100
        while(abs(difference(k))>6e-12 && iterations>0) {
            iterations--
            if(difference(k)*direction<0) lo=k else hi=k
            k=(lo+hi)/2
        }
        omega=sqrt(k/4); initialEnergy=energy(displacement,requestedVelocity,k)
    }
    fun value(tau: Double): Double {
        val t=max(0.0,tau); val x: Double; val v: Double
        if(damping<1) {
            val w=omega*sqrt(1-damping*damping); val envelope=exp(-damping*omega*t)
            val phase=sin(w*t)*(velocity+damping*omega*displacement)/w+displacement*cos(w*t)
            x=envelope*phase
            v= -damping*omega*x+envelope*(cos(w*t)*(velocity+damping*omega*displacement)-w*displacement*sin(w*t))
        } else {
            x=exp(-omega*t)*(displacement+(velocity+omega*displacement)*t)
            v= -omega*x+exp(-omega*t)*(velocity+omega*displacement)
        }
        val energy=0.5*4*omega*omega*x*x+0.5*4*v*v
        return if(initialEnergy==0.0 || energy/initialEnergy<=6e-9) 0.0 else x
    }
}
