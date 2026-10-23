package com.ngocdevv.nativecook.blocks.pickers.owlalarmruler

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import com.ngocdevv.nativecook.blocks.pickers.owlalarmruler.OwlAlarmRulerMotion as Motion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.floor

/** Main-thread owner. Only drawing and semantics observe the published snapshots. */
internal class OwlAlarmRulerRuntime(
    autoPlay: Boolean,
    still: Boolean,
    private val selectionHaptic: () -> Unit,
    private val successHaptic: () -> Unit,
) {
    val frame = mutableStateOf(Motion.REST_FRAME)
    val labelTick = mutableIntStateOf(0)
    private var engine = Motion.createEngine(Motion.clockNow(), autoPlay, still)
    private var active = false
    private var lastTick = 0
    private var lastHaptic = -1.0
    private var labelAt = -1.0
    private val events = Channel<Unit>(Channel.CONFLATED)

    fun configure(autoPlay: Boolean, still: Boolean) {
        val now = Motion.clockNow()
        engine = Motion.setAutoPlay(engine, autoPlay, still, now)
        publish(now)
    }

    fun foreground(isActive: Boolean) {
        active = isActive
        if (isActive) {
            val now = Motion.clockNow()
            engine = engine.copy(
                autoStart = now,
                lastClock = now,
                dragging = false,
                glideAt = if (engine.mode == Motion.AUTO) -1.0 else engine.glideAt,
            )
            sample(now)
        }
        events.trySend(Unit)
    }

    suspend fun runClock() {
        while (currentCoroutineContext().isActive) {
            if (!active) {
                events.receive()
                continue
            }
            if (sample(Motion.clockNow())) withFrameNanos { } else events.receive()
        }
    }

    fun rulerDown() = change { state, now -> Motion.rulerDown(state, now) }

    fun dragStart() = change { state, now -> Motion.dragStart(state, now) }

    fun dragMove(dx: Double, vx: Double) = change { state, now ->
        Motion.dragMove(state, now, dx, vx, OwlAlarmRulerTheme.layout.ruler.spacing)
    }

    fun release(vx: Double) = change { state, now ->
        Motion.release(state, now, vx, OwlAlarmRulerTheme.layout.ruler.spacing)
    }

    fun adjust(direction: Double) = change { state, now -> Motion.stepBy(state, now, direction) }

    fun pressIn() = change { state, now -> Motion.pressIn(state, now) }

    fun pressOut(commit: Boolean) {
        val now = Motion.clockNow()
        engine = Motion.pressOut(engine, now, false)
        if (commit) {
            successHaptic()
            val busyStartle = now - engine.startleAt < Motion.STARTLE.end
            engine = engine.copy(
                startleAt = if (engine.still || busyStartle) engine.startleAt else now,
                lastTouch = now,
            )
        }
        publish(now)
    }

    private fun change(update: (Motion.Engine, Double) -> Motion.Engine) {
        val now = Motion.clockNow()
        engine = update(engine, now)
        publish(now)
    }

    private fun publish(now: Double) {
        sample(now)
        events.trySend(Unit)
    }

    private fun sample(now: Double): Boolean {
        val result = Motion.step(engine, now)
        engine = result.e
        frame.value = result.f
        val tick = floor(result.f.pos + 0.5).toInt()
        if (tick != lastTick) {
            if (result.haptic && now - lastHaptic >= Motion.HAPTIC_GAP) {
                lastHaptic = now
                selectionHaptic()
            }
            lastTick = tick
        }
        if (tick != labelTick.intValue && now - labelAt >= Motion.LABEL_GAP) {
            labelTick.intValue = tick
            labelAt = now
        }
        val state = result.e
        val visual = result.f
        return state.mode != Motion.LIVE || state.dragging || state.glideAt >= 0.0 ||
            visual.startle >= 0.0 || visual.press > 0.001 || abs(state.fx) > 0.01 ||
            abs(state.fv) > 0.1 || (state.attention > 0.005 && state.attention < 0.995) ||
            abs(visual.flip - state.flipTo) > 0.05 || visual.blink > 0.0 ||
            (state.autoPlay && !state.still)
    }
}
