package com.ngocdevv.nativecook.blocks.cards.perfumeinkbloom

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.cards.perfumeinkbloom.PerfumeInkBloomMotion as M

internal class PerfumeInkBloomAudio(context: Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(2).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded=mutableSetOf<Int>()
    private val streams=mutableMapOf<String,Int>()
    private val sounds: Map<String,Int>
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0)loaded.add(id) }
        sounds=listOf("spray","slosh").associateWith { name -> context.assets.openFd("perfumeinkbloom/perfumeinkbloom_pib-$name.wav").use { pool.load(it,1) } }
    }
    fun play(name: String) {
        val id=sounds[name]?:return
        if(id !in loaded || manager?.ringerMode!=AudioManager.RINGER_MODE_NORMAL)return
        streams[name]?.let { pool.stop(it) }
        val volume=if(name=="spray").6f else .5f
        streams[name]=pool.play(id,volume,volume,1,0,1f)
    }
    fun close() { pool.release() }
}

internal class PerfumeInkBloomDriver(val auto: Boolean,val reduced: Boolean,val cue: (String)->Unit,val buzz: ()->Unit) {
    var frame=M.Frame()
    var now=0.0; var mode=if(auto&&!reduced)0 else 1
    var autoStart=0.0; var lastTouch=-100.0; var sprayAt=-100.0
    var holding=false
    private var pressAt=-100.0; private var pressFrom=0.0; private var pressAnimating=false
    private var sloshV=0.0; private var prevTilt=0.0; private var prevTiltV=0.0
    private var fingerFrom=0.0; private var fingerAt=-100.0
    private var spring: PerfumeInkBloomSpring?=null
    fun moving()=(auto&&!reduced)||holding||frame.age>=0||spring!=null||abs(frame.slosh)>.00001||abs(sloshV)>.0001||now-pressAt<.38||now-fingerAt<.15
    fun takeOver() {
        lastTouch=now
        if(mode!=0)return
        mode=1; fingerFrom=frame.finger; fingerAt=now; frame.press2=0.0
        sprayAt=if(frame.age>=0)now-frame.age else -100.0
        pressFrom=frame.press; pressAnimating=false
        spring=PerfumeInkBloomSpring(frame.tilt,.7,.45)
    }
    fun spray() { takeOver(); pressFrom=frame.press; pressAt=now; sprayAt=now; pressAnimating=true; cue("spray"); buzz() }
    fun beginDrag() { if(!reduced)holding=true }
    fun drag(dx: Double,scale: Double) {
        lastTouch=now
        if(reduced)return
        holding=true; spring=null; frame.tilt=.38*tanh(dx/(160*scale))
    }
    fun release(dx: Double,velocityX: Double,scale: Double) {
        lastTouch=now
        if(!holding)return
        holding=false
        if(abs(frame.tilt)>.08)cue("slosh")
        val u=160*scale; val sech=1/cosh(dx/u)
        spring=PerfumeInkBloomSpring(frame.tilt,.9,.35,.38*sech*sech/u*velocityX)
    }
    fun step(elapsed: Double) {
        now+=elapsed
        if(mode==0&&auto&&!reduced) { frame=M.frameAt((now-autoStart)%M.LOOP); return }
        val dt=min(.05,elapsed)
        spring?.let { frame.tilt=it.step(min(.064,elapsed)); if(it.done)spring=null }
        val p=now-pressAt
        if(pressAnimating)frame.press=if(reduced)0.0 else if(p<.12)pressFrom+(1-pressFrom)*M.out(M.clamp(p/.12)) else 1-M.out(M.clamp((p-.12)/.26))
        frame.finger=fingerFrom*(1-M.timing(M.clamp((now-fingerAt)/.15))); frame.press2=0.0
        val a=now-sprayAt; val on=a>=0&&a<6.9
        frame.age=if(on)if(reduced)2.6 else a else -1.0
        frame.condense=if(on)M.ease(M.clamp((a-1.15)/1.3)) else 0.0
        frame.evaporate=if(on)M.clamp((a-4.85)/1.2) else 0.0
        val v=if(dt>0)(frame.tilt-prevTilt)/dt else 0.0
        val acc=if(dt>0)(v-prevTiltV)/dt else 0.0
        prevTilt=frame.tilt; prevTiltV=v
        sloshV+=(-81*frame.slosh-2*.16*9*sloshV-acc.coerceIn(-400.0,400.0)*.12)*dt
        frame.slosh=if(reduced)0.0 else frame.slosh+sloshV*dt
        if(auto&&!reduced&&!holding&&now-lastTouch>=2&&frame.age<0&&abs(frame.tilt)<.002&&abs(frame.slosh)<.004&&frame.press<.001) { autoStart=now; frame.slosh=0.0; sloshV=0.0; mode=0 }
    }
}

internal class PerfumeInkBloomSpring(var x: Double,duration: Double,val z: Double,velocity: Double=0.0) {
    // Match the installed Reanimated spring: only retain velocity toward its target.
    private var v=if(x*velocity>0)0.0 else velocity
    private val initial: Double
    private val w: Double
    var done=false
    init {
        var lo=Math.ulp(1.0); var hi=8000.0
        val t=duration*1.5
        for(i in 0 until 100) {
            val mid=(lo+hi)/2; val om=sqrt(mid/4)*z
            val xt=(x+(v+x*om)*t)*exp(-om*t); val vt=xt*(-om)+(v+x*om)*exp(-om*t)
            val energy=mid*x*x+4*v*v
            val f=if(energy==0.0)0.0 else (mid*xt*xt+4*vt*vt)/energy-6e-9
            if(abs(f)<=6e-12) { lo=mid; hi=mid; break }
            if(f>0)lo=mid else hi=mid
        }
        w=sqrt((lo+hi)/8)
        initial=w*w*x*x+velocity*velocity
    }
    fun step(dt: Double): Double {
        val wd=w*sqrt(1-z*z); val s=sin(wd*dt); val c=cos(wd*dt); val e=exp(-z*w*dt)
        val a=e*(s*((v+z*w*x)/wd)+x*c)
        v=-z*w*a+e*(c*(v+z*w*x)-wd*x*s); x=a
        if(initial==0.0||(w*w*x*x+v*v)/initial<=6e-9) { x=0.0; v=0.0; done=true }
        return x
    }
}
