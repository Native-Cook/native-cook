package com.ngocdevv.nativecook.blocks.forms.beamcheckoutstepper

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.min

/** Owns one native simulation and routes the demonstration through the same form actions as touch. */
internal class BeamController(val autoPlay:Boolean, reducedMotion:Boolean) {
    val core=BeamCore(reducedMotion)
    var form by mutableStateOf(BeamForm())
        private set
    var drawTick by mutableIntStateOf(0)
        private set
    private var demoActive=autoPlay
    private var demoBegun=false
    private var demoTime=0.0
    private var demoCursor=0
    private var demoScrubbing=false
    private var fingers=0
    private var focused=false
    private var idle=0.0
    private var resuming=false
    private var frameRemainder=0.0
    var onHaptic:((Int)->Unit)?=null

    private fun command(c:BeamCommand,silent:Boolean){
        core.silent=silent
        when(c.type){
            "advance"->core.advance()
            "go"->core.goTo(c.a)
            "reject"->core.reject(c.a)
            "clearError"->core.clearError()
            "pay"->core.pay()
            "restart"->core.restart()
            "focus"->core.focus(c.a)
            "filled"->core.setFilled(c.a,c.b==1)
            "delivery"->core.setDelivery(c.a==1)
        }
    }
    fun action(a:BeamAction,silent:Boolean=false){
        val change=beamAction(form,a)
        form=change.form
        change.commands.forEach{command(it,silent)}
    }
    fun press(index:Int,down:Boolean){
        if(down)touch(true) else touch(false)
        core.press(index,down)
    }
    fun touch(down:Boolean){
        fingers=(fingers+if(down)1 else -1).coerceAtLeast(0)
        idle=0.0
        if(down&&autoPlay&&demoActive){
            demoActive=false
            core.press(BF.CTA_PRESS,false)
            core.press(BF.SEG_PRESS,false)
            if(demoScrubbing){core.endDrag(core.frame[BF.HEAD_VEL])
            demoScrubbing=false}
        }
        core.silent=false
    }
    fun focus(field:Int){
        if(field>=0){touch(true)
        touch(false)}
        focused=field>=0
        idle=0.0
        action(BeamAction("focus",field=field))
    }
    fun beginRail(){touch(true)
    core.beginDrag()}
    fun railDrag(distance:Float,velocity:Float){core.drag(distance,velocity)}
    fun endRail(velocity:Float){core.endDrag(velocity)
    touch(false)}
    fun beginDelivery(){touch(true)
    core.deliveryGrab=core.frame[BF.SEG_LEAD]}
    fun deliveryDrag(position:Float){core.dragDelivery(position)}
    fun endDelivery(velocity:Float){
        val value=core.endDelivery(velocity)
        action(BeamAction("delivery",express=value))
        touch(false)
    }
    private fun handleEvents(){
        if(core.events.isEmpty())return
        val events=core.events.toList()
        core.events.clear()
        for(e in events){
            when(e.type){
                "haptic" -> if(!e.silent)onHaptic?.invoke(e.value)
                "settled" -> action(BeamAction("settled",target=e.value),e.silent)
                "cleared","restarted","newOrder","paid" -> action(BeamAction(e.type),e.silent)
            }
            if(e.type=="restarted"&&resuming){
                resuming=false
                demoActive=true
                demoBegun=true
                demoTime=0.0
                demoCursor=0
                idle=0.0
            }
        }
    }
    private fun stepDemo(dt:Double){
        if(!autoPlay||core.frame[BF.PRESENT]<1f)return
        if(!demoActive){
            if(fingers==0&&!focused&&!resuming){
                idle+=dt
                if(idle>=3.5){resuming=true
                core.silent=true
                core.restart()}
            }
            return
        }
        if(!demoBegun){demoBegun=true
        return}
        demoTime+=dt
        if(demoTime>=BeamDemo.PERIOD-1e-8){demoTime-=BeamDemo.PERIOD
        demoCursor=0}
        core.silent=true
        while(demoCursor<BeamDemo.events.size&&BeamDemo.events[demoCursor].at<=demoTime+1e-8){
            val e=BeamDemo.events[demoCursor++]
            if(e.pressIndex>=0)core.press(e.pressIndex,e.down)
            else e.action?.let{action(it,true)}
        }
        val finger=BeamDemo.finger(demoTime)
        if(finger.first&&!demoScrubbing){core.beginDrag()
        demoScrubbing=true}
        if(finger.first)core.drag(finger.second,finger.third)
        if(!finger.first&&demoScrubbing){
            core.drag(finger.second,finger.third)
            core.endDrag(finger.third)
            demoScrubbing=false
        }
    }
    fun frame(dt:Double){
        frameRemainder+=min(dt,1.0/30.0)
        while(frameRemainder>=BeamCore.H-1e-10){
            core.step(BeamCore.H)
            handleEvents()
            stepDemo(BeamCore.H)
            handleEvents()
            frameRemainder-=BeamCore.H
        }
        drawTick++
    }
}
