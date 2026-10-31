package com.ngocdevv.nativecook.blocks.onboarding.chromestepnumerals

import android.os.SystemClock
import kotlin.math.*

internal class ChromeStepNumeralsDriver(auto: Boolean) {
    var manual=!auto
    var frame=ChromeStepNumeralsMotion.Frame()
    private var epoch=now()
    private var last=epoch
    private var lastTouch= -100.0
    private var fadeStart=0.0
    private var fadeFinger=0.0
    private var r0=0.0
    var dragging=false
    private var returning=false
    private var move: ChromeStepNumeralsMotion.Move?=null
    private var pausedAt: Double?=null
    fun now()=SystemClock.uptimeMillis()/1000.0
    fun needsFrames(auto: Boolean)=auto || move!=null || dragging || frame.finger>0.001 || abs(frame.bob)>0.00001
    fun configure(auto: Boolean) { manual=!auto; epoch=now(); last=epoch }
    fun active(value: Boolean) {
        val time=now()
        if(!value) pausedAt=time
        else pausedAt?.let {
            val gap=time-it
            epoch+=gap; lastTouch+=gap; fadeStart+=gap
            move?.let { animation -> animation.start+=gap }
            last=time; pausedAt=null
        }
    }
    fun sample(time: Double,auto: Boolean,reduced: Boolean): ChromeStepNumeralsMotion.Frame {
        val dt=min(0.05,max(0.0,time-last)); last=time
        if(!manual && auto) {
            frame=ChromeStepNumeralsMotion.frameAt(((time-epoch)%ChromeStepNumeralsMotion.LOOP+ChromeStepNumeralsMotion.LOOP)%ChromeStepNumeralsMotion.LOOP)
            return frame
        }
        val f=ChromeStepNumeralsMotion.seg(time,fadeStart,fadeStart+0.15)
        val eased=if(f<0.5) 2*f*f else 1-(-2*f+2).pow(2)/2
        frame=frame.copy(bob=if(reduced) 0.0 else frame.bob*(1-min(1.0,dt*3)),finger=fadeFinger*(1-eased))
        move?.let { animation ->
            val (r,done)=animation.sample(time)
            frame=frame.copy(r=if(done) animation.to else r)
            if(done) move=null
            lastTouch=time
        }
        if(dragging) lastTouch=time
        if(auto && !dragging && move==null) {
            if(returning && frame.r<0.001) {
                frame=ChromeStepNumeralsMotion.Frame(); manual=false; returning=false; epoch=time
            } else if(time-lastTouch>=2 && !returning) {
                returning=true
                if(frame.r>0.001) go(0.0,0.0,time,reduced)
            }
        }
        return frame
    }
    fun takeOver(auto: Boolean,reduced: Boolean) {
        val time=now(); sample(time,auto,reduced)
        lastTouch=time; returning=false
        if(!manual) {
            manual=true; fadeStart=time; fadeFinger=frame.finger; frame=frame.copy(press=0.0)
        }
    }
    private fun go(k: Double,velocity: Double,time: Double,reduced: Boolean) {
        move=ChromeStepNumeralsMotion.Move(time,frame.r,k,velocity,reduced)
    }
    fun next(auto: Boolean,reduced: Boolean) {
        takeOver(auto,reduced)
        val k=floor(frame.r+0.5)
        go(if(k>=2) 0.0 else k+1,0.0,now(),reduced)
    }
    fun begin(auto: Boolean,reduced: Boolean) { takeOver(auto,reduced); dragging=false }
    fun drag(translationX: Double,w: Double,auto: Boolean,reduced: Boolean) {
        val time=now(); sample(time,auto,reduced); lastTouch=time
        if(!dragging && abs(translationX)>10) { dragging=true; move=null; r0=frame.r }
        if(dragging) frame=frame.copy(r=ChromeStepNumeralsMotion.rubber(r0-translationX/w))
    }
    fun release(velocityX: Double,w: Double,reduced: Boolean) {
        val time=now(); lastTouch=time
        if(dragging) {
            dragging=false
            val v= -velocityX/w
            go(floor(frame.r+0.25*v+0.5).coerceIn(0.0,2.0),if(v==0.0) 0.0001 else v,time,reduced)
        }
    }
    fun touched() { lastTouch=now() }
}
