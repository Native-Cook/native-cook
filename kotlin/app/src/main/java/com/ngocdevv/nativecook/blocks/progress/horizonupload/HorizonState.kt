// One composition frame clock owns transfer, completion, interaction and appearance cues.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*

internal class HorizonState(private val haptics: HorizonHaptics, autoPlay: Boolean = true) {
    private enum class Step { Rest, Transfer, Hero, Reset, Drain }
    var phase by mutableStateOf(HorizonPhase.Idle); private set
    var label by mutableStateOf(HorizonPhase.Idle); private set
    var percent by mutableIntStateOf(0); private set
    var frame by mutableIntStateOf(0); private set
    var active = true; private set
    var reduced = false; private set
    var panelAvailable by mutableStateOf(true); private set
    var closeAvailable by mutableStateOf(true); private set
    var retryAvailable by mutableStateOf(false); private set
    var automatic = autoPlay; private set
    var now = 0.0; private set
    private var lastNanos = 0L
    private var elapsed = 0.0
    private var step = Step.Rest
    private var flipped = false
    private var rising = false
    private var hovering = false
    private var hoverTime = 0.0
    private var resetStartsUpload = false
    private var resetDuration = 300.0
    private var lastChunk = 0
    private var grabX = 0f
    private var grabY = 0f
    private var dragging = false
    private var retryWas = false
    val clock = HorizonValue()
    val power = HorizonValue(.2f)
    val crest = HorizonValue()
    val done = HorizonValue()
    val press = HorizonValue()
    val closePress = HorizonValue()
    val shiftX = HorizonValue()
    val shiftY = HorizonValue()
    val liftBase = HorizonValue()
    private val stage = HorizonValue()
    val swap = HorizonValue()
    val paused = HorizonValue()
    val figure = HorizonValue(.4f)
    val shine = HorizonValue(-1f)
    val calm = HorizonValue()
    val labels = Array(5) { HorizonValue(if (it == 0) 1f else 0f) }
    val icons = Array(5) { HorizonValue(if (it == 0) 1f else 0f) }
    val iconScale = Array(5) { HorizonValue(if (it == 0) 1f else .85f) }
    private val values = arrayOf(clock, power, crest, done, press, closePress, shiftX, shiftY,
        liftBase, stage, swap, paused, figure, shine, calm) + labels + icons + iconScale
    var rotation = 0f; private set
    val progress get() = HorizonMath.progress(clock.value)
    val pulse get() = if (phase == HorizonPhase.Uploading && step == Step.Transfer) HorizonMath.pulse(clock.value) else 0f
    val lift get() = liftBase.value + if (hovering && !reduced) .06f * sin(2 * PI * hoverTime / 4200).toFloat() else 0f
    val scale get() = if (reduced) 1 - .008f * press.value else HorizonMath.cardScale(crest.value, press.value)
    val retry get() = phase == HorizonPhase.Stopped || (phase == HorizonPhase.Done && (flipped || step == Step.Reset))
    val closeEnabled get() = phase != HorizonPhase.Done || retry
    val panelEnabled get() = !(phase == HorizonPhase.Done && step == Step.Hero && crest.value < 1)

    fun frame(nanos: Long) {
        if (lastNanos == 0L || !active) { lastNanos = nanos; return }
        val dt = max(0.0, (nanos - lastNanos) / 1e6); lastNanos = nanos
        now += dt
        for (value in values) value.step(now)
        if (automatic) {
            val before = elapsed; elapsed += dt
            val a = before % HorizonMath.LOOP; val b = elapsed % HorizonMath.LOOP
            val wrapped = floor(before / HorizonMath.LOOP) != floor(elapsed / HorizonMath.LOOP)
            if (wrapped) idle()
            if ((a < 700 || wrapped) && b >= 700) {
                startTransfer()
                clock.set(((b - 700) / HorizonMath.TRANSFER).toFloat().coerceIn(0f, 1f))
                clock.timing(1f, max(0.0, 5900 - b), now, HorizonValue.LINEAR)
            }
            if ((a < 9100 || wrapped) && b >= 9100) beginReset(false, 600.0)
        }
        if (step == Step.Transfer && phase == HorizonPhase.Uploading && clock.value >= 1) complete()
        if (step == Step.Reset && phase != HorizonPhase.Paused && stage.value >= 1) {
            liftBase.set(0f); clock.set(0f); crest.set(0f); done.set(0f)
            if (resetStartsUpload) startTransfer() else { step = Step.Rest; changePhase(HorizonPhase.Idle) }
        }
        if (step == Step.Hero) {
            if (!rising && !reduced && crest.value >= .22f) {
                rising = true; liftBase.spring(1f, 620.0, .6, now)
            }
            if (!flipped && crest.value >= .4f) {
                flipped = true
                if (reduced) done.timing(1f, 200.0, now) else done.spring(1f, 520.0, .75, now)
                haptics.play(HorizonHaptics.SUCCESS)
            }
            if (!reduced && rising && crest.value >= .7f) {
                if (!hovering) { hovering = true; hoverTime = 0.0 } else hoverTime = (hoverTime + dt) % 4200
            }
        }
        val chunk = HorizonMath.chunk(clock.value)
        if (chunk > lastChunk && chunk > 0 && !automatic && phase == HorizonPhase.Uploading && step == Step.Transfer)
            haptics.play(HorizonHaptics.SOFT)
        lastChunk = chunk
        updateAppearance()
        if (label == HorizonPhase.Uploading && !reduced) rotation = (rotation + (dt / 900 * 360).toFloat()) % 360
        if (shine.value == 1f && label == HorizonPhase.Done && !calm.running && calm.value != 1f) calm.timing(1f, 220.0, now)
        // GPU signals are Float here; absorb their ULP at exact chunk endpoints (53/90/96%).
        percent = floor(progress * 100f + 1e-5f).toInt().coerceIn(0, 100)
        frame++
    }
    private fun changePhase(next: HorizonPhase) {
        phase = next
        val target = when (next) { HorizonPhase.Idle -> .2f; HorizonPhase.Paused -> .4f; HorizonPhase.Stopped -> .1f; else -> 1f }
        power.timing(target, 400.0, now)
    }
    private fun parkLift() {
        val current = lift
        hovering = false; hoverTime = 0.0; rising = false; liftBase.set(current)
    }
    private fun startTransfer() {
        parkLift(); liftBase.set(0f); step = Step.Transfer; changePhase(HorizonPhase.Uploading)
        clock.timing(1f, (1 - clock.value) * HorizonMath.TRANSFER, now, HorizonValue.LINEAR)
        haptics.play(HorizonHaptics.LIGHT)
    }
    private fun complete() {
        parkLift(); clock.set(1f); step = Step.Hero; changePhase(HorizonPhase.Done)
        liftBase.timing(if (reduced) 1f else -.08f, if (reduced) 200.0 else 168.0, now)
        if (reduced) crest.set(1f) else crest.timing(1f, 1400.0, now, HorizonValue.LINEAR)
    }
    private fun beginReset(startAfter: Boolean, duration: Double) {
        parkLift(); flipped = false; resetStartsUpload = startAfter; resetDuration = duration; step = Step.Reset; stage.set(0f)
        changePhase(if (startAfter) HorizonPhase.Uploading else HorizonPhase.Done)
        clock.timing(0f, duration, now); crest.timing(0f, min(300.0, duration), now)
        done.timing(0f, min(300.0, duration), now)
        liftBase.timing(0f, if (reduced) min(200.0, duration) else duration, now)
        stage.timing(1f, duration, now, HorizonValue.LINEAR)
    }
    private fun idle() {
        parkLift(); liftBase.set(0f); clock.set(0f); crest.set(0f); done.set(0f)
        stage.set(0f); flipped = false; step = Step.Rest; changePhase(HorizonPhase.Idle)
    }
    fun touch() { automatic = false }
    fun toggle() {
        touch()
        if (!active || !panelEnabled) return
        when (phase) {
            HorizonPhase.Uploading -> {
                clock.cancel(); stage.cancel(); crest.cancel(); done.cancel(); parkLift()
                liftBase.timing(0f, if (reduced) 200.0 else 300.0, now)
                changePhase(HorizonPhase.Paused); haptics.play(HorizonHaptics.LIGHT)
            }
            HorizonPhase.Paused -> {
                changePhase(HorizonPhase.Uploading)
                if (step == Step.Transfer) clock.timing(1f, (1 - clock.value) * 5200.0, now, HorizonValue.LINEAR)
                if (step == Step.Reset) {
                    val remaining = 300.0 * (1 - stage.value)
                    clock.timing(0f, remaining, now); crest.timing(0f, remaining, now); done.timing(0f, remaining, now)
                    stage.timing(1f, remaining, now, HorizonValue.LINEAR)
                }
                haptics.play(HorizonHaptics.LIGHT)
            }
            HorizonPhase.Idle -> { flipped = false; startTransfer() }
            else -> beginReset(true, 300.0)
        }
        updateAppearance(); frame++
    }
    fun close() {
        touch()
        if (!active || !closeEnabled) return
        if (retry) beginReset(true, 300.0)
        else if (phase == HorizonPhase.Uploading || phase == HorizonPhase.Paused) {
            parkLift(); step = Step.Drain; flipped = false; stage.set(0f)
            clock.timing(0f, 420.0, now); stage.timing(1f, 420.0, now, HorizonValue.LINEAR)
            crest.timing(0f, 300.0, now); done.timing(0f, 300.0, now)
            liftBase.timing(0f, if (reduced) 200.0 else 300.0, now)
            changePhase(HorizonPhase.Stopped); haptics.play(HorizonHaptics.MEDIUM)
        }
        updateAppearance(); frame++
    }
    private fun updateAppearance() {
        panelAvailable = panelEnabled; closeAvailable = closeEnabled; retryAvailable = retry
        val next = if (phase == HorizonPhase.Done && !flipped && step != Step.Reset) HorizonPhase.Uploading else phase
        if (next != label) {
            label = next
            for (i in labels.indices) {
                val visible = i == next.ordinal
                labels[i].timing(if (visible) 1f else 0f, if (reduced) 120.0 else 220.0, now)
                icons[i].timing(if (visible) 1f else 0f, 220.0, now, HorizonValue.LINEAR)
                if (reduced) iconScale[i].set(1f)
                else if (visible && next == HorizonPhase.Done) { iconScale[i].set(.6f); iconScale[i].spring(1f, 420.0, .65, now) }
                else iconScale[i].timing(if (visible) 1f else .85f, 220.0, now, HorizonValue.LINEAR)
            }
            paused.timing(if (next == HorizonPhase.Paused) 1f else 0f, 220.0, now)
            figure.timing(if (next == HorizonPhase.Idle || next == HorizonPhase.Stopped) .4f else 1f, 220.0, now)
            if (next == HorizonPhase.Done) {
                shine.set(if (reduced) 1f else 0f)
                if (!reduced) shine.timing(1f, 300.0, now, HorizonValue.LINEAR)
            } else { shine.set(-1f); calm.timing(0f, 220.0, now) }
        }
        if (retryWas != retry) { retryWas = retry; swap.timing(if (retry) 1f else 0f, 260.0, now, HorizonValue.LINEAR) }
    }
    fun begin() { touch(); shiftX.cancel(); shiftY.cancel(); grabX = HorizonMath.pull(shiftX.value); grabY = HorizonMath.pull(shiftY.value); dragging = false }
    fun drag(x: Float, y: Float) {
        if (!active) return
        if (!dragging) {
            dragging = true; press.timing(0f, 90.0, now); closePress.timing(0f, 90.0, now)
            haptics.play(HorizonHaptics.LIGHT)
        }
        shiftX.set(HorizonMath.rubber(grabX + x)); shiftY.set(HorizonMath.rubber(grabY + y)); frame++
    }
    fun release(x: Float, y: Float, vx: Float, vy: Float) {
        if (reduced) { shiftX.timing(0f, 200.0, now); shiftY.timing(0f, 200.0, now) }
        else {
            shiftX.spring(0f, 560.0, .62, now, HorizonMath.velocity(grabX + x, vx), 110f)
            shiftY.spring(0f, 560.0, .62, now, HorizonMath.velocity(grabY + y, vy), 110f)
        }
        dragging = false
    }
    fun hold(close: Boolean) {
        if (close && closeEnabled) closePress.timing(1f, 90.0, now, HorizonValue.OUT)
        if (!close && panelEnabled) press.timing(1f, 90.0, now, HorizonValue.OUT)
    }
    fun releasePress() {
        if (reduced) { press.timing(0f, 120.0, now); closePress.timing(0f, 120.0, now) }
        else { press.spring(0f, 300.0, .8, now); closePress.spring(0f, 300.0, .7, now) }
    }
    fun setReduced(value: Boolean) {
        if (reduced == value) return
        reduced = value
        if (value) {
            hovering = false
            if (step == Step.Hero) { crest.set(1f); liftBase.timing(1f, 200.0, now) }
            shiftX.timing(0f, 200.0, now); shiftY.timing(0f, 200.0, now)
            for (scale in iconScale) scale.set(1f)
        }
        frame++
    }
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value; lastNanos = 0L
        if (!value) {
            // Match freeze()/resumeClocks(): transfer time excludes the background,
            // while duration springs resume from the frozen pose with zero velocity.
            clock.cancel(); crest.cancel(); done.cancel(); stage.cancel(); power.cancel(); liftBase.cancel()
            press.set(0f); closePress.set(0f); shiftX.set(0f); shiftY.set(0f); dragging = false
            return
        }
        changePhase(phase)
        if (phase == HorizonPhase.Paused) {
            liftBase.timing(0f, if (reduced) 200.0 else 300.0, now)
            return
        }
        when (step) {
            Step.Transfer -> clock.timing(1f, (1 - clock.value) * 5200.0, now, HorizonValue.LINEAR)
            Step.Hero -> {
                if (reduced) liftBase.timing(1f, 200.0, now)
                else if (rising) liftBase.spring(1f, 620.0, .6, now)
                else liftBase.timing(-.08f, max(0.0, (.12 - crest.value) * 1400), now)
                if (reduced) crest.set(1f) else crest.timing(1f, (1 - crest.value) * 1400.0, now, HorizonValue.LINEAR)
                if (flipped) {
                    if (reduced) done.timing(1f, 200.0, now) else done.spring(1f, 520.0, .75, now)
                }
            }
            Step.Drain, Step.Reset -> {
                val remaining = (if (step == Step.Drain) 420.0 else resetDuration) * (1 - stage.value)
                liftBase.timing(0f, if (reduced) min(200.0, remaining) else if (step == Step.Drain) min(300.0, remaining) else remaining, now)
                clock.timing(0f, remaining, now); stage.timing(1f, remaining, now, HorizonValue.LINEAR)
                if (step == Step.Reset) {
                    crest.timing(0f, min(300.0, remaining), now); done.timing(0f, min(300.0, remaining), now)
                }
            }
            Step.Rest -> Unit
        }
    }
}
