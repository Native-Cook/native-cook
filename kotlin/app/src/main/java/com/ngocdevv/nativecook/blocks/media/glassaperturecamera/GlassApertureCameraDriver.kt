package com.ngocdevv.nativecook.blocks.media.glassaperturecamera

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.media.glassaperturecamera.GlassApertureCameraMotion as M

internal class GlassApertureCameraDriver(private val auto: Boolean, private val reduced: Boolean, private val onLand: () -> Unit = {}) {
    var frame=M.Frame(); var time=0.0; var last= -100.0; var automatic=auto && !reduced
    var pinching=false; var pinchU=0.0; var stop=0; var ringX=0.0; var ringY=0.0
    private var autoStart = 0.0
    val loopTime: Double get() = (time-autoStart)%M.LOOP
    private var focusStart=0.0; private var focusFrom=M.NEAR; private var focusTo=M.NEAR
    private var ringAt= -100.0; private var shotStart= -1.0; private var sentAt= -100.0; private var resumeFromU=0.0; private var resumeFromFocus=0.0; private var resuming=false
    private var irisFrom=0.0; private var irisTo=0.0; private var irisStart=0.0; private var irisDuration=0.0
    private var fingerFrom=0.0; private var fingerStart= -100.0
    private fun irisTo(value:Double,duration:Double,delay:Double=0.0){irisFrom=frame.iris;irisTo=value;irisStart=time+delay;irisDuration=if(reduced).001 else duration}
    fun touch() {
        last=time; resuming=false; focusFrom=frame.focus; focusTo=frame.focus; focusStart=time
        if(automatic){automatic=false;fingerFrom=frame.finger;fingerStart=time;irisTo(0.0,.3);shotStart= -1.0;frame=frame.copy(snap=0.0,shot= -1.0)}
    }
    fun beginPinch(){touch();pinching=true;pinchU=M.uOf(frame.fnum);stop=(pinchU*21).roundToInt();irisTo(1.0,.22)}
    fun pinch(scale: Double): Boolean {last=time;val u=(pinchU-ln(scale)*.55).coerceIn(0.0,1.0);frame=frame.copy(fnum=M.fAt(u));val s=(u*21).roundToInt();return if(s!=stop){stop=s;true}else false}
    fun endPinch(){pinching=false;last=time;irisTo(0.0,.36,.5)}
    fun focus(x: Double,y: Double,w: Double,photoH: Double){touch();focusFrom=frame.focus;focusTo=M.depthAt(x/w,y/photoH);focusStart=time;ringAt=time;ringX=x;ringY=y}
    fun shoot(){touch();shotStart=time;sentAt=time;frame=frame.copy(shot=0.0)}
    fun adjust(u:Double){touch();frame=frame.copy(fnum=M.fAt(u),iris=1.0);irisTo(0.0,.36,.7)}
    fun step(dt: Double){
        time+=dt.coerceAtMost(.5)
        if(automatic){frame=M.frameAt(loopTime);return}
        val ft=if(reduced)1.0 else ((time-focusStart)/.52).coerceIn(0.0,1.0)
        val ip=if(time<irisStart)0.0 else if(irisDuration<=0)1.0 else ((time-irisStart)/irisDuration).coerceIn(0.0,1.0)
        frame=frame.copy(focus=focusFrom+(focusTo-focusFrom)*M.bezier(ft),ring=if(time-ringAt<1.2)time-ringAt else -1.0,iris=irisFrom+(irisTo-irisFrom)*M.quad(ip),finger=fingerFrom*(1-M.quad(((time-fingerStart)/.15).coerceIn(0.0,1.0))))
        if(shotStart>=0){
            val before=frame.shot; val s=time-shotStart
            if(s>=M.Shot.END){frame=frame.copy(shot= -1.0);shotStart= -1.0}
            else {
                frame=frame.copy(shot=s)
                if(!reduced && before-M.Shot.GO<M.LAND && s-M.Shot.GO>=M.LAND)onLand()
            }
        }
        val s=time-sentAt
        if(s>=0 && s<3.0){
            val snap=if(reduced)0.0 else if(s<.11)(s/.11).pow(3) else 1-M.out(((s-.11)/.23).coerceIn(0.0,1.0))
            val start=if(reduced).3 else round((M.Shot.GO+M.LAND)*1000)/1000
            val sent=if(s<start)0.0 else if(s<start+.25)M.quad((s-start)/.25) else if(s<start+1.75)1.0 else 1-M.quad((s-start-1.75)/.25)
            frame=frame.copy(snap=snap,sent=sent)
        } else frame=frame.copy(snap=0.0,sent=0.0)
        if(auto && !reduced && !pinching && time-last>=2.0){
            if(!resuming){resuming=true;resumeFromU=M.uOf(frame.fnum);resumeFromFocus=frame.focus}
            val p=M.bezier(((time-last-2)/.6).coerceIn(0.0,1.0));frame=frame.copy(fnum=M.fAt(resumeFromU+(M.uOf(2.8)-resumeFromU)*p),focus=resumeFromFocus+(M.NEAR-resumeFromFocus)*p)
            if(time-last>2.8 && frame.sent<.001 && frame.shot<0){automatic=true;autoStart=time;frame=M.frameAt(0.0)}
        }
    }
}
