package com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet

import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.loop
import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.progress
import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.spring
import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.clamp
import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.bezier
import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.mix
import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.stepEase
import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.releaseEase
import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.bounceAt
import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.sheetEase
import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.contentFade
import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.openingStage
import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.rollLabels
import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.CLOSE_DURATION
import com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet.IconMorphTimeline.STEP_DURATION
import kotlin.math.*

/** Single owner for autoplay, direct manipulation and velocity hand-over. */
internal class IconMorphMotion(val autoPlay: Boolean) {
    enum class Action { Idle, Pan, Step, Settle, Open, Close, IconHold, IconRelease, ButtonHold, PuckHold, Return }
    private data class Touch(
        val action: Action, val snapshot: IconMorphFrame, val started: Double,
        var touched: Double, var target: Double, var duration: Double = 1.15,
        var speed: Double = 0.0, var amplitude: Double = 1.0, var axis: Int = 0,
        var x: Double = 0.0, var y: Double = 0.0, var lastX: Double = 0.0,
        var lastTime: Double = 0.0, var lean: Double = 0.0, var icon: Boolean = false,
    )
    var frame = IconMorphFrame()
        private set
    var times = DoubleArray(4)
        private set
    var now = 0.0
        private set
    var reduced = false
    var layout = IconMorphLayout(402.0, 874.0, 62.0, 34.0)
    var onHaptic: (Boolean) -> Unit = {}
    private var automatic = autoPlay
    private var epoch = 0.0
    private var touching = false
    private var touch = state(Action.Idle)
    private fun state(action: Action) = Touch(action, frame.copy(), now, now, round(frame.position), lastTime = now, lean = frame.lean)
    private fun catch(action: Action) {
        touch = state(action)
        automatic = false
    }
    fun tick(delta: Double) {
        now += delta
        val previous = frame
        frame = if (automatic) loop(now - epoch, reduced) else manual()
        if (!automatic) {
            val held = touching || touch.action in setOf(Action.Pan, Action.ButtonHold, Action.IconHold, Action.PuckHold)
            if (touch.action == Action.Return && now - touch.started >= 0.6) {
                automatic = true
                epoch = now
                frame = IconMorphFrame()
            } else if (autoPlay && !held && touch.action != Action.Return && now - touch.touched >= 3.5) {
                touch = state(Action.Return)
            }
        }
        times = IconMorphPose.advance(times, frame.position, previous.position, delta, frame.running, previous.running, reduced)
    }
    private fun manual(): IconMorphFrame {
        val s = touch
        val t = max(0.0, now - s.started)
        val a = s.snapshot
        val f = a.copy()
        f.markerOpacity = a.markerOpacity * (1 - progress(t, 0.0, 0.2))
        f.markerPress = a.markerPress * (1 - progress(t, 0.0, 0.12))
        f.buttonPress = spring(t, a.buttonPress, 0.0)
        f.puckPress = spring(t, a.puckPress, 0.0)
        f.press = clamp(spring(t, a.press, 0.0))
        f.bounce = if (reduced) 0.0 else a.bounce * (1 - bezier(t / 0.2))
        f.lean = if (reduced) 0.0 else spring(t, a.lean, 0.0, 260.0, 26.0)
        f.edge = spring(t, a.edge, 0.0, 260.0, 26.0)
        when (s.action) {
            Action.Pan -> {
                if (s.axis == 1) {
                    val raw = a.position - s.x / 280
                    f.position = clamp(raw, 0.0, 3.0)
                    f.edge = (raw - f.position) * 280 * 0.2
                    f.lean = if (reduced) 0.0 else s.lean
                    if (!reduced && f.edge != 0.0) f.bounce = clamp(f.edge / 56, -1.0, 1.0) * 0.2
                } else if (s.axis == 2) f.drag = a.drag + (if (s.y > 0) s.y else s.y * 0.15)
                if (s.icon && s.axis == 0) f.press = mix(a.press, 1.0, bezier(t / 0.11))
            }
            Action.Step, Action.Settle -> {
                val p = progress(t, 0.0, s.duration)
                val e = if (s.action == Action.Step) stepEase(p) else releaseEase(p, s.target - a.position, s.speed, s.duration)
                f.position = mix(a.position, s.target, e)
                f.drag = spring(t, a.drag, 0.0, 260.0, 26.0, if (s.axis == 2) s.speed else 0.0)
                if (t >= s.duration) f.bounce = if (reduced) 0.0 else bounceAt(t - s.duration, s.amplitude)
            }
            Action.Open, Action.Close -> {
                val opening = s.action == Action.Open
                val target = if (opening) 1.0 else 0.0
                val e = if (reduced) progress(t, 0.0, 0.2) else if (s.speed > 0)
                    releaseEase(progress(t, 0.0, s.duration), 1.0, s.speed, s.duration) else sheetEase(progress(t, 0.0, s.duration))
                f.open = mix(a.open, target, e)
                f.drag = mix(a.drag, 0.0, e)
                f.contents = if (reduced) mix(a.contents, target, e) else contentFade(f.open)
                f.stage = if (reduced) f.open else if (opening) mix(a.stage, 1.0, openingStage(t)) else a.stage
                rollLabels(f, a, progress(t, 0.0, 0.26), opening)
                f.running = if (opening && t >= (if (reduced) 0.2 else 0.6)) 1.0 else 0.0
                if (!opening && e == 1.0) {
                    f.position = 0.0
                    f.stage = 0.0
                }
            }
            Action.ButtonHold -> f.buttonPress = mix(a.buttonPress, 1.0, bezier(t / 0.12))
            Action.PuckHold -> f.puckPress = mix(a.puckPress, 1.0, bezier(t / 0.12))
            Action.IconHold -> f.press = mix(a.press, 1.0, bezier(t / 0.11))
            Action.IconRelease -> f.bounce = if (reduced) 0.0 else bounceAt(t, 0.6)
            Action.Return -> {
                val p = bezier(t / 0.6)
                f.position = mix(a.position, 0.0, p)
                f.open = mix(a.open, 1.0, p)
                f.contents = if (reduced) mix(a.contents, 1.0, p) else contentFade(f.open)
                f.stage = if (reduced) f.open else mix(a.stage, 1.0, openingStage(t))
                rollLabels(f, a, progress(t, 0.0, 0.6), true)
                f.drag = mix(a.drag, 0.0, p)
                f.edge = mix(a.edge, 0.0, p)
                f.running = if (t >= 0.6) 1.0 else 0.0
            }
            Action.Idle -> Unit
        }
        return f
    }
    fun ambientDown() {
        touching = true
        if (automatic || touch.action == Action.Return) catch(Action.Idle)
    }
    fun ambientUp() {
        touching = false
        touch.touched = now
    }
    fun commitSheet(opening: Boolean, velocity: Double = 0.0) {
        val s = state(if (opening) Action.Open else Action.Close)
        val distance = max(1.0, layout.sheetHeight(frame.position) * frame.open - frame.drag)
        s.duration = if (reduced) 0.2 else if (opening) 0.56 else clamp(distance / max(velocity, distance / CLOSE_DURATION), 0.22, CLOSE_DURATION)
        s.speed = if (!opening && velocity > 0) velocity / distance else 0.0
        touch = s
        automatic = false
        onHaptic(!opening)
    }
    fun panBegin(x: Double, y: Double) {
        val travel = layout.travel(frame, reduced)
        if (frame.open <= 0 || y < layout.height - layout.sheetHeight(frame.position) + 56 + travel || y >= layout.buttonTop) return
        catch(Action.Pan)
        val cy = layout.stageTop + layout.stageHeight / 2 + travel + (if (reduced) 0.0 else 24 * (1 - frame.stage))
        touch.icon = abs(x - layout.width / 2) <= 90 && abs(y - cy) <= 90
    }
    fun panChange(x: Double, y: Double) {
        val s = touch
        if (s.action != Action.Pan) return
        if (s.axis == 0 && hypot(x, y) >= 8) s.axis = if (abs(x) > abs(y)) 1 else 2
        val dt = max(1.0 / 240, now - s.lastTime)
        val velocity = (x - s.lastX) / dt
        s.x = x
        s.y = y
        s.lastX = x
        s.lastTime = now
        s.lean = mix(s.lean, clamp(velocity / 1000, -1.0, 1.0), 1 - exp(-dt / 0.065))
        s.touched = now
    }
    fun panFinalize(vx: Double, vy: Double, cancelled: Boolean = false) {
        val s = touch
        if (s.action != Action.Pan) return
        frame = manual()
        if (!cancelled && s.icon && s.axis == 0) {
            touch = state(Action.IconRelease)
            onHaptic(false)
            return
        }
        if (!cancelled && s.axis == 2 && (frame.drag > 120 || vy > 900)) {
            commitSheet(false, max(0.0, vy))
            return
        }
        val next = state(Action.Settle)
        next.axis = s.axis
        if (cancelled) {
            next.duration = if (reduced) 0.45 else 0.4
            next.amplitude = 0.0
        } else if (s.axis == 1) {
            val base = round(s.snapshot.position)
            val direction = if (abs(vx) > 500) -sign(vx) else -sign(s.x)
            val commits = abs(s.x / 280) > 0.35 || abs(vx) > 500
            next.target = clamp(base + (if (commits) direction else 0.0), 0.0, 3.0)
            next.speed = -vx / 280
            next.duration = if (reduced) 0.45 else clamp(abs(next.target - frame.position) * 280 / max(abs(vx), 420.0), 0.28, 0.52)
            next.amplitude = clamp(abs(vx) / 1200, 0.25, 1.0)
            if (next.target != base) onHaptic(false)
        } else {
            next.speed = vy
            next.duration = if (reduced) 0.45 else 0.52
            next.amplitude = 0.0
        }
        touch = next
    }
    fun buttonDown() = catch(Action.ButtonHold)
    fun buttonUp(inside: Boolean) {
        if (touch.action != Action.ButtonHold) return
        frame = manual()
        if (!inside) {
            touch = state(Action.Idle)
        } else if (frame.open < 0.5) commitSheet(true)
        else if (frame.position >= 2.5) commitSheet(false)
        else {
            touch = state(Action.Step).apply {
                target = min(3.0, round(frame.position) + 1)
                duration = if (reduced) 0.45 else STEP_DURATION
            }
            onHaptic(false)
        }
    }
    fun puckDown(puck: Int) {
        catch(Action.PuckHold)
        touch.snapshot.puck = puck
    }
    fun puckUp(inside: Boolean, puck: Int) {
        if (touch.action != Action.PuckHold) return
        frame = manual()
        if (inside && puck == 2) commitSheet(true)
        else if (inside && frame.open > 0) commitSheet(false)
        else touch = state(Action.Idle)
    }
    fun activateIcon() {
        catch(Action.IconHold)
        touch = state(Action.IconRelease)
        onHaptic(false)
    }
}
