package com.ngocdevv.nativecook.blocks.modals.gemcutpaywall

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*

internal class GemCutPaywallAudio(context: Context) {
    private val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val pool=SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val sounds=mutableMapOf<String,Int>()
    private val ready=java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0)ready.add(id) }
        for(cue in listOf("tick","ting","shimmer")) context.assets.openFd("gemcutpaywall/gemcutpaywall_gcp-$cue.wav").use { sounds[cue]=pool.load(it,1) }
    }
    fun play(cue: String) {
        if(manager.ringerMode!=AudioManager.RINGER_MODE_NORMAL)return
        val id=sounds[cue]?:return;if(!ready.contains(id))return
        val volume=if(cue=="tick")0.5f else 0.7f
        pool.play(id,volume,volume,1,0,1f)
    }
    fun close() { pool.release() }
}

internal class GemCutPaywallDriver(val autoPlay: Boolean,val reduced: Boolean,val audio: GemCutPaywallAudio,val buzz: (Int)->Unit) {
    private val m=GemCutPaywallMotion
    var origin=now();var manual= !autoPlay||reduced;var touching=false;var sliding=false
    var lastTouch= -100.0;var yawBase=0.0;var loopTime=0.0;var frame=GemCutPaywallMotion.Frame()
    var slideFrom=0.0;var prevX=0.0;var prevY=0.0;var velocity=if(reduced)0.0 else m.IDLE_SPIN;var tickTime=now()
    var resume=false;var fadeFinger=0.0;var fingerFadeStart= -100.0;var frozenFingerCut=0.0;var spinning=false
    data class Move(val start: Double,val duration: Double,val from: Double,val to: Double,val velocity: Double=0.0,val spring: Boolean=false,val damping: Double=0.8)
    var cutMove: Move?=null;var pitchMove: Move?=null
    var flareTime= -100.0;var flareAmp=0.0;var flareRise=0.12;var flareFall=1.3
    var tier by mutableIntStateOf(0);private set
    var previousTier=0;var tierTime= -100.0;var digitFrom=DoubleArray(4)
    private val opacityFrom=doubleArrayOf(1.0,0.0,0.0)
    private val opacityTo=doubleArrayOf(1.0,0.0,0.0)
    private val opacityStart=DoubleArray(3){-100.0}
    private var ctaFrom=1.0;private var ctaTo=1.0;private var ctaTime= -100.0
    fun opacity(i: Int,t: Double): Double {
        val k=m.bezier(m.clamp01((t-opacityStart[i])/0.2),0.23,1.0,0.32,1.0)
        return opacityFrom[i]+(opacityTo[i]-opacityFrom[i])*k
    }
    fun freeOpacity(t: Double): Double {
        val k=m.bezier(m.clamp01((t-ctaTime)/0.2),0.23,1.0,0.32,1.0)
        return ctaFrom+(ctaTo-ctaFrom)*k
    }
    private fun retargetOpacity(next: Int,t: Double) {
        for(i in 0..2) {val to=if(i==next)1.0 else 0.0;if(to!=opacityTo[i]){opacityFrom[i]=opacity(i,t);opacityTo[i]=to;opacityStart[i]=t}}
        val to=if(next==0)1.0 else 0.0;if(to!=ctaTo){ctaFrom=freeOpacity(t);ctaTo=to;ctaTime=t}
    }
    private val pressFrom=DoubleArray(3)
    private val pressTo=DoubleArray(3)
    private val pressTime=DoubleArray(3){-100.0}
    var pressUntil=0.0
    fun pressAmount(i: Int,t: Double): Double {
        val k=m.bezier(m.clamp01((t-pressTime[i])/0.15),0.23,1.0,0.32,1.0)
        return pressFrom[i]+(pressTo[i]-pressFrom[i])*k
    }
    fun press(i: Int,down: Boolean,t: Double) {
        pressFrom[i]=pressAmount(i,t);pressTo[i]=if(down)1.0 else 0.0;pressTime[i]=t;pressUntil=t+0.2
    }
    val isMoving get()=touching||cutMove!=null||pitchMove!=null||now()<pressUntil
    fun moveValue(move: Move,t: Double): Double {
        val age=max(0.0,t-move.start)
        return if(move.spring)m.spring(age,move.duration,move.damping,move.from,move.to,move.velocity) else move.from+(move.to-move.from)*(if(reduced)m.easeQuadratic(m.clamp01(age/move.duration)) else m.bezier(m.clamp01(age/move.duration)))
    }
    fun land(i: Int,t: Double) {
        if(!reduced){flareTime=t;flareAmp=doubleArrayOf(0.2,0.45,1.0)[i];flareRise=0.12;flareFall=1.3;audio.play(if(i==2)"shimmer" else "ting")}
        buzz(if(i==2)2 else 1)
    }
    fun sample(t: Double): GemCutPaywallMotion.Frame {
        val dt=(t-tickTime).coerceIn(0.0,0.05);tickTime=t
        if(autoPlay&&!reduced&&!manual) {
            loopTime=(t-origin)%m.LOOP;frame=m.frameAt(loopTime);frame.yaw+=yawBase
        } else {
            if(!spinning) {val base=if(reduced)0.0 else m.IDLE_SPIN;velocity=base+(velocity-base)*exp(-1.4*dt);frame.yaw+=velocity*dt}
            cutMove?.let { move ->
                frame.cut=moveValue(move,t)
                if((move.spring&&frame.cut==move.to)||(!move.spring&&t-move.start>=move.duration)){frame.cut=move.to;cutMove=null;if(!resume)land((move.to*2).roundToInt(),t)}
            }
            pitchMove?.let { move ->frame.pitch=moveValue(move,t);if((move.spring&&frame.pitch==move.to)||(!move.spring&&t-move.start>=move.duration)){frame.pitch=move.to;pitchMove=null} }
            val age=t-flareTime
            frame.spark=if(reduced)0.0 else flareAmp*(if(age<flareRise)m.bezier(m.clamp01(age/flareRise),0.23,1.0,0.32,1.0) else 1-m.bezier(m.clamp01((age-flareRise)/flareFall),0.23,1.0,0.32,1.0))
            frame.finger=fadeFinger*(1-m.easeQuadratic(m.clamp01((t-fingerFadeStart)/0.15)));frame.press=0.0
            if(autoPlay&&!reduced&&!touching&&t-lastTouch>2&&!resume) {
                resume=true;cutMove=Move(t,0.5+0.7*frame.cut,frame.cut,0.0)
                pitchMove=Move(t,0.6,frame.pitch,m.PITCH,spring=true,damping=0.9)
            } else if(resume&&frame.cut<0.002&&abs(velocity-m.IDLE_SPIN)<0.02) {
                resume=false;manual=false;origin=t;yawBase=frame.yaw;cutMove=null
            }
        }
        val next=(frame.cut*2).roundToInt().coerceIn(0,2)
        if(next!=tier){digitFrom=digits(t);retargetOpacity(next,t);previousTier=tier;tier=next;tierTime=t;if(manual&&sliding){if(!reduced)audio.play("tick");buzz(0)}}
        return frame
    }
    fun digits(t: Double)=DoubleArray(4) { i ->
        val to=GemCutPaywallTheme.prices[tier][i].digitToIntOrNull()?.toDouble()?:0.0
        val k=if(reduced)1.0 else m.bezier(m.clamp01((t-tierTime)/(0.32+i*0.04)),0.23,1.0,0.32,1.0)
        digitFrom[i]+(to-digitFrom[i])*k
    }
    fun takeover(t: Double) {
        sample(t);lastTouch=t;resume=false
        if(!manual){manual=true;velocity=if(reduced)0.0 else m.IDLE_SPIN;fadeFinger=frame.finger;fingerFadeStart=t;frozenFingerCut=frame.cut}
    }
    fun go(i: Int,t: Double,v: Double=0.0,glide: Boolean=true) {
        val to=i/2.0;cutMove=Move(t,if(reduced)0.15 else if(glide)0.36+0.68*abs(to-frame.cut) else 0.42,frame.cut,to,v,!reduced&&!glide)
    }
    fun begin(plan: Boolean,t: Double) {takeover(t);touching=true;spinning= !plan;slideFrom=frame.cut;prevX=0.0;prevY=0.0;if(plan)cutMove=null else {velocity=0.0;pitchMove=null}}
    fun drag(plan: Boolean,dx: Double,dy: Double,t: Double,l: GemCutPaywallTheme.Layout) {
        lastTouch=t
        if(plan){if(abs(dx)>6)sliding=true;if(sliding){val raw=slideFrom+dx/l.thumbRun;val over=if(raw<0)raw else if(raw>1)raw-1 else 0.0;frame.cut=raw-over+over*0.18}}
        else if(!reduced){frame.yaw+=(dx-prevX)/(l.gemR*0.9);frame.pitch=(frame.pitch+(dy-prevY)/(l.gemR*2.4)).coerceIn(0.12,0.95)}
        prevX=dx;prevY=dy
    }
    fun end(plan: Boolean,x: Double,dx: Double,dy: Double,vx: Double,vy: Double,t: Double,l: GemCutPaywallTheme.Layout) {
        lastTouch=t;touching=false;spinning=false
        if(plan){val v=vx/l.thumbRun;val i=if(sliding)((frame.cut+v*0.18)*2).roundToInt() else floor((x-4)/l.segW).toInt();go(i.coerceIn(0,2),t,v,!sliding);sliding=false}
        else {
            velocity=if(reduced)0.0 else (vx/(l.gemR*0.9)).coerceIn(-9.0,9.0)
            if(!reduced)pitchMove=Move(t,0.7,frame.pitch,m.PITCH,vy/(l.gemR*2.4),true,0.7)
            if(hypot(dx,dy)<8){if(!reduced){flareTime=t;flareAmp=0.6;flareRise=0.1;flareFall=1.0;audio.play("ting")};buzz(1)}
        }
    }
    companion object { fun now()=System.nanoTime()/1e9 }
}
