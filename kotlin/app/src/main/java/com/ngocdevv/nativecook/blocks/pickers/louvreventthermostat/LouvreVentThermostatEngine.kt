package com.ngocdevv.nativecook.blocks.pickers.louvreventthermostat

import kotlin.math.*

internal class LouvreVentThermostatEngine {
    private val m=LouvreVentThermostatMotion
    class Channel(var value:Double) {
        var velocity=0.0; private var from=0.0; private var target=0.0; private var initialVelocity=0.0; private var start=0.0; private var damping=0.0; private var stiffness=0.0; private var duration=0.0
        var busy=false; private set
        fun set(x:Double) { value=x; velocity=0.0; busy=false }
        fun spring(to:Double,velocity:Double=0.0,at:Double,damping:Double,stiffness:Double,reduced:Boolean) {
            if(reduced) { set(to); return }
            from=value; target=to; initialVelocity=(if(busy)this.velocity else 0.0)+velocity
            if((to>value&&initialVelocity<0)||(to<value&&initialVelocity>0))initialVelocity=0.0
            start=at; this.damping=damping; this.stiffness=stiffness; duration=0.0; busy=true
        }
        fun timing(to:Double,at:Double,duration:Double,reduced:Boolean) { if(reduced) { set(to); return }; from=value; target=to; start=at; this.duration=duration; busy=true }
        fun advance(now:Double) {
            if(!busy)return; val age=now-start; val m=LouvreVentThermostatMotion
            if(duration>0) { val k=m.seg(age,0.0,duration); value=m.mix(from,target,m.inOut(k)); velocity=0.0; if(k>=1)set(target); return }
            value=m.spring(age,from,target,initialVelocity,damping,stiffness)
            velocity=(m.spring(age+.00001,from,target,initialVelocity,damping,stiffness)-value)/.00001
            val initial=.5*stiffness*(from-target).pow(2)+2*initialVelocity*initialVelocity
            val energy=.5*stiffness*(value-target).pow(2)+2*velocity*velocity
            if(initial==0.0 || energy/max(initial,.000000001)<6e-9)set(target)
        }
    }
    var autoPlay=true; var reduced=false; var manual=false; var pin:Double?=null
    var onHaptic:((Int)->Unit)?=null; var onChange:((Double)->Unit)?=null; var onCommit:((Double)->Unit)?=null
    var scene=m.scene(0.0); private set
    private var auto=true; private var touching=false; private var armed=false; private var homing=false; private var atLimit=false
    private var now=0.0; private var time=0.0; private var lastTouch=-10.0; private var lastTick=-10.0; private var wholeAt=-10.0
    private val value=Channel(24.0); private val pressure=Channel(0.0); private val slam=Channel(0.0)
    private var previous=24.0; private var speed=0.0; private var heat=24.0; private var conv=0.0; private var convOffset=0.0
    private var target=24.0; private var direction=1.0; private var clackStart=-10.0; private var clackDirection=1.0
    private var start=24.0; private var thumb=488.0; private var startY=488.0; private var lastBlade=16.0; private var lastWhole=24.0
    private data class Sample(val time:Double,val temperature:Double,val velocity:Double)
    private var history=mutableListOf(Sample(0.0,24.0,0.0)); private val crossings=mutableListOf<Pair<Double,Double>>()
    fun configure(autoPlay:Boolean,reduced:Boolean) {
        if(this.autoPlay!=autoPlay || this.reduced!=reduced)auto=autoPlay&&!reduced&&!manual
        this.autoPlay=autoPlay; this.reduced=reduced
        if(manual)auto=false
        if(reduced&&auto) { auto=false; value.set(24.0); scene=m.scene(0.0) }
        pin?.let { time=it; scene=m.scene(it) }
    }
    fun advance(dt:Double) {
        now+=dt; if(pin!=null)return
        if(auto&&autoPlay&&!manual&&!reduced) { time=m.wrap(time+dt); scene=m.scene(time).copy(conv=m.conv(time)+convOffset); return }
        value.advance(now); pressure.advance(now); slam.advance(now)
        val t=value.value; val raw=(t-previous)/max(dt,.00001)*m.PER_C; previous=t
        speed+=(raw-speed)*(1-exp(-dt/.06))
        history.add(Sample(now,t,speed)); if(history.size>48)history.removeAt(0)
        heat+=(t-heat)*(1-exp(-dt/(if(reduced).07 else .25)))
        if(!reduced)conv+=m.convectionRate(heat)*dt
        val b=floor(2*(t-16))
        if(b!=lastBlade) {
            repeat(min(abs(b-lastBlade).toInt(),6)) { i -> val level=if(b>lastBlade)lastBlade+1+i else lastBlade-i; crossings.add(16+level/2 to now) }
            while(crossings.size>6)crossings.removeAt(0); lastBlade=b
            val whole=floor(t); val isWhole=whole!=lastWhole
            if(isWhole) { lastWhole=whole; wholeAt=now }
            if(now-lastTick>=.1||(isWhole&&now-lastTick>=.05)) { lastTick=now; onHaptic?.invoke(0) }
            onChange?.invoke(m.rounded(2*t)/2)
        }
        if(!touching&&armed&&abs(speed)<40&&abs(t-target)<.02) {
            armed=false
            if(!reduced) { clackStart=now; clackDirection=direction; onHaptic?.invoke(2) }
            onCommit?.invoke(target)
        }
        if(autoPlay&&!manual&&!reduced&&!touching&&!armed&&now-lastTouch>2&&now-clackStart>.6) {
            if(abs(t-24)>.002&&!homing) { homing=true; target=24.0; direction=if(t<24)1.0 else -1.0; armed=true; value.timing(24.0,now,.9,false) }
            else if(abs(t-24)<=.002) { convOffset=conv-m.conv(0.0); time=0.0; homing=false; auto=true; value.set(24.0); scene=m.scene(0.0).copy(conv=m.conv(0.0)+convOffset); return }
        }
        val row=m.row(t,{dk -> if(reduced)m.theta(t,0.0) else { val sample=sample(now-.016*dk); m.theta(sample.first,sample.second) } },{level -> if(reduced)0.0 else crossings.filter { abs(it.first-level)<.01 }.maxOfOrNull { m.clamp(1-(now-it.second)/.08,0.0,1.0) }?:0.0 },thumb,if(reduced)0.0 else pressure.value,now-clackStart,clackDirection,if(reduced)0.0 else slam.value)
        val dw=now-wholeAt; val nudge=if(dw<.15)2*(1-dw/.15).pow(2)*(1-m.smooth(150.0,420.0,abs(speed))) else 0.0
        scene=LouvreVentThermostatMotion.Scene(t,speed,row.s0,row.k0,row.blades,heat,conv,nudge,0.0,now,m.drums(t))
    }
    private fun sample(t:Double):Pair<Double,Double> {
        for(i in history.indices.reversed())if(history[i].time<=t) {
            if(i==history.lastIndex)return history[i].temperature to history[i].velocity
            val k=(t-history[i].time)/max(history[i+1].time-history[i].time,.0001)
            return m.mix(history[i].temperature,history[i+1].temperature,k) to m.mix(history[i].velocity,history[i+1].velocity,k)
        }
        return history[0].temperature to history[0].velocity
    }
    fun begin(y:Double) {
        if(auto||pin!=null) {
            val s=scene; value.set(s.temperature); previous=s.temperature; speed=s.velocity; heat=s.heat; conv=s.conv
            history=(0..<16).map { val back=(15-it)*.02; Sample(now-back,m.temperature(time-back),m.velocity(time-back)) }.toMutableList()
            val cl=m.clack(m.wrap(time)); clackStart=now-cl.first; clackDirection=cl.second
            lastBlade=floor(2*(s.temperature-16)); lastWhole=floor(s.temperature); crossings.clear(); auto=false
        }
        pin=null; homing=false; touching=true; lastTouch=now; value.set(value.value); armed=false
        thumb=y; startY=y; start=value.value
        pressure.timing(1.0,now,.11,reduced); if(!reduced)onHaptic?.invoke(1)
    }
    fun drag(translation:Double) {
        var t=start+translation/m.PER_C
        if(t>32||t<16) {
            val lim=if(t>32)32.0 else 16.0; val over=abs(t-lim)*m.PER_C
            t=lim+(if(t>lim)1 else -1)*(over/(1+over/120))/m.PER_C
            if(!atLimit) { atLimit=true; slam.spring(if(lim==32.0)1.0 else -1.0,at=now,damping=18.0,stiffness=520.0,reduced=reduced); onHaptic?.invoke(2) }
        } else if(atLimit) { atLimit=false; slam.spring(0.0,at=now,damping=30.5,stiffness=322.3,reduced=reduced) }
        value.set(t); thumb=startY+translation
    }
    private fun glide(destination:Double,velocity:Double) { target=destination; direction=if(target>=value.value)1.0 else -1.0; armed=true; value.spring(target,velocity,now,26.0,120.0,reduced) }
    fun end(velocity:Double,cancelled:Boolean=false) {
        val v=if(cancelled)0.0 else velocity/m.PER_C
        glide(m.clamp(m.rounded(2*(value.value+v*.332))/2,16.0,32.0),v)
        if(atLimit) { atLimit=false; slam.spring(0.0,at=now,damping=30.5,stiffness=322.3,reduced=reduced) }
        pressure.timing(0.0,now,.09,reduced); touching=false; lastTouch=now
    }
    fun tap(x:Double,y:Double) {
        if(x<=327||x>=381||y<=246||y>=730) { end(0.0); return }
        val at=value.value+(488-y)/m.PER_C; target=m.clamp(2*m.rounded(at/2),16.0,32.0); direction=if(target>=value.value)1.0 else -1.0; armed=true; touching=false; lastTouch=now
        value.timing(target,now,.62,reduced); pressure.timing(0.0,now,.09,reduced)
    }
    fun adjust(delta:Double) { begin(488.0); glide(m.clamp(m.rounded(2*value.value)/2+delta,16.0,32.0),0.0); touching=false; lastTouch=now; pressure.set(0.0) }
}
