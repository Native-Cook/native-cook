package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

import kotlin.math.*

// One owner on the Compose frame clock. Angles are radians, lengths are stage points.
// The 600 Hz kernel retains the source analytic springs and transition thresholds.
private const val PEEK_SINK = 16.0

internal fun clamp(value: Double, low: Double = 0.0, high: Double = 1.0): Double = max(low, min(high, value))

private fun flatLine() = DoubleArray(73)

internal fun toward(value: Double, target: Double, amount: Double): Double {
    return value + clamp(target - value, -amount, amount)
}

internal fun spring(position: Double, velocity: Double, target: Double, stiffness: Double, damping: Double, dt: Double): SpringValue {
    val decay = damping / 2.0
    val frequency = sqrt(stiffness - decay * decay)
    val delta = position - target
    val envelope = exp(-decay * dt)
    val cosine = cos(frequency * dt)
    val sine = sin(frequency * dt)
    return SpringValue(position = target + envelope * (delta * cosine + (velocity + decay * delta) * sine / frequency), velocity = envelope * (velocity * cosine - (decay * velocity + stiffness * delta) * sine / frequency))
}

internal fun idlePose(s: TearState, dt: Double): Unit {
    val p = s.pose
    val resting = !s.down && (s.phase == "sealed" || s.phase == "revealed")
    s.idle = toward(s.idle, (if (resting) 1.0 else 0.0), dt / ((if (resting) 0.5 else 0.15)))
    val slow = 2.0 * PI * s.clock / MOTION.idleSlow
    val fast = 2.0 * PI * s.clock / MOTION.idleFast
    p.swayY = (if (s.reduced) 0.0 else s.idle * 0.055 * sin(slow))
    if (!s.reduced && (s.stageKick != 0.0 || s.stageVelocity != 0.0)) {
        val kick = spring(s.stageKick, s.stageVelocity, 0.0, MOTION.stageKick.stiffness, MOTION.stageKick.damping, dt)
        s.stageKick = kick.position
        s.stageVelocity = kick.velocity
    }
    p.swayX = (if (s.reduced) 0.0 else s.idle * 0.025 * sin(fast + 0.8) + s.stageKick)
    val finger = s.down && s.hold != "none"
    val peekLamp = (if (s.phase == "peek") 0.3 * s.peekLife * sin(2.0 * PI * s.age / 2.6) else 0.0)
    val lx = (if (finger) clamp((s.fingerX - 201.0) / 130.0, -1.0, 1.0) else (if (s.reduced) 0.0 else s.idle * 0.25 * sin(slow + 2.0) + peekLamp))
    val ly = (if (finger) clamp((s.fingerY - 364.0) / 200.0, -1.0, 1.0) else (if (s.reduced) 0.0 else s.idle * 0.15 * sin(fast)))
    if (s.phase != "revealing") {
        val blend = 1.0 - exp(-dt / MOTION.lightTau)
        p.lightX = (if (!finger && s.idle == 1.0) lx else p.lightX + (lx - p.lightX) * blend)
        p.lightY = (if (!finger && s.idle == 1.0) ly else p.lightY + (ly - p.lightY) * blend)
    }
    val calmGoal = (if (s.phase == "sealed" && !s.down) 1.0 else 0.0)
    p.calm = toward(p.calm, calmGoal, dt / ((if (calmGoal != 0.0) 0.6 else 0.15)))
}

internal fun initialTear(reduced: Boolean = false): TearState {
    val state = TearState(reduced = reduced)
    idlePose(state, 0.0)
    return state
}

internal fun emit(s: TearState, event: Double): Unit {
    if (!s.silent) s.events.add(event)
}

internal fun bezier(t: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
    if (t <= 0.0) return 0.0
    if (t >= 1.0) return 1.0
    var low = 0.0
    var high = 1.0
    var u = t
    run {
        var i = 0.0
        while (i < 18.0) {
            val one = 1.0 - u
            val x = 3.0 * one * one * u * x1 + 3.0 * one * u * u * x2 + u * u * u
            if (x < t) low = u else high = u
            u = (low + high) / 2.0
            i++
        }
    }
    val one = 1.0 - u
    return 3.0 * one * one * u * y1 + 3.0 * one * u * u * y2 + u * u * u
}

internal fun ease(t: Double): Double {
    return bezier(clamp(t), 0.23, 1.0, 0.32, 1.0)
}

internal fun hero(t: Double): Double {
    return bezier(clamp(t), 0.77, 0.0, 0.175, 1.0)
}

internal fun phase(s: TearState, next: String): Unit {
    s.phase = next
    s.age = 0.0
}

internal fun openPack(s: TearState): Unit {
    val p = s.pose
    p.tearFront = PACK.width
    p.stripFree = 1.0
    s.stripVX = clamp(s.vx * 0.5, 40.0, 320.0)
    s.stripVY = -260.0
    p.burst = (if (s.reduced) 0.4 else 1.0)
    s.openAge = 0.0
    s.coast = 0.0
    s.autoTear = false
    s.hold = "none"
    phase(s, "opened")
    emit(s, HAPTIC.open)
}

internal fun advanceFront(s: TearState, goal: Double, localY: Double): Unit {
    val p = s.pose
    val before = p.tearFront
    val next = clamp(max(before, goal), 0.0, PACK.width)
    if (next <= before) return
    val wanted = TEAR.wander * tanh((localY - TEAR.y - s.grabDy) / TEAR.wander)
    val end = clamp(wanted, s.lineEnd - 0.8 * (next - before), s.lineEnd + 0.8 * (next - before))
    run {
        var i = 0.0
        while (i < TEAR.columns) {
            val column = i * TEAR.step
            if (column > before && column <= next) {
                val value = s.lineEnd + (end - s.lineEnd) * (column - before) / (next - before)
                p.tearLine[(i).toInt()] = clamp(value, p.tearLine[(i - 1.0).toInt()] - 0.8 * TEAR.step, p.tearLine[(i - 1.0).toInt()] + 0.8 * TEAR.step)
            } else if (column > next) p.tearLine[(i).toInt()] = end
            i++
        }
    }
    s.lineEnd = end
    s.lineX = next
    s.lineY = localY
    p.tearFront = next
    if (!s.advanced) {
        s.advanced = true
        emit(s, HAPTIC.grab)
    }
    run {
        var tooth = floor(before / MOTION.tearTick) + 1.0
        while (tooth <= floor(next / MOTION.tearTick)) {
            emit(s, HAPTIC.tooth)
            tooth++
        }
    }
    if (next > 6.0) p.leak = 0.15 + 0.85 * next / PACK.width
    if (next >= PACK.width - 2.0) openPack(s)
}

internal fun reveal(s: TearState): Unit {
    s.revealX = s.pose.cardX
    s.revealY = s.pose.cardY
    s.revealTiltX = s.pose.cardTiltX
    s.revealTiltY = s.pose.cardTiltY
    s.packVY = -60.0
    s.faceAge = -1.0
    s.hold = "none"
    phase(s, "revealing")
    emit(s, HAPTIC.reveal)
}

internal fun face(s: TearState): Unit {
    if (s.faceAge >= 0.0) return
    s.faceAge = 0.0
    s.pose.sparkleAge = (if (s.reduced) -1.0 else 0.0)
    if (!s.reduced) {
        s.cardVS += 0.9
        s.stageKick += 0.03
        s.pose.swayX += 0.03
    }
    emit(s, HAPTIC.face)
}

internal fun collect(s: TearState): Unit {
    s.collectY = s.pose.cardY
    s.collectScale = s.pose.cardScale
    s.newPack = false
    s.hold = "none"
    if (!s.reduced) {
        s.stageKick -= 0.02
        s.pose.swayX -= 0.02
    }
    phase(s, "collecting")
    emit(s, HAPTIC.collect)
}

internal fun inputTear(s: TearState, input: TearInput): Unit {
    val p = s.pose
    if (input.type == "activate") {
        s.silent = false
        if (s.phase == "sealed" || s.phase == "tearing") {
            s.autoTear = true
            s.grabDy = -s.lineEnd
            s.advanced = false
            phase(s, "tearing")
        } else if (s.phase == "peek") reveal(s) else if (s.phase == "revealed") collect(s)
        return
    }
    if (input.type == "down") {
        s.down = true
        s.downAt = s.clock
        s.fingerX = input.x
        s.downX = input.x
        s.fingerY = input.y
        s.downY = input.y
        s.travel = 0.0
        s.vy = 0.0
        s.vx = 0.0
        s.silent = input.silent
        s.advanced = false
        s.autoTear = false
        s.hold = "none"
        val frontX = PACK.x + p.tearFront
        if ((s.phase == "sealed" || s.phase == "tearing") && input.y >= PACK.y - 24.0 && input.y <= PACK.y + 96.0 && input.x >= frontX - 44.0 && input.x <= frontX + 64.0) {
            s.hold = "tear"
            s.coast = 0.0
            s.grabDy = input.y - PACK.y - TEAR.y - s.lineEnd
            s.catching = input.x - PACK.x > p.tearFront + 0.01
            phase(s, "tearing")
        } else if (s.phase == "peek" && abs(input.x - p.cardX) <= CARD.width / 2.0 + 12.0 && input.y >= p.cardY - CARD.height / 2.0 - 12.0 && input.y <= min(p.cardY + CARD.height / 2.0, PACK.y + TEAR.y + p.packY) + 12.0) {
            s.hold = "card"
            s.pullStart = p.cardY
            s.returning = true
            s.cardVY = 0.0
        } else if (s.phase == "revealed" && abs(input.x - p.cardX) <= CARD.width * p.cardScale / 2.0 + 12.0 && abs(input.y - p.cardY) <= CARD.height * p.cardScale / 2.0 + 12.0) {
            s.hold = "tilt"
            s.pressAge = 0.0
            s.tiltVY = 0.0
            s.tiltVX = 0.0
        } else if (input.x >= FRAME.x && input.x <= FRAME.x + FRAME.width && input.y >= FRAME.y && input.y <= FRAME.y + FRAME.height) s.hold = "look"
        return
    }
    if (!s.down) return
    if (input.type == "move") {
        s.fingerX = input.x
        s.fingerY = input.y
        s.vx = input.vx
        s.vy = input.vy
        val dx = input.x - s.downX
        val dy = input.y - s.downY
        s.travel = max(s.travel, hypot(dx, dy))
        if (s.hold == "tear" && !s.catching) advanceFront(s, input.x - PACK.x, input.y - PACK.y) else if (s.hold == "card") {
            val proposed = s.pullStart + dy
            p.cardY = (if (proposed < CARD.peekY - 48.0) CARD.peekY - 48.0 - 24.0 * tanh((CARD.peekY - 48.0 - proposed) / 24.0) else (if (proposed > CARD.peekY) CARD.peekY + 10.0 * tanh((proposed - CARD.peekY) / 10.0) else proposed))
        } else if (s.hold == "tilt" && !s.reduced) {
            p.cardTiltY = clamp(dx / 110.0, -0.32, 0.32)
            p.cardTiltX = clamp(-dy / 140.0, -0.26, 0.26)
        }
        return
    }
    s.down = false
    s.vx = (if (input.cancelled) 0.0 else input.vx)
    s.vy = (if (input.cancelled) 0.0 else input.vy)
    val tap = !input.cancelled && s.travel < MOTION.tapTravel && s.clock - s.downAt < MOTION.tapTime
    if (s.hold == "tear") {
        s.coast = (if (!input.cancelled && s.vx > 900.0 && p.tearFront > PACK.width * 0.6) s.vx else 0.0)
    } else if (s.hold == "card") {
        if (!input.cancelled && (CARD.peekY - p.cardY > 24.0 || s.vy < -500.0 || tap)) reveal(s) else s.cardVY = s.vy
    } else if (s.hold == "tilt") {
        s.tiltHomeAge = 0.0
        s.tiltVX = clamp(-s.vy / 140.0, -3.0, 3.0)
        s.tiltVY = clamp(s.vx / 110.0, -3.0, 3.0)
        if (tap) collect(s)
    }
    s.hold = "none"
}

internal fun actTear(state: TearState, input: TearInput): TearState {
    val s = state
    inputTear(s, input)
    return s
}

internal fun freeStrip(s: TearState, dt: Double): Unit {
    if (s.openAge < 0.0 || s.phase == "collecting" || s.phase == "sealed") return
    val p = s.pose
    s.openAge += dt
    if (s.reduced) p.stripOpacity = 1.0 - clamp(s.openAge / 0.15) else if (p.stripOpacity > 0.0) {
        p.stripX += s.stripVX * dt
        p.stripY += s.stripVY * dt + 1300.0 * dt * dt
        s.stripVY += 2600.0 * dt
        p.stripRot += 9.0 * dt
        p.stripOpacity = 1.0 - clamp((p.stripY - 380.0) / 140.0)
    }
    p.burst = ((if (s.reduced) 0.4 else 1.0)) * exp(-s.openAge / 0.18)
    if (p.burst < 0.0001) p.burst = 0.0
}

internal fun tug(s: TearState, dt: Double): Unit {
    val p = s.pose
    val pulling = s.hold == "tear"
    val progress = p.tearFront / PACK.width
    val hanging = s.phase == "tearing"
    val x = (if (pulling) 7.0 * tanh(s.vx / 600.0) + 4.0 * progress else (if (hanging) 2.0 * progress else 0.0))
    val rotation = (if (pulling) 0.045 * tanh(s.vx / 500.0) + 0.015 * progress else (if (hanging) 0.008 * progress else 0.0))
    if (s.reduced) {
        p.packX = 0.0
        p.packRot = 0.0
        return
    }
    val offset = spring(p.packX, s.packVX, x, MOTION.tug.stiffness, MOTION.tug.damping, dt)
    val turn = spring(p.packRot, s.packVR, rotation, MOTION.tug.stiffness, MOTION.tug.damping, dt)
    p.packX = offset.position
    s.packVX = offset.velocity
    p.packRot = turn.position
    s.packVR = turn.velocity
}

internal fun stepPeek(s: TearState, dt: Double): Unit {
    val p = s.pose
    p.dust = clamp(s.age / 0.4)
    p.tabGlow = 0.75 + ((if (s.reduced) 0.0 else 0.1 * sin(2.0 * PI * s.age / 1.2)))
    p.leak += (0.8 - p.leak) * (1.0 - exp(-dt / 0.12))
    if (s.reduced) {
        p.packY = PEEK_SINK
        if (s.hold != "card") p.cardY = CARD.peekY
        p.cardOpacity = clamp(s.age / 0.2)
    } else {
        p.cardOpacity = 1.0
        val sink = spring(p.packY, s.packVY, PEEK_SINK, MOTION.sink.stiffness, MOTION.sink.damping, dt)
        p.packY = sink.position
        s.packVY = sink.velocity
        if (s.hold != "card") {
            val config = (if (s.returning) MOTION.pullReturn else MOTION.rise)
            val rise = spring(p.cardY, s.cardVY, CARD.peekY, config.stiffness, config.damping, dt)
            p.cardY = rise.position
            s.cardVY = rise.velocity
        }
        if (!s.peekSettled && !s.down && abs(p.cardY - CARD.peekY) < 0.5 && abs(s.cardVY) < 3.0) s.peekSettled = true
        val alive = s.peekSettled && !s.down
        s.peekLife = toward(s.peekLife, (if (alive) 1.0 else 0.0), dt / ((if (alive) MOTION.peekFadeIn else MOTION.peekFadeOut)))
        p.cardTiltY = s.peekLife * 0.06 * sin(2.0 * PI * s.age / 2.6)
        p.cardTiltX = s.peekLife * 0.03 * sin(2.0 * PI * s.age / 3.4 + 1.0)
    }
    if (!s.peekHit && ((if (s.reduced) s.age >= 0.2 else p.cardY <= CARD.peekY))) {
        s.peekHit = true
        emit(s, HAPTIC.peek)
    }
}

internal fun stepReveal(s: TearState, dt: Double): Unit {
    val p = s.pose
    val t = s.age
    p.leak = 0.8 * (1.0 - clamp(t / 0.25))
    if (s.reduced) {
        p.cardOpacity = (if (t < 0.16) 1.0 - clamp(t / 0.16) else clamp((t - 0.16) / 0.2))
        p.packOpacity = 1.0 - clamp(t / 0.2)
        if (t >= 0.16) {
            p.cardX = CARD.insideX
            p.cardY = CARD.revealY
            p.cardScale = CARD.revealScale
            p.cardFlip = PI
            face(s)
        }
        if (t >= 0.36) phase(s, "revealed")
        return
    }
    val extractedY = CARD.peekY - MOTION.extractLift
    val progress = hero((t - MOTION.extractTime) / MOTION.revealTravel)
    p.cardX = s.revealX + (CARD.insideX - s.revealX) * progress
    p.cardY = (if (t < MOTION.extractTime) s.revealY + (extractedY - s.revealY) * ease(t / MOTION.extractTime) else extractedY + (CARD.revealY - extractedY) * progress)
    val peekFade = 1.0 - clamp(t / MOTION.peekFadeOut)
    p.cardTiltY = s.revealTiltY * peekFade
    p.cardTiltX = s.revealTiltX * peekFade - 0.1 * ease(t / 0.08) * (1.0 - ease((t - MOTION.extractTime) / 0.22))
    p.packY += s.packVY * dt + 1100.0 * dt * dt
    s.packVY += 2200.0 * dt
    p.packRot = -0.22 * ease(t / 0.6)
    p.packOpacity = min(1.0 - clamp((p.packY - 120.0) / 140.0), 1.0 - clamp((t - MOTION.extractTime) / MOTION.revealPackFade))
    if (t >= MOTION.flipAt) {
        val scale = spring(p.cardScale, s.cardVS, CARD.revealScale, MOTION.scale.stiffness, MOTION.scale.damping, dt)
        p.cardScale = scale.position
        s.cardVS = scale.velocity
    }
    if (t >= MOTION.flipAt) {
        val turn = spring(p.cardFlip, s.flipV, PI, MOTION.flip.stiffness, MOTION.flip.damping, dt)
        p.cardFlip = turn.position
        s.flipV = turn.velocity
        if (p.cardFlip >= PI * 0.92) face(s)
    }
    p.lightX = -0.4 + 0.7 * clamp(p.cardFlip / PI)
    if (t >= MOTION.revealDeadline - 1e-9 || (t > MOTION.extractTime + MOTION.revealTravel && abs(p.cardFlip - PI) < 0.005 && abs(s.flipV) < 0.015)) {
        p.cardFlip = PI
        p.cardScale = CARD.revealScale
        p.cardY = CARD.revealY
        s.tiltHomeAge = 1.0
        phase(s, "revealed")
    }
}

internal fun arrival(s: TearState, dt: Double): Unit {
    if (s.faceAge < 0.0 || s.phase == "collecting") return
    val p = s.pose
    s.faceAge += dt
    val t = s.faceAge
    p.frameHeat = clamp(t / ((if (s.reduced) 0.2 else 0.42)))
    p.frameRing = (if (s.reduced) 1.0 else ease(t / 0.52))
    p.tabGlow = (if (t < 0.9) 1.0 else 0.85)
    p.sparkleAge = (if (s.reduced || t > 1.6) -1.0 else t)
    p.sheen = (if (s.reduced) -0.3 else -0.3 + 1.6 * clamp((t - 0.08) / 0.7))
}

internal fun stepRevealed(s: TearState, dt: Double): Unit {
    val p = s.pose
    s.tiltHomeAge += dt
    val fast = 2.0 * PI * s.clock / MOTION.idleFast
    val slow = 2.0 * PI * s.clock / MOTION.idleSlow
    p.cardY = CARD.revealY + ((if (s.reduced) 0.0 else s.idle * 3.0 * sin(fast)))
    if (s.hold == "tilt") {
        s.pressAge += dt
        p.cardScale = CARD.revealScale * (1.0 - 0.03 * ease(s.pressAge / 0.12))
    } else if (s.reduced) {
        p.cardTiltY = 0.0
        p.cardTiltX = 0.0
        p.cardScale = CARD.revealScale
    } else {
        val tx = s.idle * 0.05 * sin(fast + 1.0)
        val ty = s.idle * 0.07 * sin(slow)
        val x = spring(p.cardTiltX, s.tiltVX, tx, MOTION.tilt.stiffness, MOTION.tilt.damping, dt)
        val y = spring(p.cardTiltY, s.tiltVY, ty, MOTION.tilt.stiffness, MOTION.tilt.damping, dt)
        p.cardTiltX = x.position
        p.cardTiltY = y.position
        s.tiltVX = x.velocity
        s.tiltVY = y.velocity
        val scale = spring(p.cardScale, s.cardVS, CARD.revealScale, MOTION.scale.stiffness, MOTION.scale.damping, dt)
        p.cardScale = scale.position
        s.cardVS = scale.velocity
    }
}

internal fun resetPack(s: TearState): Unit {
    val p = s.pose
    p.tearFront = 0.0
    p.tearLine = flatLine()
    s.lineEnd = 0.0
    p.stripRot = 0.0
    p.stripY = 0.0
    p.stripX = 0.0
    p.stripFree = 0.0
    p.stripOpacity = 1.0
    p.burst = 0.0
    p.leak = 0.0
    p.mouth = 0.0
    s.openAge = -1.0
    p.packY = (if (s.reduced) 0.0 else 72.0)
    p.packScale = (if (s.reduced) 1.0 else 0.94)
    p.packX = 0.0
    p.packRot = 0.0
    p.packOpacity = 0.0
    s.packVR = 0.0
    s.packVX = 0.0
    s.packVS = 0.0
    s.packVY = 0.0
    s.newPack = true
}

internal fun resetCard(s: TearState): Unit {
    val p = s.pose
    p.cardX = CARD.insideX
    p.cardY = CARD.insideY
    p.cardScale = 1.0
    p.cardOpacity = 0.0
    p.cardTiltY = 0.0
    p.cardTiltX = 0.0
    p.cardFlip = 0.0
    p.sheen = -0.3
    p.sparkleAge = -1.0
}

internal fun stepCollect(s: TearState, dt: Double): Unit {
    val p = s.pose
    val t = s.age
    val departureEnd = (if (s.reduced) MOTION.newPackAt else 0.54)
    if (t < departureEnd) {
        if (s.reduced) p.cardOpacity = 1.0 - clamp(t / 0.2) else {
            val travel = hero((t - 0.08) / 0.46)
            p.cardY = s.collectY + (-260.0 - s.collectY) * travel
            p.cardScale = (if (t < 0.08) s.collectScale + (CARD.revealScale * 1.02 - s.collectScale) * ease(t / 0.08) else CARD.revealScale * (1.02 - 0.3 * travel))
            p.cardTiltX = 0.5 * travel
            p.cardOpacity = 1.0 - clamp((t - 0.4) / 0.14)
            p.sheen = -0.3 + 1.6 * clamp(t / 0.38)
        }
    } else resetCard(s)
    if (!s.newPack && t >= MOTION.newPackAt) resetPack(s)
    p.frameHeat = 1.0 - clamp((t - 0.2) / 0.6)
    p.tabGlow = 0.85 - 0.4 * clamp((t - 0.2) / 0.6)
    p.dust = 1.0 - clamp((t - 0.2) / 0.4)
    if (p.frameHeat == 0.0) p.frameRing = 0.0
    if (s.newPack) {
        val age = t - MOTION.newPackAt
        p.packOpacity = clamp(age / 0.22)
        if (!s.reduced) {
            val y = spring(p.packY, s.packVY, 0.0, MOTION.landing.stiffness, MOTION.landing.damping, dt)
            val scale = spring(p.packScale, s.packVS, 1.0, MOTION.landing.stiffness, MOTION.landing.damping, dt)
            p.packY = y.position
            s.packVY = y.velocity
            p.packScale = scale.position
            s.packVS = scale.velocity
        }
        if (t >= 1.1 && (s.reduced || (abs(p.packY) < 0.1 && abs(s.packVY) < 1.0))) {
            val clock = s.clock
            val silent = s.silent
            val events = s.events
            val fresh = initialTear(s.reduced)
            s.resetFrom(fresh)
            s.clock = clock
            s.age = 0.0
            s.silent = silent
            s.events = events
            s.pose.calm = 0.0
            s.idle = 0.0
            emit(s, HAPTIC.land)
        }
    }
}

internal fun tickTear(s: TearState, dt: Double): Unit {
    s.clock += dt
    s.age += dt
    val p = s.pose
    idlePose(s, dt)
    if (s.phase == "sealed" || s.phase == "tearing") {
        p.cardOpacity = 0.0
        tug(s, dt)
        if (s.autoTear) {
            s.vx = 420.0
            advanceFront(s, p.tearFront + 420.0 * dt, TEAR.y)
        } else if (s.hold == "tear" && s.catching) {
            val goal = clamp(s.fingerX - PACK.x, p.tearFront, PACK.width)
            advanceFront(s, min(goal, p.tearFront + MOTION.catchSpeed * dt), s.fingerY - PACK.y)
            if (p.tearFront >= goal - 0.001) s.catching = false
        } else if (s.coast > 0.1) {
            val decay = exp(-dt / MOTION.coastTau)
            s.vx = s.coast
            advanceFront(s, p.tearFront + s.coast * MOTION.coastTau * (1.0 - decay), s.lineY)
            s.coast *= decay
        } else if (!s.down && p.tearFront > 6.0) p.leak = 0.15 + 0.85 * p.tearFront / PACK.width + ((if (s.reduced) 0.0 else 0.05 * sin(2.0 * PI * s.clock / 1.6)))
    }
    freeStrip(s, dt)
    if (s.phase == "opened" || s.phase == "peek") {
        tug(s, dt)
        if (s.reduced) p.mouth = clamp(s.openAge / 0.2) else {
            val mouth = spring(p.mouth, s.mouthV, 1.0, MOTION.mouth.stiffness, MOTION.mouth.damping, dt)
            p.mouth = mouth.position
            s.mouthV = mouth.velocity
        }
        if (s.phase == "opened" && s.openAge >= MOTION.peekDelay - 1e-9) phase(s, "peek")
        if (s.phase == "peek") stepPeek(s, dt)
    } else if (s.phase == "revealing") stepReveal(s, dt) else if (s.phase == "revealed") stepRevealed(s, dt) else if (s.phase == "collecting") stepCollect(s, dt)
    arrival(s, dt)
}

internal fun stepTear(state: TearState, elapsed: Double): TearState {
    val s = state
    var remaining = clamp(elapsed, 0.0, MOTION.maxElapsed)
    while (remaining > 1e-9) {
        val dt = min(remaining, MOTION.step)
        tickTear(s, dt)
        remaining -= dt
    }
    return s
}
