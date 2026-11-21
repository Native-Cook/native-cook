package com.ngocdevv.nativecook.blocks.modals.islandglassorb

import kotlin.math.*

/** Analytic live spring; taking it over samples its current position and velocity. */
internal class IslandGlassOrbSpring(initial: Double = 0.0) {
    var value = initial; private set
    private var from = initial
    private var target = initial
    private var velocity = 0.0
    private var start = 0.0
    private var damping = 0.0
    private var stiffness = 0.0
    private var duration = 0.0
    private var initialEnergy = 0.0
    var active = false; private set
    fun set(v: Double) { value=v; target=v; active=false }
    fun animate(to: Double,now: Double,d: Double,k: Double,reduced: Boolean,v: Double?=null) {
        if(reduced) { set(to); return }
        // Reanimated continues an already-running spring with the same toValue.
        if(active && duration==0.0 && target==to)return
        update(now)
        val oldVelocity=if(active && duration==0.0)speed(now) else 0.0
        from=value; target=to; velocity=oldVelocity+(v?:0.0); start=now; damping=d; stiffness=k
        if((to>value && velocity<0) || (to<value && velocity>0))velocity=0.0
        initialEnergy=.5*k*(from-to).pow(2)+.5*(v?:0.0).pow(2)
        duration=0.0; active=true
    }
    fun timing(to: Double,now: Double,seconds: Double,reduced: Boolean) {
        if(reduced) { set(to); return }
        if(active && duration>0 && target==to)return
        update(now); from=value; target=to; start=now; duration=seconds; active=true
    }
    private fun position(t: Double) = IslandGlassOrbMotion.spring(t,from,target,velocity,damping,stiffness)
    private fun speed(now: Double): Double {
        val t=now-start
        val w0=sqrt(stiffness); val z=damping/(2*w0); val x0=from-target
        if(z<1) {
            val wd=w0*sqrt(1-z*z); val e=exp(-z*w0*t)
            val fragment=e*(sin(wd*t)*(velocity+z*w0*x0)/wd+x0*cos(wd*t))
            return -z*w0*fragment+e*(cos(wd*t)*(velocity+z*w0*x0)-wd*x0*sin(wd*t))
        }
        return (position(t+.000001)-position(t))/.000001
    }
    fun update(now: Double) {
        if(!active)return
        val t=now-start
        if(duration>0) {
            val u=(t/duration).coerceIn(0.0,1.0)
            val e=if(u<.5)2*u*u else 1-2*(1-u)*(1-u)
            value=IslandGlassOrbMotion.mix(from,target,e)
            if(u>=1) { value=target; active=false }
        } else {
            value=position(t)
            val energy=.5*stiffness*(target-value).pow(2)+.5*speed(now).pow(2)
            if(initialEnergy==0.0 || energy/initialEnergy<=6e-9) { value=target; active=false }
        }
    }
}
