package com.ngocdevv.nativecook.blocks.lists.badgevaultgrid

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

internal const val BadgeVaultDemoPeriod = 18_000f
internal const val BadgeVaultTau = (PI * 2).toFloat()

internal fun clamp(value: Float, low: Float = 0f, high: Float = 1f) = max(low, min(high, value))
internal fun lerp(a: Float, b: Float, amount: Float) = a + (b - a) * amount

internal fun cubicBezier(progress: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    if (progress <= 0f) return 0f
    if (progress >= 1f) return 1f
    var low = 0f
    var high = 1f
    var t = progress
    repeat(12) {
        val q = 1f - t
        val x = 3f * q * q * t * x1 + 3f * q * t * t * x2 + t * t * t
        if (x < progress) low = t else high = t
        t = (low + high) / 2f
    }
    return 3f * (1f - t) * (1f - t) * t * y1 + 3f * (1f - t) * t * t * y2 + t * t * t
}

internal fun ease(progress: Float) = cubicBezier(progress, 0.23f, 1f, 0.32f, 1f)

internal data class BadgeVaultSpring(var x: Float = 0f, var velocity: Float = 0f)

internal fun spring(
    value: BadgeVaultSpring,
    target: Float,
    seconds: Float,
    stiffness: Float,
    damping: Float,
): BadgeVaultSpring {
    val a = damping / 2f
    val w = sqrt(max(0.001f, stiffness - a * a))
    val delta = value.x - target
    val b = (value.velocity + a * delta) / w
    val attenuation = exp(-a * seconds)
    val sn = sin(w * seconds)
    val cs = cos(w * seconds)
    val x = target + attenuation * (delta * cs + b * sn)
    val velocity = attenuation * ((b * w - a * delta) * cs - (delta * w + a * b) * sn)
    return if (abs(x - target) < 0.00005f && abs(velocity) < 0.0001f) {
        BadgeVaultSpring(target, 0f)
    } else {
        BadgeVaultSpring(x, velocity)
    }
}

internal fun springAt(seconds: Float, stiffness: Float, damping: Float, velocity: Float = 0f) =
    spring(BadgeVaultSpring(0f, velocity), 1f, max(0f, seconds), stiffness, damping).x

private fun settleTail(seconds: Float, start: Float, end: Float): Float {
    val progress = clamp((seconds - start) / (end - start))
    return 1f - progress * progress * (3f - 2f * progress)
}

internal fun flightSpring(seconds: Float, stiffness: Float, damping: Float, start: Float, end: Float) =
    1f + (springAt(seconds, stiffness, damping) - 1f) * settleTail(seconds, start, end)

internal fun launchTurn(seconds: Float): Float {
    val clock = if (seconds < 0.95f) {
        0.95f * (max(0f, seconds) / 0.95f).pow(1.9f)
    } else {
        0.95f + (seconds - 0.95f) * 1.9f
    }
    return BadgeVaultTau * (1f + (springAt(clock, 17.76f, 5.233f) - 1f) * settleTail(seconds, 1.35f, 1.6f))
}

internal fun scrollCurve(progress: Float) = (1f - exp(-5.2f * clamp(progress))) / (1f - exp(-5.2f))

internal enum class BadgeVaultPhase { Rest, Opening, Held, Closing }
internal enum class BadgeVaultTurnMode { Launch, Free, Drag, Slow }
internal enum class BadgeVaultEvent { Selection, Warning, Light, Medium, Success, Progress }

internal data class BadgeVaultHero(
    val index: Int,
    val pose: BadgeVaultPose,
    val recede: Float,
    val alpha: Float,
    val age: Float,
    val details: Float,
    val floodSpread: Float,
    val floodPower: Float,
    val floodRays: Float,
    val floodSince: Float,
    val originX: Float,
    val originY: Float,
)

internal class BadgeVaultEngine(private val autoPlay: Boolean) {
    var now = 0f
    var demoTime = 0f
    var selected = -1
    var phase = BadgeVaultPhase.Rest
    var phaseAt = 0f
    var scrollY = 0f
    var scrollFrom = 0f
    var scrollTo = 0f
    var scrollAt = -1f
    var scrollDuration = 1500f
    var bandAt = -10_000f
    var lastIdleBand = 0f
    var rattleIndex = -1
    var rattleAt = -10_000f
    var pillAt = -10_000f
    var pillDown = false
    var pillFrom = 0f
    var turnMode = BadgeVaultTurnMode.Launch
    var turn = 0f
    var turnVelocity = 0f
    var turnTarget = 0f
    var turnFriction = 3.8f
    var turnDetent = false
    var turnSnapped = true
    var turnAt = 0f
    var turnFrom = 0f
    var turnTo = 0f
    var turnAdvancedAt = 0f
    var dragY = 0f
    var dragSpring = BadgeVaultSpring()
    var dragging = false
    var closeFrom: BadgeVaultHero? = null
    var touchAt = -1f
    var returningAt = -1f
    var touching = false
    var gestureStartTurn = 0f
    var gestureAxis = 0
    var eventCursor = 0
    var started = false
    var readyAt = -1f
    val have = IntArray(BadgeVaultBadges.size) { BadgeVaultBadges[it].goal?.have ?: 0 }
    val progressFrom = FloatArray(BadgeVaultBadges.size) { index ->
        BadgeVaultBadges[index].goal?.let { it.have.toFloat() / it.need } ?: 1f
    }
    val progressAt = FloatArray(BadgeVaultBadges.size) { -10_000f }
    val earned = BooleanArray(BadgeVaultBadges.size) { BadgeVaultBadges[it].earned != null }
    val unlockAt = FloatArray(BadgeVaultBadges.size) { -1f }
    val popAt = FloatArray(BadgeVaultBadges.size) { -10_000f }
    val popGain = FloatArray(BadgeVaultBadges.size)
    val pressAt = FloatArray(BadgeVaultBadges.size) { -10_000f }
    val pressFrom = FloatArray(BadgeVaultBadges.size)
    val pressTo = FloatArray(BadgeVaultBadges.size)
    val fingerX = FloatArray(BadgeVaultBadges.size)
    val fingerY = FloatArray(BadgeVaultBadges.size)
    val followers = Array(3) { BadgeVaultSpring() }
    var previousScroll = 0f
    var scrollVelocity = 0f
    var rippleAt = -10_000f
    var rippleX = 0f
    var rippleY = 0f
    var rippleGain = 0f
    var landed = false
    var launchPose = BadgeVaultPose()
    val haptics = ArrayDeque<BadgeVaultEvent>()

    private val events = listOf(
        DemoEvent(400f, "band", -1),
        DemoEvent(1300f, "press", 4),
        DemoEvent(1420f, "open", 4),
        DemoEvent(3700f, "flick", 1),
        DemoEvent(5100f, "close", -1),
        DemoEvent(6000f, "open", 5),
        DemoEvent(6900f, "scroll-end", -1),
        DemoEvent(8700f, "pill-in", -1),
        DemoEvent(8820f, "log", -1),
        DemoEvent(11100f, "press", 14),
        DemoEvent(11220f, "open", 14),
        DemoEvent(12900f, "slow-turn", -1),
        DemoEvent(14000f, "flick", 1),
        DemoEvent(14900f, "close", -1),
        DemoEvent(15700f, "scroll-top", -1),
        DemoEvent(17300f, "reset", -1),
    )

    fun update(deltaMs: Float, layout: BadgeVaultLayout, reduced: Boolean) {
        now += max(0f, deltaMs)
        if (readyAt < 0f) readyAt = now
        if (autoPlay && !reduced) advanceDemo(deltaMs, layout)
        if (!autoPlay && !reduced && phase == BadgeVaultPhase.Rest && now - lastIdleBand >= 6000f) {
            bandAt = now
            lastIdleBand = now
        }
        if (scrollAt >= 0f) {
            val progress = (now - scrollAt) / scrollDuration
            scrollY = lerp(scrollFrom, scrollTo, scrollCurve(progress))
            if (progress >= 1f) scrollAt = -1f
        }
        for (index in unlockAt.indices) {
            if (!earned[index] && unlockAt[index] >= 0f && now - unlockAt[index] >= if (reduced) 300f else 1150f) {
                earned[index] = true
                popAt[index] = now
                popGain[index] = 0.14f
                ripple(layout, index, 1f)
                haptics.add(BadgeVaultEvent.Success)
                haptics.add(BadgeVaultEvent.Progress)
            }
        }
        val dt = deltaMs / 1000f
        scrollVelocity = if (dt > 0f) (previousScroll - scrollY) / dt else 0f
        previousScroll = scrollY
        val target = if (reduced) 0f else clamp(scrollVelocity / 2400f, -1f, 1f) * 0.42f
        val stiffness = floatArrayOf(260f, 170f, 110f)
        val damping = floatArrayOf(20f, 14f, 10f)
        followers.indices.forEach { index ->
            followers[index] = spring(followers[index], target, min(dt, .064f), stiffness[index], damping[index])
        }
        if (!dragging && selected >= 0 && phase != BadgeVaultPhase.Closing) {
            dragSpring = spring(dragSpring, 0f, min(dt, .064f), 240f, 24f)
            dragY = dragSpring.x
        }
        layout.scrollY = scrollY
    }

    private fun advanceDemo(deltaMs: Float, layout: BadgeVaultLayout) {
        if (touchAt >= 0f) {
            if (touching || now - touchAt < 3500f) return
            if (returningAt < 0f) {
                returningAt = now
                close(layout)
                scroll(0f, 600f)
            }
            if (now - returningAt < 850f) return
            reset(layout)
            touchAt = -1f
            returningAt = -1f
            demoTime = 0f
            eventCursor = 0
            started = true
            return
        }
        if (!started) {
            if (now - readyAt < 300f) return
            started = true
            demoTime = now - readyAt - 300f
        } else {
            demoTime += deltaMs
        }
        while (true) {
            while (eventCursor < events.size && events[eventCursor].at <= demoTime) {
                runEvent(events[eventCursor], layout)
                eventCursor += 1
            }
            if (demoTime < BadgeVaultDemoPeriod) break
            demoTime -= BadgeVaultDemoPeriod
            eventCursor = 0
        }
    }

    private fun runEvent(event: DemoEvent, layout: BadgeVaultLayout) {
        when (event.action) {
            "band" -> bandAt = now
            "press" -> press(event.index, 0f, 0f)
            "open" -> open(event.index, layout)
            "flick" -> flick(13f, front = true)
            "close" -> close(layout, 900f)
            "scroll-end" -> scroll(layout.scrollEnd, 1500f)
            "pill-in" -> changePillDown(true)
            "log" -> { changePillDown(false); logDay() }
            "slow-turn" -> slowTurn()
            "scroll-top" -> scroll(0f, 1500f)
            "reset" -> reset(layout)
        }
    }

    fun touched() {
        touching = true
        touchAt = now
        returningAt = -1f
        scrollAt = -1f
        gestureStartTurn = currentTurn()
        gestureAxis = 0
    }

    fun touchEnded() {
        touching = false
        touchAt = now
        dragging = false
    }

    fun open(index: Int, layout: BadgeVaultLayout) {
        if (index !in BadgeVaultBadges.indices || phase != BadgeVaultPhase.Rest) return
        if (!earned[index]) {
            rattleIndex = index
            rattleAt = now
            haptics.add(BadgeVaultEvent.Warning)
            return
        }
        launchPose = cellPose(layout, index, false)
        release(index)
        selected = index
        phase = BadgeVaultPhase.Opening
        phaseAt = now
        turnMode = BadgeVaultTurnMode.Launch
        turn = 0f
        turnVelocity = 0f
        dragY = 0f
        dragSpring = BadgeVaultSpring()
        landed = false
        ripple(layout, index, 1f)
        haptics.add(BadgeVaultEvent.Selection)
        layout.scrollY = scrollY
    }

    fun close(layout: BadgeVaultLayout, velocityY: Float = 0f) {
        if (selected < 0 || phase == BadgeVaultPhase.Closing) return
        closeFrom = hero(layout, false)
        phase = BadgeVaultPhase.Closing
        phaseAt = now
        turnVelocity = velocityY
        turnTo = round(currentTurn() / BadgeVaultTau) * BadgeVaultTau
        dragging = false
    }

    fun scroll(target: Float, duration: Float) {
        scrollAt = now
        scrollFrom = scrollY
        scrollTo = target
        scrollDuration = duration
    }

    fun logDay() {
        var stagger = 0f
        BadgeVaultBadges.forEachIndexed { index, badge ->
            val goal = badge.goal ?: return@forEachIndexed
            if (earned[index] || unlockAt[index] >= 0f) return@forEachIndexed
            progressFrom[index] = progress(index)
            have[index] = min(goal.need, have[index] + 1)
            progressAt[index] = now
            if (have[index] == goal.need) {
                unlockAt[index] = now + 300f + stagger
                stagger += 250f
            }
        }
        haptics.add(BadgeVaultEvent.Progress)
    }

    fun slowTurn() {
        if (selected < 0) return
        val start = currentTurn()
        turnMode = BadgeVaultTurnMode.Slow
        turnAt = now
        turnFrom = start
        turnTo = round((turnFrom - PI.toFloat()) / BadgeVaultTau) * BadgeVaultTau + PI.toFloat()
    }

    fun flick(velocity: Float, front: Boolean = false) {
        if (selected < 0) return
        val start = currentTurn()
        turnMode = BadgeVaultTurnMode.Free
        turn = start
        turnVelocity = velocity
        turnAdvancedAt = now
        val interval = if (front) BadgeVaultTau else PI.toFloat()
        turnTarget = round((turn + velocity / 3.8f) / interval) * interval
        turnFriction = if (velocity * (turnTarget - turn) > 0f) clamp(velocity / (turnTarget - turn), 1.5f, 6f) else 3.8f
        turnDetent = false
        turnSnapped = false
    }

    fun horizontalDrag(deltaX: Float, heldRadius: Float) {
        if (selected < 0) return
        turnMode = BadgeVaultTurnMode.Drag
        turn = gestureStartTurn + deltaX / (2f * heldRadius) * PI.toFloat()
    }

    fun verticalDrag(deltaY: Float) {
        dragging = true
        dragY = when {
            deltaY < 0f -> deltaY * 0.12f
            deltaY <= 160f -> deltaY
            else -> 160f + (deltaY - 160f) * 0.28f
        }
        dragSpring.x = dragY
    }

    fun finishDrag(layout: BadgeVaultLayout, velocityX: Float, velocityY: Float) {
        if (gestureAxis == 1) flick(velocityX / (2f * layout.heldR) * PI.toFloat())
        if (gestureAxis == 2 && (dragY > 90f || velocityY > 700f)) close(layout, velocityY)
        dragging = false
        if (gestureAxis == 2) dragSpring.velocity = velocityY
    }

    private fun reset(layout: BadgeVaultLayout) {
        BadgeVaultBadges.forEachIndexed { index, badge ->
            have[index] = badge.goal?.have ?: 0
            progressFrom[index] = badge.goal?.let { it.have.toFloat() / it.need } ?: 1f
            progressAt[index] = -10_000f
            earned[index] = badge.earned != null
            unlockAt[index] = -1f
            popAt[index] = -10_000f
            popGain[index] = 0f
            pressAt[index] = -10_000f
            pressFrom[index] = 0f
            pressTo[index] = 0f
        }
        selected = -1
        phase = BadgeVaultPhase.Rest
        closeFrom = null
        scrollY = 0f
        scrollAt = -1f
        layout.scrollY = 0f
        previousScroll = 0f
        scrollVelocity = 0f
        rippleAt = -10_000f
        followers.indices.forEach { followers[it] = BadgeVaultSpring() }
    }

    fun changePillDown(down: Boolean) {
        if (pillDown == down) return
        pillFrom = pillPress()
        pillDown = down
        pillAt = now
        if (down) haptics.add(BadgeVaultEvent.Light)
    }

    fun press(index: Int, x: Float, y: Float) {
        if (index !in BadgeVaultBadges.indices || phase != BadgeVaultPhase.Rest) return
        pressFrom[index] = pressAmount(index)
        pressTo[index] = 1f
        pressAt[index] = now
        fingerX[index] = clamp(x, -1f, 1f)
        fingerY[index] = clamp(y, -1f, 1f)
    }

    fun release(index: Int) {
        if (index !in BadgeVaultBadges.indices) return
        pressFrom[index] = pressAmount(index)
        pressTo[index] = 0f
        pressAt[index] = now
    }

    fun pressAmount(index: Int): Float {
        val age = now - pressAt[index]
        val progress = if (pressTo[index] > 0f) ease(age / 110f) else springAt(age / 1000f, 320f, 22f)
        return pressFrom[index] + (pressTo[index] - pressFrom[index]) * progress
    }

    fun ripple(layout: BadgeVaultLayout, index: Int, gain: Float) {
        rippleAt = now
        rippleX = halfPoint(layout.columnLeft(index % 3) + layout.columnWidth(index % 3) / 2f)
        rippleY = layout.rowTop(index) + layout.badgeY
        rippleGain = gain
    }

    fun cellPose(layout: BadgeVaultLayout, index: Int, reduced: Boolean): BadgeVaultPose {
        val column = index % 3
        val x = halfPoint(layout.columnLeft(column) + layout.columnWidth(column) / 2f)
        val y = layout.rowTop(index) + layout.badgeY
        val press = pressAmount(index)
        val rattle = clamp((now - rattleAt) / 460f)
        val shake = if (reduced || rattleIndex != index) 0f else 7f * sin(5f * PI.toFloat() * rattle) * (1f - rattle).pow(1.5f)
        val h = ((index * 73 + 19) % 101) / 101f
        val swing = if (reduced) 0f else followers[column].x * (1f - h) + followers[(index + 1) % 3].x * h
        var sheen = -1f
        var rippleTiltX = 0f
        var rippleTiltY = 0f
        if (!reduced) {
            val sweep = ((now - bandAt) / 1000f - (x + y) / 900f) / .7f
            if (sweep in 0f..1f) sheen = sweep
            if (abs(scrollVelocity) > 500f) sheen = clamp(y / layout.height)
            val dx = x - rippleX
            val dy = y - rippleY
            val range = sqrt(dx * dx + dy * dy)
            val travelled = (now - rippleAt) * 1.1f
            val separation = range - travelled
            if (abs(separation) < 210f && travelled >= 0f) {
                val strength = exp(-.5f * separation * separation / 4900f) * exp(-range / 1000f) * rippleGain
                rippleTiltX = -dy / max(1f, range) * .30f * strength
                rippleTiltY = dx / max(1f, range) * .30f * strength
                sheen = clamp((travelled - range + 105f) / 210f)
            }
            if (popGain[index] > 0f && now - popAt[index] < 450f) sheen = clamp((now - popAt[index]) / 450f)
        }
        val popAge = (now - popAt[index]) / 1000f
        val pop = if (reduced || popAge > 1.5f) 1f else 1f + popGain[index] * (1f - springAt(popAge, if (popGain[index] > 0f) 300f else 320f, if (popGain[index] > 0f) 14f else 18f))
        val rising = if (!reduced && !earned[index] && unlockAt[index] >= 0f) ease((now - unlockAt[index]) / 300f) else 0f
        val unlockClock = if (unlockAt[index] >= 0f) clamp((now - unlockAt[index]) / if (reduced) 300f else 1150f) else 0f
        val size = if (earned[index]) pop else 1f + .14f * rising
        val lift = if (reduced) 0f else if (earned[index] && popGain[index] > 0f) .5f * clamp((pop - 1f) / .14f) else .5f * rising
        return BadgeVaultPose(
            cx = x + shake,
            cy = y,
            r = min(57f * layout.scale, layout.radius * (1f - .05f * press) * size),
            tiltX = swing - fingerY[index] * .24f * press + rippleTiltX,
            tiltY = fingerX[index] * .24f * press + shake / 7f * .2f + rippleTiltY,
            roll = shake / 7f * .06f,
            envX = clamp((x - layout.width / 2f) / (layout.width / 2f), -1f, 1f),
            envY = clamp((y - layout.height / 2f) / (layout.height / 2f), -1f, 1f),
            sheen = sheen,
            fill = fill(index, reduced),
            wave = if (reduced || unlockAt[index] < 0f || now < unlockAt[index] || unlockClock >= 1f) 0f else 1f - clamp((unlockClock - .85f) / .15f),
            time = if (unlockAt[index] >= 0f && unlockClock < 1f) unlockClock * 1.15f else 0f,
            lift = lift,
            present = if (selected == index) 0f else 1f,
            socket = 1f,
        )
    }

    fun pillPress(): Float {
        val target = if (pillDown) 1f else 0f
        return pillFrom + (target - pillFrom) * ease((now - pillAt) / 110f)
    }

    fun fill(index: Int, reduced: Boolean): Float {
        if (earned[index]) return 1f
        val start = unlockAt[index]
        if (start < 0f) return 0f
        val duration = if (reduced) 300f else 1150f
        return if (reduced) clamp((now - start) / duration) else cubicBezier(clamp((now - start) / duration), 0.3f, 0.7f, 0.3f, 1f)
    }

    fun progress(index: Int): Float {
        val goal = BadgeVaultBadges[index].goal ?: return 1f
        val target = have[index].toFloat() / goal.need
        return progressFrom[index] + (target - progressFrom[index]) * ease((now - progressAt[index]) / 300f)
    }

    fun hero(layout: BadgeVaultLayout, reduced: Boolean): BadgeVaultHero {
        if (selected < 0) return emptyHero()
        val age = now - phaseAt
        val seconds = age / 1000f
        val closing = phase == BadgeVaultPhase.Closing
        val recess = cubicBezier(age / if (reduced) 200f else 500f, .32f, .72f, 0f, 1f)
        val recede = if (closing) (closeFrom?.recede ?: 1f) * (1f - recess) else recess
        val home = cellHome(layout, selected, recede)
        val drag = if (reduced) 0f else dragY
        val shrink = 1f - 0.3f * clamp(drag / 300f)
        val xProgress = if (reduced) 1f else flightSpring(seconds, 80f, 15f, 0.35f, 0.45f)
        val yProgress = if (reduced) 1f else flightSpring(seconds, 42f, 11f, 0.50f, 0.62f)
        val radiusProgress = if (reduced) 1f else flightSpring(seconds, 52f, 8.8f, 0.72f, 0.9f)
        var cx = lerp(home.first, layout.heldX, xProgress)
        var cy = lerp(home.second, layout.heldY, yProgress) + drag
        var radius = lerp(launchPose.r, layout.heldR, radiusProgress) * shrink
        var tiltX = if (reduced) 0f else launchPose.tiltX * (1f - clamp(yProgress)) + 0.25f * sin(PI.toFloat() * clamp(age / 600f))
        var tiltY = if (reduced) 0f else launchPose.tiltY * (1f - clamp(xProgress))
        var spin = if (reduced) 0f else currentTurn()
        var details = 1f - clamp(drag / 300f)
        var alpha = if (reduced) clamp(age / 200f) else 1f
        var spread = if (reduced) alpha else ease((age - 60f) / 700f)
        var power = details
        var driftX = 0f
        var driftY = 0f
        if (!reduced && !closing && age > 900f) {
            val sway = min(1f, (age - 900f) / 500f)
            val driftPhase = (age - 900f) / 5500f * BadgeVaultTau
            driftX = sin(driftPhase) * sway
            driftY = cos(driftPhase) * sway
            tiltX += driftX * .09f
            tiltY += driftY * .09f
        }
        if (!closing && age >= (if (reduced) 200f else 620f) && !landed) {
            landed = true
            haptics.add(BadgeVaultEvent.Medium)
        }
        if (!closing && age >= 1600f) phase = BadgeVaultPhase.Held
        if (closing) {
            val from = closeFrom ?: return emptyHero()
            val progress = if (reduced) 1f else flightSpring(seconds, 75f, 15f, 0.42f, 0.56f)
            cx = lerp(from.pose.cx, home.first, progress)
            val impulse = if (reduced) 0f else spring(BadgeVaultSpring(0f, turnVelocity), 0f, seconds, 75f, 15f).x * settleTail(seconds, 0.42f, 0.56f)
            cy = lerp(from.pose.cy, home.second, progress) + impulse
            radius = lerp(from.pose.r, layout.radius, progress) * (if (reduced) 1f else 1f - .04f * ease((age - 500f) / 60f))
            spin = if (reduced) 0f else lerp(from.pose.spin, turnTo, progress)
            tiltX = if (reduced) 0f else from.pose.tiltX * (1f - clamp(progress)) - 0.3f * sin(PI.toFloat() * clamp(age / 560f))
            tiltY = from.pose.tiltY * (1f - clamp(progress))
            details = from.details * (1f - clamp(age / 140f))
            spread = from.floodSpread * (1f - ease(age / 420f))
            power = from.floodPower * (1f - ease(age / 420f))
            alpha = if (reduced) 1f - clamp(age / 200f) else 1f
            if (age >= if (reduced) 200f else 560f) {
                popAt[selected] = now
                popGain[selected] = -.04f
                ripple(layout, selected, .5f)
                haptics.add(BadgeVaultEvent.Light)
                selected = -1
                phase = BadgeVaultPhase.Rest
                closeFrom = null
                dragY = 0f
                return emptyHero()
            }
        }
        val envX = clamp((cx - layout.width / 2f) / (layout.width / 2f), -1f, 1f)
        val envY = clamp((cy - layout.height / 2f) / (layout.height / 2f), -1f, 1f)
        val envReturn = if (reduced) 0f else flightSpring(seconds, 75f, 15f, .42f, .56f)
        val sourceSheen = if (closing) closeFrom?.pose?.sheen ?: -1f else launchPose.sheen
        val sheenBand = if (reduced) -1f else if (closing) {
            if (sourceSheen < 0f) -1f else sourceSheen + age / 500f
        } else (age - 620f) / 500f
        return BadgeVaultHero(
            index = selected,
            pose = BadgeVaultPose(
                cx = cx,
                cy = cy,
                r = radius,
                tiltX = tiltX,
                tiltY = tiltY,
                spin = spin,
                envX = if (closing) lerp(closeFrom?.pose?.envX ?: envX, envX, envReturn) else clamp(envX + .35f * driftX, -1f, 1f),
                envY = if (closing) lerp(closeFrom?.pose?.envY ?: envY, envY, envReturn) else clamp(envY + .35f * driftY, -1f, 1f),
                sheen = if (sheenBand in 0f..1f) sheenBand else if (!closing && age < 100f) sourceSheen else -1f,
                lift = if (closing) (closeFrom?.pose?.lift ?: 1f) * (1f - ease((age - 380f) / 180f)) else if (reduced) 1f else ease(age / 180f),
                socket = 0f,
            ),
            recede = recede,
            alpha = alpha,
            age = if (closing) closeFrom?.age ?: age else age,
            details = details,
            floodSpread = spread,
            floodPower = power,
            floodRays = if (closing) (closeFrom?.floodRays ?: 1f) * (1f - ease(age / 420f)) else ease((age - 620f) / 400f),
            floodSince = if (reduced) -1f else if (closing) (closeFrom?.floodSince ?: -1f) + seconds else if (age < 620f) -1f else (age - 620f) / 1000f,
            originX = home.first,
            originY = home.second,
        )
    }

    private fun currentTurn(): Float {
        val age = now - phaseAt
        when (turnMode) {
            BadgeVaultTurnMode.Launch -> turn = launchTurn(age / 1000f)
            BadgeVaultTurnMode.Slow -> {
                turn = lerp(turnFrom, turnTo, clamp((now - turnAt) / 700f))
                if (now - turnAt >= 700f) {
                    turnMode = BadgeVaultTurnMode.Drag
                    haptics.add(BadgeVaultEvent.Light)
                }
            }
            BadgeVaultTurnMode.Free -> {
                val dt = ((now - turnAdvancedAt) / 1000f).coerceIn(0f, 0.064f)
                turnAdvancedAt = now
                val steps = max(1, ceil(dt * 240f).toInt())
                repeat(steps) {
                    val slice = dt / steps
                    if (!turnDetent && abs(turnVelocity) > 1.8f) {
                        val decay = exp(-turnFriction * slice)
                        turn += turnVelocity * (1f - decay) / turnFriction
                        turnVelocity *= decay
                    } else {
                        turnDetent = true
                        val moved = spring(BadgeVaultSpring(turn, turnVelocity), turnTarget, slice, 85f, 13f)
                        turn = moved.x
                        turnVelocity = moved.velocity
                        if (abs(turn - turnTarget) < .012f && abs(turnVelocity) < .07f) {
                            turn = turnTarget
                            turnVelocity = 0f
                            if (!turnSnapped) haptics.add(BadgeVaultEvent.Light)
                            turnSnapped = true
                        }
                    }
                }
            }
            BadgeVaultTurnMode.Drag -> Unit
        }
        return turn
    }

    private fun cellHome(layout: BadgeVaultLayout, index: Int, recede: Float): Pair<Float, Float> {
        val column = index % 3
        val scale = 1f - 0.06f * recede
        val x = halfPoint(layout.columnLeft(column) + layout.columnWidth(column) / 2f)
        val rowY = layout.headerHeight + (index / 3) * layout.pitch - scrollY
        return Pair(
            layout.width / 2f + (x - layout.width / 2f) * scale,
            layout.height / 2f + (rowY + layout.badgeY - layout.height / 2f) * scale,
        )
    }

    private fun emptyHero() = BadgeVaultHero(-1, BadgeVaultPose(present = 0f), 0f, 0f, 0f, 0f, 0f, 0f, 0f, -1f, 0f, 0f)

    private data class DemoEvent(val at: Float, val action: String, val index: Int)
}
