package com.ngocdevv.nativecook.blocks.cards.liquidmetalswap

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.cards.liquidmetalswap.LiquidMetalSwapMotion as M

internal class LiquidMetalSwapAudio(private val context: Context) {
    private val audioManager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ids=mutableMapOf<String,Int>()
    private val loaded=mutableSetOf<Int>()
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0) synchronized(loaded) { loaded.add(id) } }
        for(cue in listOf("press","pour","cast")) {
            context.assets.openFd("liquidmetalswap/liquidmetalswap_lms-$cue.wav").use { ids[cue]=pool.load(it,1) }
        }
    }
    fun play(cue: String) {
        if(audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id=ids[cue] ?: return
        if(!synchronized(loaded) { id in loaded }) return
        val volume=if(cue=="press") 0.5f else 0.7f
        pool.play(id,volume,volume,1,0,1f)
    }
    fun close() { pool.release() }
}
internal data class LiquidMetalSwapTween(val from: Double=0.0,val to: Double=0.0,val began: Double= -100.0,val duration: Double=0.0,val curve: Int=0) {
    fun value(t: Double): Double {
        val x=M.seg(t,began,began+max(duration,0.00001))
        val p=when(curve) { 1 -> M.bezier(x,0.23,1.0,0.32,1.0); 2 -> M.bezier(x,0.77,0.0,0.175,1.0); else -> if(x<0.5) 2*x*x else 1-(-2*x+2).pow(2)/2 }
        return from+(to-from)*p
    }
}
internal class LiquidMetalSwapDriver(var auto: Boolean,var reduced: Boolean,val cue: (String)->Unit,val buzz: (Int)->Unit) {
    var revision by mutableIntStateOf(0)
    var frame=M.Frame(); private set
    var clock=0.0; private set
    var autoStart=0.0; private set
    var lift=0.0; private set
    var mode=if(auto) 0 else 1; private set
    private var holding=false; private var turning=false
    private var yawVel=0.0; private var prevX=0.0; private var lastTouch= -100.0
    private var resumeAsked=false; private var wasUnder=false
    private var press=LiquidMetalSwapTween(); private var flip=LiquidMetalSwapTween()
    private var finger=LiquidMetalSwapTween(); private var swapped=LiquidMetalSwapTween(); private var done=LiquidMetalSwapTween()
    private var completeAt= -100.0; private var returnAt= -100.0; private var returnFill=0.0
    private var fillSpring: LiquidMetalSwapSpring?=null; private var liftSpring: LiquidMetalSwapSpring?=null
    private var liftAt= -100.0
    private var accessAt= -100.0; private var accessFrom=0.0
    fun configure(autoPlay: Boolean,newReduced: Boolean) {
        reduced=newReduced; auto=autoPlay && !newReduced; mode=if(auto) 0 else 1
        if(auto) { autoStart=clock; frame=M.Frame() }
        revision++
    }
    fun step(dt: Double) {
        clock+=dt
        if(mode==0) {
            frame=M.frameAt(((clock-autoStart)%M.LOOP+M.LOOP)%M.LOOP)
            wasUnder=M.look(frame.m).sink>0.96
            revision++; return
        }
        if(holding) {
            frame.fill=min(1.0,frame.fill+dt/M.HOLD)
            if(frame.fill>=1) complete()
        } else if(accessAt> -99) {
            frame.fill=accessFrom+(1-accessFrom)*M.bezier(M.seg(clock,accessAt,accessAt+0.9),0.77,0.0,0.175,1.0)
            if(clock>=accessAt+0.9) { accessAt= -100.0; complete() }
        } else if(returnAt> -99) {
            val tau=clock-returnAt
            frame.fill=if(reduced) returnFill*(1-LiquidMetalSwapTween(0.0,1.0,0.0,0.15).value(tau)) else (fillSpring?.value(tau) ?: 0.0)
            if(frame.fill<0.00001) { frame.fill=0.0; returnAt= -100.0 }
        }
        if(!turning && !reduced) {
            var v=yawVel*exp(-2.2*dt)
            if(abs(v)<2.5) { val aim=floor(frame.yaw/PI+0.5)*PI; v+=(-14*(frame.yaw-aim)-5*v)*dt }
            yawVel=v; frame.yaw+=v*dt
            val tau=max(0.0,clock-liftAt)
            lift=liftSpring?.value(tau) ?: 0.0
        }
        frame.press=press.value(clock); frame.flip=flip.value(clock); frame.finger=finger.value(clock)
        if(completeAt> -99) {
            val tau=clock-completeAt
            frame.swapped=1-LiquidMetalSwapTween(0.0,1.0,1.1,0.3).value(tau)
            frame.doneText=if(tau<1.1) LiquidMetalSwapTween(0.0,1.0,0.0,0.2).value(tau) else 1-LiquidMetalSwapTween(0.0,1.0,1.1,0.3).value(tau)
        } else { frame.swapped=swapped.value(clock); frame.doneText=done.value(clock) }
        frame.m=if(reduced) frame.start else if(frame.start==0.0) frame.fill else 1-frame.fill
        val under=M.look(frame.m).sink>0.96
        if(under && !wasUnder && !reduced) { cue("pour"); buzz(1) }
        wasUnder=under
        if(auto && !resumeAsked && !holding && !turning && clock-lastTouch>2 && frame.fill<0.002 && abs(yawVel)<0.05) {
            resumeAsked=true
            if(frame.start==1.0) holding=true
        } else if(resumeAsked && !holding && frame.start==0.0 && frame.fill<0.002 && abs(frame.yaw-floor(frame.yaw/(2*PI)+0.5)*2*PI)<0.02) {
            resumeAsked=false; frame.yaw=0.0; autoStart=clock; mode=0
        }
        revision++
    }
    val needsFrames: Boolean get()= !reduced || auto || holding || turning || accessAt> -99 || returnAt> -99 || (!reduced && (abs(yawVel)>0.0001 ||
        abs(frame.yaw-floor(frame.yaw/PI+0.5)*PI)>0.0001)) || clock<completeAt+1.4 || clock<press.began+press.duration || clock<finger.began+finger.duration || clock<liftAt+1.4
    private fun takeOver() {
        lastTouch=clock; resumeAsked=false
        if(mode==0) {
            mode=1; yawVel=0.0
            finger=LiquidMetalSwapTween(frame.finger,0.0,clock,0.15)
            press=LiquidMetalSwapTween(0.0,0.0,clock,0.0)
            swapped=LiquidMetalSwapTween(frame.swapped,0.0,clock,0.2)
            done=LiquidMetalSwapTween(frame.doneText,0.0,clock,0.2)
            flip=LiquidMetalSwapTween(frame.flip,frame.flip,clock,0.0)
            completeAt= -100.0
        }
        revision++
    }
    private fun complete() {
        holding=false; returnAt= -100.0; accessAt= -100.0
        frame.start=1-frame.start; frame.fill=0.0
        flip=LiquidMetalSwapTween(frame.flip,frame.start,clock,0.35,2); completeAt=clock
        if(!reduced) cue("cast")
        buzz(2)
    }
    fun beginHold() {
        takeOver(); returnAt= -100.0; accessAt= -100.0; holding=true
        press=LiquidMetalSwapTween(frame.press,1.0,clock,0.1,1)
        if(!reduced) cue("press")
        buzz(0)
    }
    fun endHold() {
        lastTouch=clock; press=LiquidMetalSwapTween(frame.press,0.0,clock,0.15,1)
        if(holding) { holding=false; returnFill=frame.fill; returnAt=clock; fillSpring=LiquidMetalSwapSpring(frame.fill,1/M.HOLD,0.6,1.0) }
        revision++
    }
    fun beginTurn() { takeOver(); turning=true; yawVel=0.0; prevX=0.0 }
    fun turn(x: Double,y: Double) {
        lastTouch=clock
        val dx=x-prevX; prevX=x
        if(!reduced) { frame.yaw+=dx/(78*0.9); lift=(-y/900).coerceIn(-0.12,0.12) }
        revision++
    }
    fun endTurn(velocity: Double) {
        lastTouch=clock; turning=false
        if(!reduced) { yawVel=(velocity/(78*0.9)).coerceIn(-14.0,14.0); liftAt=clock; liftSpring=LiquidMetalSwapSpring(lift,0.0,0.7,0.8) }
        revision++
    }
    fun swapNow() {
        takeOver(); holding=false; returnAt= -100.0
        if(reduced) { frame.fill=1.0; complete() }
        else { accessAt=clock; accessFrom=frame.fill }
    }
}
