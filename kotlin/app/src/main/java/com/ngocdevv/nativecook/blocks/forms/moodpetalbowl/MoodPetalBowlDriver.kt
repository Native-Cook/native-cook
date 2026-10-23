package com.ngocdevv.nativecook.blocks.forms.moodpetalbowl

import kotlin.math.*

internal fun moodPetalBowlNow() = System.nanoTime() / 1_000_000_000.0
internal enum class MoodPetalBowlOwner { Bowl, Label }
internal data class MoodPetalBowlSession(val generation: Int,val owner: MoodPetalBowlOwner,val base: MoodPetalBowlScene,
    var current: MoodPetalBowlScene,val anchor: MoodPetalBowlPoint,var lastPoint: MoodPetalBowlPoint,var lastTime: Double,
    var velocity: MoodPetalBowlPoint=MoodPetalBowlPoint(0.0,0.0),var translation: MoodPetalBowlPoint=MoodPetalBowlPoint(0.0,0.0),var haptic: Boolean=false)
internal sealed interface MoodPetalBowlEventKind {
    data class Transition(val plan: MoodPetalBowlTransition): MoodPetalBowlEventKind
    data class Release(val from: MoodPetalBowlScene,val base: MoodPetalBowlScene,val cancelled: Boolean): MoodPetalBowlEventKind
    data class Hold(val scene: MoodPetalBowlScene): MoodPetalBowlEventKind
}
internal data class MoodPetalBowlEvent(val stamp: Double,val kind: MoodPetalBowlEventKind)
internal data class MoodPetalBowlQuietSave(val stamp: Double,val day: Int)

/** Plain event state. Only the drawing clock and discrete event revision are Compose state. */
internal class MoodPetalBowlDriver {
    var epoch=moodPetalBowlNow()
    val records=MoodPetalBowlMath.initialRecords.toMutableList()
    var chosenMood=MoodPetalBowlMood.Calm;var chosenDay=3
    var event: MoodPetalBowlEvent?=null
    var session: MoodPetalBowlSession?=null
    var quietSave: MoodPetalBowlQuietSave?=null
    var rendered: MoodPetalBowlScene?=null
    private var generation=0
    private fun eventScene(event: MoodPetalBowlEvent,age: Double)=when(val kind=event.kind) {
        is MoodPetalBowlEventKind.Transition -> MoodPetalBowlMath.transitioning(kind.plan,age)
        is MoodPetalBowlEventKind.Release -> MoodPetalBowlMath.released(kind.from,kind.base,age,kind.cancelled)
        is MoodPetalBowlEventKind.Hold -> kind.scene
    }
    fun scene(now: Double,autoPlay: Boolean,reduced: Boolean,preview: Double?): MoodPetalBowlScene {
        if(reduced) {val s=MoodPetalBowlMath.settled(chosenMood,chosenDay,records);return quietSave?.let { s.copy(captionAlpha=1.0,captionDay=it.day) }?:s}
        if(preview!=null) return MoodPetalBowlMath.authored(preview,records)
        session?.let {return it.current}
        event?.let { e -> val age=max(0.0,now-e.stamp)
            if(age<2) return eventScene(e,age)
            if(age<2.70) return MoodPetalBowlMath.recovering(eventScene(e,2.0),age-2,records)
            return if(autoPlay) MoodPetalBowlMath.authored(age-2.70,records) else MoodPetalBowlMath.rest(records)
        }
        return if(autoPlay) MoodPetalBowlMath.authored(max(0.0,now-epoch),records) else MoodPetalBowlMath.rest(records)
    }
    private fun snapshot(now: Double,auto: Boolean,reduced: Boolean,preview: Double?)=rendered?:scene(now,auto,reduced,preview)
    private fun allowed(preview: Double?)=preview==null&&session==null
    fun chooseMood(step: Int,now: Double,auto: Boolean,reduced: Boolean,preview: Double?): Boolean {
        if(!allowed(preview)) return false
        val old=snapshot(now,auto,reduced,preview)
        choose(old.dominantMood.stepped(step),old.selectedDay,step.toDouble(),0.0,old,now,reduced);return true
    }
    fun chooseDay(day: Int,now: Double,auto: Boolean,reduced: Boolean,preview: Double?): Boolean {
        if(!allowed(preview)) return false
        val old=snapshot(now,auto,reduced,preview)
        choose(records[day]?:MoodPetalBowlMood.Calm,day,if(day<old.selectedDay) -1.0 else 1.0,0.0,old,now,reduced);return true
    }
    private fun choose(mood: MoodPetalBowlMood,day: Int,direction: Double,velocity: Double,old: MoodPetalBowlScene,now: Double,reduced: Boolean) {
        chosenMood=mood;chosenDay=day;quietSave=null
        event=if(reduced) null else MoodPetalBowlEvent(now,MoodPetalBowlEventKind.Transition(MoodPetalBowlMath.transition(old,mood,day,records,direction,velocity)))
    }
    fun save(now: Double,auto: Boolean,reduced: Boolean,preview: Double?): Boolean {
        if(!allowed(preview)) return false
        val current=snapshot(now,auto,reduced,preview);val day=current.selectedDay;val mood=current.dominantMood
        records[day]=mood;chosenMood=mood;chosenDay=day
        val dots=current.dots.toMutableList();dots[day]=MoodPetalBowlRGBA.hex(mood.hex)
        val saved=current.copy(dots=dots,captionAlpha=1.0,captionDay=day)
        if(reduced) quietSave=MoodPetalBowlQuietSave(now,day) else event=MoodPetalBowlEvent(now,MoodPetalBowlEventKind.Hold(saved))
        return true
    }
    fun begin(owner: MoodPetalBowlOwner,point: MoodPetalBowlPoint,now: Double,auto: Boolean,reduced: Boolean,preview: Double?,sampleTime: Double=now): Boolean {
        if(reduced||preview!=null||session!=null) return false
        val capture=snapshot(now,auto,reduced,preview);event=null;quietSave=null
        session=MoodPetalBowlSession(++generation,owner,capture,capture,point,point,sampleTime);return true
    }
    /** Returns true only for the single meaningful stir haptic. */
    fun changed(owner: MoodPetalBowlOwner,point: MoodPetalBowlPoint,now: Double): Boolean {
        val active=session?:return false;if(active.owner!=owner||point==active.lastPoint) return false
        val dt=max(1.0/240,now-active.lastTime)
        active.velocity=MoodPetalBowlPoint((point.x-active.lastPoint.x)/dt,(point.y-active.lastPoint.y)/dt)
        active.lastPoint=point;active.lastTime=now;active.translation=MoodPetalBowlPoint(point.x-active.anchor.x,point.y-active.anchor.y)
        if(owner==MoodPetalBowlOwner.Bowl) {
            active.current=MoodPetalBowlMath.stirring(active.base,active.anchor,active.translation,active.velocity)
            val distance=active.base.tokens.zip(active.current.tokens).maxOf { it.first.center.distance(it.second.center) }
            if(!active.haptic&&distance>=3) {active.haptic=true;return true}
        }
        return false
    }
    fun finish(owner: MoodPetalBowlOwner,now: Double,cancelled: Boolean,reduced: Boolean,sampleTime: Double=now): Boolean {
        val active=session?:return false;if(active.owner!=owner) return false;session=null
        if(owner==MoodPetalBowlOwner.Label&&!cancelled&&abs(active.translation.x)>=48) {
            val step=if(active.translation.x<0) 1 else -1
            val velocity=active.velocity.x*(1-MoodPetalBowlMath.smooth(max(0.0,sampleTime-active.lastTime)/.12))
            choose(active.base.dominantMood.stepped(step),active.base.selectedDay,step.toDouble(),velocity,active.current,now,reduced)
            return true
        }
        event=MoodPetalBowlEvent(now,if(owner==MoodPetalBowlOwner.Bowl) MoodPetalBowlEventKind.Release(active.current,active.base,cancelled) else MoodPetalBowlEventKind.Hold(active.current))
        return false
    }
    fun complete(now: Double): Boolean {
        var changed=false
        event?.let {if(session==null&&now>=it.stamp+2.70) {event=null;chosenMood=MoodPetalBowlMood.Calm;chosenDay=3;epoch=it.stamp+2.70;changed=true}}
        quietSave?.let {if(now>=it.stamp+2) {quietSave=null;changed=true}}
        return changed
    }
    fun nextDeadline(): Double?=listOfNotNull(event?.stamp?.plus(2.70),quietSave?.stamp?.plus(2)).minOrNull()
    fun clear(now: Double) {session=null;event=null;quietSave=null;rendered=null;epoch=now;generation++}
}
