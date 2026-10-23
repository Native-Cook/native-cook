package com.ngocdevv.nativecook.blocks.pickers.velvetnapstudio

import kotlin.math.*

internal data class VelvetNapStudioTint(val r: Double=1.0,val g: Double=1.0,val b: Double=1.0) {
    fun mixed(to: VelvetNapStudioTint,u: Double)=VelvetNapStudioTint(r+(to.r-r)*u,g+(to.g-g)*u,b+(to.b-b)*u)
}
internal data class VelvetNapStudioSession(
    val id: Long,var from: VelvetNapStudioFrame,var target: VelvetNapStudioField?,var stamp: Double,
    var settled: Boolean=false,var lastPoint: VelvetNapStudioPoint,var samplePoint: VelvetNapStudioPoint,
    var lastDate: Double,var angle: Double,var samples: List<VelvetNapStudioPoint>,
)
internal data class VelvetNapStudioEvent(val date: Double,val session: VelvetNapStudioSession)
internal data class VelvetNapStudioControl(val date: Double,val frame: VelvetNapStudioFrame)
internal data class VelvetNapStudioPalette(val date: Double,val from: VelvetNapStudioTint,val to: VelvetNapStudioTint)
internal data class VelvetNapStudioRendered(val frame: VelvetNapStudioFrame,val tint: VelvetNapStudioTint,val date: Double)

/** Event state only; frame reads never mutate Compose state or accumulate motion. */
internal class VelvetNapStudioDriver {
    var epoch=0.0
    var session: VelvetNapStudioSession?=null
    var event: VelvetNapStudioEvent?=null
    var control: VelvetNapStudioControl?=null
    var palette: VelvetNapStudioPalette?=null
    var frozenTint: VelvetNapStudioTint?=null
    var baseTint=VelvetNapStudioTint()
    var isControlHeld=false
    var rendered: VelvetNapStudioRendered?=null
    var selected=3
    var yards=2
    var liked=false
    var addedAt: Double?=null
    var lastAngle=atan2(-.6,.8)
    private var ringFrom=3.0
    private var ringDate=-100.0
    fun tint(now: Double): VelvetNapStudioTint {
        frozenTint?.let{return it}
        palette?.let{return it.from.mixed(it.to,VelvetNapStudioMath.smooth(max(0.0,now-it.date)/.18))}
        return baseTint
    }
    fun ring(now: Double,reduced: Boolean): Double {
        if(reduced) return selected.toDouble()
        val t=max(0.0,now-ringDate);if(t>=.7) return selected.toDouble()
        val omega=2*PI/.22;val damp=.85;val wd=omega*sqrt(1-damp*damp)
        return selected+(ringFrom-selected)*exp(-damp*omega*t)*(cos(wd*t)+damp*omega/wd*sin(wd*t))
    }
    private fun freezeTint(visible: VelvetNapStudioTint) {frozenTint=visible;baseTint=visible;palette=null}
    private fun resumeTint(visible: VelvetNapStudioTint,toward: VelvetNapStudioTint,now: Double) {
        frozenTint=null;baseTint=toward;palette=if(visible==toward) null else VelvetNapStudioPalette(now,visible,toward)
    }
    private fun held(s: VelvetNapStudioSession,now: Double,reduced: Boolean): VelvetNapStudioFrame {
        val target=s.target?:return s.from
        return VelvetNapStudioMath.mixed(s.from,target,if(reduced||s.settled) 1.0 else VelvetNapStudioMath.smooth(max(0.0,now-s.stamp)/.09))
    }
    fun frame(now: Double,autoPlay: Boolean,reduced: Boolean,preview: Double?): VelvetNapStudioFrame {
        if(preview!=null) return if(reduced) VelvetNapStudioFrame.REST else VelvetNapStudioMath.authored(preview)
        session?.let{return held(it,now,reduced)}
        control?.let {val age=max(0.0,now-it.date)
            if(isControlHeld||age<2) return it.frame
            if(!reduced&&age<2.6) return VelvetNapStudioMath.recovering(it.frame,age-2)
            return if(autoPlay&&!reduced) VelvetNapStudioMath.authored(age-if(reduced)2.0 else 2.6) else VelvetNapStudioFrame.REST
        }
        event?.let {val age=max(0.0,now-it.date)
            if(age<2) return held(it.session,now,reduced)
            if(age<2.6&&!reduced) return VelvetNapStudioMath.recovering(held(it.session,it.date+2,reduced),age-2)
            return if(autoPlay&&!reduced) VelvetNapStudioMath.authored(age-2.6) else VelvetNapStudioFrame.REST
        }
        return if(autoPlay&&!reduced) VelvetNapStudioMath.authored(max(0.0,now-epoch)) else VelvetNapStudioFrame.REST
    }
    private fun snapshot(now: Double,autoPlay: Boolean,reduced: Boolean)=rendered?.frame?:frame(now,autoPlay,reduced,null)
    fun begin(id: Long,p: VelvetNapStudioPoint,now: Double,autoPlay: Boolean,reduced: Boolean): Boolean {
        if(session!=null) return false
        val from=VelvetNapStudioMath.coalesced(snapshot(now,autoPlay,reduced));freezeTint(rendered?.tint?:tint(now));control=null;event=null
        val base=VelvetNapStudioMath.manualBaked(from)
        val target=VelvetNapStudioMath.stamped(base,p,p,lastAngle,44.0,from)
        session=VelvetNapStudioSession(id,from,target,now,lastPoint=p,samplePoint=p,lastDate=now,angle=lastAngle,samples=listOf(p))
        return true
    }
    fun move(id: Long,p: VelvetNapStudioPoint,now: Double,reduced: Boolean): Boolean {
        val s=session?:return false;if(s.id!=id||s.lastPoint==p) return false
        val dx=p.x-s.lastPoint.x;val dy=p.y-s.lastPoint.y;val dt=max(.001,now-s.lastDate)
        val speed=hypot(dx,dy)/dt;val theta=atan2(dy,dx)
        s.angle=VelvetNapStudioMath.angle(s.angle,theta,1-exp(-dt/.045));lastAngle=s.angle;s.lastPoint=p;s.lastDate=now
        if(hypot(p.x-s.samplePoint.x,p.y-s.samplePoint.y)>=4) {
            val current=held(s,now,reduced);val base=VelvetNapStudioMath.manualBaked(current)
            s.from=VelvetNapStudioFrame(time=current.time,authorWeight=current.authorWeight*current.strength,fields=listOf(base),weights=listOf(1.0))
            s.target=VelvetNapStudioMath.stamped(base,s.samplePoint,p,s.angle,52+min(14.0,speed*.02),s.from)
            s.samplePoint=p;s.stamp=now;s.settled=false;s.samples=s.samples+p
            if(s.samples.size>64) s.samples=s.samples.filterIndexed{i,_->i%2==0||i==s.samples.lastIndex}
        }
        return true
    }
    fun finish(id: Long,p: VelvetNapStudioPoint,now: Double,reduced: Boolean,cancelled: Boolean=false): Boolean {
        if(session?.id!=id) return false
        if(!cancelled) move(id,p,now,reduced)
        val s=session?:return false;session=null
        if(isControlHeld) {control=VelvetNapStudioControl(now,rendered?.frame?:held(s,now,reduced));event=null}
        else {event=VelvetNapStudioEvent(now,s);resumeTint(frozenTint?:tint(now),paletteTint(selected),now)}
        return true
    }
    fun captureControl(now: Double,autoPlay: Boolean,reduced: Boolean) {
        val captured=snapshot(now,autoPlay,reduced);val visibleTint=rendered?.tint?:tint(now);event=null
        val s=session
        if(s!=null) {s.from=captured;s.target=null;s.stamp=now;s.settled=true;control=null;freezeTint(visibleTint)}
        else {control=VelvetNapStudioControl(now,captured);resumeTint(visibleTint,paletteTint(selected),now)}
    }
    fun controlPressed(pressed: Boolean,now: Double,autoPlay: Boolean,reduced: Boolean) {
        val visibleTint=rendered?.tint?:tint(now);isControlHeld=pressed;captureControl(now,autoPlay,reduced)
        if(pressed) freezeTint(visibleTint)
    }
    fun choose(index: Int,now: Double,autoPlay: Boolean,reduced: Boolean): Boolean {
        val visibleTint=rendered?.tint?:tint(now);captureControl(now,autoPlay,reduced)
        if(selected==index) return false
        ringFrom=ring(now,reduced);ringDate=now;selected=index
        if(session==null) resumeTint(visibleTint,paletteTint(index),now)
        return true
    }
    fun save(now: Double,autoPlay: Boolean,reduced: Boolean) {captureControl(now,autoPlay,reduced);addedAt=now}
    fun confirmation(now: Double)=addedAt?.let{max(0.0,now-it)<1.2}?:false
    fun accessibleBrush(up: Boolean,now: Double,autoPlay: Boolean,reduced: Boolean) {
        val p=VelvetNapStudioPoint(153.5,137.0);val from=VelvetNapStudioMath.coalesced(snapshot(now,autoPlay,reduced))
        val visibleTint=rendered?.tint?:tint(now);control=null;session=null;resumeTint(visibleTint,paletteTint(selected),now)
        val base=VelvetNapStudioMath.manualBaked(from)
        val target=VelvetNapStudioMath.stamped(base,VelvetNapStudioPoint(p.x-45,p.y+30),VelvetNapStudioPoint(p.x+45,p.y-30),atan2(-.6,.8)+(if(up)0.0 else PI),58.0,from)
        event=VelvetNapStudioEvent(now,VelvetNapStudioSession(-1,from,target,now,lastPoint=p,samplePoint=p,lastDate=now,angle=0.0,samples=emptyList()))
    }
    fun complete(now: Double,reduced: Boolean): Boolean {
        var changed=false
        palette?.let {if(now-it.date>=.18) {palette=null;changed=true}}
        session?.let {if(!it.settled&&now-it.stamp>=.095) {it.settled=true;changed=true}}
        event?.session?.let {if(!it.settled&&now-it.stamp>=.095) {it.settled=true;changed=true}}
        val duration=if(reduced)2.0 else 2.6
        control?.let {if(!isControlHeld&&now-it.date>=duration) {epoch=it.date+duration;control=null;rendered=null;changed=true}}
        event?.let {if(now-it.date>=duration) {epoch=it.date+duration;event=null;rendered=null;changed=true}}
        addedAt?.let {if(now-it>=1.2) {addedAt=null;changed=true}}
        return changed
    }
    fun needsFrames(now: Double,autoPlay: Boolean,reduced: Boolean,preview: Double?): Boolean {
        if(preview!=null) return false
        if(palette!=null||(!reduced&&now-ringDate<.7)) return true
        if(isControlHeld) return false
        session?.let{return !reduced&&!it.settled}
        event?.session?.let{if(!reduced&&!it.settled&&now-it.stamp<.095)return true}
        val stamp=control?.date?:event?.date
        if(stamp!=null) return !reduced&&now-stamp>=2&&now-stamp<2.6
        return autoPlay&&!reduced
    }
    fun nextDeadline(now: Double,reduced: Boolean): Double? {
        val out=mutableListOf<Double>();palette?.let{out+=it.date+.18}
        session?.let{if(!it.settled)out+=it.stamp+.095}
        event?.session?.let{if(!it.settled)out+=it.stamp+.095}
        if(!isControlHeld) {val stamp=control?.date?:event?.date;if(stamp!=null){if(!reduced&&now<stamp+2)out+=stamp+2;out+=stamp+(if(reduced)2.0 else 2.6)}}
        addedAt?.let{out+=it+1.2}
        return out.filter{it>now+1e-7}.minOrNull()
    }
    fun clear(now: Double) {session=null;event=null;control=null;palette=null;frozenTint=null;baseTint=paletteTint(selected);isControlHeld=false;addedAt=null;rendered=null;epoch=now;lastAngle=atan2(-.6,.8);ringFrom=selected.toDouble();ringDate=-100.0}
    companion object {
        val colors=intArrayOf(0xC7BDA0,0xA88444,0x7C253E,0x073C68,0xD3A5AD,0x438D97,0x41485C,0x737A43)
        val names=arrayOf("Oat","Ochre","Claret","Midnight","Blush","Lagoon","Slate","Moss")
        fun paletteTint(index: Int): VelvetNapStudioTint {
            if(index==3) return VelvetNapStudioTint()
            val c=colors[index];return VelvetNapStudioTint(((c shr 16)and 255)/255.0/.028,((c shr 8)and 255)/255.0/.235,(c and 255)/255.0/.408)
        }
    }
}
