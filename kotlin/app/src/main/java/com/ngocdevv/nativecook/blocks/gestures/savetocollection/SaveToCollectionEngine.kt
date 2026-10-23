package com.ngocdevv.nativecook.blocks.gestures.savetocollection

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class SaveToCollectionEngine(
    private val autoPlay: Boolean,
    val reducedMotion: Boolean,
    private val onHaptic: (SaveHaptic) -> Unit,
) {
    private val tokens = SaveToCollectionTokens
    private val gone = BooleanArray(tokens.cards.size)
    private var flightSequence = 0
    private var nextPileAt = -1.0
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownSeconds = 0.0
    private var touchCard = -1
    private var lastVelocityX = 0f
    private var lastVelocityY = 0f
    private var dragMode = DRAG_NONE
    private var dragAnimationStart = 0.0
    private var dragAnimationDuration = 0.0
    private var dragFromX = 0f
    private var dragFromY = 0f
    private var dragFromScale = 1f
    private var dragVelocityX = 0f
    private var dragVelocityY = 0f
    private var scaleVelocity = 0f
    private var tiltVelocity = 0f
    private var dragScaleTarget = 1f
    private var dragTiltTarget = 0f
    private var magnetWasOpen = false

    var width = 0f
        private set
    var height = 0f
        private set
    var layoutScale = 1f
        private set
    var layoutOffsetY = 0f
        private set
    var clock = 0.0
        private set
    var scriptTime = 0.0
        private set
    var pile = 0
        private set
    var saved = 0
        private set
    var pileCount = 0
        private set
    var dealStart = -100.0
        private set
    var rushStart = -1.0
        private set
    var riseStart = -1.0
        private set
    var ringStart = -1.0
        private set
    var bounceStart = -1.0
        private set
    var pressTarget = -1
        private set
    var pressAmount = 0f
        private set
    var dragCard = -1
        private set
    var dragX = 0f
        private set
    var dragY = 0f
        private set
    var dragScale = 1f
        private set
    var dragTilt = 0f
        private set
    var dragOpen = 0f
        private set
    var ghostX = 0f
        private set
    var ghostY = 0f
        private set
    var ghostAlpha = 0f
        private set
    var ghostPress = 0f
        private set
    var touching = false
        private set
    var paused = !autoPlay
        private set
    var quiet = 0.0
        private set
    val flights = mutableListOf<SaveFlight>()

    val bookmarkX: Float
        get() = width / 2f

    val bookmarkY: Float
        get() = tokens.bookmarkY * layoutScale + layoutOffsetY

    val bookmarkRiseY: Float
        get() = tokens.bookmarkRiseY * layoutScale + layoutOffsetY

    fun setSize(newWidth: Float, newHeight: Float) {
        if (newWidth == width && newHeight == height) {
            return
        }
        width = newWidth
        height = newHeight
        layoutScale = newWidth / tokens.designWidth
        layoutOffsetY = (newHeight - tokens.designHeight * layoutScale) / 2f
    }

    fun cardRect(index: Int): SaveRect {
        val card = tokens.cards[index]
        return SaveRect(
            x = card.x * layoutScale,
            y = card.y * layoutScale + layoutOffsetY,
            width = card.width * layoutScale,
            height = card.height * layoutScale,
        )
    }

    fun visibleIndices(): List<Int> {
        return tokens.cards.indices.filter { index ->
            tokens.cards[index].pile == pile && !gone[index]
        }
    }

    fun isGone(index: Int): Boolean {
        return gone[index]
    }

    fun step(deltaSeconds: Float) {
        val delta = min(tokens.maxFrameSeconds, max(0f, deltaSeconds)).toDouble()
        if (autoPlay && !paused) {
            stepAutoplay(delta)
        } else {
            clock += delta
            if (!touching) {
                quiet += delta
            }
            stepManual(delta)
        }
        updateDragAnimation(delta)
        updateFlights()
        if (autoPlay && paused && canResume()) {
            resetLoop()
            paused = false
        }
    }

    private fun stepAutoplay(delta: Double) {
        val previous = scriptTime
        val next = previous + delta
        if (next >= LOOP / 1000.0) {
            resetLoop()
            return
        }
        scriptTime = next
        clock = next
        val dragIndex = tokens.cards.indexOfFirst { card -> card.id == tokens.demoDragCard }
        if (crossed(previous, next, tokens.demoDragDown.toDouble())) {
            val rect = cardRect(dragIndex)
            ghostX = rect.x + rect.width / 2f
            ghostY = rect.y + rect.height / 2f
        }
        if (crossed(previous, next, tokens.demoDragLifted.toDouble())) {
            beginDrag(dragIndex, silent = true)
        }
        if (dragCard == dragIndex && dragMode == DRAG_ACTIVE && next < tokens.demoDragUp.toDouble()) {
            val span = tokens.demoDragEnd - tokens.demoDragLifted
            val progress = cosineEase(((next - tokens.demoDragLifted) / span).toFloat())
            val base = cardRect(dragIndex)
            val baseX = base.x + base.width / 2f
            val baseY = base.y + base.height / 2f
            val translationX = (bookmarkX - baseX) * progress + sin(PI * progress).toFloat() * tokens.demoDragArc
            val translationY = (bookmarkY - baseY) * progress
            val previousTranslation = dragX - baseX
            val velocityX = ((translationX - previousTranslation) / max(delta.toFloat(), 0.001f))
            updateDrag(translationX, translationY, velocityX, 0f, silent = true)
            ghostX = baseX + translationX
            ghostY = baseY + translationY
        }
        if (crossed(previous, next, tokens.demoDragUp.toDouble()) && dragCard >= 0) {
            releaseDrag(silent = true, forceSave = true)
        }
        if (crossed(previous, next, tokens.demoSaveAll.toDouble())) {
            saveAll(silent = true)
        }
        if (crossed(previous, next, tokens.demoSaveAllAgain.toDouble())) {
            saveAll(silent = true)
        }
        if (crossed(previous, next, tokens.demoRise.toDouble())) {
            pileCount = tokens.cards.count { card -> card.pile == 0 }
            riseStart = clock
        }
        if (crossed(previous, next, tokens.demoRiseAgain.toDouble())) {
            pileCount = tokens.cards.count { card -> card.pile == 1 }
            riseStart = clock
        }
        val firstDeal = tokens.demoRise + (tokens.timingRise + tokens.timingRiseHold) / 1000f
        val secondDeal = tokens.demoRiseAgain + (tokens.timingRise + tokens.timingRiseHold) / 1000f
        if (crossed(previous, next, firstDeal.toDouble())) {
            nextPile()
        }
        if (crossed(previous, next, secondDeal.toDouble())) {
            nextPile()
        }
        updateDemoFinger(next.toFloat(), dragIndex)
    }

    private fun updateDemoFinger(time: Float, dragIndex: Int) {
        ghostAlpha = 0f
        ghostPress = 0f
        pressTarget = -1
        pressAmount = 0f
        val drag = tokens
        if (time >= drag.demoDragDown && time <= drag.demoDragUp + drag.demoFingerFade) {
            val rect = cardRect(dragIndex)
            if (time < drag.demoDragLifted) {
                ghostX = rect.x + rect.width / 2f
                ghostY = rect.y + rect.height / 2f
            }
            ghostAlpha = fadeWindow(time, drag.demoDragDown, drag.demoDragUp, drag.demoFingerFade)
            ghostPress = if (time < drag.demoDragLifted) {
                clamp01((time - drag.demoDragDown) / (drag.demoDragLifted - drag.demoDragDown))
            } else {
                clamp01(1f - (time - drag.demoDragUp) / (tokens.demoPressIn / 1000f))
            }
        }
        updateTapFinger(time, tokens.demoSaveAll)
        updateTapFinger(time, tokens.demoSaveAllAgain)
    }

    private fun updateTapFinger(time: Float, tapAt: Float) {
        val down = tapAt - 0.25f
        val end = tapAt + tokens.demoFingerFade
        if (time < down || time > end) {
            return
        }
        ghostX = bookmarkX
        ghostY = bookmarkY
        ghostAlpha = fadeWindow(time, down, tapAt, tokens.demoFingerFade)
        ghostPress = when {
            time < down + tokens.demoPressIn / 1000f -> clamp01((time - down) / (tokens.demoPressIn / 1000f))
            else -> clamp01(1f - (time - down - tokens.demoPressIn / 1000f) / (tokens.demoPressOut / 1000f))
        }
        pressTarget = -2
        pressAmount = ghostPress
    }

    private fun stepManual(delta: Double) {
        if (nextPileAt >= 0.0 && clock >= nextPileAt) {
            nextPileAt = -1.0
            nextPile()
        }
        if (!touching && pressAmount > 0f) {
            pressAmount = max(0f, pressAmount - delta.toFloat() / (tokens.timingPressOut / 1000f))
            if (pressAmount == 0f) {
                pressTarget = -1
            }
        }
    }

    private fun updateFlights() {
        val iterator = flights.iterator()
        var landed = false
        while (iterator.hasNext()) {
            val flight = iterator.next()
            if (clock >= flight.start + flight.duration) {
                iterator.remove()
                land(silent = autoPlay && !paused)
                landed = true
            }
        }
        if (landed) {
            maybeFinishPile()
        }
    }

    fun touchDown(x: Float, y: Float, eventSeconds: Double) {
        touching = true
        quiet = 0.0
        if (autoPlay && !paused) {
            paused = true
            ghostAlpha = 0f
            if (dragCard >= 0) {
                startBack()
            }
        }
        touchDownX = x
        touchDownY = y
        touchDownSeconds = eventSeconds
        touchCard = hit(x, y)
        pressTarget = if (hypot(x - bookmarkX, y - bookmarkY) < 44f) -2 else touchCard
        pressAmount = 1f
    }

    fun touchMove(x: Float, y: Float, eventSeconds: Double, velocityX: Float, velocityY: Float) {
        quiet = 0.0
        if (dragCard < 0 && touchCard >= 0 && eventSeconds - touchDownSeconds >= tokens.saveHoldMs / 1000.0) {
            beginDrag(touchCard, silent = false)
        }
        if (dragCard < 0 || dragMode != DRAG_ACTIVE) {
            return
        }
        lastVelocityX = velocityX
        lastVelocityY = velocityY
        updateDrag(x - touchDownX, y - touchDownY, velocityX, velocityY, silent = false)
    }

    fun touchUp(velocityX: Float, velocityY: Float) {
        touching = false
        quiet = 0.0
        lastVelocityX = velocityX
        lastVelocityY = velocityY
        if (dragCard >= 0 && dragMode == DRAG_ACTIVE) {
            releaseDrag(silent = false, forceSave = false)
        } else if (pressTarget == -2) {
            saveAll(silent = false)
        } else if (touchCard >= 0 && !gone[touchCard]) {
            fly(listOf(touchCard), silent = false)
            onHaptic(SaveHaptic.Select)
        }
        pressAmount = 0f
        pressTarget = -1
        touchCard = -1
    }

    private fun beginDrag(card: Int, silent: Boolean) {
        if (card < 0 || gone[card] || dragCard >= 0) {
            return
        }
        val rect = cardRect(card)
        dragCard = card
        dragMode = DRAG_ACTIVE
        dragX = rect.x + rect.width / 2f
        dragY = rect.y + rect.height / 2f
        dragScale = 1f
        dragTilt = 0f
        dragScaleTarget = tokens.saveLiftScale
        dragTiltTarget = 0f
        scaleVelocity = 0f
        tiltVelocity = 0f
        dragOpen = 0f
        magnetWasOpen = false
        if (!silent) {
            onHaptic(SaveHaptic.Lift)
        }
    }

    private fun updateDrag(
        translationX: Float,
        translationY: Float,
        velocityX: Float,
        velocityY: Float,
        silent: Boolean,
    ) {
        val rect = cardRect(dragCard)
        val freeX = rect.x + rect.width / 2f + translationX
        val freeY = rect.y + rect.height / 2f + translationY
        val distance = hypot(bookmarkX - freeX, bookmarkY - freeY)
        val raw = clamp01(1f - distance / tokens.saveMagnetRadius)
        val magnet = raw * raw * (3f - 2f * raw)
        if (!magnetWasOpen && magnet > 0f && !silent) {
            onHaptic(SaveHaptic.Magnet)
        }
        magnetWasOpen = magnet > 0f
        dragOpen = magnet
        dragX = freeX + (bookmarkX - freeX) * magnet * tokens.saveMagnetPull
        dragY = freeY + (bookmarkY - freeY) * magnet * tokens.saveMagnetPull
        dragScaleTarget = if (reducedMotion) {
            1f
        } else {
            tokens.saveLiftScale * (1f - tokens.saveMagnetShrink * magnet)
        }
        dragTiltTarget = if (reducedMotion) {
            0f
        } else {
            (velocityX * tokens.saveTiltPerVelocity).coerceIn(-tokens.saveTiltMax, tokens.saveTiltMax)
        }
        lastVelocityX = velocityX
        lastVelocityY = velocityY
    }

    private fun releaseDrag(silent: Boolean, forceSave: Boolean) {
        val speed = hypot(lastVelocityX, lastVelocityY)
        val targetX = bookmarkX - dragX
        val targetY = bookmarkY - dragY
        val targetLength = hypot(targetX, targetY)
        val aim = if (speed > 0f && targetLength > 0f) {
            (lastVelocityX * targetX + lastVelocityY * targetY) / (speed * targetLength)
        } else {
            0f
        }
        val flicked = speed > tokens.saveFlickSpeed && aim > tokens.saveFlickAim
        if (forceSave || dragOpen > 0.05f || flicked) {
            if (!silent) {
                onHaptic(SaveHaptic.Commit)
            }
            dragMode = DRAG_DROP
            dragAnimationStart = clock
            dragAnimationDuration = when {
                reducedMotion -> tokens.timingReduced / 1000.0
                flicked && dragOpen <= 0.05f -> tokens.timingFlickFlight / 1000.0
                else -> tokens.timingDropFlight / 1000.0
            }
            dragFromX = dragX
            dragFromY = dragY
            dragFromScale = dragScale
            dragOpen = 0f
            return
        }
        startBack()
    }

    private fun startBack() {
        dragMode = DRAG_BACK
        dragVelocityX = lastVelocityX
        dragVelocityY = lastVelocityY
        scaleVelocity = 0f
        tiltVelocity = 0f
        dragOpen = 0f
    }

    private fun updateDragAnimation(delta: Double) {
        if (dragCard < 0) {
            return
        }
        if (dragMode == DRAG_ACTIVE) {
            if (reducedMotion) {
                dragScale = dragScaleTarget
                dragTilt = 0f
                return
            }
            val seconds = delta.toFloat()
            val scale = springStep(dragScale, scaleVelocity, dragScaleTarget, tokens.springLift, seconds)
            dragScale = scale.first
            scaleVelocity = scale.second
            val tilt = springStep(dragTilt, tiltVelocity, dragTiltTarget, tokens.springTilt, seconds)
            dragTilt = tilt.first
            tiltVelocity = tilt.second
            return
        }
        if (dragMode == DRAG_DROP) {
            val progress = clamp01(((clock - dragAnimationStart) / dragAnimationDuration).toFloat())
            val eased = cubicInOut(progress)
            dragX = mix(dragFromX, bookmarkX, eased)
            dragY = mix(dragFromY, bookmarkY, eased)
            dragScale = mix(dragFromScale, tokens.saveEndScale, eased)
            dragTilt = mix(dragTilt, 0f, eased)
            if (progress >= 1f) {
                val card = dragCard
                gone[card] = true
                dragCard = -1
                dragMode = DRAG_NONE
                land(silent = autoPlay && !paused)
                maybeFinishPile()
            }
            return
        }
        if (dragMode != DRAG_BACK) {
            return
        }
        val rect = cardRect(dragCard)
        val targetX = rect.x + rect.width / 2f
        val targetY = rect.y + rect.height / 2f
        val seconds = delta.toFloat()
        val x = springStep(dragX, dragVelocityX, targetX, tokens.springBack, seconds)
        dragX = x.first
        dragVelocityX = x.second
        val y = springStep(dragY, dragVelocityY, targetY, tokens.springBack, seconds)
        dragY = y.first
        dragVelocityY = y.second
        val scale = springStep(dragScale, scaleVelocity, 1f, tokens.springBack, seconds)
        dragScale = scale.first
        scaleVelocity = scale.second
        val tilt = springStep(dragTilt, tiltVelocity, 0f, tokens.springTilt, seconds)
        dragTilt = tilt.first
        tiltVelocity = tilt.second
        if (hypot(dragX - targetX, dragY - targetY) < 0.2f && hypot(dragVelocityX, dragVelocityY) < 2f) {
            dragCard = -1
            dragMode = DRAG_NONE
            dragScale = 1f
            dragTilt = 0f
        }
    }

    private fun fly(cards: List<Int>, silent: Boolean) {
        val available = cards.filter { card ->
            !gone[card] && flights.none { flight -> flight.card == card } && card != dragCard
        }
        if (available.isEmpty()) {
            return
        }
        if (reducedMotion) {
            available.forEach { card ->
                gone[card] = true
                land(silent)
            }
            maybeFinishPile()
            return
        }
        available.forEachIndexed { index, card ->
            gone[card] = true
            flights += SaveFlight(
                id = flightSequence++,
                card = card,
                from = cardRect(card),
                start = clock + index * tokens.timingFlockStagger / 1000.0,
                duration = tokens.timingFlight / 1000.0,
            )
        }
    }

    private fun saveAll(silent: Boolean) {
        val visible = visibleIndices().reversed()
        if (visible.isEmpty()) {
            return
        }
        if (!reducedMotion) {
            rushStart = clock
        }
        fly(visible, silent)
    }

    private fun land(silent: Boolean) {
        saved += 1
        if (!reducedMotion) {
            bounceStart = clock
            ringStart = clock
        }
        if (!silent) {
            onHaptic(SaveHaptic.Land)
        }
    }

    private fun maybeFinishPile() {
        if (autoPlay && !paused) {
            return
        }
        if (visibleIndices().isNotEmpty() || flights.isNotEmpty() || dragCard >= 0 || nextPileAt >= 0.0) {
            return
        }
        pileCount = tokens.cards.count { card -> card.pile == pile }
        onHaptic(SaveHaptic.Success)
        riseStart = clock
        val up = if (reducedMotion) tokens.timingReduced else tokens.timingRise
        nextPileAt = clock + (up + tokens.timingRiseHold) / 1000.0
    }

    private fun nextPile() {
        pile = if (pile == 0) 1 else 0
        gone.fill(false)
        dealStart = clock
    }

    fun riseProgress(): Float {
        if (riseStart < 0.0) {
            return 0f
        }
        val elapsed = clock - riseStart
        val up = (if (reducedMotion) tokens.timingReduced else tokens.timingRise) / 1000.0
        val hold = tokens.timingRiseHold / 1000.0
        val down = (if (reducedMotion) tokens.timingReduced else tokens.timingRiseDown) / 1000.0
        return when {
            elapsed < 0.0 -> 0f
            elapsed < up -> cubicBezier((elapsed / up).toFloat(), tokens.riseEase)
            elapsed < up + hold -> 1f
            elapsed < up + hold + down -> cubicInOut((1.0 - (elapsed - up - hold) / down).toFloat())
            else -> 0f
        }
    }

    fun ringProgress(): Float {
        if (ringStart < 0.0 || reducedMotion) {
            return 1f
        }
        return clamp01(((clock - ringStart) / (tokens.timingLandRing / 1000.0)).toFloat())
    }

    fun bounceProgress(): Float {
        if (bounceStart < 0.0 || reducedMotion) {
            return 0f
        }
        val elapsed = (clock - bounceStart).toFloat()
        if (elapsed <= tokens.bounceUp) {
            return clamp01(elapsed / tokens.bounceUp)
        }
        val decay = max(0f, 1f - (elapsed - tokens.bounceUp) / tokens.bounceSettle)
        return decay * cos((elapsed - tokens.bounceUp) * tokens.bounceFrequency)
    }

    fun dealProgress(order: Int): Float {
        if (dealStart < 0.0) {
            return 1f
        }
        val start = dealStart + order * tokens.timingDealStagger / 1000.0
        val raw = ((clock - start) / (tokens.timingDeal / 1000.0)).toFloat()
        return cubicBezier(clamp01(raw), tokens.easeOut)
    }

    fun flightProgress(flight: SaveFlight): Float {
        return clamp01(((clock - flight.start) / flight.duration).toFloat())
    }

    private fun hit(x: Float, y: Float): Int {
        val visible = visibleIndices()
        for (position in visible.indices.reversed()) {
            val index = visible[position]
            val rect = cardRect(index)
            if (x >= rect.x && x <= rect.x + rect.width && y >= rect.y && y <= rect.y + rect.height) {
                return index
            }
        }
        return -1
    }

    private fun canResume(): Boolean {
        val pileZeroCount = tokens.cards.count { card -> card.pile == 0 }
        return quiet >= tokens.resumeMs / 1000.0 &&
            !touching &&
            dragCard < 0 &&
            riseProgress() == 0f &&
            pile == 0 &&
            visibleIndices().size == pileZeroCount
    }

    private fun resetLoop() {
        gone.fill(false)
        flights.clear()
        clock = 0.0
        scriptTime = 0.0
        pile = 0
        saved = 0
        pileCount = 0
        dealStart = -100.0
        rushStart = -1.0
        riseStart = -1.0
        ringStart = -1.0
        bounceStart = -1.0
        nextPileAt = -1.0
        pressTarget = -1
        pressAmount = 0f
        dragCard = -1
        dragMode = DRAG_NONE
        dragScale = 1f
        dragScaleTarget = 1f
        dragTilt = 0f
        dragTiltTarget = 0f
        dragOpen = 0f
        ghostAlpha = 0f
        ghostPress = 0f
        quiet = 0.0
    }

    private fun crossed(start: Double, end: Double, point: Double): Boolean {
        return start < point && end >= point
    }

    private fun fadeWindow(time: Float, start: Float, finish: Float, fade: Float): Float {
        val fadeIn = clamp01((time - start) / fade)
        val fadeOut = clamp01((finish + fade - time) / fade)
        return min(fadeIn, fadeOut)
    }

    companion object {
        private const val DRAG_NONE = 0
        private const val DRAG_ACTIVE = 1
        private const val DRAG_DROP = 2
        private const val DRAG_BACK = 3
    }
}

fun saveStreamDrops(): List<SaveStreamDrop> {
    var seed = SaveToCollectionTokens.streamSeed.toLong()
    fun random(): Float {
        seed = seed * 16807L % 2147483647L
        return ((seed - 1L).toDouble() / 2147483646.0).toFloat()
    }
    return List(SaveToCollectionTokens.streamCount) { index ->
        val big = random() < SaveToCollectionTokens.streamBigChance
        val width = if (big) {
            SaveToCollectionTokens.streamBigWidth + random() * SaveToCollectionTokens.streamBigWidthRange
        } else {
            SaveToCollectionTokens.streamSmallWidth + random() * SaveToCollectionTokens.streamSmallWidthRange
        }
        val normalizedStart = index.toFloat() / SaveToCollectionTokens.streamCount
        SaveStreamDrop(
            art = index % SaveToCollectionTokens.streaks.size,
            x = (random() - 0.5f) * 2f * SaveToCollectionTokens.streamSpread,
            width = width,
            start = normalizedStart * SaveToCollectionTokens.streamPour,
            duration = min(
                SaveToCollectionTokens.streamFall * (SaveToCollectionTokens.streamDurationMin + random() * SaveToCollectionTokens.streamDurationRange),
                1f - normalizedStart * SaveToCollectionTokens.streamPour,
            ),
            y0 = SaveToCollectionTokens.streamStartY + random() * SaveToCollectionTokens.streamStartYRange,
        )
    }
}

fun clamp01(value: Float): Float {
    return value.coerceIn(0f, 1f)
}

fun mix(start: Float, end: Float, progress: Float): Float {
    return start + (end - start) * progress
}

fun cosineEase(value: Float): Float {
    val progress = clamp01(value)
    return ((1f - cos(PI.toFloat() * progress)) / 2f)
}

fun cubicInOut(value: Float): Float {
    val progress = clamp01(value)
    return if (progress < 0.5f) {
        4f * progress * progress * progress
    } else {
        1f - (-2f * progress + 2f).let { term -> term * term * term } / 2f
    }
}

fun cubicSpeed(value: Float): Float {
    val progress = clamp01(value)
    return if (progress < 0.5f) {
        12f * progress * progress
    } else {
        3f * (-2f * progress + 2f).let { term -> term * term }
    }
}

fun cubicBezier(value: Float, curve: SaveCubicBezier): Float {
    val target = clamp01(value)
    var parameter = target
    repeat(5) {
        val oneMinus = 1f - parameter
        val x = 3f * oneMinus * oneMinus * parameter * curve.x1 +
            3f * oneMinus * parameter * parameter * curve.x2 +
            parameter * parameter * parameter
        val derivative = 3f * oneMinus * oneMinus * curve.x1 +
            6f * oneMinus * parameter * (curve.x2 - curve.x1) +
            3f * parameter * parameter * (1f - curve.x2)
        if (derivative > 0.0001f) {
            parameter = (parameter - (x - target) / derivative).coerceIn(0f, 1f)
        }
    }
    val oneMinus = 1f - parameter
    return 3f * oneMinus * oneMinus * parameter * curve.y1 +
        3f * oneMinus * parameter * parameter * curve.y2 +
        parameter * parameter * parameter
}

fun springStep(
    value: Float,
    velocity: Float,
    target: Float,
    spring: SaveSpring,
    delta: Float,
): Pair<Float, Float> {
    val acceleration = (-spring.stiffness * (value - target) - spring.damping * velocity) / spring.mass
    val nextVelocity = velocity + acceleration * delta
    return Pair(value + nextVelocity * delta, nextVelocity)
}
