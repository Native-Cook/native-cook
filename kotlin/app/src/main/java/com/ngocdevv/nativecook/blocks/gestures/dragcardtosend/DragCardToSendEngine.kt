package com.ngocdevv.nativecook.blocks.gestures.dragcardtosend

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

@Stable
internal class DragCardToSendEngine(
    private val autoPlay: Boolean,
    val reducedMotion: Boolean,
    private val onHaptic: (DragCardToSendHaptic) -> Unit,
) {
    var width by mutableDoubleStateOf(402.0)
        private set
    var height by mutableDoubleStateOf(874.0)
        private set
    var phase by mutableStateOf(DragCardToSendPhase.Rest)
        private set
    var g by mutableDoubleStateOf(0.0)
        private set
    var veil by mutableDoubleStateOf(0.0)
        private set
    var cardX by mutableDoubleStateOf(0.0)
        private set
    var cardY by mutableDoubleStateOf(0.0)
        private set
    var cardScale by mutableDoubleStateOf(1.0)
        private set
    var cardOpacity by mutableDoubleStateOf(1.0)
        private set
    var press by mutableDoubleStateOf(1.0)
        private set
    var tilt by mutableDoubleStateOf(0.0)
        private set
    var sway by mutableDoubleStateOf(0.0)
        private set
    var hoverIndex by mutableIntStateOf(-1)
        private set
    var hoverAmount by mutableDoubleStateOf(0.0)
        private set
    var sendIndex by mutableIntStateOf(-1)
        private set
    var ring by mutableDoubleStateOf(0.0)
        private set
    var check by mutableDoubleStateOf(0.0)
        private set
    var sent by mutableDoubleStateOf(0.0)
        private set
    var undo by mutableDoubleStateOf(0.0)
        private set
    var balanceProgress by mutableDoubleStateOf(0.0)
        private set
    var clockMs by mutableDoubleStateOf(0.0)
        private set
    var scriptedFinger by mutableStateOf<Offset?>(null)
        private set

    val cardWidth: Double
        get() = width - DragCardToSendTokens.Layout.side * 2.0
    val homeCenterX: Double
        get() = width / 2.0
    val homeCenterY: Double
        get() = DragCardToSendTokens.Layout.cardTop + DragCardToSendTokens.Layout.cardHeight / 2.0
    val displayedBalance: Int
        get() = DragCardToSendTokens.startBalance - (selectedCents * balanceProgress).toInt()

    private val selectedCents = DragCardToSendTokens.amounts[DragCardToSendTokens.defaultAmountIndex] * 100
    private var lastFrameNanos: Long? = null
    private var scriptTimeMs = 0.0
    private var scriptEnabled = autoPlay
    private var quietMs = 0.0
    private var touching = false
    private var downPosition = Offset.Zero
    private var downCardX = 0.0
    private var downCardY = 0.0
    private var travel = 0.0
    private var finger = Offset.Zero
    private var grabOffset = Offset.Zero
    private var didHitCard = false
    private var sendTimeMs = 0.0
    private var sentTimeMs = 0.0
    private var closeTimeMs = 0.0
    private var successHapticSent = false
    private var targetG = 0.0
    private var gVelocity = 0.0
    private var targetCardX = 0.0
    private var cardXVelocity = 0.0
    private var targetCardY = 0.0
    private var cardYVelocity = 0.0
    private var targetScale = 1.0
    private var scaleVelocity = 0.0

    fun setSize(newWidth: Double, newHeight: Double) {
        width = newWidth
        height = newHeight
    }

    fun frame(frameNanos: Long) {
        val previous = lastFrameNanos
        lastFrameNanos = frameNanos
        if (previous == null) {
            return
        }
        val dtSeconds = min((frameNanos - previous) / 1_000_000_000.0, 0.1)
        val dtMs = dtSeconds * 1_000.0
        clockMs = (clockMs + dtMs) % DragCardToSendTokens.Ambient.sheenMs
        if (scriptEnabled) {
            scriptTimeMs += dtMs / DragCardToSendTokens.QA_SLOWMO
            val heldAt = DragCardToSendTokens.QA_HOLD_AT
            if (heldAt != null) {
                scriptTimeMs = min(scriptTimeMs, heldAt)
            }
            if (scriptTimeMs >= DragCardToSendTokens.LOOP_MS) {
                scriptTimeMs %= DragCardToSendTokens.LOOP_MS
            }
            sampleScript(scriptTimeMs)
            return
        }
        stepManual(dtSeconds, dtMs)
    }

    fun pointerDown(position: Offset): Boolean {
        takeOver()
        touching = true
        downPosition = position
        finger = position
        travel = 0.0
        val scale = max(cardScale, 0.08)
        val center = Offset((homeCenterX + cardX).toFloat(), (homeCenterY + cardY).toFloat())
        didHitCard = abs(position.x - center.x).toDouble() <= cardWidth * scale / 2.0 &&
            abs(position.y - center.y).toDouble() <= DragCardToSendTokens.Layout.cardHeight * scale / 2.0 &&
            phase != DragCardToSendPhase.Docked &&
            phase != DragCardToSendPhase.Sending &&
            phase != DragCardToSendPhase.Closing
        if (!didHitCard) {
            return false
        }
        downCardX = cardX
        downCardY = cardY
        grabOffset = position - center
        press = DragCardToSendTokens.Press.scale
        if (phase == DragCardToSendPhase.Holding) {
            return true
        }
        if (phase == DragCardToSendPhase.Sent) {
            clearReceipt()
        }
        phase = DragCardToSendPhase.Pulling
        return true
    }

    fun pointerMove(position: Offset, velocityX: Double) {
        if (!didHitCard) {
            return
        }
        finger = position
        travel = max(travel, (abs(position.x - downPosition.x) + abs(position.y - downPosition.y)).toDouble())
        if (phase == DragCardToSendPhase.Pulling) {
            var y = downCardY + position.y.toDouble() - downPosition.y.toDouble()
            if (y < 0.0) {
                y *= DragCardToSendTokens.Pull.upResist
            }
            cardY = y
            cardX = downCardX + (position.x.toDouble() - downPosition.x.toDouble()) * DragCardToSendTokens.Pull.sideFollow
            tilt = clamp01(y / DragCardToSendTokens.Pull.full)
            if (!reducedMotion) {
                g = clamp01(y / DragCardToSendTokens.Pull.full)
            }
            if (y >= DragCardToSendTokens.Pull.commit) {
                commit(position)
            }
            return
        }
        if (phase == DragCardToSendPhase.Holding) {
            followFinger(position)
            sway = (velocityX * 0.006).coerceIn(-7.0, 7.0)
            updateHover(position)
        }
    }

    fun pointerUp(position: Offset, velocity: Offset) {
        touching = false
        quietMs = 0.0
        press = 1.0
        if (!didHitCard) {
            tap(position)
            return
        }
        didHitCard = false
        when (phase) {
            DragCardToSendPhase.Pulling -> finishPull(position, velocity)
            DragCardToSendPhase.Holding -> {
                if (hoverIndex >= 0) {
                    beginSend(hoverIndex)
                } else {
                    dock()
                }
            }
            else -> Unit
        }
    }

    private fun takeOver() {
        if (!scriptEnabled) {
            return
        }
        scriptEnabled = false
        scriptedFinger = null
        quietMs = 0.0
        if (phase == DragCardToSendPhase.Holding) {
            hoverIndex = -1
            dock()
        }
    }

    private fun finishPull(position: Offset, velocity: Offset) {
        if (travel < 6.0) {
            val localX = position.x.toDouble() - homeCenterX - (-cardWidth / 2.0 + 44.0)
            val localY = position.y.toDouble() - homeCenterY - (DragCardToSendTokens.Layout.cardHeight / 2.0 - 64.0)
            if (hypot(localX, localY) < 30.0) {
                openSheet()
                return
            }
        }
        if (cardY > 20.0) {
            onHaptic(DragCardToSendHaptic.Light)
        }
        phase = DragCardToSendPhase.Rest
        targetCardX = 0.0
        targetCardY = 0.0
        cardXVelocity = velocity.x * DragCardToSendTokens.Pull.sideFollow
        cardYVelocity = velocity.y.toDouble()
        targetScale = 1.0
        targetG = 0.0
    }

    private fun commit(position: Offset) {
        phase = DragCardToSendPhase.Holding
        onHaptic(DragCardToSendHaptic.Medium)
        finger = position
        targetScale = DragCardToSendTokens.Token.scale
        targetG = 2.0
        if (reducedMotion) {
            g = 2.0
            veil = 1.0
        }
    }

    private fun followFinger(position: Offset) {
        val scale = cardScale
        cardX = position.x.toDouble() - grabOffset.x.toDouble() * scale - homeCenterX
        cardY = position.y.toDouble() - grabOffset.y.toDouble() * scale - homeCenterY
    }

    private fun updateHover(position: Offset) {
        if (g < 1.6 && veil < 0.9) {
            return
        }
        val targets = targets()
        var best = -1
        var bestDistance = Double.MAX_VALUE
        targets.forEachIndexed { index, target ->
            val rowTarget = index >= DragCardToSendTokens.favourites.size
            val inside = abs(position.x.toDouble() - width / 2.0) < innerWidth() / 2.0 + 8.0
            val distance = if (rowTarget) {
                if (inside) abs(position.y - target.y).toDouble() * 1.6 else Double.MAX_VALUE
            } else {
                hypot((position.x - target.x).toDouble(), (position.y - target.y).toDouble())
            }
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        val next = if (best >= 0 && bestDistance < DragCardToSendTokens.Magnet.over) best else -1
        if (next != hoverIndex) {
            hoverIndex = next
            hoverAmount = if (next >= 0) 1.0 else 0.0
            if (next >= 0) {
                onHaptic(DragCardToSendHaptic.Select)
            }
        }
        if (best >= 0 && bestDistance < DragCardToSendTokens.Magnet.radius) {
            val target = targets[best]
            val amount = 1.0 - smooth(
                (bestDistance - DragCardToSendTokens.Magnet.over * 0.4) /
                    (DragCardToSendTokens.Magnet.radius - DragCardToSendTokens.Magnet.over * 0.4),
            )
            val pull = DragCardToSendTokens.Magnet.pull * amount
            cardX += (target.x - position.x).toDouble() * pull
            cardY += (target.y - position.y).toDouble() * pull
        }
    }

    private fun dock() {
        phase = DragCardToSendPhase.Docked
        hoverIndex = -1
        hoverAmount = 0.0
        cardOpacity = 0.0
        targetCardX = 0.0
        targetCardY = 0.0
        targetScale = 1.0
        sway = 0.0
    }

    private fun openSheet() {
        phase = DragCardToSendPhase.Docked
        cardOpacity = 0.0
        targetG = 2.0
        if (reducedMotion) {
            g = 2.0
            veil = 1.0
        }
    }

    private fun beginSend(index: Int) {
        phase = DragCardToSendPhase.Sending
        sendIndex = index
        hoverIndex = index
        hoverAmount = 1.0
        sendTimeMs = 0.0
        sentTimeMs = 0.0
        successHapticSent = false
        sway = 0.0
    }

    private fun beginClose() {
        if (phase != DragCardToSendPhase.Docked && phase != DragCardToSendPhase.Holding) {
            return
        }
        onHaptic(DragCardToSendHaptic.Light)
        phase = DragCardToSendPhase.Closing
        closeTimeMs = 0.0
        hoverIndex = -1
        hoverAmount = 0.0
        targetCardX = 0.0
        targetCardY = 0.0
        targetScale = 1.0
        cardOpacity = 1.0
    }

    private fun tap(position: Offset) {
        if (phase == DragCardToSendPhase.Sent && position.y <= DragCardToSendTokens.Layout.pillTop + DragCardToSendTokens.Layout.pillHeight) {
            onHaptic(DragCardToSendHaptic.Light)
            clearReceipt()
            return
        }
        if (phase != DragCardToSendPhase.Docked) {
            return
        }
        val sheetLeft = DragCardToSendTokens.Sheet.inset
        val sheetTop = DragCardToSendTokens.Sheet.top
        val closeCenterX = width - sheetLeft - DragCardToSendTokens.Sheet.closeRight - DragCardToSendTokens.Sheet.close / 2.0
        val closeCenterY = sheetTop + DragCardToSendTokens.Sheet.titleTop + DragCardToSendTokens.Sheet.close / 2.0
        if (hypot(position.x.toDouble() - closeCenterX, position.y.toDouble() - closeCenterY) <= DragCardToSendTokens.Sheet.close / 2.0) {
            beginClose()
            return
        }
        val target = targets().indexOfFirst { point ->
            hypot((position.x - point.x).toDouble(), (position.y - point.y).toDouble()) <= DragCardToSendTokens.Magnet.radius
        }
        if (target >= 0) {
            hoverIndex = target
            hoverAmount = 1.0
            onHaptic(DragCardToSendHaptic.Select)
            return
        }
        val nextLeft = (width - DragCardToSendTokens.Sheet.nextWidth) / 2.0
        val nextTop = sheetTop + DragCardToSendTokens.Sheet.nextTop
        if (position.x.toDouble() in nextLeft..(nextLeft + DragCardToSendTokens.Sheet.nextWidth) &&
            position.y.toDouble() in nextTop..(nextTop + DragCardToSendTokens.Sheet.nextHeight) && hoverIndex >= 0
        ) {
            beginSend(hoverIndex)
        }
    }

    private fun stepManual(dtSeconds: Double, dtMs: Double) {
        if (!touching) {
            val resting = phase == DragCardToSendPhase.Rest || phase == DragCardToSendPhase.Docked
            quietMs = if (resting) quietMs + dtMs else 0.0
            if (phase == DragCardToSendPhase.Docked && quietMs >= DragCardToSendTokens.RESUME_AFTER_MS) {
                beginClose()
                quietMs = 0.0
            } else if (autoPlay && phase == DragCardToSendPhase.Rest && quietMs >= DragCardToSendTokens.RESUME_AFTER_MS) {
                resetRest()
                scriptTimeMs = 0.0
                scriptEnabled = true
                return
            }
        }
        if (phase == DragCardToSendPhase.Sending) {
            sendTimeMs += dtMs
            sampleSend(sendTimeMs, sendIndex)
            return
        }
        if (phase == DragCardToSendPhase.Sent) {
            sentTimeMs += dtMs
            undo = 1.0 - clamp01(sentTimeMs / DragCardToSendTokens.Send.undoMs)
            if (sentTimeMs >= DragCardToSendTokens.Send.undoMs) {
                clearReceipt()
            }
            return
        }
        if (phase == DragCardToSendPhase.Closing) {
            closeTimeMs += dtMs
            val duration = if (reducedMotion) DragCardToSendTokens.Fade.reducedMs else DragCardToSendTokens.Send.closeMs
            val progress = clamp01(closeTimeMs / duration)
            if (reducedMotion) {
                veil = 1.0 - progress
            } else {
                g = 2.0 * (1.0 - cubicBezier(progress, DragCardToSendTokens.Send.closeEase))
            }
            if (progress >= 1.0) {
                resetRest()
            }
            return
        }
        val cardSpring = DragCardToSendTokens.Springs.card
        val openSpring = DragCardToSendTokens.Springs.open
        val tokenSpring = DragCardToSendTokens.Springs.token
        val x = spring(cardX, cardXVelocity, targetCardX, cardSpring, dtSeconds)
        cardX = x.first
        cardXVelocity = x.second
        val y = spring(cardY, cardYVelocity, targetCardY, cardSpring, dtSeconds)
        cardY = y.first
        cardYVelocity = y.second
        val scale = spring(cardScale, scaleVelocity, targetScale, tokenSpring, dtSeconds)
        cardScale = scale.first
        scaleVelocity = scale.second
        if (!reducedMotion) {
            val shape = spring(g, gVelocity, targetG, openSpring, dtSeconds)
            g = shape.first
            gVelocity = shape.second
        }
        if (phase == DragCardToSendPhase.Holding && touching) {
            updateHover(finger)
        }
    }

    private fun sampleScript(timeMs: Double) {
        resetVisualOnly()
        val script = DragCardToSendTokens.Script
        when {
            timeMs < script.down -> {
                phase = DragCardToSendPhase.Rest
                scriptedFinger = null
            }
            timeMs < script.pullEnd -> {
                phase = DragCardToSendPhase.Pulling
                val progress = easeInOut((timeMs - script.down - 100.0) / (script.pullEnd - script.down - 100.0))
                val x = homeCenterX + 24.0 + 6.0 * progress
                val y = DragCardToSendTokens.Layout.cardTop + 118.0 + script.pullBy * progress
                scriptedFinger = Offset(x.toFloat(), y.toFloat())
                cardX = 6.0 * progress * DragCardToSendTokens.Pull.sideFollow
                cardY = script.pullBy * progress
                tilt = clamp01(cardY / DragCardToSendTokens.Pull.full)
                if (!reducedMotion) {
                    g = clamp01(cardY / DragCardToSendTokens.Pull.full)
                }
                press = DragCardToSendTokens.Press.scale
            }
            timeMs < script.release -> sampleScriptHolding(timeMs)
            timeMs < script.undoTap -> sampleScriptSending(timeMs)
            else -> {
                val receiptFade = 1.0 - clamp01((timeMs - script.undoTap) / DragCardToSendTokens.Send.pillSwapMs)
                phase = if (receiptFade > 0.0) DragCardToSendPhase.Sent else DragCardToSendPhase.Rest
                sent = receiptFade
                undo = 0.0
                balanceProgress = receiptFade
                cardScale = 1.0
                cardOpacity = 1.0
                scriptedFinger = if (timeMs < script.undoTap + 120.0) {
                    Offset((width / 2.0 + 92.0).toFloat(), 29.0f)
                } else {
                    null
                }
            }
        }
    }

    private fun sampleScriptHolding(timeMs: Double) {
        val script = DragCardToSendTokens.Script
        phase = DragCardToSendPhase.Holding
        val opening = clamp01((timeMs - script.pullEnd) / 600.0)
        g = if (reducedMotion) 2.0 else 1.0 + springEase(opening)
        veil = if (reducedMotion) clamp01((timeMs - script.pullEnd) / DragCardToSendTokens.Fade.reducedMs) else 0.0
        cardScale = mix(1.0 - DragCardToSendTokens.Pull.shrink, DragCardToSendTokens.Token.scale, springEase(clamp01((timeMs - script.pullEnd) / 360.0)))
        val startX = homeCenterX + 30.0
        val startY = DragCardToSendTokens.Layout.cardTop + 118.0 + script.pullBy
        val target = targets()[2]
        val targetX = target.x.toDouble() + 24.0 * DragCardToSendTokens.Token.scale
        val targetY = target.y.toDouble() - 18.0 * DragCardToSendTokens.Token.scale
        val progress = easeInOut((timeMs - script.hoverStart) / (script.hoverArrive - script.hoverStart))
        val point = if (timeMs < script.hoverStart) {
            Offset(startX.toFloat(), startY.toFloat())
        } else {
            quadratic(
                Offset(startX.toFloat(), startY.toFloat()),
                Offset((targetX + 70.0).toFloat(), ((startY + targetY) / 2.0 + 30.0).toFloat()),
                Offset(targetX.toFloat(), targetY.toFloat()),
                progress,
            )
        }
        scriptedFinger = point
        cardX = point.x.toDouble() - 24.0 * cardScale - homeCenterX
        cardY = point.y.toDouble() + 18.0 * cardScale - homeCenterY
        hoverIndex = if (timeMs >= script.hoverArrive - 180.0) 2 else -1
        hoverAmount = if (hoverIndex >= 0) springEase(clamp01((timeMs - script.hoverArrive + 180.0) / 180.0)) else 0.0
        sway = if (timeMs in script.hoverStart..script.hoverArrive) 7.0 * (1.0 - progress) else 0.0
    }

    private fun sampleScriptSending(timeMs: Double) {
        val elapsed = timeMs - DragCardToSendTokens.Script.release
        sendIndex = 2
        scriptedFinger = null
        sampleSend(elapsed, sendIndex)
    }

    private fun sampleSend(elapsed: Double, target: Int) {
        val send = DragCardToSendTokens.Send
        val absorb = clamp01(elapsed / send.absorbMs)
        val targetPoint = targets()[target]
        val fromX = targetPoint.x.toDouble() - homeCenterX
        val fromY = targetPoint.y.toDouble() - homeCenterY
        phase = DragCardToSendPhase.Sending
        sendIndex = target
        hoverIndex = target
        if (elapsed <= send.absorbMs) {
            val eased = cubicBezier(absorb, send.absorbEase)
            cardX = mix(fromX - 12.0, fromX, eased)
            cardY = mix(fromY - 10.0, fromY, eased)
            cardScale = mix(DragCardToSendTokens.Token.scale, send.absorbScale, eased)
            cardOpacity = 1.0 - clamp01((absorb - 0.6) / 0.4)
            hoverAmount = if (absorb < 0.7) 1.0 else 1.0 + 0.7 * springEase((absorb - 0.7) / 0.3)
        } else {
            cardX = fromX
            cardY = fromY
            cardScale = send.absorbScale
            cardOpacity = 0.0
            hoverAmount = 1.0
        }
        ring = easeInOut((elapsed - send.absorbMs) / send.ringMs)
        check = springEase(clamp01((elapsed - send.absorbMs - send.ringMs) / 260.0))
        val closeAt = send.absorbMs + send.ringMs + send.holdMs
        if (!scriptEnabled && !successHapticSent && elapsed >= send.absorbMs + send.ringMs) {
            successHapticSent = true
            onHaptic(DragCardToSendHaptic.Success)
        }
        if (elapsed >= closeAt) {
            val close = clamp01((elapsed - closeAt) / send.closeMs)
            if (reducedMotion) {
                veil = 1.0 - clamp01((elapsed - closeAt) / DragCardToSendTokens.Fade.reducedMs)
                g = 2.0
            } else {
                g = 2.0 * (1.0 - cubicBezier(close, send.closeEase))
            }
            sent = clamp01((elapsed - closeAt - send.closeMs * 0.55) / send.pillSwapMs)
        } else {
            g = 2.0
            veil = if (reducedMotion) 1.0 else 0.0
        }
        if (elapsed >= closeAt + send.closeMs) {
            phase = DragCardToSendPhase.Sent
            g = 0.0
            veil = 0.0
            sendIndex = -1
            hoverIndex = -1
            ring = 0.0
            check = 0.0
            cardX = 0.0
            cardY = 0.0
            cardOpacity = 1.0
            cardScale = mix(send.enterFrom, 1.0, springEase(clamp01((elapsed - closeAt - send.closeMs) / 320.0)))
            sent = 1.0
            undo = 1.0 - clamp01((elapsed - closeAt - send.closeMs) / send.undoMs)
            balanceProgress = easeOutCubic((elapsed - closeAt - send.closeMs) / send.rollMs)
            if (!scriptEnabled && sentTimeMs == 0.0) {
                sentTimeMs = 0.001
            }
        }
    }

    private fun clearReceipt() {
        phase = DragCardToSendPhase.Rest
        sent = 0.0
        undo = 0.0
        balanceProgress = 0.0
        sentTimeMs = 0.0
    }

    private fun resetRest() {
        resetVisualOnly()
        phase = DragCardToSendPhase.Rest
        targetG = 0.0
        targetCardX = 0.0
        targetCardY = 0.0
        targetScale = 1.0
        gVelocity = 0.0
        cardXVelocity = 0.0
        cardYVelocity = 0.0
        scaleVelocity = 0.0
    }

    private fun resetVisualOnly() {
        g = 0.0
        veil = 0.0
        cardX = 0.0
        cardY = 0.0
        cardScale = 1.0
        cardOpacity = 1.0
        press = 1.0
        tilt = 0.0
        sway = 0.0
        hoverIndex = -1
        hoverAmount = 0.0
        sendIndex = -1
        ring = 0.0
        check = 0.0
        sent = 0.0
        undo = 0.0
        balanceProgress = 0.0
    }

    fun targets(): List<Offset> {
        val innerX = DragCardToSendTokens.Sheet.inset + DragCardToSendTokens.Sheet.favPad
        val innerWidth = innerWidth()
        val favouriteY = DragCardToSendTokens.Sheet.top + DragCardToSendTokens.Sheet.favTop + DragCardToSendTokens.Sheet.favSize / 2.0
        val result = mutableListOf<Offset>()
        DragCardToSendTokens.favourites.indices.forEach { index ->
            result += Offset(
                x = (innerX + innerWidth * (index + 0.5) / 4.0).toFloat(),
                y = favouriteY.toFloat(),
            )
        }
        DragCardToSendTokens.contacts.indices.forEach { index ->
            result += Offset(
                x = (DragCardToSendTokens.Sheet.inset + DragCardToSendTokens.Sheet.rowAvatarX).toFloat(),
                y = (DragCardToSendTokens.Sheet.top + DragCardToSendTokens.Sheet.rowsTop +
                    index * DragCardToSendTokens.Sheet.rowHeight + DragCardToSendTokens.Sheet.rowHeight / 2.0).toFloat(),
            )
        }
        return result
    }

    private fun innerWidth(): Double {
        val innerX = DragCardToSendTokens.Sheet.inset + DragCardToSendTokens.Sheet.favPad
        return width - innerX * 2.0
    }
}

private fun spring(
    value: Double,
    velocity: Double,
    target: Double,
    spec: DragCardToSendTokens.Spring,
    dt: Double,
): Pair<Double, Double> {
    val acceleration = (-spec.stiffness * (value - target) - spec.damping * velocity) / spec.mass
    val nextVelocity = velocity + acceleration * dt
    val nextValue = value + nextVelocity * dt
    return nextValue to nextVelocity
}

private fun quadratic(start: Offset, control: Offset, end: Offset, progress: Double): Offset {
    val t = clamp01(progress).toFloat()
    val a = (1.0f - t) * (1.0f - t)
    val b = 2.0f * (1.0f - t) * t
    val c = t * t
    return Offset(
        x = a * start.x + b * control.x + c * end.x,
        y = a * start.y + b * control.y + c * end.y,
    )
}

internal fun clamp01(value: Double): Double {
    return value.coerceIn(0.0, 1.0)
}

internal fun smooth(value: Double): Double {
    val clamped = clamp01(value)
    return clamped * clamped * (3.0 - 2.0 * clamped)
}

internal fun easeInOut(value: Double): Double {
    val clamped = clamp01(value)
    return if (clamped < 0.5) {
        4.0 * clamped * clamped * clamped
    } else {
        1.0 - (-2.0 * clamped + 2.0).pow(3.0) / 2.0
    }
}

internal fun springEase(value: Double): Double {
    val clamped = clamp01(value)
    return 1.0 - (1.0 - clamped).pow(3.0) * (1.0 - 0.16 * kotlin.math.sin(clamped * Math.PI * 3.0))
}

internal fun easeOutCubic(value: Double): Double {
    val clamped = clamp01(value)
    return 1.0 - (1.0 - clamped).pow(3.0)
}

internal fun mix(start: Double, end: Double, progress: Double): Double {
    return start + (end - start) * progress
}

internal fun cubicBezier(progress: Double, points: DoubleArray): Double {
    val targetX = clamp01(progress)
    var low = 0.0
    var high = 1.0
    repeat(10) {
        val t = (low + high) / 2.0
        val x = bezierCoordinate(t, points[0], points[2])
        if (x < targetX) {
            low = t
        } else {
            high = t
        }
    }
    return bezierCoordinate((low + high) / 2.0, points[1], points[3])
}

private fun bezierCoordinate(t: Double, first: Double, second: Double): Double {
    val inverse = 1.0 - t
    return 3.0 * inverse * inverse * t * first + 3.0 * inverse * t * t * second + t * t * t
}
