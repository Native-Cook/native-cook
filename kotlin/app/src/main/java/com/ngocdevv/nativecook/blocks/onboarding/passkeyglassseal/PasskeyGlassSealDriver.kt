package com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal

import com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal.PasskeyGlassSealMotion.Animation
import com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal.PasskeyGlassSealMotion.LIGHT
import com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal.PasskeyGlassSealMotion.UNWIND
import com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal.PasskeyGlassSealMotion.KEY
import com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal.PasskeyGlassSealMotion.TURN
import com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal.PasskeyGlassSealMotion.FLIP
import com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal.PasskeyGlassSealMotion.SCAN_WEIGHT
import com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal.PasskeyGlassSealMotion.FINGER
import com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal.PasskeyGlassSealMotion.PRESS
import com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal.PasskeyGlassSealMotion.LOOP
import com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal.PasskeyGlassSealMotion.SCAN
import com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal.PasskeyGlassSealMotion.HOLD
import com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal.PasskeyGlassSealMotion.at
import kotlin.math.*

internal class PasskeyGlassSealDriver(val auto:Boolean,val reduced:Boolean,val buzz:(Int)->Unit) {
    var frame=doubleArrayOf(0.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0)
    var now=0.0
    var autoStart=0.0
    private var lastTouch=-100.0
    private var manual=!auto
    private var holding=false
    private var read=false
    private var resumeAsked=false
    private var ticks=0
    private var clickAt:Double?=null
    private var flipSpring:PasskeyGlassSealMotion.FlipSpring?=null
    private var swinging=false
    private var swingFrom=0.0
    private val animations=mutableMapOf<Int,Animation>()
    val running get()=auto||holding||swinging||flipSpring!=null||animations.isNotEmpty()||clickAt!=null||(frame[SCAN_WEIGHT]>0)||(!read&&frame[LIGHT]>0)
    private fun animate(k:Int,to:Double,duration:Double,curve:Int=0,delay:Double=0.0){if(k==FLIP)flipSpring=null;animations[k]=Animation(frame[k],to,now+delay,duration,curve)}
    private fun springFlip(to:Double,delay:Double=0.0,velocity:Double?=null,normalise:Boolean=false){
        val speed=velocity?:flipSpring?.state(now)?.velocity?:0.0
        animations.remove(FLIP)
        flipSpring=PasskeyGlassSealMotion.FlipSpring(frame[FLIP],to,now+delay,speed,normalise)
    }
    private fun open(){
        read=true;buzz(1);animate(SCAN_WEIGHT,0.0,0.2)
        if(reduced){animate(FLIP,1.0,0.25);lastTouch=now+0.25;return}
        animate(UNWIND,1.0,1.2,2);animate(KEY,1.0,0.6,1,1.0)
        animate(TURN,1.0,0.6,3,1.7);clickAt=now+2.3
        springFlip(1.0,delay=2.5);lastTouch=now+3.2
    }
    private fun relock(lead:Double){
        read=false
        fun delay(s:Double)=if(reduced)0.0 else max(0.0,s-lead)
        animate(TURN,0.0,if(reduced)0.001 else 0.4,delay=delay(0.5));clickAt=null
        animate(KEY,0.0,if(reduced)0.001 else 0.4,delay=delay(0.75))
        animate(UNWIND,0.0,if(reduced)0.001 else 1.2,2,delay(0.7))
        animate(LIGHT,0.0,if(reduced)0.001 else 0.9,delay=delay(1.2))
        animate(SCAN_WEIGHT,1.0,0.4,delay=delay(1.7))
    }
    private fun close(){
        if(reduced)animate(FLIP,0.0,0.25)else springFlip(0.0)
        relock(0.0)
    }
    fun drag(tx:Double,width:Double){
        if(!read||reduced)return
        if(!swinging){if(abs(tx)<6)return;swinging=true;swingFrom=frame[FLIP]}
        animations.remove(FLIP);flipSpring=null
        frame[FLIP]=swingFrom+1.15*tanh((tx/(1.1*width))/1.15)
    }
    private fun swingTo(v:Double){
        val from=PasskeyGlassSealMotion.nearestFace(swingFrom)
        val goal=PasskeyGlassSealMotion.nearestFace(frame[FLIP]+v*0.18).coerceIn(from-1,from+1)
        val front=PasskeyGlassSealMotion.faceOf(goal)==0
        if(front&&read)relock(0.15)
        springFlip(goal,velocity=v,normalise=true)
        if(!front)buzz(0)
    }
    private fun takeOver(){
        lastTouch=now;resumeAsked=false
        if(manual)return
        manual=true;animate(FINGER,0.0,0.15);frame[PRESS]=0.0
        if(frame[FLIP]>0.5){read=true;for(k in intArrayOf(FLIP,UNWIND,KEY,TURN,LIGHT))animate(k,1.0,0.3);animate(SCAN_WEIGHT,0.0,0.2)}
        else{read=false;frame[LIGHT]=0.0;animations.remove(LIGHT);for(k in intArrayOf(FLIP,UNWIND,KEY,TURN))animate(k,0.0,0.4);animate(SCAN_WEIGHT,1.0,0.3)}
    }
    fun begin(){takeOver();if(read){swingFrom=frame[FLIP];return};ticks=0;holding=true;animate(SCAN_WEIGHT,0.0,0.25)}
    fun end(tap:Boolean,velocityX:Double=0.0,width:Double=327.0,cancelled:Boolean=false){
        val wasSwinging=swinging
        if(swinging&&!cancelled)swingTo(velocityX/(1.1*width))
        swinging=false
        lastTouch=max(lastTouch,now)
        if(holding){holding=false;if(!read)animate(SCAN_WEIGHT,1.0,0.3,delay=0.3)}
        if(!wasSwinging&&tap&&read&&abs(frame[FLIP]-1)<0.01){takeOver();close()}
    }
    fun activate(){takeOver();if(read)close()else{frame[LIGHT]=1.0;animations.remove(LIGHT);open()}}
    fun step(dt:Double){
        now+=dt
        if(!manual&&auto)frame=at(((now-autoStart)%LOOP+LOOP)%LOOP)
        else{
            val iterator=animations.iterator()
            while(iterator.hasNext()){val(k,a)=iterator.next();if(now>=a.start){frame[k]=a.value(now);if(now>=a.start+a.duration)iterator.remove()}}
            flipSpring?.let{s->if(now>=s.start){val state=s.state(now);frame[FLIP]=state.position
                if(state.finished){if(s.normalise)frame[FLIP]=if(PasskeyGlassSealMotion.faceOf(s.to)==0)0.0 else 1.0;flipSpring=null}}}
            clickAt?.let{if(now>=it){clickAt=null;buzz(2)}}
            if(holding&&!read&&frame[UNWIND]<0.01){
                frame[LIGHT]=min(1.0,frame[LIGHT]+if(reduced)1.0 else min(dt,0.05)/HOLD)
                val q=floor(frame[LIGHT]*4).toInt();if(q>ticks&&q<4){ticks=q;buzz(0)}
                if(frame[LIGHT]>=1)open()
            }else if(!read&&!holding&&frame[LIGHT]>0&&frame[UNWIND]<0.001)frame[LIGHT]=max(0.0,frame[LIGHT]-min(dt,0.05)*1.6)
            if(auto&&!holding&&!swinging&&!(read&&abs(frame[FLIP]-1)>0.001)&&now-lastTouch>=2){
                if(!resumeAsked){resumeAsked=true;if(read)close()}
                else if(frame[FLIP]<0.001&&frame[UNWIND]<0.001&&frame[LIGHT]<0.001&&frame[SCAN_WEIGHT]>0.999&&now-lastTouch>3.6){resumeAsked=false;autoStart+=ceil((now-autoStart)/SCAN)*SCAN;manual=false}
            }
        }
    }
}
