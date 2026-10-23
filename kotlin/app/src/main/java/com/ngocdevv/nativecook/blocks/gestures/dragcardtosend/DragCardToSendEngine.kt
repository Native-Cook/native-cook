package com.ngocdevv.nativecook.blocks.gestures.dragcardtosend

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@Stable
internal class DragCardToSendEngine(
    private val autoPlay: Boolean,
    val reducedMotion: Boolean,
    private val onHaptic: (Haptic) -> Unit,
) {
    enum class Phase { Rest, Pulling, Falling, Sent }
    enum class Haptic { Selection, Light, Success }

    data class Flight(
        val fromX: Float = 0f,
        val fromY: Float = 0f,
        val radius: Float = 0f,
        val toX: Float = 0f,
        val toY: Float = 0f,
    )

    var layout by mutableStateOf(dragCardToSendLayout(402f))
        private set
    var phase by mutableStateOf(Phase.Rest)
        private set
    var cardY by mutableFloatStateOf(0f)
        private set
    var press by mutableFloatStateOf(0f)
        private set
    var step by mutableIntStateOf(-1)
        private set
    var dropRadius by mutableFloatStateOf(0f)
        private set
    var selectedPerson by mutableIntStateOf(-1)
        private set
    var ringX by mutableFloatStateOf(layout.avatarX[0])
        private set
    var ringOn by mutableFloatStateOf(0f)
        private set
    var flightProgress by mutableFloatStateOf(-1f)
        private set
    var flight by mutableStateOf(Flight())
        private set
    var targetPerson by mutableIntStateOf(-1)
        private set
    var ripple by mutableFloatStateOf(0f)
        private set
    var catchAmount by mutableFloatStateOf(0f)
        private set
    var receipt by mutableFloatStateOf(0f)
        private set
    var undoProgress by mutableFloatStateOf(0f)
        private set
    var transactionProgress by mutableFloatStateOf(0f)
        private set
    var transactionElapsedMs by mutableFloatStateOf(-1f)
        private set
    var transactionReturning by mutableStateOf(false)
        private set
    var lastPerson by mutableIntStateOf(-1)
        private set
    var lastCents by mutableIntStateOf(0)
        private set

    val displayedBalance: Int
        get() = DragCardToSendTokens.StartBalance - (lastCents * transactionProgress).roundToInt()
    val hint: String
        get() {
            val valid = step >= 0 && selectedPerson >= 0
            val amount = if (valid) DragCardToSendTokens.Amounts[step] else 0
            val name = if (valid) DragCardToSendTokens.People[selectedPerson].name else ""
            return when {
                phase == Phase.Pulling && valid -> "Let go to send \$$amount to $name"
                phase == Phase.Falling && lastPerson >= 0 -> "Sending \$${lastCents / 100} to ${DragCardToSendTokens.People[lastPerson].name}"
                else -> "Pull the card to send"
            }
        }

    private var lastFrameNanos: Long? = null
    private var active = false
    private var scriptedGesture = false
    private var touching = false
    private var downX = 0f
    private var downY = 0f
    private var downBase = 0f
    private var travel = 0f
    private var latestY = 0f
    private var scriptEnabled = autoPlay && !reducedMotion
    private var scriptTimeMs = 0f
    private var quietMs = 0f

    private var cardTarget = 0f
    private var cardVelocity = 0f
    private var dropTarget = 0f
    private var dropVelocity = 0f
    private var ringTarget = ringX
    private var ringVelocity = 0f
    private var pressTarget = 0f
    private var ringOnTarget = 0f
    private var receiptTarget = 0f
    private var receiptVelocity = 0f
    private var catchVelocity = 0f
    private var flightElapsedMs = 0f
    private var contactSent = false
    private var receiptDelayMs = 0f
    private var rippleElapsedMs = -1f
    private var undoElapsedMs = 0f
    private var tapElapsedMs = -1f
    private var closeElapsedMs = -1f
    private var closeFrom = 0f
    private var transactionFrom = 0f
    private var transactionTarget = 0f

    fun setSize(width: Float) {
        if (abs(width - layout.width) < 0.1f) return
        layout = dragCardToSendLayout(width)
        ringX = layout.avatarX[max(0, selectedPerson)]
        ringTarget = ringX
    }

    fun frame(frameNanos: Long) {
        val previous = lastFrameNanos
        lastFrameNanos = frameNanos
        if (previous == null) return
        val dtMs = min(64f, (frameNanos - previous) / 1_000_000f)
        if (scriptEnabled) updateScript(dtMs)
        updateMotion(dtMs)
        if (!scriptEnabled && autoPlay && !reducedMotion && !touching) {
            val resting = phase == Phase.Rest && receipt < 0.01f && flightProgress < 0f
            quietMs = if (resting) quietMs + dtMs else 0f
            if (quietMs >= DragCardToSendTokens.Send.ResumeAfterMs) {
                resetForLoop()
                scriptEnabled = true
            }
        }
    }

    fun pointerDown(position: Offset, fromScript: Boolean = false) {
        if (!fromScript) takeOver()
        touching = !fromScript
        scriptedGesture = fromScript
        active = false
        downX = position.x
        downY = position.y
        latestY = position.y
        travel = 0f
        if (phase == Phase.Falling || phase == Phase.Pulling) return
        val top = layout.cardTop + cardY
        if (position.x !in DragCardToSendTokens.Layout.Edge..(DragCardToSendTokens.Layout.Edge + layout.cardWidth) ||
            position.y !in top..(top + layout.cardHeight)
        ) return
        if (phase == Phase.Sent) closeReceipt(undo = false)
        active = true
        downBase = untether(cardY)
        cardVelocity = 0f
        pressTarget = 1f
        phase = Phase.Pulling
        setSelection(-1)
        step = -1
    }

    fun pointerMove(position: Offset) {
        if (!active || phase != Phase.Pulling) return
        latestY = position.y
        val d = position.y - downY
        travel = max(travel, abs(position.x - downX) + abs(d))
        cardY = tether(downBase + d)
        cardTarget = cardY
        val nextStep = stepAt(d)
        var ticked = false
        if (nextStep != step) {
            updateStep(nextStep)
            haptic(Haptic.Selection)
            ticked = true
        }
        val nextPerson = if (nextStep >= 0) {
            ((position.x - layout.avatarX[0]) / layout.pitch).roundToInt().coerceIn(0, DragCardToSendTokens.People.lastIndex)
        } else {
            -1
        }
        if (nextPerson != selectedPerson) {
            setSelection(nextPerson)
            if (nextPerson >= 0 && !ticked) haptic(Haptic.Selection)
        }
    }

    fun pointerUp(position: Offset, velocityY: Float, fromScript: Boolean = false) {
        touching = false
        quietMs = 0f
        latestY = position.y
        if (!active) {
            if (!fromScript) tap(position)
            return
        }
        active = false
        pressTarget = 0f
        if (phase != Phase.Pulling) return
        val cardReleaseVelocity = tetherSlope(downBase + position.y - downY) * velocityY
        if (step >= 0 && selectedPerson >= 0) {
            send(selectedPerson, step, cardReleaseVelocity)
        } else {
            cancel(travel < DragCardToSendTokens.Pull.TapTravel, cardReleaseVelocity)
        }
    }

    fun tapUndo(fromScript: Boolean = false) {
        if (phase != Phase.Sent) return
        scriptedGesture = fromScript
        if (!fromScript) onHaptic(Haptic.Light)
        closeReceipt(undo = true)
    }

    private fun takeOver() {
        if (!scriptEnabled) return
        scriptEnabled = false
        quietMs = 0f
        if (active && scriptedGesture) {
            active = false
            pressTarget = 0f
            cancel(tap = false, velocity = 0f)
        }
    }

    private fun tap(position: Offset) {
        if (phase != Phase.Sent) return
        val receipt = DragCardToSendTokens.Receipt
        val right = layout.width - DragCardToSendTokens.Layout.Edge - receipt.UndoWidth / 2f
        val centerY = receipt.Top + receipt.Height / 2f
        if (position.x in (right - receipt.UndoWidth / 2f)..(right + receipt.UndoWidth / 2f) &&
            position.y in (centerY - receipt.UndoHeight / 2f)..(centerY + receipt.UndoHeight / 2f)
        ) {
            tapUndo()
        }
    }

    private fun updateStep(next: Int) {
        step = next
        dropTarget = if (next >= 0) radiusFor(DragCardToSendTokens.Amounts[next]) else 0f
        if (reducedMotion) dropVelocity = 0f
    }

    private fun setSelection(next: Int) {
        if (next == selectedPerson) return
        if (next >= 0) {
            if (selectedPerson < 0 || ringOn < 0.05f) {
                ringX = layout.avatarX[next]
                ringVelocity = 0f
            }
            ringTarget = layout.avatarX[next]
            ringOnTarget = 1f
        } else {
            ringOnTarget = 0f
        }
        selectedPerson = next
    }

    private fun cancel(tap: Boolean, velocity: Float) {
        setSelection(-1)
        updateStep(-1)
        phase = Phase.Rest
        cardTarget = 0f
        cardVelocity = velocity
        if (tap) {
            tapElapsedMs = 0f
            if (!reducedMotion) cardY = DragCardToSendTokens.Send.Nod
            dropRadius = DragCardToSendTokens.Send.Bead
            dropTarget = DragCardToSendTokens.Send.Bead
        }
    }

    private fun send(person: Int, amountStep: Int, velocity: Float) {
        phase = Phase.Falling
        targetPerson = person
        lastPerson = person
        lastCents = DragCardToSendTokens.Amounts[amountStep] * 100
        cardTarget = 0f
        cardVelocity = velocity
        pressTarget = 0f
        flightElapsedMs = 0f
        contactSent = false
        if (reducedMotion) {
            flightProgress = -1f
            dropTarget = 0f
            return
        }
        val radius = max(dropRadius, 1f)
        val fromY = layout.islandCenterY + DragCardToSendTokens.Island.Height / 2f + DragCardToSendTokens.Drop.Hang * radius
        flight = Flight(layout.islandCenterX, fromY, radius, layout.avatarX[person], layout.avatarY)
        dropRadius = 0f
        dropTarget = 0f
        flightProgress = 0f
    }

    private fun land() {
        if (contactSent) return
        contactSent = true
        haptic(Haptic.Success)
        if (!reducedMotion) {
            catchAmount = 1f
            catchVelocity = 0f
        }
        ripple = 0f
        rippleElapsedMs = 0f
        receiptDelayMs = if (reducedMotion) 0f else 60f
        receiptTarget = 1f
        receiptVelocity = 0f
        phase = Phase.Sent
        undoProgress = 1f
        undoElapsedMs = 0f
        animateTransaction(1f)
    }

    private fun closeReceipt(undo: Boolean) {
        if (undo) animateTransaction(0f)
        closeFrom = receipt
        closeElapsedMs = 0f
        receiptTarget = 0f
        undoProgress = 0f
        phase = Phase.Rest
    }

    private fun animateTransaction(target: Float) {
        transactionFrom = transactionProgress
        transactionTarget = target
        transactionReturning = target < transactionFrom
        transactionElapsedMs = 0f
    }

    private fun updateMotion(dtMs: Float) {
        val dt = dtMs / 1_000f
        if (tapElapsedMs >= 0f) {
            tapElapsedMs += dtMs
            if (tapElapsedMs >= DragCardToSendTokens.Send.NodMs) {
                cardTarget = 0f
                dropTarget = 0f
            }
            if (tapElapsedMs >= DragCardToSendTokens.Send.NodMs + 520f) tapElapsedMs = -1f
        }

        if (!active) {
            val card = spring(cardY, cardVelocity, cardTarget, 400f, if (reducedMotion) 1f else 0.8f, dt)
            cardY = card.first
            cardVelocity = card.second
        }
        press = timedToward(press, pressTarget, dtMs, if (pressTarget > press) DragCardToSendTokens.Send.PressInMs else DragCardToSendTokens.Send.PressOutMs)

        if (reducedMotion) {
            dropRadius = timedToward(dropRadius, dropTarget, dtMs, 90f)
        } else {
            val drop = spring(dropRadius, dropVelocity, dropTarget, 420f, 0.5f, dt)
            dropRadius = drop.first.coerceAtLeast(0f)
            dropVelocity = drop.second
        }
        val ring = spring(ringX, ringVelocity, ringTarget, 300f, 0.8f, dt)
        ringX = ring.first
        ringVelocity = ring.second
        ringOn = timedToward(ringOn, ringOnTarget, dtMs, if (ringOnTarget > ringOn) 200f else 160f)

        if (phase == Phase.Falling && reducedMotion) {
            flightElapsedMs += dtMs
            if (flightElapsedMs >= DragCardToSendTokens.Send.ReducedMs) {
                land()
                step = -1
            }
        } else if (!reducedMotion && flightProgress >= 0f) {
            flightElapsedMs += dtMs
            flightProgress = clamp01(flightElapsedMs / DragCardToSendTokens.Flight.DurationMs)
            if (flightProgress >= DragCardToSendTokens.Flight.Contact) land()
            if (flightProgress >= 1f) {
                flightProgress = -1f
                step = -1
            }
        }

        if (receiptDelayMs > 0f) {
            receiptDelayMs -= dtMs
        } else if (closeElapsedMs < 0f) {
            if (reducedMotion) {
                receipt = timedToward(receipt, receiptTarget, dtMs, DragCardToSendTokens.Send.ReducedMs)
            } else {
                val result = spring(receipt, receiptVelocity, receiptTarget, 300f, 0.8f, dt)
                receipt = result.first.coerceIn(0f, 1.06f)
                receiptVelocity = result.second
            }
        }
        if (closeElapsedMs >= 0f) {
            closeElapsedMs += dtMs
            val p = clamp01(closeElapsedMs / DragCardToSendTokens.Send.CloseMs)
            receipt = closeFrom * (1f - cubicBezier(p, EASE_IN_OUT))
            if (p >= 1f) {
                receipt = 0f
                closeElapsedMs = -1f
                targetPerson = -1
                selectedPerson = -1
                ringOn = 0f
                ringOnTarget = 0f
            }
        }

        if (catchAmount > 0.0001f) {
            val result = spring(catchAmount, catchVelocity, 0f, 620f, 0.42f, dt)
            catchAmount = result.first.coerceAtLeast(0f)
            catchVelocity = result.second
        } else {
            catchAmount = 0f
        }
        if (rippleElapsedMs >= 0f) {
            rippleElapsedMs += dtMs
            ripple = cubicBezier(clamp01(rippleElapsedMs / DragCardToSendTokens.Send.RippleMs), EASE_OUT)
            if (rippleElapsedMs >= DragCardToSendTokens.Send.RippleMs) {
                ripple = 0f
                rippleElapsedMs = -1f
                selectedPerson = -1
                ringOn = 0f
                ringOnTarget = 0f
            }
        }
        if (phase == Phase.Sent) {
            undoElapsedMs += dtMs
            undoProgress = 1f - clamp01(undoElapsedMs / DragCardToSendTokens.Send.UndoMs)
            if (undoProgress <= 0f) closeReceipt(undo = false)
        }
        if (transactionElapsedMs >= 0f) {
            transactionElapsedMs += dtMs
            val p = cubicBezier(clamp01(transactionElapsedMs / DragCardToSendTokens.Send.RollMs), EASE_OUT)
            transactionProgress = mix(transactionFrom, transactionTarget, p)
            val rowsDuration = DragCardToSendTokens.RowsMotion.ExitMs +
                DragCardToSendTokens.RowsMotion.EnterAfterMs + DragCardToSendTokens.RowsMotion.EnterMs
            if (transactionElapsedMs >= rowsDuration) transactionElapsedMs = -1f
        }
    }

    private fun updateScript(dtMs: Float) {
        var previous = scriptTimeMs
        var now = previous + dtMs
        if (now >= DragCardToSendTokens.Script.LoopMs) {
            resetForLoop()
            previous = 0f
            now %= DragCardToSendTokens.Script.LoopMs
        }
        val script = DragCardToSendTokens.Script
        val x0 = DragCardToSendTokens.Layout.Edge + script.TouchX
        val y0 = layout.cardTop + script.TouchY
        if (previous < script.Down && now >= script.Down) pointerDown(Offset(x0, y0), fromScript = true)
        if (now > script.PullStart && previous < script.PullEnd) {
            val u = smoother((now - script.PullStart) / (script.PullEnd - script.PullStart))
            pointerMove(Offset(x0, y0 + script.PullBy * u))
        }
        if (now > script.SlideStart && previous < script.SlideEnd) {
            val u = smoother((now - script.SlideStart) / (script.SlideEnd - script.SlideStart))
            val x = mix(x0, layout.avatarX[script.SlideTo], u)
            pointerMove(Offset(x, y0 + script.PullBy))
        }
        if (previous < script.Release && now >= script.Release) {
            pointerUp(Offset(layout.avatarX[script.SlideTo], y0 + script.PullBy), 0f, fromScript = true)
        }
        if (previous < script.Undo && now >= script.Undo) tapUndo(fromScript = true)
        scriptTimeMs = now
    }

    private fun resetForLoop() {
        phase = Phase.Rest
        cardY = 0f
        cardTarget = 0f
        cardVelocity = 0f
        press = 0f
        pressTarget = 0f
        step = -1
        dropRadius = 0f
        dropTarget = 0f
        dropVelocity = 0f
        selectedPerson = -1
        targetPerson = -1
        ringX = layout.avatarX[0]
        ringTarget = ringX
        ringVelocity = 0f
        ringOn = 0f
        ringOnTarget = 0f
        flightProgress = -1f
        ripple = 0f
        rippleElapsedMs = -1f
        catchAmount = 0f
        receipt = 0f
        receiptTarget = 0f
        receiptVelocity = 0f
        closeElapsedMs = -1f
        undoProgress = 0f
        transactionProgress = 0f
        transactionElapsedMs = -1f
        transactionReturning = false
        lastPerson = -1
        lastCents = 0
        active = false
        scriptedGesture = false
        touching = false
        quietMs = 0f
        scriptTimeMs = 0f
    }

    private fun haptic(kind: Haptic) {
        if (!scriptedGesture) onHaptic(kind)
    }

    private fun tether(distance: Float): Float {
        val pull = DragCardToSendTokens.Pull
        return if (distance >= 0f) pull.Tether * (1f - exp(-distance / pull.Falloff)) else distance * pull.UpResistance
    }

    private fun untether(value: Float): Float {
        val pull = DragCardToSendTokens.Pull
        return if (value < 0f) value / pull.UpResistance else -pull.Falloff * ln(1f - min(value / pull.Tether, 0.999f))
    }

    private fun tetherSlope(distance: Float): Float {
        val pull = DragCardToSendTokens.Pull
        return if (distance >= 0f) (pull.Tether / pull.Falloff) * exp(-distance / pull.Falloff) else pull.UpResistance
    }

    private fun stepAt(distance: Float): Int {
        val pull = DragCardToSendTokens.Pull
        if (distance < pull.Dead) return -1
        return ((distance - pull.Dead) / pull.Step).toInt().coerceAtMost(DragCardToSendTokens.Amounts.lastIndex)
    }
}

internal val EASE_OUT = floatArrayOf(0.23f, 1f, 0.32f, 1f)
internal val EASE_IN_OUT = floatArrayOf(0.77f, 0f, 0.175f, 1f)

internal fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)
internal fun mix(start: Float, end: Float, progress: Float): Float = start + (end - start) * progress
internal fun smoother(value: Float): Float {
    val t = clamp01(value)
    return t * t * t * (t * (t * 6f - 15f) + 10f)
}
internal fun radiusFor(dollars: Int): Float {
    return DragCardToSendTokens.Drop.BaseRadius + DragCardToSendTokens.Drop.RadiusScale * dollars.toFloat().pow(1f / 3f)
}

internal fun cubicBezier(progress: Float, points: FloatArray): Float {
    val x = clamp01(progress)
    var lo = 0f
    var hi = 1f
    repeat(20) {
        val t = (lo + hi) / 2f
        if (bezierCoordinate(t, points[0], points[2]) < x) lo = t else hi = t
    }
    return bezierCoordinate((lo + hi) / 2f, points[1], points[3])
}

private fun bezierCoordinate(t: Float, first: Float, second: Float): Float {
    val q = 1f - t
    return 3f * q * q * t * first + 3f * q * t * t * second + t * t * t
}

private fun spring(
    value: Float,
    velocity: Float,
    target: Float,
    durationMs: Float,
    dampingRatio: Float,
    dt: Float,
): Pair<Float, Float> {
    val omega = 4.6f / max(0.001f, dampingRatio * durationMs / 1_000f)
    val acceleration = -2f * dampingRatio * omega * velocity - omega * omega * (value - target)
    val nextVelocity = velocity + acceleration * dt
    return (value + nextVelocity * dt) to nextVelocity
}

private fun timedToward(value: Float, target: Float, dtMs: Float, durationMs: Float): Float {
    if (abs(target - value) < 0.0001f) return target
    val alpha = 1f - exp(-5f * dtMs / max(1f, durationMs))
    return mix(value, target, alpha)
}
