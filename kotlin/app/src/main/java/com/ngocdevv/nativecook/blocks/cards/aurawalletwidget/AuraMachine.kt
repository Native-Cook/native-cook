package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

// One conductor owns the score. The lip can accept a new touch after unlock;
// the independent receipt clock finishes the previous landing without rewinding.
internal class AuraMachine(
    val drive: AuraDrive,
    private val feedback: (Int) -> Unit,
    private val announce: (Int, Int) -> Unit,
    private val readTime: () -> Double = { System.nanoTime() / 1e6 },
) {
    val wake = Channel<Unit>(Channel.CONFLATED)
    var reduced by mutableStateOf(false)
    var landed by mutableStateOf(false)
        private set
    var balanceCents by mutableLongStateOf(5200250L)
        private set
    var receipt by mutableStateOf(AuraReceipt())
        private set
    var previous by mutableStateOf<AuraReceipt?>(null)
        private set
    var sent by mutableStateOf<AuraReceipt?>(null)
        private set
    var touched = false
        private set
    private var sinking = false
    private var wasArmed = false
    private var pullOwner = -1
    private var warm = 0.0
    private var arrived = false
    private var priorFrame = 0.0
    private var now = readTime()
    private var weightedAmount = drive.amount
    private var routeActive = false
    private var previewActive = false

    val active: Boolean
        get() = drive.phase != AuraPhase.Rest
    val preview: Boolean
        get() = active && drive.amount > 0 && !(drive.phase == AuraPhase.Sending && landed)
    val displayedBalance: Long
        get() = balanceCents - if (preview) drive.amount * 100L else 0L
    val route: Boolean
        get() = active && drive.chosen >= 0 && !(drive.phase == AuraPhase.Sending && landed)
    val needsFrames: Boolean
        get() {
            if (!arrived) return true
            for (value in drive.values) if (value.running) return true
            return false
        }

    private fun changed() {
        updateViewTargets()
        wake.trySend(Unit)
    }

    private fun interaction() {
        now = readTime()
    }

    fun touch() {
        interaction()
        if (touched) return
        touched = true
        drive.nudge.set(-1.0)
    }

    fun cue() {
        interaction()
        if (reduced || touched || drive.entered.value >= 0 || drive.phase != AuraPhase.Rest) return
        drive.nudge.set(0.0)
        drive.nudge.timing(auraNudgeLength(), auraNudgeLength(), now, curve = AuraValue.linear)
        changed()
    }

    fun isLocked(): Boolean {
        if (drive.entered.value >= 0 || sinking) return true
        return drive.phase == AuraPhase.Sending && auraSendMoments(drive.fromY) { _, _, _, unlock, _, _ ->
            drive.clock.value < unlock
        }
    }

    private fun available(): Boolean {
        if (drive.entered.value >= 0 || sinking) return false
        if (drive.phase != AuraPhase.Sending) return drive.phase != AuraPhase.Pulling
        if (isLocked()) return false
        reset()
        return true
    }

    fun pick(index: Int) {
        touch()
        if (!available()) return
        if (drive.phase == AuraPhase.Armed && drive.chosen == index) dismiss() else arm(index)
        changed()
    }

    private fun arm(index: Int) {
        val oldChosen = drive.chosen
        if (oldChosen != index) {
            val oldFormed = drive.formed.value
            val oldLeaving = drive.leaving
            val oldUnformed = drive.unformed.value
            val nextFormed = if (index == oldLeaving) oldUnformed else 0.0
            // A rapid third choice preserves its current face transform while
            // the shader keeps the same two-drop history as the source.
            for (friend in 0 until AuraPalette.Lip.count) {
                auraLipPose(friend, oldChosen, oldFormed, oldLeaving, oldUnformed) { x, y, scale, opacity, _ ->
                    auraLipPose(friend, index, nextFormed, oldChosen, oldFormed) { nextX, nextY, nextScale, nextOpacity, _ ->
                        val offset = friend * 4
                        drive.offsets[offset].set(x + drive.offsets[offset].value - nextX)
                        drive.offsets[offset + 1].set(y + drive.offsets[offset + 1].value - nextY)
                        drive.offsets[offset + 2].set(scale + drive.offsets[offset + 2].value - nextScale)
                        drive.offsets[offset + 3].set(opacity + drive.offsets[offset + 3].value - nextOpacity)
                        for (component in 0..3) form(drive.offsets[offset + component], 0.0)
                    }
                }
            }
            drive.leaving = oldChosen
            drive.unformed.set(oldFormed)
            form(drive.unformed, 0.0)
            drive.chosen = index
            drive.formed.set(nextFormed)
        }
        form(drive.formed, 1.0)
        morph(1.0)
        drive.amount = AuraFlow.Pull.amountDefault.toInt()
        drive.phase = AuraPhase.Armed
        sent = null
    }

    fun dismiss() {
        interaction()
        if (drive.phase != AuraPhase.Armed || sinking) return
        drive.phase = AuraPhase.Rest
        form(drive.formed, 0.0)
        form(drive.unformed, 0.0)
        morph(0.0)
        changed()
    }

    fun beginPull(index: Int) {
        interaction()
        if (!available()) return
        wasArmed = drive.phase == AuraPhase.Armed
        arm(index)
        drive.phase = AuraPhase.Pulling
        pullOwner = index
        drive.amount = 0
        drive.pullX.set(0.0)
        drive.pullY.set(0.0)
        changed()
    }

    fun movePull(index: Int, dx: Double, dy: Double) {
        interaction()
        if (drive.phase != AuraPhase.Pulling || pullOwner != index) return
        val depth = auraRubber(max(dy, 0.0), auraPullLimit())
        drive.pullX.set(sign(dx) * auraRubber(abs(dx), AuraFlow.Pull.sideways))
        drive.pullY.set(depth)
        val next = auraAmountAt(depth).toInt()
        if (next != drive.amount) {
            drive.amount = next
            feedback(0)
        }
        changed()
    }

    fun endPull(index: Int, velocityX: Double, velocityY: Double, cancelled: Boolean) {
        interaction()
        if (drive.phase != AuraPhase.Pulling || pullOwner != index) return
        pullOwner = -1
        if (!cancelled && drive.amount > 0) {
            release()
        } else {
            form(drive.pullX, 0.0, velocityX)
            form(drive.pullY, 0.0, velocityY)
            drive.phase = AuraPhase.Armed
            if (wasArmed) drive.amount = AuraFlow.Pull.amountDefault.toInt() else dismiss()
        }
        changed()
    }

    fun send() {
        touch()
        if (!available()) return
        if (drive.phase == AuraPhase.Rest) {
            drive.nudge.set(0.0)
            drive.nudge.timing(auraNudgeLength(), auraNudgeLength(), now, curve = AuraValue.linear)
            feedback(1)
        } else {
            sinking = true
            drive.sinkClock.set(0.0)
            drive.pullY.set(0.0)
            drive.sinkClock.timing(AuraFlow.Send.sink, AuraFlow.Send.sink, now, curve = AuraValue.linear)
        }
        changed()
    }

    fun press(index: Int, down: Boolean, friend: Boolean = false) {
        interaction()
        val value = if (friend) drive.friendPress[index] else drive.press[index]
        if (down) value.timing(1.0, AuraPalette.Motion.pressIn, now)
        else value.spring(0.0, AuraPalette.Motion.releaseDuration, AuraPalette.Motion.releaseDampingRatio, now)
        changed()
    }

    fun clearSent(id: Int) {
        if (sent?.id == id) sent = null
    }

    private fun release() {
        drive.fromX = auraSlotX(drive.chosen) + drive.pullX.value
        drive.fromY = AuraPalette.Lip.cy + AuraFlow.Drop.sag * drive.formed.value + drive.pullY.value
        drive.pullX.set(0.0)
        drive.pullY.set(0.0)
        sinking = false
        landed = false
        drive.phase = AuraPhase.Sending
        drive.clock.set(0.0)
        auraSendMoments(drive.fromY) { _, _, _, _, _, end ->
            drive.clock.timing(end, end, now, curve = AuraValue.linear)
        }
    }

    private fun reset() {
        drive.phase = AuraPhase.Rest
        drive.chosen = -1
        drive.formed.set(0.0)
        drive.leaving = -1
        drive.unformed.set(0.0)
        drive.pullX.set(0.0)
        drive.pullY.set(0.0)
        drive.clock.set(0.0)
        landed = false
        sinking = false
        pullOwner = -1
    }

    fun frame(milliseconds: Double) {
        now = milliseconds
        if (!arrived) {
            val step = if (priorFrame == 0.0) 0.0 else now - priorFrame
            if (step > 0 && step < AuraFlow.Enter.smooth) warm += step
            if (warm >= AuraFlow.Enter.hold) {
                arrived = true
                drive.shown.timing(1.0, AuraFlow.Enter.reveal, now)
                if (reduced) drive.entered.set(-1.0)
                else drive.entered.timing(auraEnterLength(), auraEnterLength(), now, curve = AuraValue.linear)
            }
            priorFrame = now
        }
        for (value in drive.values) value.step(now)
        if (drive.entered.value >= auraEnterLength()) drive.entered.set(-1.0)
        if (sinking) {
            drive.pullY.set(auraSinkDepth(drive.sinkClock.value, auraDepthFor(drive.amount.toDouble())))
            if (drive.sinkClock.value >= AuraFlow.Send.sink) release()
        }
        if (drive.phase == AuraPhase.Sending) {
            auraSendMoments(drive.fromY) { _, _, impact, _, _, end ->
                if (drive.clock.value >= impact && !landed) commit(drive.clock.value - impact)
                if (drive.clock.value >= end) reset()
            }
        }
        val receiptDuration = if (reduced) AuraFlow.Views.receiptFade else AuraFlow.Send.rippleLife
        if (previous != null && drive.receiptClock.value >= receiptDuration) previous = null
        if (drive.nudge.value >= auraNudgeLength()) drive.nudge.set(-1.0)
        if (drive.phase == AuraPhase.Rest && drive.formed.value == 0.0 && drive.unformed.value == 0.0) {
            drive.chosen = -1
            drive.leaving = -1
        }
        updateViewTargets()
    }

    private fun commit(late: Double) {
        landed = true
        morph(0.0)
        form(drive.formed, 0.0)
        drive.receiptX = drive.fromX
        val duration = if (reduced) AuraFlow.Views.receiptFade else AuraFlow.Send.rippleLife
        drive.receiptClock.set(min(late, duration))
        drive.receiptClock.timing(duration, max(0.0, duration - late), now, curve = AuraValue.linear)
        previous = if (late < duration) receipt else null
        receipt = AuraReceipt(receipt.id + 1, drive.chosen, drive.amount * 100L)
        balanceCents -= drive.amount * 100L
        sent = receipt
        feedback(2)
        announce(drive.chosen, drive.amount)
    }

    private fun updateViewTargets() {
        if (weightedAmount != drive.amount) {
            weightedAmount = drive.amount
            drive.weight.spring(drive.amount / AuraFlow.Pull.amountMax,
                AuraFlow.Drop.weighDuration, AuraFlow.Drop.weighDampingRatio, now)
        }
        if (route != routeActive) {
            routeActive = route
            drive.identity.timing(if (route) 1.0 else 0.0, AuraFlow.Views.routeDuration, now)
        }
        if (preview != previewActive) {
            previewActive = preview
            drive.chip.timing(if (preview) 1.0 else 0.0, AuraFlow.Views.chipFade, now, curve = AuraValue.easeInOut)
        }
    }

    private fun form(value: AuraValue, target: Double, velocity: Double = 0.0) {
        value.spring(target, AuraFlow.Drop.formDuration, AuraFlow.Drop.formDampingRatio, now, initialVelocity = velocity)
    }

    private fun morph(target: Double) {
        drive.wide.spring(target, AuraFlow.Pills.morphDuration, AuraFlow.Pills.morphDampingRatio, now)
    }
}
