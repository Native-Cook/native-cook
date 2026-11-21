package com.ngocdevv.nativecook.blocks.media.islandprintcamera

import android.view.HapticFeedbackConstants
import android.view.View
import kotlinx.coroutines.channels.Channel
import kotlin.math.*

/** Single main-thread owner of the clock, gesture state and interruptible Reanimated-compatible springs. */
internal class IslandPrintCameraEngine(autoPlay: Boolean, reduced: Boolean) {
    var autoPlay = autoPlay
    var reduced = reduced
    var auto = autoPlay && !reduced
    var time = 0.0
    var now = 0.0
    var sequence = -1.0
    var touching = false
    var lastTouch = 0.0
    var flashOn = false
    var view: View? = null
    var cue: (String)->Unit = {}
    val wake = Channel<Unit>(Channel.CONFLATED)
    var pin: Double? = null
    private val dragX = Spring(); private val dragY = Spring()
    private var grab = 0; private var dragStartX = 0.0; private var dragStartY = 0.0; private var lastY = 0.0
    private var downX = 0.0; private var downY = 0.0; private var lastDX = 0.0; private var lastDY = 0.0
    private val progress = Spring()
    private val depth = Spring()
    private val focus = Spring()
    private var startP = 0.0
    private var panStarted = false
    private var tapPossible = false
    private var gestureActive = false
    private var tapStartedAt = 0.0

    fun requestFrame() { wake.trySend(Unit) }

    fun configure(play: Boolean, reduce: Boolean) {
        autoPlay = play
        reduced = reduce
        auto = play && !reduce
        if (reduce) {
            progress.finish()
            depth.finish()
            focus.finish()
        }
        requestFrame()
    }

    fun frame(): IslandPrintCameraFrame {
        if (auto) return IslandPrintCameraMotion.frameAt(time)
        if (sequence >= 0) return IslandPrintCameraMotion.frameAt(3.5 + sequence).copy(dragX=dragX.value,dragY=dragY.value)
        return IslandPrintCameraMotion.liveFrameAt(progress.value, depth.value, focus.value)
    }

    fun swayTime() = if (auto) time else now

    fun advance(raw: Double) {
        val dt = min(raw, .05)
        now += dt
        pin?.let { time=it; auto=true; return }
        if (gestureActive && tapPossible && now - tapStartedAt > 8) cancelTap()
        progress.step(min(raw, .064))
        depth.step(min(raw, .064))
        focus.step(min(raw, .064)); dragX.step(min(raw,.064)); dragY.step(min(raw,.064))
        if (auto) {
            time = (time + dt) % IslandPrintCameraMotion.LOOP
            return
        }
        if (sequence >= 0) {
            val old=sequence
            if(!(grab==2 && sequence>=5.6))sequence += dt
            if(old<1.35 && sequence>=1.35)cue("motor")
            if(old<3.41 && sequence>=3.41) { cue("land"); buzz(HapticFeedbackConstants.VIRTUAL_KEY) }
            if (sequence > 6.5) {
                sequence = -1.0
                progress.set(0.0); dragX.set(0.0); dragY.set(0.0)
                lastTouch = now
            }
            return
        }
        if (!reduced && autoPlay && !touching && now - lastTouch > 2) {
            if (progress.value > .004) {
                progress.animate(0.0, 22.1, 157.9, reduced = reduced)
                lastTouch = now - 1.4
            } else {
                time = 0.0
                auto = true
            }
        }
    }

    // RN advances `now` even behind the closed lens, so opening later retains its exact sway phase.
    fun needsFrames() = true

    fun down(x: Double, y: Double) {
        pin?.let { time=it }; pin=null; downX=x; downY=y; lastDX=0.0; lastDY=0.0
        if (auto) {
            val f = IslandPrintCameraMotion.frameAt(time)
            if (time > 3.5 && time < 10) sequence = time - 3.5 else progress.set(f.p)
            auto = false
        }
        touching = true
        lastTouch = now
        tapPossible = true
        gestureActive = true
        tapStartedAt = now
        panStarted = false
        val open = progress.value > .9 && sequence < 0
        if (open && hit(x, y, 201.0, 362.0, 47.0)) {
            depth.animate(.45, 30.5, 322.3, reduced = reduced)
            focus.animate(1.0, 22.1, 157.9, reduced = reduced)
            buzz(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        requestFrame()
    }

    /** Motion uses canvas points; gesture activation thresholds retain physical dp. */
    fun move(dx: Double, dy: Double, physicalScale: Double=1.0) {
        lastDX=dx; lastDY=dy
        if (hypot(dx, dy)*physicalScale > 12 && tapPossible) cancelTap()
        if (!panStarted && abs(dy)*physicalScale > 6) {
            panStarted = true
            if (sequence < 0) {
                startP = progress.value
                buzz(HapticFeedbackConstants.KEYBOARD_TAP)
            } else {
                val x=downX+dx; val y=downY+dy; val f=IslandPrintCameraMotion.frameAt(3.5+sequence)
                val onPrint=x>88+dragX.value && x<314+dragX.value && y>f.printTop-10+dragY.value && y<f.printTop+257+dragY.value
                if(onPrint && sequence>=1.35 && sequence<3.15)grab=1
                else if(onPrint && sequence>=3.41 && sequence<5.7) { grab=2; dragStartX=dragX.value; dragStartY=dragY.value; dragX.set(dragStartX); dragY.set(dragStartY) }
                lastY=0.0; if(grab>0)buzz(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
        if(panStarted && sequence>=0) {
            if(grab==1) { val delta=dy-lastY; lastY=dy; if(delta>0)sequence=min(sequence+.55*delta/137,3.149) }
            else if(grab==2) { dragX.set(dragStartX+dx); dragY.set(dragStartY+if(dy>0)dy else dy/(1-dy/80)) }
        }
        if (panStarted && sequence < 0) {
            var p = startP + dy / 346
            if (p > 1) {
                val over = (p - 1) * 346
                p = 1 + over / (1 + over / 120) / 346
            }
            progress.set(max(p, 0.0))
        }
        requestFrame()
    }

    fun up(x: Double, y: Double, velocityY: Double, durationMs: Long, cancelled: Boolean, velocityX: Double=0.0) {
        if ((cancelled || durationMs > 8000) && tapPossible) cancelTap()
        if (panStarted && sequence < 0) {
            val v = if(cancelled)0.0 else velocityY / 346
            val target = if ((if(cancelled)startP else progress.value + v * .12) >= .5) 1.0 else 0.0
            progress.animate(target, 22.1, 157.9, v, reduced)
            if(!cancelled)buzz(if (target == 1.0) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY)
        }
        if(panStarted && sequence>=0 && grab==2) {
            if(!cancelled && (velocityY>700 || lastDY>140)) { sequence=max(sequence,5.7); buzz(HapticFeedbackConstants.VIRTUAL_KEY) }
            else { dragX.animate(0.0,19.0,195.0,if(cancelled)0.0 else velocityX,reduced); dragY.animate(0.0,19.0,195.0,if(cancelled)0.0 else velocityY,reduced) }
        }
        val open = progress.value > .9 && sequence < 0
        val ok = tapPossible && durationMs <= 8000 && !cancelled
        if (ok && open && hit(x, y, 201.0, 362.0, 53.0)) {
            sequence = 0.0; cue("shutter")
            depth.set(0.0)
            focus.set(0.0)
            buzz(HapticFeedbackConstants.CONTEXT_CLICK)
        } else if (ok && open && hit(x, y, 73.0, 362.0, 32.0)) {
            flashOn = !flashOn
            buzz(HapticFeedbackConstants.CLOCK_TICK)
        } else if (ok && sequence < 0 && y > 83 + max(progress.value, 0.0) * 371 &&
            y < 143 + max(progress.value, 0.0) * 371 && abs(x - 201) < 60) {
            progress.animate(if (progress.value > .5) 0.0 else 1.0, 22.1, 157.9, reduced = reduced)
            buzz(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        if (tapPossible) finalizeTap()
        grab=0; gestureActive = false
        touching = false
        lastTouch = now
        requestFrame()
    }

    private fun cancelTap() {
        tapPossible = false
        finalizeTap()
    }

    private fun finalizeTap() {
        if (depth.value > 0 && sequence < 0) {
            depth.animate(0.0, 19.0, 195.0, reduced = reduced)
            focus.animate(0.0, 22.1, 157.9, reduced = reduced)
        }
        touching = gestureActive
        lastTouch = now
        requestFrame()
    }

    private fun buzz(kind: Int) { view?.performHapticFeedback(kind) }
    private fun hit(x: Double, y: Double, cx: Double, cy: Double, r: Double) = (x - cx).pow(2) + (y - cy).pow(2) <= r * r

    /** RN 4's withSpring defaults to mass=4, energyThreshold=6e-9, overshootClamping=false. */
    private class Spring {
        var value = 0.0
        var running = false
        private var velocity = 0.0
        private var target = 0.0
        private var damping = 22.1
        private var stiffness = 157.9
        private var initialEnergy = 0.0
        fun set(v: Double) { value = v; velocity = 0.0; running = false }
        fun finish() { if (running) set(target) }
        fun animate(to: Double, d: Double, k: Double, v: Double = 0.0, reduced: Boolean) {
            if (reduced) { set(to); return }
            if (running && target == to && damping == d && stiffness == k) return
            velocity = (if (running) velocity else 0.0) + v
            if ((to > value && velocity < 0) || (to < value && velocity > 0)) velocity = 0.0
            target = to
            damping = d
            stiffness = k
            initialEnergy = .5 * k * (value - to).pow(2) + .5 * 4 * v * v
            running = initialEnergy > 0
            if (!running) set(to)
        }
        fun step(dt: Double) {
            if (!running) return
            val x = value - target
            val w0 = sqrt(stiffness / 4)
            val z = damping / (2 * sqrt(stiffness * 4))
            val e = exp(-z * w0 * dt)
            if (z < 1) {
                val wd = w0 * sqrt(1 - z * z)
                val b = velocity + z * w0 * x
                val fragment = e * (sin(wd * dt) * b / wd + x * cos(wd * dt))
                value = target + fragment
                velocity = -z * w0 * fragment + e * (cos(wd * dt) * b - wd * x * sin(wd * dt))
            } else {
                val envelope = exp(-w0 * dt)
                val b = velocity + w0 * x
                value = target + envelope * (x + b * dt)
                velocity = envelope * (-w0 * (x + b * dt) + b)
            }
            val energy = .5 * stiffness * (target - value).pow(2) + .5 * 4 * velocity * velocity
            if (initialEnergy == 0.0 || energy / initialEnergy <= 6e-9) set(target)
        }
    }
}
