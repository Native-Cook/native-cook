package com.ngocdevv.nativecook.blocks.navigation.claycategorytabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*

internal class ClayCategoryTabsDriver(private val auto: Boolean, private val reduced: Boolean,
    private val buzz: (Boolean) -> Unit) {
    private data class Tween(val start: Double, val from: Double, val to: Double, val duration: Double,
        val out: Boolean = false, val spring: ClayCategoryTabsSpring? = null) {
        fun sample(clock: Double): Pair<Double, Boolean> {
            spring?.let { return it.sample(clock-start) }
            val u = ClayCategoryTabsMotion.seg(clock,start,start+duration)
            return Pair(from+(to-from)*(if (out) ClayCategoryTabsMotion.touchOut(u) else ClayCategoryTabsMotion.touchEase(u)),u >= 1)
        }
    }
    var frame by mutableStateOf(ClayCategoryTabsMotion.Frame())
        private set
    var time by mutableDoubleStateOf(0.0)
        private set
    var selected by mutableStateOf(0)
        private set
    private var clock = 0.0; private var autoStart = 0.0; private var lastTouch = -100.0
    private var manual = !auto || reduced; private var resumeAsked = false
    private var moving = false; private var dragging = false
    private var animation: Tween? = null
    private var landedAt: Double? = null; private var bellAt: Double? = null
    private var squashSpring: ClayCategoryTabsSpring? = null; private var wobbleSpring: ClayCategoryTabsSpring? = null
    private var squashFrom = 0.0; private var knobFrom = 0.0; private var wobbleFrom = 0.0
    private var fadeFrame = ClayCategoryTabsMotion.Frame(); private var takeoverAt = -100.0
    private var startX = 0f; private var startY = 0f; private var dragStart = 0.0; private var failed = false
    fun step(dt0: Double) {
        val dt = dt0.coerceIn(0.0,0.05); clock += dt
        if (!manual && auto && !reduced) {
            time = (clock-autoStart)%ClayCategoryTabsMotion.LOOP
            frame = ClayCategoryTabsMotion.frameAt(time); return
        }
        var f = frame
        if (!reduced) time += dt
        animation?.let {
            val (v, done) = it.sample(clock); f = f.copy(r=v)
            if (done) { animation = null; moving = false; land(it.to.toInt(),f) }
        }
        val landing = landedAt
        if (landing != null && !reduced) {
            val u = clock-landing
            f = f.copy(squash=if (u < 0.09) squashFrom+(0.55-squashFrom)*ClayCategoryTabsMotion.touchOut(u/0.09) else squashSpring?.sample(u-0.09)?.first ?: 0.0)
        }
        val bell = bellAt
        if (bell != null && !reduced) {
                val u = clock-bell
                val knob = if (u < 0.12) knobFrom+(1-knobFrom)*ClayCategoryTabsMotion.touchOut(u/0.12) else 1-ClayCategoryTabsMotion.touchOut(((u-0.12)/0.18).coerceIn(0.0,1.0))
                val q = (u/0.07).coerceIn(0.0,1.0)
                val ease = if (q < 0.5) 2*q*q else 1-(-2*q+2).pow(2)/2
                val wobble = if (u < 0.07) wobbleFrom+(0.14-wobbleFrom)*ease else wobbleSpring?.sample(u-0.07)?.first ?: 0.0
                f = f.copy(knob=knob,wobble=wobble)
        }
        if (landing == null) {
            val u = clock-takeoverAt
            f = f.copy(squash=fadeFrame.squash*(1-ClayCategoryTabsMotion.defaultEase((u/0.2).coerceIn(0.0,1.0))),
                knob=fadeFrame.knob*(1-ClayCategoryTabsMotion.defaultEase((u/0.15).coerceIn(0.0,1.0))),wobble=fadeFrame.wobble*(1-ClayCategoryTabsMotion.defaultEase((u/0.2).coerceIn(0.0,1.0))))
        }
        val fade = 1-ClayCategoryTabsMotion.defaultEase(((clock-takeoverAt)/0.15).coerceIn(0.0,1.0))
        f = f.copy(finger=fadeFrame.finger*fade,press=fadeFrame.press*fade); frame = f
        if (moving || dragging) lastTouch = clock
        if (!auto || reduced || dragging || clock-lastTouch < 2) return
        if (!resumeAsked) { resumeAsked = true; if (f.r > 0.001) go(0) }
        else if (!moving && f.r < 0.001 && abs(f.squash) < 0.002 && abs(f.wobble) < 0.002) {
            resumeAsked = false; manual = false; autoStart = clock; time = 0.0
            frame = ClayCategoryTabsMotion.Frame(); landedAt = null; bellAt = null
        }
    }
    private fun land(k: Int, current: ClayCategoryTabsMotion.Frame) {
        selected = k; buzz(true)
        if (reduced) return
        landedAt = clock
        squashFrom = current.squash
        if (k == 2) {
            bellAt = clock; knobFrom = current.knob; wobbleFrom = current.wobble
            wobbleSpring = ClayCategoryTabsSpring(0.14,0.0,duration=1.0,damping=0.2)
        }
        squashSpring = ClayCategoryTabsSpring(0.55,0.0,duration=0.65,damping=0.35)
    }
    fun takeOver() {
        lastTouch = clock; resumeAsked = false
        if (manual) return
        manual = true; fadeFrame = frame; takeoverAt = clock; landedAt = null; bellAt = null
    }
    fun tap(k: Int) {
        takeOver()
        if (abs(frame.r-k) < 0.001 && !moving) return
        go(k)
    }
    private fun go(k: Int) {
        moving = true
        animation = Tween(clock,frame.r,k.toDouble(),if (reduced) 0.26 else 0.7+0.4*abs(k-frame.r))
    }
    fun down(x: Float,y: Float) { takeOver(); startX = x; startY = y; failed = false; dragging = false }
    fun move(x: Float,y: Float,layout: ClayCategoryTabsTheme.Layout): Boolean {
        lastTouch = clock
        if (startY < layout.pagerTop || failed) return false
        val dx = x-startX; val dy = y-startY
        if (!dragging) {
            if (abs(dy) > 12) { failed = true; return false }
            if (abs(dx) <= 10) return false
            dragging = true; moving = false; animation = null; dragStart = frame.r
        }
        var v = dragStart-dx/layout.w
        if (v < 0) v = -0.12*(1-exp(v/0.12))
        if (v > 2) v = 2+0.12*(1-exp(-(v-2)/0.12))
        val before = floor(frame.r.coerceIn(0.0,2.0)+0.5).toInt()
        frame = frame.copy(r=v)
        if (floor(v.coerceIn(0.0,2.0)+0.5).toInt() != before) buzz(false)
        return true
    }
    fun up(x: Float,y: Float,velocity: Float,layout: ClayCategoryTabsTheme.Layout,cancelled: Boolean) {
        lastTouch = clock
        if (dragging) {
            dragging = false; moving = true
            val v = -velocity/layout.w.toDouble()
            val k = floor(frame.r+0.22*v+0.5).coerceIn(0.0,2.0)
            animation = Tween(clock,frame.r,k,0.26,true,if (reduced) null else ClayCategoryTabsSpring(frame.r,k,v,0.65,0.9))
        } else if (!cancelled && !failed && abs(x-startX) <= 10 && abs(y-startY) <= 12 && startY >= layout.stageTop && startY < layout.pagerTop) {
            val k = layout.xs.indices.minByOrNull { abs(layout.xs[it]-x) } ?: 0
            if (abs(layout.xs[k]-x) <= layout.slot/2) tap(k)
        }
    }
}
