package com.ngocdevv.nativecook.blocks.cards.elasticfoodcards

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

internal fun elasticFoodCardsClipTop(f: ElasticFoodCardsFrame): Double {
    val desiredTop = min(381.0, 389 + 148 * min(0.0, f.q[4]))
    return max(desiredTop, min(381.0, 379 + 148 * f.q[3]))
}

internal class ElasticFoodCardsDriver(private val motion: ElasticFoodCardsMotion) {
    private var epoch = elasticFoodCardsNow()
    private var held = false
    var heroPan = false
        private set
    private var panStart = ElasticFoodCardsFrame()
    private var drive: ElasticFoodCardsDrive? = null
    private var searchSnapshot: ElasticFoodCardsFrame? = null
    private var lastTouch = epoch
    private var quietResetAllowed = true
    private var detentPlayed = false
    private var saved = BooleanArray(4)
    private val savedFrom = DoubleArray(4)
    private val savedTarget = DoubleArray(4)
    private val savedDate = DoubleArray(4) { -100.0 }
    private var scroll = 0.0
    private var scrollFrom = 0.0
    private var scrollVelocity = 0.0
    private var scrollDate = -100.0
    var maximumScroll = 0.0
    var selectedCategory = 0
    var searchQuery = ""
    val hasManual get() = drive != null
    val isHeld get() = held
    fun heldSettled(now: Double) = held && now - lastTouch >= 2
    fun isSaved(index: Int) = saved[index]
    fun savedMix(index: Int, now: Double, reduced: Boolean): Double {
        if (reduced) return if (saved[index]) 1.0 else 0.0
        val u = ElasticFoodCardsMath.bezier((now - savedDate[index]) / 0.12, 0.42, 0.0, 0.58, 1.0)
        return ElasticFoodCardsMath.mix(savedFrom[index], savedTarget[index], u)
    }
    fun toggleSaved(index: Int, now: Double, reduced: Boolean) {
        savedFrom[index] = savedMix(index, now, reduced); saved[index] = !saved[index]
        savedTarget[index] = if (saved[index]) 1.0 else 0.0; savedDate[index] = now
    }
    fun frame(now: Double, autoPlay: Boolean, reduced: Boolean, searching: Boolean, preview: Double?): ElasticFoodCardsFrame {
        if (reduced) return ElasticFoodCardsFrame()
        if (preview != null) return motion.frame(preview)
        searchSnapshot?.let { return it }
        val currentDrive = drive ?: return motion.frame(if (autoPlay) now - epoch else 0.0)
        val current = currentDrive.frame(now)
        if (held || searching) return current
        val idle = now - lastTouch - 2
        if (idle <= 0) return current
        val origin = currentDrive.frame(lastTouch + 2)
        val duration = motion.recoveryDuration(origin)
        return if (idle < duration) motion.recovery(idle, origin) else motion.frame(if (autoPlay) idle - duration else 0.0)
    }
    fun beginPan(startY: Double, now: Double, autoPlay: Boolean, reduced: Boolean) {
        panStart = frame(now, autoPlay, reduced, false, null)
        drive = ElasticFoodCardsDrive(panStart, now).also { it.append(panStart, now, true) }
        held = true; detentPlayed = panStart.q[0] >= 0.9
        heroPan = scrollOffset(now) <= 0.5 && startY <= 220 + 148 * max(0.0, panStart.q[0])
        setScroll(scrollOffset(now)); lastTouch = now
    }
    fun movePan(dx: Double, dy: Double, now: Double): Boolean {
        if (!held) return false
        lastTouch = now
        if (!heroPan) return false
        val target = panStart.q[0] + ElasticFoodCardsMath.rubber(panStart.q[0] + dy / 148) - ElasticFoodCardsMath.rubber(panStart.q[0])
        val delta = target - panStart.q[0]
        val blend = ElasticFoodCardsMath.smooth(abs(dy) / 60)
        val pose = ElasticFoodCardsFrame.open(target, panStart.handAngle + ElasticFoodCardsMath.clamp(dx * 0.03, -6.0, 6.0))
        pose.handBX += ElasticFoodCardsMath.clamp(dx * 0.04, -12.0, 12.0)
        val r = ElasticFoodCardsMath.rotated(4.0, 16.0, pose.handAngle)
        pose.gatherX = pose.handBX - r.first; pose.gatherY = pose.handBY - r.second
        val next = panStart.mixed(pose, ElasticFoodCardsMath.smooth(abs(dy) / 40))
        next.q = DoubleArray(6) { ElasticFoodCardsMath.mix(panStart.q[it] + delta, target, blend) }; next.q[5] = next.q[4]
        drive?.append(next, now, true)
        if (target >= 0.9 && !detentPlayed) { detentPlayed = true; return true }
        return false
    }
    fun finishPan(velocity: Double, now: Double) {
        if (!held) return
        val current = drive?.frame(now) ?: panStart
        held = false
        val initial = current.deepCopy(); initial.qVel = DoubleArray(6) { current.qVel[it].coerceIn(-700.0 / 148, 700.0 / 148) }
        val q = if (current.q[0] >= 0.55 || velocity >= 650) 1.0 else 0.0
        val target = if (heroPan) ElasticFoodCardsFrame.open(q, current.handAngle) else current
        drive = ElasticFoodCardsDrive(initial, now).also { it.append(target, now, !heroPan || q >= current.q[0]) }
        heroPan = false; lastTouch = now
    }
    fun registerAction(now: Double, autoPlay: Boolean, reduced: Boolean, searching: Boolean) {
        val current = searchSnapshot ?: frame(now, autoPlay, reduced, searching, null)
        quietResetAllowed = !reduced
        searchSnapshot = null; held = false; heroPan = false
        drive = ElasticFoodCardsDrive(current, now).also { it.append(current, now, true) }; lastTouch = now
    }
    fun presentSearch(now: Double, autoPlay: Boolean, reduced: Boolean) {
        searchSnapshot = frame(now, autoPlay, reduced, false, null).deepCopy()
    }
    fun refresh(now: Double, reduced: Boolean) {
        if (reduced) return
        val current = drive?.frame(now) ?: motion.frame(0.0)
        drive = ElasticFoodCardsDrive(current, now).also { it.append(ElasticFoodCardsFrame.open(1.0), now, true) }
    }
    fun resetDelay(now: Double): Double? {
        if (!quietResetAllowed) return null
        val current = drive ?: return null
        if (held) return null
        return max(0.0, 2 + motion.recoveryDuration(current.frame(lastTouch + 2)) - (now - lastTouch))
    }
    fun complete(now: Double, searching: Boolean) {
        if (drive != null && !held && !searching && (resetDelay(now) ?: 1.0) <= 0) {
            drive = null; epoch = now; clearControls(); resetScroll()
        }
    }
    fun cancelLifecycle() { held = false; heroPan = false; scrollVelocity = 0.0 }
    fun reset(now: Double) { held = false; heroPan = false; drive = null; searchSnapshot = null; quietResetAllowed = true; epoch = now; clearControls(); resetScroll() }
    private fun clearControls() {
        saved = BooleanArray(4); selectedCategory = 0; searchQuery = ""
        savedFrom.fill(0.0); savedTarget.fill(0.0); savedDate.fill(-100.0)
    }
    fun resetScroll() { scroll = 0.0; scrollFrom = 0.0; scrollVelocity = 0.0; scrollDate = -100.0 }
    fun scrollOffset(now: Double): Double {
        val age = max(0.0, now - scrollDate)
        return (if (scrollVelocity == 0.0) scroll else scrollFrom + scrollVelocity * (1 - exp(-8 * min(1.0, age))) / 8).coerceIn(0.0, maximumScroll)
    }
    fun scrollMoving(now: Double) = scrollVelocity != 0.0 && now - scrollDate < 1
    fun setScroll(value: Double) { scroll = value.coerceIn(0.0, maximumScroll); scrollFrom = scroll; scrollVelocity = 0.0 }
    fun scrollBy(amount: Double) = setScroll(scrollOffset(elasticFoodCardsNow()) + amount)
    fun flingScroll(velocity: Double, now: Double) { scrollFrom = scrollOffset(now); scroll = scrollFrom; scrollVelocity = velocity.coerceIn(-1400.0, 1400.0); scrollDate = now }
    fun canScroll(now: Double, autoPlay: Boolean, reduced: Boolean) = frame(now, autoPlay, reduced, false, null).q[0] <= 0.02 && !(held && heroPan)
    fun hit(x: Double, y: Double, f: ElasticFoodCardsFrame, now: Double, extra: Double): ElasticFoodCardsAction? {
        fun inside(left: Double, top: Double, w: Double, h: Double) = x >= left && x <= left + w && y >= top && y <= top + h
        if (inside(22.0, 66 + 148 * f.q[0], 44.0, 44.0)) return ElasticFoodCardsAction.Search
        if (inside(293.0, 132.5 + 148 * f.q[1], 84.0, 44.0)) return ElasticFoodCardsAction.All
        val categoryY = 194 + 148 * f.q[2]
        for ((index, pair) in arrayOf(16.0 to 117.0, 139.0 to 108.0, 253.0 to 104.0, 363.0 to 104.0).withIndex()) {
            if (inside(pair.first, categoryY, pair.second, 45.0)) return ElasticFoodCardsAction.Category(index)
        }
        val scroll = scrollOffset(now)
        for (index in 3 downTo 0) {
            val row = index / 2; val cardX = if (index % 2 == 0) 16.0 else 203.0
            val cardY = 389 + row * (238 + extra) + 148 * f.q[row + 4] - scroll
            if (y >= elasticFoodCardsClipTop(f) && y <= 852 && inside(cardX + 1.5, cardY + 7.5, 44.0, 44.0)) return ElasticFoodCardsAction.Save(index)
        }
        return null
    }
}
