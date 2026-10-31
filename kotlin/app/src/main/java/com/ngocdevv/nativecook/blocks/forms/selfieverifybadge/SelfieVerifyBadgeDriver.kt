package com.ngocdevv.nativecook.blocks.forms.selfieverifybadge

import android.os.SystemClock
import kotlin.math.*

internal class SelfieVerifyBadgeDriver {
    private val origin=SystemClock.uptimeMillis()
    val now get()=(SystemClock.uptimeMillis()-origin)/1000.0
    var manual=false
    var lastTouch = -100.0
    private var captureTime: Double?=null
    private var captureWait=0.0
    private data class Track(val from: Double=0.0,val to: Double=0.0,val start: Double=0.0,val duration: Double=0.0,val curve: Int=0,val velocity: Double=0.0,val stiffness: Double=0.0,val suppliedVelocity: Double=0.0) {
        fun value(now: Double): Double {
            val t=now-start
            if(t<=0) return from
            if(curve==4) {
                val z=0.45;val w=sqrt(stiffness/4);val wd=w*sqrt(1-z*z);val dx=from-to
                val env=exp(-z*w*t);val a=velocity+z*w*dx
                val displacement=env*(dx*cos(wd*t)+a/wd*sin(wd*t))
                val speed = -z*w*displacement+env*(a*cos(wd*t)-wd*dx*sin(wd*t))
                val energy=0.5*stiffness*displacement*displacement+2*speed*speed
                val initialEnergy=0.5*stiffness*dx*dx+2*suppliedVelocity*suppliedVelocity
                return if(initialEnergy==0.0 || energy/initialEnergy<=6e-9) to else to+displacement
            }
            if(duration<=0 || t>=duration) return to
            val x=SelfieVerifyBadgeMotion.clamp01(t/duration)
            val u=when(curve) {
                1->SelfieVerifyBadgeMotion.bezier(x)
                2->if(x<0.5) 2*x*x else 1-(-2*x+2).pow(2)/2
                3->SelfieVerifyBadgeMotion.bezier(x,0.23,1.0,0.32,1.0)
                else->x
            }
            return SelfieVerifyBadgeMotion.mix(from,to,u)
        }
    }
    private val tracks=mutableMapOf<String,Track>()
    private fun track(name: String,t: Double)=tracks[name]?.value(t) ?: if(name=="scan") -9.0 else 0.0
    private fun animate(name: String,to: Double,duration: Double,t: Double,curve: Int=0,velocity: Double=0.0) {
        val from=track(name,t)
        val effectiveVelocity=if((from-to)*velocity>0) 0.0 else velocity
        tracks[name]=Track(from,to,t,duration,curve,effectiveVelocity,
            if(curve==4) SelfieVerifyBadgeMotion.releaseStiffness(from-to,effectiveVelocity) else 0.0,velocity)
    }
    private fun raw(t: Double): SelfieVerifyBadgeMotion.Frame {
        val f=SelfieVerifyBadgeMotion.Frame(track("link",t),track("detected",t),track("scan",t),track("flash",t),track("shutter",t),track("yaw",t),track("spin",t),track("fade",t))
        captureTime?.let { ct->
            val dt=t-ct
            if(captureWait>0) f.scan=if(dt<0.52) SelfieVerifyBadgeMotion.mix(-0.8,0.95,SelfieVerifyBadgeMotion.bezier(SelfieVerifyBadgeMotion.clamp01(dt/0.52))) else -9.0
            val u=dt-captureWait
            if(u>=0) f.flash=if(u<0.06) u/0.06 else 1-SelfieVerifyBadgeMotion.clamp01((u-0.06)/0.26)
        }
        return f
    }
    fun frame(t: Double,auto: Boolean,reduced: Boolean): SelfieVerifyBadgeMotion.Frame {
        if(auto && !reduced) {
            if(!manual) return SelfieVerifyBadgeMotion.frameAt(t%SelfieVerifyBadgeMotion.LOOP)
            val elapsed=t-lastTouch
            if(elapsed>3.4) return SelfieVerifyBadgeMotion.frameAt((elapsed-3.4)%SelfieVerifyBadgeMotion.LOOP).apply {spin=track("spin",t)}
            if(elapsed>2) {
                val f=raw(lastTouch+2);val u=elapsed-2
                f.link*=1-SelfieVerifyBadgeMotion.bezier(SelfieVerifyBadgeMotion.clamp01(u/if(f.link>0.01) 1.3 else 0.0001))
                val x=SelfieVerifyBadgeMotion.clamp01(u/0.4)
                f.detected*=1-(if(x<0.5) 2*x*x else 1-(-2*x+2).pow(2)/2)
                f.yaw*=1-SelfieVerifyBadgeMotion.bezier(SelfieVerifyBadgeMotion.clamp01(u/0.7))
                return f
            }
        }
        return raw(t)
    }
    fun isManual(t: Double,auto: Boolean,reduced: Boolean)=manual && (!auto || reduced || t-lastTouch<=3.4)
    fun takeOver(t: Double,auto: Boolean,reduced: Boolean) {
        val returnStart=lastTouch+2
        val returning=auto && !reduced && manual && t-lastTouch>2 && t-lastTouch<3.4
        val returnFrame=if(returning) raw(returnStart) else null
        val f=frame(t,auto,reduced)
        if(!manual || t-lastTouch>2) {
            mapOf("link" to f.link,"detected" to f.detected,"scan" to f.scan,"flash" to f.flash,"shutter" to f.shutter,"yaw" to f.yaw,"spin" to f.spin,"fade" to f.fade).forEach {(key,v)->tracks[key]=Track(v,v)}
            captureTime=null
        }
        // RN resets resumeAsked without cancelling the return's withTiming tracks.
        returnFrame?.let { base->
            tracks["link"]=Track(base.link,0.0,returnStart,if(base.link>0.01) 1.3 else 0.0,1)
            tracks["detected"]=Track(base.detected,0.0,returnStart,0.4,2)
            tracks["yaw"]=Track(base.yaw,0.0,returnStart,0.7,1)
        }
        manual=true;lastTouch=t
    }
    fun capture(auto: Boolean,reduced: Boolean): Boolean {
        val t=now;takeOver(t,auto,reduced)
        if(track("link",t)>0.02) return false
        if(reduced) {animate("detected",1.0,0.0,t);animate("fade",1.0,0.28,t,2);animate("link",1.0,0.0,t);return true}
        captureWait=if(track("detected",t)>0.95) 0.0 else 0.7;captureTime=t
        if(captureWait>0) animate("detected",1.0,0.22,t,2)
        animate("link",1.0,3.2,t+captureWait)
        return true
    }
    fun back(auto: Boolean,reduced: Boolean) {
        val t=now;takeOver(t,auto,reduced);captureTime=null
        animate("link",0.0,if(reduced) 0.0 else 1.3,t,1);animate("detected",0.0,if(reduced) 0.0 else 0.4,t,2)
        if(reduced) animate("fade",0.0,0.28,t,2)
    }
    fun drag(dx: Double) {val t=now;lastTouch=t;animate("yaw",(dx/120).coerceIn(-1.1,1.1),0.0,t)}
    fun release(velocity: Double,reduced: Boolean) {val t=now;lastTouch=t;animate("yaw",0.0,if(reduced) 0.0 else 0.7,t,if(reduced) 0 else 4,velocity/120)}
    fun spin(auto: Boolean,reduced: Boolean): Boolean {
        val t=now;takeOver(t,auto,reduced)
        if(track("link",t)<0.9) return false
        tracks["spin"]=Track(0.0,PI*2,t,if(reduced) 0.0 else 0.9,3);return true
    }
}
