package com.ngocdevv.nativecook.blocks.animations.superchargecard

import com.ngocdevv.nativecook.blocks.animations.superchargecard.SuperchargeState.Phase.*
import kotlin.math.*

/** One 240 Hz simulation owns light, springs, particles, input and the 12-second script. */
internal class SuperchargeSimulation(var autoPlay: Boolean = false, var reduced: Boolean = false) {
    var state = SuperchargeState()
        private set

    enum class Action {
        Down,
        Move,
        Up,
        Cancel,
        Activate,
    }

    private fun clamp(x: Double, low: Double = 0.0, high: Double = 1.0) = x.coerceIn(low, high)

    private fun random(s: SuperchargeState): Double {
        s.seed = (s.seed * 1664525L + 1013904223L) and 0xffffffffL
        return s.seed / 4294967296.0
    }

    private fun emit(s: SuperchargeState, event: Int) {
        if (!s.silent) s.events = s.events or event
    }

    private fun phase(s: SuperchargeState, next: SuperchargeState.Phase) {
        s.phase = next
        s.phaseTime = 0.0
    }

    fun chargeAt(t: Double): Double {
        val p = clamp(t / M.chargeDuration)
        if (p <= M.chargeLinearEnd) return p * M.chargeKnee / M.chargeLinearEnd
        val tail = (p - M.chargeLinearEnd) / (1 - M.chargeLinearEnd)
        return M.chargeKnee + (1 - M.chargeKnee) * (1 - (1 - tail).pow(2))
    }

    private fun chargeTimeAt(e: Double): Double {
        return M.chargeDuration *
            (if (e <= M.chargeKnee) e * M.chargeLinearEnd / M.chargeKnee
            else
                M.chargeLinearEnd +
                    (1 - M.chargeLinearEnd) * (1 - sqrt(max(0.0, (1 - e) / (1 - M.chargeKnee)))))
    }

    fun drainEase(t: Double): Double {
        val target = clamp(t)
        var lo = 0.0
        var hi = 1.0
        repeat(18) {
            val u = (lo + hi) / 2
            val v = 1 - u
            val x = 3 * v * v * u * M.drain.bezierX1 + 3 * v * u * u * M.drain.bezierX2 + u * u * u
            if (x < target) lo = u else hi = u
        }
        val u = (lo + hi) / 2
        return 3 * (1 - u) * u * u + u * u * u
    }

    private fun strikeDim(age: Double): Double {
        if (age <= 0 || age >= M.strike.leader) return 1.0
        if (age >= M.strike.dimDuration) return M.strike.dim
        val target = age / M.strike.dimDuration
        var lo = 0.0
        var hi = 1.0
        repeat(18) {
            val u = (lo + hi) / 2
            val v = 1 - u
            val x = 3 * v * v * u * .23 + 3 * v * u * u * .32 + u * u * u
            if (x < target) lo = u else hi = u
        }
        val v = 1 - (lo + hi) / 2
        return 1 - (1 - M.strike.dim) * (1 - v * v * v)
    }

    private fun localPoint(s: SuperchargeState, x: Double, y: Double): SuperchargeLightning.Point {
        val dx = x - 201 - s.card[0]
        val dy = y - 458 - s.card[1]
        val c = cos(s.card[2])
        val n = sin(s.card[2])
        return SuperchargeLightning.Point(
            201 + (dx * c + dy * n) / s.card[3],
            458 + (-dx * n + dy * c) / s.card[3],
        )
    }

    private fun move(s: SuperchargeState, x: Double, y: Double) {
        s.touch[0] = clamp(x, 75.0, 327.0)
        s.touch[1] = clamp(y - s.sink, 298.0, 440.0)
    }

    private fun hit(x: Double, y: Double): Boolean {
        val h = M.touch.hitSlop
        return (x >= 65 - h && x <= 337 + h && y >= 288 - h && y <= 622 + h) ||
            (x >= 65 - h && x <= 341 + h && y >= 448 - h && y <= 628 + h)
    }

    private fun down(s: SuperchargeState, x: Double, y: Double) {
        if (s.phase == Strike || !hit(x, y)) return
        s.down = true
        s.downAt = s.clock
        s.downX = x
        s.downY = y
        s.travel = 0.0
        move(s, x, y)
        s.filamentAt = -1.0
        if (s.phase == Rest || s.phase == Draining || s.phase == Discharging) {
            s.chargeTime = chargeTimeAt(s.energy)
            s.tickTime = 0.0
            phase(s, Charging)
        }
        emit(s, 2)
    }

    private fun spawn(s: SuperchargeState, drain: Boolean = false) {
        val count =
            if (drain) M.spark.drainCount
            else M.spark.ringCount + M.spark.boltCount + M.spark.groundCount
        repeat(count.toInt()) { i ->
            val ring = drain || i < M.spark.ringCount
            val ground = !drain && i >= M.spark.ringCount + M.spark.boltCount
            var theta = (if (ground) PI / 2 else -PI / 2) + (random(s) - .5) * PI * 150 / 180
            if (ring && random(s) < .15) theta = random(s) * PI
            val speed =
                (M.spark.minSpeed + random(s).pow(3) * (M.spark.maxSpeed - M.spark.minSpeed)) *
                    (if (drain) .4 else 1.0)
            val life = M.spark.minLife + random(s) * (M.spark.maxLife - M.spark.minLife)
            val x =
                if (ring) 268 + (random(s) - .5) * 14
                else if (ground) s.geometry?.groundX ?: 200.0 else 240.0
            val y = if (ring) 466 - 30 + random(s) * 6 else if (ground) 628.0 else 220.0
            val delay = if (drain) 0.0 else random(s) * M.spark.birthSpan
            s.particles.addAll(
                listOf(x, y, cos(theta) * speed, sin(theta) * speed, life, life, delay)
            )
        }
    }

    private fun drainingGlyph(energy: Double, time: Double): Double {
        val base = max(M.ember.base, M.drain.glyph * energy.pow(M.drain.glyphPower))
        val dip =
            !reduced &&
                ((time > M.drain.flickers[0] && time < M.drain.flickers[1]) ||
                    (time > M.drain.flickers[2] && time < M.drain.flickers[3]))
        return max(M.ember.base, base * (if (dip) M.drain.flickerLevel else 1.0))
    }

    private fun discharge(s: SuperchargeState) {
        s.drainFrom = s.energy
        s.glowFrom = s.glow
        s.treeFrom = s.treeGlow
        s.ringFrom = s.ringLit
        s.typeFrom = s.charged
        phase(s, Discharging)
        if (!reduced) spawn(s, true)
        emit(s, 16)
    }

    private fun up(s: SuperchargeState, cancelled: Boolean = false) {
        if (!s.down) return
        s.down = false
        if (s.phase == Primed && !cancelled) {
            phase(s, Strike)
            s.caption = -1
            val geometry = SuperchargeLightning.strikeGeometry(s.seed)
            s.geometry = geometry
            random(s)
            s.lightning = SuperchargeLightning.strikeFrame(geometry, 0.0, reduced)
        } else if (s.phase == Charging || s.phase == Primed) {
            phase(s, Draining)
            emit(s, 16)
        } else if (
            s.phase == Charged &&
                !cancelled &&
                s.clock - s.downAt <= M.touch.tapTime &&
                s.travel <= M.touch.tapTravel
        )
            discharge(s)
    }

    fun act(action: Action, x: Double = 0.0, y: Double = 0.0) {
        val s = state
        s.events = 0
        if (s.phase == Strike) {
            if (action == Action.Down) {
                s.scriptPaused = true
                s.real = true
            }
            if (action == Action.Up || action == Action.Cancel) {
                s.real = false
                s.resumeAt = s.clock + M.script.resume
            }
            return
        }
        if (action == Action.Down || action == Action.Activate) {
            s.scriptPaused = true
            s.real = true
            s.down = false
            s.silent = false
            s.automated = false
            if (action == Action.Activate) {
                if (s.phase == Charged) discharge(s)
                else {
                    down(s, 201.0, 392.0)
                    s.automated = true
                }
                s.real = false
                s.resumeAt = s.clock + M.script.resume
            } else {
                val p = localPoint(s, x, y)
                if (!hit(p.x, p.y) && (s.phase == Charging || s.phase == Primed)) phase(s, Draining)
                down(s, p.x, p.y)
            }
        } else if (action == Action.Move && s.real && s.down) {
            val p = localPoint(s, x, y)
            s.travel = max(s.travel, hypot(p.x - s.downX, p.y - s.downY))
            move(s, p.x, p.y)
        } else if (action == Action.Up || action == Action.Cancel) {
            up(s, action == Action.Cancel)
            s.real = false
            s.resumeAt = s.clock + M.script.resume
        }
    }

    private fun spring(
        x: Double,
        v: Double,
        target: Double,
        k: Double,
        c: Double,
        dt: Double,
    ): Pair<Double, Double> {
        val velocity = v + (-k * (x - target) - c * v) * dt
        return Pair(x + velocity * dt, velocity)
    }

    private fun physics(s: SuperchargeState, dt: Double) {
        val before = s.phaseTime
        s.phaseTime = round((s.phaseTime + dt) / M.step) * M.step
        val t = s.phaseTime
        val e = s.energy
        s.dim = 1.0
        s.flash = 0.0
        s.pulse = -1.0
        s.shock = 0.0
        s.shaft = doubleArrayOf(240.0, 0.0)
        when (s.phase) {
            Charging,
            Primed -> {
                s.chargeTime += dt
                s.energy = chargeAt(s.chargeTime)
                s.glow = M.charging.glow * s.energy
                s.glyph = M.ember.base + (1 - M.ember.base) * s.energy.pow(M.charging.glyphPower)
                if (
                    !reduced &&
                        e < M.charging.flickerUntil &&
                        t % M.flicker.period < M.flicker.duration
                )
                    s.glyph *= M.flicker.depth
                s.tickTime += dt
                val tick = M.tick.slow + (M.tick.fast - M.tick.slow) * s.energy
                if (s.tickTime >= tick && s.phase == Charging) {
                    s.tickTime -= tick
                    emit(s, if (e < M.tick.light) 1 else if (e < M.tick.medium) 2 else 4)
                }
                if (s.phase == Charging && s.chargeTime + M.epsilon >= M.chargeDuration) {
                    s.energy = 1.0
                    phase(s, Primed)
                    emit(s, 8)
                }
                if (s.automated && s.phase == Primed) {
                    s.automated = false
                    up(s)
                    s.resumeAt = s.clock + M.script.resume
                }
            }
            Draining -> {
                s.energy =
                    max(0.0, s.energy - (M.drain.rate + M.drain.acceleration * (1 - s.energy)) * dt)
                s.glow = M.charging.glow * s.energy
                s.glyph = drainingGlyph(s.energy, t)
                if (s.energy == 0.0) phase(s, Rest)
            }
            Strike -> {
                s.energy = 1.0
                val age = max(0.0, t - M.strike.leader)
                s.dim = if (reduced) 1.0 else strikeDim(t)
                if (t < M.strike.leader) {
                    s.glow = M.charging.glow
                    s.glyph = 1.0
                } else {
                    if (before + M.epsilon < M.strike.leader && t + M.epsilon >= M.strike.leader) {
                        emit(s, 32 or 64)
                        if (!reduced) {
                            s.drop = M.strike.kick
                            s.dropV = 0.0
                            s.rotation = M.strike.rotation
                        }
                    }
                    s.flash =
                        (if (reduced) M.reduced.flash else M.strike.flash) *
                            exp(-max(0.0, age - M.strike.flashHold) / M.strike.flashTau)
                    s.glow = clamp(age / M.strike.glowRise)
                    s.glyph =
                        1 + (M.strike.glyph - 1) * (1 - clamp(age / M.strike.glyphCool)).pow(2)
                    s.glyphScale =
                        if (reduced) 1.0
                        else 1 + (M.strike.glyphScale - 1) * exp(-age / M.strike.glyphScaleTau)
                    s.tree = clamp(age / M.strike.treeDuration)
                    s.treeGlow =
                        M.strike.treeCold +
                            (M.strike.treeHot - M.strike.treeCold) * exp(-age / M.strike.treeTau)
                    s.charged = clamp((t - M.strike.stampAt) / M.strike.stampDuration)
                }
                if (t + M.epsilon >= M.strike.ringAt) {
                    val ringAge = t - M.strike.ringAt
                    s.ringLit =
                        1 +
                            (M.strike.ringHot - 1) *
                                (1 - clamp(ringAge / M.strike.ringSettle)).pow(2)
                    s.shock =
                        if (reduced || ringAge >= M.strike.shock) 0.0
                        else clamp(ringAge / M.strike.shock)
                    if (before + M.epsilon < M.strike.ringAt && !reduced) spawn(s)
                }
                if (t + M.epsilon >= M.strike.end) {
                    phase(s, Charged)
                    s.glyph = 1.0
                    s.glyphScale = 1.0
                    s.charged = 1.0
                    s.glow = 1.0
                    s.flash = 0.0
                }
            }
            Charged -> {
                s.energy = 1.0
                s.ringLit = 1.0
                s.glyph = 1.0
                s.glyphScale = 1.0
                s.tree = 1.0
                s.charged = 1.0
                s.glow = 1 + M.charged.breath * sin(2 * PI * t / M.charged.period)
                s.treeGlow = M.strike.treeCold
                val cycle = t % M.charged.pulsePeriod
                s.pulse =
                    if (reduced || cycle >= M.charged.pulseDuration) -1.0
                    else cycle / M.charged.pulseDuration
            }
            Discharging -> {
                s.energy = s.drainFrom * (1 - drainEase(t / M.drain.energy))
                if (t >= M.drain.energy) s.energy = 0.0
                s.glow = s.glowFrom * (1 - clamp(t / M.drain.glow))
                s.treeGlow = s.treeFrom * (1 - clamp(t / M.drain.glow))
                if (s.treeGlow == 0.0) s.tree = 0.0
                s.ringLit = s.ringFrom * (1 - clamp(t / M.drain.ring))
                s.charged = s.typeFrom * (1 - clamp(t / M.drain.type))
                s.glyph = drainingGlyph(s.energy, t)
                if (t + M.epsilon >= M.drain.end) phase(s, Rest)
            }
            Rest -> Unit
        }
        if (s.phase == Rest) {
            s.energy = 0.0
            s.glow = 0.0
            s.ringLit = 0.0
            s.tree = 0.0
            s.treeGlow = 0.0
            s.charged = 0.0
            s.glyphScale = 1.0
            s.glyph = M.ember.base + M.ember.amplitude * sin(2 * PI * s.clock / M.ember.period)
            if (
                !reduced &&
                    s.clock > M.ember.tickPeriod &&
                    s.clock % M.ember.tickPeriod < M.ember.tickDuration
            )
                s.glyph = M.ember.tickLevel
        }
        s.ring = s.energy
        s.meter = s.energy
        s.caption =
            when (s.phase) {
                Primed -> 1
                Strike -> if (t >= M.print.captionAt) 2 else -1
                Charged -> 2
                else -> 0
            }
        val held = s.down && (s.phase == Charging || s.phase == Primed || s.phase == Charged)
        s.touch[2] = clamp(s.touch[2] + if (held) dt / M.touch.rise else -dt / M.touch.fall)
        val strike = s.phase == Strike
        val anticipation = strike && t < M.strike.leader
        val k =
            if (held) M.press.stiffness else if (strike) M.strike.stiffness else M.release.stiffness
        val c = if (held) M.press.damping else if (strike) M.strike.damping else M.release.damping
        val sink =
            spring(
                s.sink,
                s.sinkV,
                if (held) if (s.phase == Charged) M.touch.holdSink else M.press.sink else 0.0,
                k,
                c,
                dt,
            )
        s.sink = sink.first
        s.sinkV = sink.second
        val scale =
            spring(
                s.scale,
                s.scaleV,
                if (anticipation) M.strike.dipScale else if (held) M.press.scale else 1.0,
                k,
                c,
                dt,
            )
        s.scale = scale.first
        s.scaleV = scale.second
        val drop = spring(s.drop, s.dropV, if (anticipation) M.strike.drop else 0.0, k, c, dt)
        s.drop = if (reduced) 0.0 else drop.first
        s.dropV = if (reduced) 0.0 else drop.second
        val rotation = spring(s.rotation, s.rotationV, 0.0, k, c, dt)
        s.rotation = if (reduced) 0.0 else rotation.first
        s.rotationV = if (reduced) 0.0 else rotation.second
        var shake = 0.0
        if (!reduced && (s.phase == Charging || s.phase == Primed)) {
            shake = M.shake.base + M.shake.gain * s.energy * s.energy
            val slot = floor((s.clock + M.epsilon) / M.shake.interval)
            if (slot != s.shakeAt) {
                s.shakeAt = slot
                s.shakeX = random(s) * 2 - 1
                s.shakeY = random(s) * 2 - 1
                s.shakeR = random(s) * 2 - 1
            }
        }
        s.card =
            doubleArrayOf(
                s.shakeX * shake,
                s.drop + s.shakeY * shake,
                s.rotation +
                    if (shake != 0.0) s.shakeR * M.shake.rotation * s.energy.pow(2) else 0.0,
                s.scale,
            )
        if (s.phase == Rest && abs(s.sink) + abs(s.sinkV) + abs(s.scaleV) < M.settleTolerance) {
            s.sink = 0.0
            s.sinkV = 0.0
            s.scaleV = 0.0
            s.drop = 0.0
            s.dropV = 0.0
            s.rotation = 0.0
            s.rotationV = 0.0
            s.scale = 1.0
            s.card = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        }
        s.sparks = mutableListOf()
        val particles = mutableListOf<Double>()
        for (i in s.particles.indices step 7) {
            val delay = max(0.0, s.particles[i + 6] - dt)
            if (s.particles[i + 6] > 0) {
                particles.addAll(s.particles.subList(i, i + 6))
                particles.add(delay)
                continue
            }
            val life = s.particles[i + 4] - dt
            if (life <= 0) continue
            val vx = s.particles[i + 2] * exp(-M.spark.drag * dt)
            val vy = s.particles[i + 3] * exp(-M.spark.drag * dt) + M.spark.gravity * dt
            val x = s.particles[i] + vx * dt
            val y = s.particles[i + 1] + vy * dt
            particles.addAll(listOf(x, y, vx, vy, life, s.particles[i + 5], 0.0))
            s.sparks.addAll(listOf(x, y, vx, vy, life / s.particles[i + 5]))
        }
        s.particles = particles
    }

    private fun electricity(s: SuperchargeState) {
        val geometry = s.geometry
        if (s.phase == Strike && geometry != null) {
            s.lightning = SuperchargeLightning.strikeFrame(geometry, s.phaseTime, reduced)
            s.shaft = doubleArrayOf(geometry.meanX, s.lightning.level * M.strike.shaft)
            return
        }
        val held = s.down && (s.phase == Charging || s.phase == Primed || s.phase == Charged)
        if (held) {
            val interval =
                if (s.energy > M.filament.fastAt) M.filament.fastInterval else M.filament.interval
            val fingerMoved =
                s.filamentX != s.touch[0] ||
                    s.filamentY != s.touch[1] ||
                    s.filamentEnergy != s.energy
            if (
                s.filamentAt < 0 ||
                    (reduced && fingerMoved) ||
                    (!reduced && s.clock - s.filamentAt + M.epsilon >= interval)
            ) {
                if (!reduced || s.filamentAt < 0) random(s)
                s.filamentAt = s.clock
                s.filamentX = s.touch[0]
                s.filamentY = s.touch[1]
                s.filamentEnergy = s.energy
                s.filament =
                    SuperchargeLightning.filaments(
                        s.touch[0],
                        s.touch[1],
                        s.energy,
                        s.seed,
                        s.phase == Charged,
                    )
            }
        }
        val charged = s.phase == Charged
        val coronaPeriod = if (charged) M.corona.chargedPeriod else M.corona.period
        if (
            !reduced &&
                (charged ||
                    ((s.phase == Charging || s.phase == Primed) &&
                        s.energy >= M.corona.threshold)) &&
                s.clock - s.coronaAt >= coronaPeriod
        ) {
            s.coronaAt = s.clock
            s.coronaUntil =
                s.clock + M.corona.lifeMin + random(s) * (M.corona.lifeMax - M.corona.lifeMin)
            val seed = s.seed
            val count =
                if (charged) 2 + floor(random(s) * 2).toInt()
                else if (s.phase == Primed) 4 + floor(random(s) * 3).toInt()
                else 1 + floor(s.energy * 4).toInt()
            s.coronaLines = SuperchargeLightning.corona(seed, count)
        }
        val arcEligible =
            charged || ((s.phase == Charging || s.phase == Primed) && s.energy >= M.arc.threshold)
        if (!reduced && arcEligible && s.clock >= s.arcAt) {
            val life = M.arc.lifeMin + random(s) * (M.arc.lifeMax - M.arc.lifeMin)
            s.arcUntil = s.clock + life
            val progress = clamp((s.energy - M.arc.threshold) / (1 - M.arc.threshold))
            s.arcAt =
                s.clock +
                    if (charged) M.arc.chargedPeriod
                    else life + M.arc.slowPause + (M.arc.fastPause - M.arc.slowPause) * progress
            s.arc =
                SuperchargeLightning.rimArcs(
                    s.seed,
                    s.energy,
                    if (charged) 1 else if (s.phase == Primed) 3 else if (s.energy >= .7) 2 else 1,
                )
        }
        val activeArc = arcEligible && s.clock < s.arcUntil
        val activeCorona = s.clock < s.coronaUntil
        s.lightning =
            SuperchargeLightning.Frame(
                branch =
                    if (held && !activeArc) s.filament.branch.toMutableList() else mutableListOf(),
                twig = if (held) s.filament.twig.toMutableList() else mutableListOf(),
                level =
                    if (held) M.filament.base + M.filament.gain * s.energy
                    else if (activeCorona) .7 else 0.0,
            )
        if (activeArc) {
            if (held) s.lightning.twig.addAll(s.filament.branch)
            s.lightning.branch.addAll(s.arc.branch)
            s.lightning.twig.addAll(s.arc.twig)
            s.lightning.level = max(s.lightning.level, s.arc.level)
        }
        if (activeCorona) s.lightning.twig.addAll(s.coronaLines)
    }

    private fun script(s: SuperchargeState, previous: Double) {
        if (!autoPlay || reduced) return
        if (s.scriptPaused) {
            if (
                s.real ||
                    s.automated ||
                    s.clock < s.resumeAt ||
                    (s.phase != Rest && s.phase != Charged)
            )
                return
            s.scriptPaused = false
            s.script = if (s.phase == Charged) M.script.tap else 0.0
            s.silent = true
            if (s.phase == Charged) {
                down(s, 201.0, 380.0)
                up(s)
            }
            return
        }
        s.silent = true
        val t = s.script
        if (previous < M.script.firstDown && t + M.epsilon >= M.script.firstDown)
            down(s, 98.0, 330.0)
        if (previous < M.script.firstUp && t + M.epsilon >= M.script.firstUp) up(s)
        if (previous < M.script.secondDown && t + M.epsilon >= M.script.secondDown)
            down(s, 300.0, 410.0)
        if (previous < M.script.release && t + M.epsilon >= M.script.release) up(s)
        if (previous < M.script.playDown && t + M.epsilon >= M.script.playDown) down(s, 96.0, 312.0)
        if (previous < M.script.playUp && t + M.epsilon >= M.script.playUp) up(s)
        if (previous < M.script.tap && t + M.epsilon >= M.script.tap) {
            down(s, 201.0, 380.0)
            up(s)
        }
        if (s.down) {
            val gliding = t >= M.script.playDown
            val glide = clamp(t - M.script.playDown)
            val x =
                if (gliding) 96 + glide * (312 - 96)
                else if (t < M.script.secondDown) 98.0 else 300.0
            val y =
                if (gliding) 312 + glide * (420 - 312)
                else if (t < M.script.secondDown) 330.0 else 410.0
            move(
                s,
                x + M.script.wanderX * sin(2 * PI * M.script.frequencyX * t),
                y + M.script.wanderY * sin(2 * PI * M.script.frequencyY * t + 1),
            )
            s.travel = if (gliding) 150.0 else 30.0
        }
    }

    fun step(seconds: Double) {
        var s = state
        s.events = 0
        s.accumulator += clamp(seconds, 0.0, M.maxFrame)
        while (s.accumulator + M.epsilon >= M.step) {
            s.accumulator = max(0.0, s.accumulator - M.step)
            if (s.accumulator < M.epsilon) s.accumulator = 0.0
            s.ticks++
            s.clock = s.ticks * M.step
            val previous = s.script
            if (!s.scriptPaused && autoPlay && !reduced)
                s.script = round((s.script + M.step) / M.step) * M.step
            physics(s, M.step)
            script(s, previous)
            electricity(s)
            if (
                !s.scriptPaused && autoPlay && !reduced && s.script + M.epsilon >= M.script.period
            ) {
                val remainder = s.accumulator
                s = SuperchargeState(s.initialSeed)
                s.accumulator = remainder
                state = s
            }
        }
    }
}
