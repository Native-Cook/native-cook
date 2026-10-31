package com.ngocdevv.nativecook.blocks.cards.glassorbshelf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*

internal class GlassOrbShelfDriver {
    var clock by mutableDoubleStateOf(0.0)
    var offset by mutableDoubleStateOf(0.0)
    var spin=0.0
    var pop=0.0
    var lean=0.0
    var manual=false
    var dragging=false
    var reduced=false
    var autoPlay=false
    var onReducedSelection: (() -> Unit)? = null
    private var previousOffset=0.0
    private var autoStart=0.0
    private var lastTouch = -100.0
    private var startOffset=0.0
    private var moveStart=0.0
    private var moveFrom=0.0
    private var moveTo=0.0
    private var moveVelocity=0.0
    private var moveDuration=0.42
    private var moveDamping=0.82
    private var homeFrom=0.0
    private var resuming=false
    private var tapTime = -100.0
    private var spinFrom=0.0
    fun frame(dt: Double): Boolean {
        val old=previousOffset
        clock+=dt
        if (!manual && autoPlay && !reduced) {
            val t=(clock-autoStart)%GlassOrbShelfMotion.LOOP
            offset=GlassOrbShelfMotion.offsetAt(t); spin=t/GlassOrbShelfMotion.LOOP*PI*2
        } else {
            if (!reduced) spin+=dt*0.35
            if (!dragging) {
                offset=if (reduced) moveTo else GlassOrbShelfMotion.spring(clock-moveStart,moveFrom,moveTo,moveVelocity,moveDuration,moveDamping)
                if (manual && autoPlay && !reduced && clock-lastTouch>2) {
                    if (!resuming) { homeFrom=offset; resuming=true }
                    offset=homeFrom*(1-GlassOrbShelfMotion.bezier(GlassOrbShelfMotion.seg(clock,lastTouch+2,lastTouch+2.9),0.77,0.0,0.175,1.0))
                    if (abs(offset)<0.0005 && clock-lastTouch>3) { manual=false; autoStart=clock; resuming=false }
                }
            }
            if (tapTime > -99 && !reduced) {
                val d=clock-tapTime
                if (d<=0.9) spin=spinFrom+PI*0.6*GlassOrbShelfMotion.bezier(GlassOrbShelfMotion.seg(clock,tapTime,tapTime+0.9),0.23,1.0,0.32,1.0)
                pop=if (d<0.09) (d/0.09).coerceAtLeast(0.0).let { x -> if (x<0.5) 2*x*x else 1-(-2*x+2).pow(2)/2 } else GlassOrbShelfMotion.spring(d-0.09,1.0,0.0,duration=0.7,damping=0.3)
            }
        }
        val aim=(-(offset-old)/max(dt,0.001)*0.16).coerceIn(-0.5,0.5)
        lean=if (reduced) 0.0 else lean+(aim-lean)*min(1.0,dt*9)
        previousOffset=offset
        return manual && GlassOrbShelfMotion.rounded(old)!=GlassOrbShelfMotion.rounded(offset)
    }
    private fun takeOver() {
        manual=true; lastTouch=clock; resuming=false; moveFrom=offset; moveTo=offset; moveStart=clock
    }
    fun begin() { takeOver(); dragging=true; startOffset=offset }
    fun drag(translation: Double,step: Float) { lastTouch=clock; offset=GlassOrbShelfMotion.rubber(startOffset-translation/step) }
    fun animate(next: Double,velocity: Double=0.0,duration: Double=0.42,damping: Double=0.82) {
        moveFrom=offset; moveTo=next; moveStart=clock; moveVelocity=velocity; moveDuration=duration; moveDamping=damping
        if (reduced) {
            if (GlassOrbShelfMotion.rounded(offset)!=GlassOrbShelfMotion.rounded(next)) onReducedSelection?.invoke()
            offset=next
        }
    }
    fun release(velocityX: Double,step: Float) {
        dragging=false; lastTouch=clock
        val v=-velocityX/step
        animate(GlassOrbShelfMotion.rounded(offset+v*0.18).coerceIn(0.0,3.0),v)
    }
    fun tap(x: Float,y: Float,l: GlassOrbShelfLayout): Boolean {
        dragging=false; lastTouch=clock
        val dx=x-l.orbX; val dy=y-l.orbY
        if (dx*dx+dy*dy<l.orbR*l.orbR*1.2) { tapTime=clock; spinFrom=spin; return true }
        val cur=GlassOrbShelfMotion.rounded(offset)
        if (y>=l.cardTop) {
            if (x>l.cardLeft+l.cardW && cur<3) animate(cur+1,duration=0.48,damping=0.85)
            else if (x<l.cardLeft && cur>0) animate(cur-1,duration=0.48,damping=0.85)
        }
        return false
    }
    fun adjust(direction: Int) { takeOver(); animate((GlassOrbShelfMotion.rounded(offset)+direction).coerceIn(0.0,3.0)) }
}
