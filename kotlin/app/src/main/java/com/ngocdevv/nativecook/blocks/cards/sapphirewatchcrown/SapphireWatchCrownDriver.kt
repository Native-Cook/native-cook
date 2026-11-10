package com.ngocdevv.nativecook.blocks.cards.sapphirewatchcrown

import kotlin.math.*

internal class SapphireWatchCrownDriver(val auto: Boolean,val reduced: Boolean,private val buzz: (Int)->Unit,private val cue: (String)->Unit) {
    private data class Tween(val a: Double,val b: Double,val start: Double,val duration: Double,val custom: Boolean=true) {
        fun value(now: Double): Double {
            val p=((now-start)/max(duration,.000001)).coerceIn(0.0,1.0)
            val k=if(custom)SapphireWatchCrownMotion.bezier(p) else if(p<.5)2*p*p else 1-(-2*p+2).pow(2)/2
            return a+(b-a)*k
        }
        fun done(now: Double)=now>=start+duration
    }
    var frame=SapphireWatchCrownMotion.Frame(); private set
    var now=0.0; private set
    var time=0.0; private set
    var sizeAt=1
    private var mode=if(auto && !reduced)0 else 1
    private var autoStart=0.0; private var lastTouch=-100.0
    private var turning=false; private var resumeAsked=false
    private var hours0=SapphireWatchCrownMotion.REST_HOURS; private var lastDetent=0
    private var flipTween: Tween?=null; private var metalTween: Tween?=null
    private var hoursTween: Tween?=null; private var crownTween: Tween?=null; private var fingerTween: Tween?=null
    val moving get() = (auto && !reduced) || frame.flip>.001 || turning || flipTween!=null || metalTween!=null || hoursTween!=null || fingerTween!=null || crownTween!=null
    private fun tween(a: Double,b: Double,d: Double,custom: Boolean=true)=Tween(a,b,now,d,custom)
    fun takeOver() {
        lastTouch=now; resumeAsked=false
        if(mode!=0)return
        mode=1; fingerTween=tween(frame.finger,0.0,.15,false); frame.press=0.0
        if(frame.flip>.001 && frame.flip<.999)flipTween=tween(frame.flip,if(frame.flip>.5)1.0 else 0.0,.4,false)
        if(frame.metalK>0)recast(frame.selectedMetal)
        frame.hours=SapphireWatchCrownMotion.mod(frame.hours,12.0)
    }
    fun turnOver(to: Double) {
        flipTween=tween(frame.flip,to,if(reduced).25 else .85); buzz(1); if(to>.5)cue("tick")
    }
    fun flip() { takeOver(); turnOver(if(frame.flip>.5)0.0 else 1.0) }
    private fun recast(to: Int) {
        val current=frame.selectedMetal
        if(current==to && frame.metalK==0.0)return
        frame.metalFrom=current; frame.metalTo=to; frame.metalK=0.0
        metalTween=tween(0.0,1.0,if(reduced).2 else .6)
    }
    fun pick(to: Int) { buzz(0); takeOver(); recast(to) }
    fun windStart() { turning=true; hours0=frame.hours; hoursTween=null; lastDetent=floor(frame.hours*30+.5).toInt() }
    fun wind(dx: Double,dy: Double) {
        lastTouch=now; val along=(dx-dy)*.7
        frame.hours=hours0+along*.06; frame.crown=along*.12; crownTween=null
        val det=floor(frame.hours*30+.5).toInt()
        if(det!=lastDetent) { lastDetent=det; buzz(2); cue("detent") }
    }
    fun end() { lastTouch=now; turning=false }
    fun adjust(increment: Boolean) { takeOver(); hoursTween=tween(frame.hours,frame.hours+(if(increment).25 else -.25),.3,false) }
    fun step(dt: Double) {
        now+=dt
        val lt=SapphireWatchCrownMotion.mod(now-autoStart,10.0)
        if(mode==0)frame=SapphireWatchCrownMotion.frameAt(lt)
        else {
            flipTween?.let { frame.flip=it.value(now); if(it.done(now)) { frame.flip=it.b; flipTween=null } }
            metalTween?.let { frame.metalK=it.value(now); if(it.done(now)) { frame.metalFrom=frame.metalTo; frame.metalK=0.0; metalTween=null } }
            hoursTween?.let { frame.hours=it.value(now); if(it.done(now)) { frame.hours=it.b; hoursTween=null } }
            crownTween?.let { frame.crown=it.value(now); if(it.done(now)) { frame.crown=it.b; crownTween=null } }
            fingerTween?.let { frame.finger=it.value(now); if(it.done(now)) { frame.finger=0.0; fingerTween=null } }
            if(auto && !reduced && !turning && now-lastTouch>=2) {
                if(!resumeAsked) {
                    resumeAsked=true
                    if(frame.flip>.001)turnOver(0.0)
                    if(frame.selectedMetal!=1)recast(1)
                    var d=SapphireWatchCrownMotion.REST_HOURS-SapphireWatchCrownMotion.mod(frame.hours,12.0)
                    if(d>6)d-=12; if(d< -6)d+=12
                    hoursTween=tween(frame.hours,frame.hours+d,.9)
                    crownTween=tween(frame.crown,frame.crown+d*PI,.9)
                } else if(frame.flip<.001 && frame.metalK==0.0 && frame.metalFrom==1 && abs(SapphireWatchCrownMotion.mod(frame.hours,12.0)-SapphireWatchCrownMotion.REST_HOURS)<.002 && now-lastTouch>3.2) {
                    resumeAsked=false; autoStart=now; frame.crown=0.0; mode=0
                }
            }
        }
        time=if(mode==0)lt else SapphireWatchCrownMotion.mod(now,10.0)
    }
}
