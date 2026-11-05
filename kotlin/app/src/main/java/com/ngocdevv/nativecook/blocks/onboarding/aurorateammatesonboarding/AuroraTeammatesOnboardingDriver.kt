package com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingMotion as M
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingMotion.GL
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingTheme.Layout

/** UI-thread owner of time and touch. Only the draw observer changes on each display frame. */
internal class AuroraTeammatesOnboardingDriver(private val autoPlay:Boolean,private val reduced:Boolean,private val previewTime:Double?) {
    private sealed interface Mode {
        data object Rest:Mode
        data class Page(val from:Double,val to:Double,val at:Double,val duration:Double):Mode
        data class Finale(val at:Double):Mode
    }
    private var mode:Mode=Mode.Rest
    val scene=DoubleArray(M.G+37)
    val input=M.Input()
    var position=previewTime?.let{M.posAt(it)}?:0.0;private set
    private var clock=previewTime?:0.0
    private var ambient=previewTime?:0.0
    var now=0.0;private set
    private var held=false
    private var dragging=false
    private var dragFrom=0.0
    private var dragAt=0.0
    private var verticalRejected=false
    private var resumeAt=0.0
    private var resumeFromPos=false
    private var took=false
    private var releaseAt=-10.0
    private var downAt=-10.0
    private var releaseDown=0.0
    private var releaseDrag=0.0
    private var releaseVx=0.0
    private var lastTouched=-1
    private var lastCheck=false
    var buttonPress=0.0;private set
    private var buttonAim=0.0
    private var buttonTarget=false
    val moving get()=mode!=Mode.Rest||held||(!reduced&&autoPlay)||buttonPress>.001
    fun step(dt:Double,l:Layout,tilt:AuroraTeammatesOnboardingTilt,buzz:(Int)->Unit,onComplete:()->Unit) {
        if(previewTime!=null){input.autoVis=1.0;input.holdQ=(clock%M.SEG/M.HOLD).coerceAtMost(1.0);M.compute(position,ambient,l,reduced,input,scene);return}
        now+=dt
        if(!reduced){ambient=(ambient+dt)%M.LOOP;tilt.step(dt);input.tiltX=tilt.x;input.tiltY=tilt.y;input.drift=if(tilt.available)0.0 else 1.0}
        input.autoVis+=((if(autoPlay&&!reduced&&!took)1.0 else 0.0)-input.autoVis)*(1-exp(-dt/.22))
        buttonPress+=(buttonAim-buttonPress)*(1-exp(-dt/(if(buttonAim>0).04 else .085)))
        if(held)input.down=1-(1-M.clamp((now-downAt)/.22)).pow(3)
        else {
            val q=M.clamp((now-releaseAt)/.52);input.down=releaseDown*(1-q).pow(3)
            input.drag=releaseDrag*(1-M.spring(M.clamp((now-releaseAt)/.9),.38,10.0))
            input.vx=releaseVx*(1-M.spring(M.clamp((now-releaseAt)/.7),.55,10.0))
        }
        if(dragging)input.drag=1-(1-M.clamp((now-dragAt)/.2)).pow(3)
        when(val m=mode) {
            is Mode.Page->{val q=M.clamp((now-m.at)/m.duration);val eased=if(reduced)if(q<.5)2*q*q else 1-(-2*q+2).pow(2)/2 else q;position=M.mix(m.from,m.to,eased)
                if(q>=1){position=M.wrap(nearest(position));input.fin=0.0;mode=Mode.Rest;resumeAt=now+M.RESUME;resumeFromPos=true;buzz(0)}}
            is Mode.Finale->{input.fin=M.clamp((now-m.at)/(if(reduced).9 else 2.1));if(input.fin>=1){onComplete();playTo(nearest(position)+1,1.0)}}
            Mode.Rest->if(autoPlay&&!reduced&&!held&&now>=resumeAt){took=false;if(resumeFromPos){clock=M.clockAt(position);resumeFromPos=false};clock=(clock+dt)%M.LOOP;position=M.posAt(clock)}
        }
        val tau=clock%M.SEG;input.holdQ=if(tau<M.HOLD)tau/M.HOLD else 1.0
        M.compute(position,ambient,l,reduced,input,scene)
        val touched=scene[GL.touched].toInt();if(touched>=0&&touched!=lastTouched)buzz(1);lastTouched=touched
        val check=scene[GL.finCheck]>.5;if(check&&!lastCheck)buzz(2);lastCheck=check
    }
    private fun nearest(value:Double)=floor(value+.5)
    fun prepare(l:Layout){
        input.autoVis=if(previewTime!=null)1.0 else 0.0
        input.holdQ=(clock%M.SEG/M.HOLD).coerceAtMost(1.0)
        M.compute(position,ambient,l,reduced,input,scene)
    }
    private fun playTo(target:Double,speed:Double){mode=Mode.Page(position,target,now,if(reduced).45 else max(.12,abs(target-position)*M.TRANS*speed))}
    fun next(){took=true;if(input.fin>0)return
        if(mode==Mode.Rest&&abs(M.wrap(position)-2)<1e-3){mode=Mode.Finale(now);input.fin=0.0;return}
        val base=if(mode is Mode.Page)nearest(position+.5)else floor(position+1e-4);val speed=if(mode is Mode.Page).7 else 1.0;playTo(base+1,speed)
    }
    fun accessibilityStep(dir:Int){took=true;playTo(nearest(position)+dir,1.0)}
    fun begin(x:Double,y:Double,l:Layout){
        held=true;dragging=false;verticalRejected=false;input.x=x;input.y=y;downAt=now
        buttonTarget=abs(x-l.cx)<=l.buttonW/2&&abs(y-scene[GL.sinkY])<=l.buttonH/2
        if(buttonTarget)buttonAim=1.0
    }
    fun move(x:Double,y:Double,dx:Double,dy:Double,vx:Double,l:Layout){
        input.x=x;input.y=y
        if(!dragging&&abs(dy)>24)verticalRejected=true
        if(!dragging&&!verticalRejected&&abs(dx)>12){mode=Mode.Rest;input.fin=0.0;dragging=true;took=true;dragFrom=position;dragAt=now;buttonAim=0.0;buttonTarget=false}
        if(dragging){input.vx=vx;val p=dragFrom+M.dragToPos(dx,l.w);val lo=floor(dragFrom+1e-4)-1
            val over=if(p>lo+2)p-lo-2 else if(p<lo)p-lo else 0.0;val give=sign(over)*(1-exp(-abs(over)*3))/6;position=p.coerceIn(lo,lo+2)+give}
    }
    fun end(x:Double,y:Double,dx:Double,dy:Double,vx:Double,l:Layout,cancelled:Boolean){
        held=false;buttonAim=0.0
        if(dragging){val projected=position+M.dragToPos(vx*.2,l.w);val start=floor(dragFrom+1e-4);playTo(nearest(projected).coerceIn(start-1,start+1),.85);resumeAt=now+M.RESUME;resumeFromPos=true}
        else if(!cancelled&&buttonTarget&&hypot(dx,dy)<12&&abs(x-l.cx)<=l.buttonW/2&&abs(y-scene[GL.sinkY])<=l.buttonH/2)next()
        else if(mode==Mode.Rest&&abs(position-nearest(position))>1e-3)playTo(ceil(position),1.0)
        if(!dragging&&mode==Mode.Rest)resumeAt=now+.25
        releaseAt=now;releaseDown=input.down;releaseDrag=input.drag;releaseVx=input.vx;dragging=false;buttonTarget=false
    }
}
