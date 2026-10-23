package com.ngocdevv.nativecook.blocks.cards.lenticulareditionpass

import kotlin.math.max

internal fun lenticularEditionPassNow()=System.nanoTime()/1_000_000_000.0
internal data class LenticularEditionPassSession(val id: Int,val base: LenticularEditionPassFrame,val anchor: LenticularEditionPassPoint,
    var lastPoint: LenticularEditionPassPoint,var lastDate: Double,var supportDate: Double,
    var pointer: LenticularEditionPassPoint=LenticularEditionPassPoint(),var translation: LenticularEditionPassPoint=LenticularEditionPassPoint(),
    var yawSupport: LenticularEditionPassVelocitySupport=LenticularEditionPassVelocitySupport(),
    var pitchSupport: LenticularEditionPassVelocitySupport=LenticularEditionPassVelocitySupport(),
    var supportActive: Boolean=false,var changed: Boolean=false,var terminal: LenticularEditionPassFrame?=null,
    var hapticView: Int,var hapticArmed: Boolean=true)
internal sealed interface LenticularEditionPassEventKind {
    data class Release(val view: Int): LenticularEditionPassEventKind
    data class Cancel(val base: LenticularEditionPassFrame): LenticularEditionPassEventKind
    data object Collect: LenticularEditionPassEventKind
    data class Reduced(val view: Double,val collection: Boolean): LenticularEditionPassEventKind
}
internal data class LenticularEditionPassEvent(val stamp: Double,val from: LenticularEditionPassFrame,val kind: LenticularEditionPassEventKind,var phase: Int=0) {
    val settleDuration: Double get()=when(kind) {is LenticularEditionPassEventKind.Release -> .75;is LenticularEditionPassEventKind.Cancel -> .24;else -> .18}
    val isReduced get()=kind is LenticularEditionPassEventKind.Reduced
    val hasCollection get()=(kind as? LenticularEditionPassEventKind.Reduced)?.let {it.collection||from.collectionMix>0}?:false
}

/** Plain event state; intermediate frames are derived only in draw and semantics. */
internal class LenticularEditionPassDriver {
    var epoch=lenticularEditionPassNow()
    var session: LenticularEditionPassSession?=null
    var event: LenticularEditionPassEvent?=null
    var reducedBase=LenticularEditionPassFrame(LenticularEditionPassPose.Zero,explicitViewB=0.0)
    var reducedTargetView=0.0
    var rendered: LenticularEditionPassFrame?=null
    private var generation=0
    private fun eventFrame(e: LenticularEditionPassEvent,age: Double): LenticularEditionPassFrame=when(val k=e.kind) {
        is LenticularEditionPassEventKind.Release -> LenticularEditionPassMath.released(e.from,k.view,age)
        is LenticularEditionPassEventKind.Cancel -> LenticularEditionPassMath.cancelled(e.from,k.base,age)
        LenticularEditionPassEventKind.Collect -> LenticularEditionPassMath.collecting(e.from,age)
        is LenticularEditionPassEventKind.Reduced -> {
            var result=LenticularEditionPassMath.reduced(e.from,k.view,age)
            if(k.collection) result=result.copy(collectionMix=e.from.collectionMix+(1-e.from.collectionMix)*LenticularEditionPassMath.ease(age/.18))
            if(age>=2) result=result.copy(collectionMix=result.collectionMix*(1-LenticularEditionPassMath.ease((age-2)/.18)))
            result
        }
    }
    fun heldFrame(s: LenticularEditionPassSession,now: Double): LenticularEditionPassFrame {
        s.terminal?.let {if(!s.supportActive) return it}
        if(!s.changed) return s.base.copy(angularVelocity=LenticularEditionPassPose.Zero)
        return LenticularEditionPassMath.dragged(s.base,s.translation,s.yawSupport,s.pitchSupport,max(0.0,now-s.supportDate))
    }
    fun frame(now: Double,auto: Boolean,reduced: Boolean,preview: Double?): LenticularEditionPassFrame {
        if(reduced) return event?.let {eventFrame(it,if(it.phase==1) it.settleDuration else max(0.0,now-it.stamp))}?:reducedBase
        if(preview!=null) return LenticularEditionPassMath.authored(preview)
        session?.let {return heldFrame(it,now)}
        event?.let { e -> val age=if(e.phase==1) e.settleDuration else max(0.0,now-e.stamp)
            if(age<2) return eventFrame(e,age)
            if(age<2.65) return LenticularEditionPassMath.recovering(eventFrame(e,2.0),age-2)
            return if(auto) LenticularEditionPassMath.authored(age-2.65) else LenticularEditionPassMath.rest()
        }
        return if(auto) LenticularEditionPassMath.authored(max(0.0,now-epoch)) else LenticularEditionPassMath.rest()
    }
    private fun snapshot(now: Double,auto: Boolean,reduced: Boolean,preview: Double?)=rendered?:frame(now,auto,reduced,preview)
    fun begin(point: LenticularEditionPassPoint,now: Double,auto: Boolean,reduced: Boolean,preview: Double?): Boolean {
        if(reduced||preview!=null||session!=null) return false
        val captured=snapshot(now,auto,reduced,preview)
        if(LenticularEditionPassGeometry.inverseHit(point,captured.pose)==null) return false
        event=null
        session=LenticularEditionPassSession(++generation,captured,point,point,now,now,
            hapticView=if(LenticularEditionPassGeometry.meanViewB(captured)>.5) 1 else 0)
        return true
    }
    fun move(point: LenticularEditionPassPoint,now: Double): Boolean {
        val s=session?:return false
        if(s.lastPoint==point) return false
        val dt=max(1.0/240,now-s.lastDate)
        val pointer=LenticularEditionPassPoint((point.x-s.lastPoint.x)/dt,(point.y-s.lastPoint.y)/dt)
        val age=max(0.0,now-s.supportDate)
        s.yawSupport=s.yawSupport.retarget(pointer.x,age);s.pitchSupport=s.pitchSupport.retarget(-pointer.y,age)
        s.supportDate=now;s.supportActive=true;s.lastPoint=point;s.lastDate=now;s.pointer=pointer
        s.translation=LenticularEditionPassPoint(point.x-s.anchor.x,point.y-s.anchor.y);s.changed=true;s.terminal=null
        return true
    }
    fun finish(point: LenticularEditionPassPoint,now: Double,cancelled: Boolean): Boolean {
        if(session==null) return false
        if(!cancelled) move(point,now)
        val s=session?:return false
        var captured=heldFrame(s,now)
        captured=captured.copy(angularVelocity=LenticularEditionPassMath.releaseVelocity(s.base,s.translation,s.pointer,max(0.0,now-s.lastDate),
            if(s.changed) null else s.base.angularVelocity,s.yawSupport,s.pitchSupport,max(0.0,now-s.supportDate)))
        val aged=1-LenticularEditionPassMath.smooth(max(0.0,now-s.lastDate)/.12)
        // Cap raw input BEFORE aging; a stationary Up never refreshes lastDate.
        val pointer=LenticularEditionPassPoint(LenticularEditionPassMath.clamp(s.pointer.x,-1200.0,1200.0)*aged,
            LenticularEditionPassMath.clamp(s.pointer.y,-1200.0,1200.0)*aged)
        val view=LenticularEditionPassMath.targetView(captured,pointer)
        session=null;event=LenticularEditionPassEvent(now,captured,if(cancelled) LenticularEditionPassEventKind.Cancel(s.base) else LenticularEditionPassEventKind.Release(view))
        return true
    }
    fun chooseView(view: Int,now: Double,auto: Boolean,reduced: Boolean,preview: Double?): Boolean {
        if(preview!=null||session!=null) return false
        val captured=snapshot(now,auto,reduced,preview)
        if(reduced) reducedTargetView=view.toDouble()
        event=LenticularEditionPassEvent(now,captured,if(reduced) LenticularEditionPassEventKind.Reduced(view.toDouble(),false) else LenticularEditionPassEventKind.Release(view))
        return true
    }
    fun collect(now: Double,auto: Boolean,reduced: Boolean,preview: Double?): Boolean {
        if(preview!=null||session!=null) return false
        event=LenticularEditionPassEvent(now,snapshot(now,auto,reduced,preview),if(reduced) LenticularEditionPassEventKind.Reduced(reducedTargetView,true) else LenticularEditionPassEventKind.Collect)
        return true
    }
    fun haptic(now: Double): Boolean {
        val s=session?:return false;val f=heldFrame(s,now)
        val value=if(f.pose.yaw<=-7.5) 0 else if(f.pose.yaw>=7.5) 1 else -1
        if(value == -1) s.hapticArmed=true
        else if(value!=s.hapticView&&s.hapticArmed) {s.hapticView=value;s.hapticArmed=false;return true}
        return false
    }
    /** Finite deadline boundaries only, never a per-frame animation accumulator. */
    fun complete(now: Double): Boolean {
        var changed=false
        session?.let {s -> if(s.supportActive&&now>=s.supportDate+.60) {
            s.terminal=LenticularEditionPassMath.dragged(s.base,s.translation,s.yawSupport,s.pitchSupport,.60)
            s.supportActive=false;changed=true
        }}
        event?.let {e ->
            if(e.phase==0&&now>=e.stamp+e.settleDuration) {
                if(e.isReduced&&!e.hasCollection) {reducedBase=eventFrame(e,.18);event=null;return true}
                e.phase=1;changed=true
            }
            if(now>=e.stamp+2&&e.phase<2) {e.phase=2;changed=true}
            val end=if(e.isReduced) 2.18 else 2.65
            if(now>=e.stamp+end) {
                if(e.isReduced) reducedBase=eventFrame(e,end)
                epoch=e.stamp+end;event=null;rendered=null;changed=true
            }
        }
        return changed
    }
    fun needsFrames(auto: Boolean,reduced: Boolean,preview: Double?): Boolean {
        if(preview!=null) return false
        session?.let {return it.supportActive}
        event?.let {return it.phase!=1}
        return !reduced&&auto
    }
    fun nextDeadline(): Double? {
        session?.let {return if(it.supportActive) it.supportDate+.60 else null}
        event?.let {return it.stamp+when(it.phase) {0 -> it.settleDuration;1 -> 2.0;else -> if(it.isReduced) 2.18 else 2.65}}
        return null
    }
    fun clear(now: Double,reduced: Boolean) {
        session=null;event=null;generation++;epoch=now;rendered=null
        reducedBase=reducedBase.copy(collectionMix=0.0)
        if(reduced) reducedBase=LenticularEditionPassFrame(LenticularEditionPassPose.Zero,explicitViewB=reducedTargetView)
    }
    fun reducedChanged(now: Double) {clear(now,true);reducedTargetView=0.0;reducedBase=LenticularEditionPassFrame(LenticularEditionPassPose.Zero,explicitViewB=0.0)}
}
