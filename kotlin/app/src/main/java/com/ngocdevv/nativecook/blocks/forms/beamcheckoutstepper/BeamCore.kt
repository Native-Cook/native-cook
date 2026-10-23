package com.ngocdevv.nativecook.blocks.forms.beamcheckoutstepper

import kotlin.math.*

internal object BF {
    const val HEAD=0
    const val HEAD_GLOW=1
    const val HEAD_VEL=2
    const val TONE=3
    const val WARM=4
    const val BURST=5
    const val SURGE=6
    const val PRESENT=7
    const val PANEL_H=8
    const val PANEL_FROM=9
    const val PANEL_TO=10
    const val PANEL_MIX=11
    const val ERR=12
    const val FOCUS_X=13
    const val FOCUS_Y=14
    const val FOCUS_W=15
    const val FOCUS_H=16
    const val FOCUS_A=17
    const val CTA_PRESS=18
    const val CTA_SPLIT=19
    const val CTA_LENS=20
    const val CTA_SPIN=21
    const val CTA_DONE=22
    const val BACK_PRESS=23
    const val SEG_LEAD=24
    const val SEG_TRAIL=25
    const val SEG_PRESS=26
    const val DRAG=27
    const val NODE_ON=28
    const val NODE_DONE=33
    const val NODE_ACTIVE=38
    const val NODE_FLASH=43
    const val NODE_SHAKE=48
    const val NODE_PRESS=53
    const val ROW_ACTIVE=58
    const val ROW_DONE=63
    const val ROW_RISE=68
    const val ROW_SWEEP=73
    const val ROW_PRESS=78
    const val FIELD_FLOAT=83
    const val FIELD_ERR=90
    const val SIZE=97
}
internal val beamFieldRects = arrayOf(
    intArrayOf(0,16,52,322), intArrayOf(1,16,52,322), intArrayOf(1,16,116,198),
    intArrayOf(1,222,116,116), intArrayOf(2,16,52,322), intArrayOf(2,16,116,157),
    intArrayOf(2,181,116,157)
)
internal fun beamPanelHeight(step: Int, err: Float = 0f): Float =
    intArrayOf(108,232,172,180,68)[step] + 88f + 24f * err
internal fun beamRest(step: Int, furthest: Int, express: Boolean, filled: BooleanArray,
    errorField: Int = -1, focusField: Int = -1): FloatArray {
    val f = FloatArray(BF.SIZE)
    val finished = step == 4
    f[BF.HEAD] = step.toFloat()
    f[BF.WARM] = if (finished) 1f else 0f
    f[BF.BURST] = 1f
    f[BF.PRESENT] = 1f
    f[BF.PANEL_H] = beamPanelHeight(step, if (errorField >= 0) 1f else 0f)
    f[BF.PANEL_FROM] = step.toFloat()
    f[BF.PANEL_TO] = step.toFloat()
    f[BF.PANEL_MIX] = 1f
    f[BF.ERR] = if (errorField >= 0) 1f else 0f
    f[BF.CTA_SPLIT] = if (step in 1..3) 1f else 0f
    f[BF.CTA_DONE] = if (finished) 1f else 0f
    f[BF.SEG_LEAD] = if (express) 1f else 0f
    f[BF.SEG_TRAIL] = f[BF.SEG_LEAD]
    for (i in 0..4) {
        val done = finished || i < furthest
        val active = i == step && !finished
        f[BF.NODE_ON+i] = if (i <= step) 1f else if (done) .4f else 0f
        f[BF.NODE_DONE+i] = if (done && !active) 1f else 0f
        f[BF.NODE_ACTIVE+i] = if (active) 1f else 0f
        f[BF.ROW_ACTIVE+i] = if (active) 1f else 0f
        f[BF.ROW_DONE+i] = if (done && !active) 1f else 0f
        f[BF.ROW_RISE+i] = if (active || finished && i == 4) 1f else 0f
        f[BF.ROW_SWEEP+i] = if (active) 1f else 0f
    }
    for (i in 0..6) {
        f[BF.FIELD_FLOAT+i] = if (filled[i] || i == focusField) 1f else 0f
        f[BF.FIELD_ERR+i] = if (i == errorField) 1f else 0f
    }
    if (focusField >= 0) {
        val rect = beamFieldRects[focusField]
        f[BF.FOCUS_X] = rect[1].toFloat()
        f[BF.FOCUS_Y] = rect[2].toFloat()
        f[BF.FOCUS_W] = rect[3].toFloat()
        f[BF.FOCUS_H] = 56f
        f[BF.FOCUS_A] = 1f
    }
    return f
}

internal data class BeamEvent(val type: String, val value: Int, val silent: Boolean)
private data class BeamLane(val index: Int, val target: Float, val at: Double,
    val duration: Double, val kind: Int, val omega: Float = 0f, val zeta: Float = 0f,
    var from: Float = 0f, var started: Boolean = false)

internal class BeamCore(val still: Boolean) {
    companion object { const val H = 1.0 / 240.0 }
    val filled = BooleanArray(7)
    val frame = beamRest(0,0,false,filled)
    val velocity = FloatArray(BF.SIZE)
    val presses = FloatArray(BF.SIZE)
    val events = mutableListOf<BeamEvent>()
    private val lanes = mutableListOf<BeamLane>()
    var time = 0.0
    private var remainder = 0.0
    var step = 0
    var furthest = 0
    var express = false
    var focusField = -1
    var errorField = -1
    var mode = "idle"
    private var started = 0.0
    private var arrival = 0.0
    private var target = 0
    private var origin = 0
    private var flags = 0
    var silent = false
    var dragging = false
    private var grab = 0f
    var deliveryGrab = 0f
    private var paidAt = -1.0
    private var newOrderSent = false
    private var pendingPanel = -1
    private var panelSwapAt = 0.0
    init { frame[BF.PRESENT] = 0f
    tween(BF.PRESENT,1f,.3) }
    private fun emit(type: String, value: Int = 0) { events += BeamEvent(type,value,silent) }
    private fun cancel(index: Int) { lanes.removeAll { it.index == index } }
    private fun lane(index: Int, value: Float, duration: Double, delay: Double, kind: Int,
        omega: Float = 0f, zeta: Float = 0f) {
        if (delay == 0.0) {
            cancel(index)
            if (frame[index] == value && abs(velocity[index]) < .00001f) return
        }
        lanes += BeamLane(index,value,time+delay,duration,kind,omega,zeta,frame[index])
    }
    private fun tween(index: Int, value: Float, duration: Double, delay: Double = 0.0, kind: Int = 1) {
        val fade = still && (index in BF.NODE_ON until BF.NODE_FLASH ||
            index in BF.ROW_ACTIVE until BF.ROW_PRESS ||
            index == BF.CTA_SPLIT || index == BF.CTA_DONE)
        lane(index,value,if (fade) .2 else duration,delay,if (fade) 0 else kind)
    }
    private fun spring(index: Int, value: Float, omega: Float, zeta: Float, delay: Double = 0.0) {
        lane(index,value,if (still) .2 else 1.5,delay,if (still) 0 else 3,omega,zeta)
    }
    private fun impulse(index: Int, value: Float) {
        cancel(index)
        frame[index] = value
        velocity[index] = 0f
    }
    private fun clearMajor() {
        lanes.removeAll { val n=it.index
            n < BF.FIELD_FLOAT && n != BF.PRESENT &&
                n !in BF.FOCUS_X..BF.FOCUS_A && n != BF.SEG_LEAD && n != BF.SEG_TRAIL
        }
        flags=0
        started=time
        origin=step
        pendingPanel=-1
        tween(BF.DRAG,0f,.12)
        tween(BF.TONE,0f,.2)
        for (i in 0..4) tween(BF.NODE_SHAKE+i,0f,.15)
    }
    private fun fadeFocus() {
        focusField=-1
        tween(BF.FOCUS_A,0f,.16)
        for (i in 0..6) tween(BF.FIELD_FLOAT+i,if(filled[i])1f else 0f,.18)
    }
    private fun departure(from: Int) {
        val done=from<furthest || mode=="advance" || mode=="pay"
        tween(BF.NODE_DONE+from,if(done)1f else 0f,.22)
        tween(BF.ROW_DONE+from,if(done)1f else 0f,.36)
        for (n in intArrayOf(BF.NODE_ACTIVE,BF.ROW_ACTIVE,BF.ROW_RISE,BF.ROW_SWEEP))
            tween(n+from,0f,.36)
        fadeFocus()
    }
    private fun panel(to: Int, arrive: Double, delay: Double = 0.0) {
        val transitioning=frame[BF.PANEL_MIX]<.999f && frame[BF.PANEL_TO]!=frame[BF.PANEL_FROM]
        if (!transitioning) {
            frame[BF.PANEL_FROM]=step.toFloat()
            frame[BF.PANEL_TO]=to.toFloat()
            frame[BF.PANEL_MIX]=0f
        } else { pendingPanel=to
        panelSwapAt=time+.12 }
        errorField=-1
        tween(BF.ERR,0f,.16)
        for (i in 0..6) tween(BF.FIELD_ERR+i,0f,.16)
        if (still) {
            tween(BF.PANEL_MIX,.4f,.12,delay,0)
            tween(BF.PANEL_H,beamPanelHeight(to),H,delay+.12,0)
            tween(BF.PANEL_MIX,1f,.18,max(delay+.12,arrive),0)
        } else {
            if (transitioning) {
                tween(BF.PANEL_MIX,if(frame[BF.PANEL_MIX]>=.4f).4f else .3f,.12,0.0,0)
                tween(BF.PANEL_MIX,.4f,max(.02,arrive-.12),.12,0)
            } else {
                tween(BF.PANEL_MIX,.3f,.16,delay+.06,0)
                tween(BF.PANEL_MIX,.4f,max(.02,arrive-delay-.22),delay+.22,0)
            }
            tween(BF.PANEL_H,beamPanelHeight(to),.5,delay+.3,2)
            tween(BF.PANEL_MIX,1f,.38,arrive,0)
        }
    }
    private fun ignition(to: Int, strength: Float, haptic: Int) {
        if (!still) {
            impulse(BF.NODE_FLASH+to,strength)
            impulse(BF.BURST,if(mode=="pay")2.6f else if(strength>=1f)2f else 1.5f)
            impulse(BF.HEAD_GLOW,if(mode=="advance")1.6f else 1.2f)
        }
        spring(BF.NODE_ON+to,1f,26f,.55f)
        tween(BF.NODE_ACTIVE+to,if(mode=="pay")0f else 1f,if(still).2 else .18)
        tween(BF.NODE_DONE+to,0f,.22)
        tween(BF.ROW_DONE+to,0f,.22)
        tween(BF.ROW_ACTIVE+to,if(mode=="pay")0f else 1f,.24)
        spring(BF.ROW_RISE+to,1f,22f,.5f)
        tween(BF.ROW_SWEEP+to,if(mode=="pay")0f else 1f,.42)
        emit("haptic",haptic)
    }
    fun advance() {
        if(step>=3)return
        clearMajor()
        mode="advance"
        target=step+1
        arrival=.54
        furthest=max(furthest,target)
        departure(step)
        if(!still) { tween(BF.HEAD,target.toFloat(),.42,.12,2)
        tween(BF.HEAD_GLOW,1f,.08,.12) }
        else tween(BF.HEAD,target.toFloat(),H,.12,0)
        panel(target,.54)
        spring(BF.CTA_SPLIT,1f,18f,.8f,.3)
    }
    fun goTo(to: Int) {
        if(step==4||mode=="pay"||paidAt>=0||to<0||to>furthest)return
        clearMajor()
        mode="go"
        target=to
        arrival=min(.64,.32+.11*abs(to-frame[BF.HEAD]))
        departure(step)
        tween(BF.HEAD,to.toFloat(),if(still)H else arrival,if(still).12 else 0.0,2)
        if(!still)tween(BF.HEAD_GLOW,1f,.08)
        panel(to,arrival)
        spring(BF.CTA_SPLIT,if(to>0)1f else 0f,18f,.8f)
    }
    fun focus(field: Int) {
        focusField=field
        for(i in 0..6)tween(BF.FIELD_FLOAT+i,if(filled[i]||i==field)1f else 0f,.18)
        if(field<0){tween(BF.FOCUS_A,0f,.16)
        return}
        val r=beamFieldRects[field]
        for(i in 0..3) {
            val value=if(i==3)56f else r[i+1].toFloat()
            if(frame[BF.FOCUS_A]<.001f)impulse(BF.FOCUS_X+i,value)
            else spring(BF.FOCUS_X+i,value,26f,.82f)
        }
        tween(BF.FOCUS_A,1f,.14)
    }
    fun setFilled(field:Int,value:Boolean) {
        filled[field]=value
        tween(BF.FIELD_FLOAT+field,if(value||focusField==field)1f else 0f,.18)
    }
    fun reject(field:Int) {
        clearMajor()
        mode="reject"
        target=step
        errorField=field
        arrival=.22
        if(frame[BF.PANEL_MIX]<1f) {
            pendingPanel=step
            panelSwapAt=time+.12
            tween(BF.PANEL_MIX,.4f,.12,0.0,0)
            tween(BF.PANEL_MIX,1f,.18,.12,0)
        }
        if(!still){tween(BF.HEAD,step+.4f,.22)
        tween(BF.HEAD_GLOW,1f,.08)}
        tween(BF.TONE,1f,.11,.11)
    }
    fun clearError() {
        errorField=-1
        for(i in 0..6)tween(BF.FIELD_ERR+i,0f,.16)
        tween(BF.ERR,0f,.3,0.0,2)
        tween(BF.PANEL_H,beamPanelHeight(step),.3,0.0,2)
    }
    fun pay() {
        if(step!=3||mode=="pay"||paidAt>=0)return
        clearMajor()
        mode="pay"
        target=4
        arrival=1.2
        furthest=4
        departure(3)
        tween(BF.CTA_SPLIT,0f,.38,0.0,2)
        if(!still) {
            tween(BF.CTA_LENS,1f,.38,0.0,2)
            tween(BF.HEAD,4f,.9,.3,2)
            tween(BF.HEAD_GLOW,1.3f,.9,.3)
            tween(BF.BURST,1.4f,.9,.3)
        } else tween(BF.HEAD,4f,H,1.2,0)
    }
    fun restart() {
        clearMajor()
        mode="restart"
        target=0
        arrival=.9
        paidAt=-1.0
        newOrderSent=false
        furthest=0
        express=false
        errorField=-1
        fadeFocus()
        tween(BF.WARM,0f,.5)
        tween(BF.CTA_DONE,0f,.3)
        tween(BF.CTA_LENS,0f,.3)
        tween(BF.CTA_SPLIT,0f,.5)
        tween(BF.SURGE,0f,.2)
        tween(BF.SEG_LEAD,0f,.3)
        tween(BF.SEG_TRAIL,0f,.3)
        tween(BF.ERR,0f,.16)
        for(i in 0..6){filled[i]=false
        tween(BF.FIELD_FLOAT+i,0f,.18)
        tween(BF.FIELD_ERR+i,0f,.16)}
        for(i in 0..4)for(n in intArrayOf(BF.NODE_ACTIVE,BF.ROW_ACTIVE,BF.ROW_RISE,BF.ROW_SWEEP))tween(n+i,0f,.2)
        if(!still)tween(BF.HEAD_GLOW,.8f,.08)
        tween(BF.HEAD,0f,if(still)H else .9,if(still).42 else 0.0,2)
        if(frame[BF.PANEL_MIX]<1f){pendingPanel=0
        panelSwapAt=time+.12
        tween(BF.PANEL_MIX,.4f,.12,0.0,0)}
        else {frame[BF.PANEL_FROM]=step.toFloat()
        frame[BF.PANEL_TO]=0f
        frame[BF.PANEL_MIX]=0f}
        tween(BF.PANEL_MIX,1f,.5,.3,0)
        tween(BF.PANEL_H,beamPanelHeight(0),if(still)H else .5,if(still).5 else .3,2)
    }
    fun press(index:Int,down:Boolean){presses[index]=if(down)1f else 0f}
    fun setDelivery(value:Boolean) {
        if(express!=value)emit("haptic",0)
        express=value
        spring(BF.SEG_LEAD,if(value)1f else 0f,30f,.82f)
        spring(BF.SEG_TRAIL,if(value)1f else 0f,18f,.74f)
    }
    private fun rubber(value:Float,lo:Float,hi:Float,band:Float):Float = when {
        value<lo -> (lo-band*(1-exp(((value-lo)/band).toDouble()))).toFloat()
        value>hi -> (hi+band*(1-exp(((hi-value)/band).toDouble()))).toFloat()
        else -> value
    }
    fun beginDrag() {
        if(step==4||mode=="pay"||paidAt>=0||furthest==0)return
        clearMajor()
        mode="drag"
        dragging=true
        grab=frame[BF.HEAD]
        tween(BF.DRAG,1f,.12)
        tween(BF.NODE_ACTIVE+step,.3f,.12)
        if(!still)tween(BF.HEAD_GLOW,1f,.08)
    }
    private fun pingCrossings(before:Float,after:Float,flash:Float) {
        if(still)return
        for(i in 0..4)if(before<i&&after>=i||before>i&&after<=i){
            impulse(BF.NODE_FLASH+i,flash)
            if(dragging)emit("haptic",0)
        }
    }
    fun drag(displacement:Float,vel:Float) {
        if(!dragging)return
        val before=frame[BF.HEAD]
        frame[BF.HEAD]=rubber(grab+displacement,0f,furthest.toFloat(),.18f)
        velocity[BF.HEAD]=vel
        frame[BF.HEAD_VEL]=vel
        pingCrossings(before,frame[BF.HEAD],.4f)
    }
    fun endDrag(vel:Float) {
        if(!dragging)return
        dragging=false
        mode="release"
        started=time
        flags=0
        target=(frame[BF.HEAD]+.12f*vel).roundToInt().coerceIn(0,furthest)
        velocity[BF.HEAD]=vel
        arrival=.4
        if(still)tween(BF.HEAD,target.toFloat(),H,0.0,0)
        else spring(BF.HEAD,target.toFloat(),22f,.72f)
        tween(BF.DRAG,0f,.12)
        if(target!=step){departure(step)
        panel(target,arrival)}
        spring(BF.CTA_SPLIT,if(target>0)1f else 0f,18f,.8f)
    }
    fun dragDelivery(position:Float){
        cancel(BF.SEG_LEAD)
        frame[BF.SEG_LEAD]=rubber(position,0f,1f,.08f)
        spring(BF.SEG_TRAIL,frame[BF.SEG_LEAD],18f,.74f)
    }
    fun endDelivery(vel:Float):Boolean {
        val value=frame[BF.SEG_LEAD]+.1f*vel>.5f
        setDelivery(value)
        return value
    }
    private fun bezier(t:Double,x1:Double,y1:Double,x2:Double,y2:Double):Double {
        if(t<=0)return 0.0
        if(t>=1)return 1.0
        var lo=0.0
        var hi=1.0
        var u=t
        repeat(18) {
            val q=1-u
            val x=3*q*q*u*x1+3*q*u*u*x2+u*u*u
            if(x<t)lo=u else hi=u
            u=(lo+hi)/2
        }
        val q=1-u
        return 3*q*q*u*y1+3*q*u*u*y2+u*u*u
    }
    private fun ease(t:Double,kind:Int):Double {
        val u=t.coerceIn(0.0,1.0)
        return when(kind){1->bezier(u,.23,1.0,.32,1.0)
        2->bezier(u,.77,0.0,.175,1.0)
        else->u}
    }
    private fun integrateLanes() {
        var i=0
        while(i<lanes.size){
            val l=lanes[i]
            if(time+1e-9<l.at){i++
            continue}
            if(!l.started){
                l.started=true
                l.from=frame[l.index]
                var j=i-1
                while(j>=0){if(lanes[j].index==l.index){lanes.removeAt(j)
                i--}
                j--}
            }
            val previous=frame[l.index]
            val elapsed=max(0.0,time-l.at)
            val finished:Boolean
            if(l.kind==3){
                val disp=previous-l.target
                val acceleration=-l.omega*l.omega*disp-2*l.zeta*l.omega*velocity[l.index]
                velocity[l.index]+=(acceleration*H).toFloat()
                frame[l.index]+=(velocity[l.index]*H).toFloat()
                finished=abs(frame[l.index]-l.target)<.00001f&&abs(velocity[l.index])<.0001f||elapsed>=l.duration
            }else{
                frame[l.index]=(l.from+(l.target-l.from)*ease(elapsed/l.duration,l.kind)).toFloat()
                velocity[l.index]=((frame[l.index]-previous)/H).toFloat()
                finished=elapsed+1e-9>=l.duration
            }
            if(finished){frame[l.index]=l.target
            velocity[l.index]=0f
            lanes.removeAt(i)}
            else i++
        }
    }
    private fun settle(){
        step=target
        if(errorField<0)emit("settled",step)
        if(mode=="restart"){emit("restarted")
        mode="idle"}else mode="settling"
        val rest=beamRest(step,furthest,express,filled,errorField,focusField)
        for(i in 0..4)for(n in intArrayOf(BF.NODE_DONE,BF.ROW_DONE,BF.NODE_ON,BF.NODE_ACTIVE,BF.ROW_ACTIVE,BF.ROW_RISE,BF.ROW_SWEEP))
            tween(n+i,rest[n+i],.2)
    }
    private fun advanceMode(){
        val t=time-started
        if(mode=="advance"||mode=="go"||mode=="release"){
            if(flags and 1==0 && t+1e-9>=arrival){
                flags=flags or 1
                ignition(target,if(mode=="advance")1f else .55f,if(target>origin)2 else 1)
            }
            if(flags and 1!=0 && frame[BF.PANEL_MIX]>=1f && t>=arrival+(if(still).18 else .38)-1e-9)settle()
        }else if(mode=="reject"){
            if(flags and 1==0 && t>=.22-1e-9){
                flags=flags or 1
                if(!still){impulse(BF.BURST,1.5f)
                spring(BF.HEAD,step.toFloat(),24f,.6f)}
                tween(BF.FIELD_ERR+errorField,1f,.16)
                tween(BF.ERR,1f,.3)
                tween(BF.PANEL_H,beamPanelHeight(step,1f),.3)
                focus(errorField)
                emit("haptic",3)
                tween(BF.TONE,0f,.5,if(still)1.0 else .68)
            }
            if(!still&&t in .48.. .84){val q=t-.48
            frame[BF.NODE_SHAKE+step]=(7*sin(2*PI*q/.12)*(1-q/.36)).toFloat()}
            if(t>=.84)frame[BF.NODE_SHAKE+step]=0f
            if(t>=1.75){mode="settling"
            settle()}
        }else if(mode=="pay"){
            if(flags and 1==0&&t>=1.2-1e-9){
                flags=flags or 1
                paidAt=time
                ignition(4,1.2f,4)
                emit("paid",4)
                tween(BF.WARM,1f,.5)
                tween(BF.CTA_DONE,1f,.24)
                if(!still){tween(BF.SURGE,1f,.52,0.0,0)
                spring(BF.CTA_LENS,0f,16f,.62f,.12)}
                tween(BF.NODE_DONE+4,1f,.22,.1)
                tween(BF.ROW_DONE+4,1f,.22,.1)
            }
            if(flags and 2==0&&t>=1.5-1e-9){
                flags=flags or 2
                frame[BF.PANEL_FROM]=3f
                frame[BF.PANEL_TO]=4f
                frame[BF.PANEL_MIX]=0f
                tween(BF.PANEL_MIX,1f,.5,0.0,0)
                tween(BF.PANEL_H,beamPanelHeight(4),if(still)H else .5,if(still).15 else 0.0,2)
            }
            if(!still&&t in 1.2..1.75)for(i in 0..3){
                val bit=1 shl (i+2)
                if(flags and bit==0&&frame[BF.SURGE]>=(4-i)/4f-1e-9f){
                    flags=flags or bit
                    impulse(BF.NODE_FLASH+i,.8f)
                }
            }
            if(t>=2.0-1e-9&&frame[BF.PANEL_MIX]==1f)settle()
        }else if(mode=="restart"){
            if(flags and 1==0&&t>=.3&&frame[BF.PANEL_MIX]>=.3f){flags=flags or 1
            emit("cleared")}
            for(i in 1..4){val bit=1 shl (i+1)
                if(flags and bit==0&&frame[BF.HEAD]<=i){flags=flags or bit
                tween(BF.NODE_ON+i,0f,.2)
                tween(BF.NODE_DONE+i,0f,.2)
                tween(BF.ROW_DONE+i,0f,.2)}
            }
            if(flags and 2==0&&t>=.9-1e-9){
                flags=flags or 2
                ignition(0,.5f,1)
                tween(BF.NODE_DONE,0f,.2)
                tween(BF.ROW_DONE,0f,.2)
                for(n in intArrayOf(BF.NODE_FLASH,BF.HEAD_GLOW,BF.BURST,BF.ROW_RISE,BF.ROW_SWEEP,BF.NODE_ON))
                    tween(n,if(n==BF.BURST||n==BF.ROW_RISE||n==BF.ROW_SWEEP||n==BF.NODE_ON)1f else 0f,.26)
            }
            if(t>=1.17-1e-9)settle()
        }
    }
    private fun substep(){
        time+=H
        val previousHead=frame[BF.HEAD]
        integrateLanes()
        if(pendingPanel>=0&&time>=panelSwapAt-1e-9){frame[BF.PANEL_FROM]=step.toFloat()
        frame[BF.PANEL_TO]=pendingPanel.toFloat()
        pendingPanel=-1}
        advanceMode()
        if((mode=="go"||mode=="release")&&flags and 1==0)pingCrossings(previousHead,frame[BF.HEAD],.6f)
        val glowOwned=lanes.any{it.index==BF.HEAD_GLOW}
        val burstOwned=lanes.any{it.index==BF.BURST}
        val travelling=mode in arrayOf("advance","go","release","pay","restart")&&flags and (if(mode=="restart")2 else 1)==0
        if(!glowOwned&&!travelling&&!dragging)frame[BF.HEAD_GLOW]*=exp(-H/.16).toFloat()
        if(!burstOwned)frame[BF.BURST]=1f+(frame[BF.BURST]-1f)*exp(-H/(if(paidAt>=0).5 else if(mode=="reject").3 else .35)).toFloat()
        for(i in 0..4){
            if(lanes.none{it.index==BF.NODE_FLASH+i})frame[BF.NODE_FLASH+i]*=exp(-H/.22).toFloat()
            if(mode=="drag"||mode=="go"||mode=="release"){
                val ember=if(i<furthest).4f else 0f
                val on=ember+(1-ember)*((frame[BF.HEAD]-i+.18f)/.18f).coerceIn(0f,1f)
                if(flags and 1==0||dragging)frame[BF.NODE_ON+i]=on
            }
        }
        val pressIndices=intArrayOf(BF.CTA_PRESS,BF.BACK_PRESS,BF.SEG_PRESS,*IntArray(5){BF.NODE_PRESS+it},*IntArray(5){BF.ROW_PRESS+it})
        for(n in pressIndices){
            val p=presses[n]
            frame[n]+=(p-frame[n])*(1-exp(-H/(if(p>0).045 else .11))).toFloat()
            if(abs(p-frame[n])<1e-5)frame[n]=p
        }
        frame[BF.CTA_SPLIT]=frame[BF.CTA_SPLIT].coerceIn(0f,1f)
        frame[BF.HEAD_VEL]=velocity[BF.HEAD]
        if(frame[BF.CTA_LENS]>0&&frame[BF.CTA_DONE]<.99999f)frame[BF.CTA_SPIN]+=(H*PI*4).toFloat()
        if(paidAt>=0&&!newOrderSent&&time-paidAt>=2.6){newOrderSent=true
        emit("newOrder")}
        if(still){
            frame[BF.HEAD_GLOW]=0f
            frame[BF.BURST]=1f
            frame[BF.SURGE]=0f
            frame[BF.CTA_LENS]=0f
            frame[BF.HEAD_VEL]=0f
            for(i in 0..4){frame[BF.NODE_FLASH+i]=0f
            frame[BF.NODE_SHAKE+i]=0f}
        }
        if(mode=="idle"||mode=="settling"){
            if(frame[BF.PANEL_MIX]==1f){frame[BF.PANEL_FROM]=step.toFloat()
            frame[BF.PANEL_TO]=step.toFloat()}
            if(frame[BF.FOCUS_A]==0f)for(i in BF.FOCUS_X..BF.FOCUS_H)frame[i]=0f
            if(abs(frame[BF.CTA_LENS])<1e-5)frame[BF.CTA_SPIN]=0f
            if(step==4&&frame[BF.SURGE]==1f)frame[BF.SURGE]=0f
            if(lanes.isEmpty()){
                val rest=beamRest(step,furthest,express,filled,errorField,focusField)
                for(i in 0 until BF.SIZE)if(abs(frame[i]-rest[i])<.001f){frame[i]=rest[i]
                velocity[i]=0f}
                mode="idle"
            }
        }
    }
    fun step(dt:Double){
        remainder+=dt.coerceIn(0.0,1.0/30.0)
        while(remainder+1e-10>=H){remainder-=H
        substep()}
    }
}
