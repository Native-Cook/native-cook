import Foundation

enum SuperchargeCardPhase: String {
    case rest
    case charging
    case draining
    case primed
    case strike
    case charged
    case discharging
}

struct SuperchargeCardConfiguration {
    var reduced: Bool
    var autoPlay: Bool
}

enum SuperchargeCardAction {
    case down(x: Double, y: Double)
    case move(x: Double, y: Double)
    case up
    case cancel
    case activate
}

struct SuperchargeCardState: Equatable {
    var phase = SuperchargeCardPhase.rest
    var clock = 0.0
    var phaseTime = 0.0
    var accumulator = 0.0
    var ticks = 0
    var seed: UInt32
    var initialSeed: UInt32
    var events = 0
    var silent = false
    var down = false
    var real = false
    var automated = false
    var script = 0.0
    var scriptPaused = false
    var resumeAt = 0.0
    var downAt = 0.0
    var downX = 0.0
    var downY = 0.0
    var travel = 0.0
    var energy = 0.0
    var glow = 0.0
    var glyph = SuperchargeCardConstants.Ember.base
    var glyphScale = 1.0
    var ring = 0.0
    var ringLit = 0.0
    var dim = 1.0
    var flash = 0.0
    var tree = 0.0
    var treeGlow = 0.0
    var pulse = -1.0
    var shock = 0.0
    var meter = 0.0
    var charged = 0.0
    var caption = 0
    var card = [0.0, 0.0, 0.0, 1.0]
    var sink = 0.0
    var shaft = [240.0, 0.0]
    var touch = [201.0, 392.0, 0.0]
    var sinkVelocity = 0.0
    var scale = 1.0
    var scaleVelocity = 0.0
    var drop = 0.0
    var dropVelocity = 0.0
    var rotation = 0.0
    var rotationVelocity = 0.0
    var chargeTime = 0.0
    var tickTime = 0.0
    var drainFrom = 1.0
    var glowFrom = 1.0
    var treeFrom = 0.3
    var ringFrom = 1.0
    var typeFrom = 1.0
    var geometry: SuperchargeCardBoltGeometry?
    var lightning = SuperchargeCardLightningFrame()
    var filament = SuperchargeCardLightningFrame()
    var filamentAt = -1.0
    var filamentX = 0.0
    var filamentY = 0.0
    var filamentEnergy = 0.0
    var arc = SuperchargeCardLightningFrame()
    var arcAt = 0.0
    var arcUntil = 0.0
    var coronaLines: [Double] = []
    var coronaAt = -1.0
    var coronaUntil = 0.0
    var shakeAt = -1
    var shakeX = 0.0
    var shakeY = 0.0
    var shakeRotation = 0.0
    var particles: [Double] = []
    var sparks: [Double] = []

    init(seed: UInt32 = SuperchargeCardConstants.seed) {
        self.seed = seed
        initialSeed = seed
    }

    func number(_ key: String) -> Double {
        switch key {
        case "clock": clock
        case "events": Double(events)
        case "sink": sink
        case "energy": energy
        case "glow": glow
        case "glyph": glyph
        case "glyphScale": glyphScale
        case "ring": ring
        case "ringLit": ringLit
        case "dim": dim
        case "flash": flash
        case "tree": tree
        case "treeGlow": treeGlow
        case "pulse": pulse
        case "shock": shock
        case "meter": meter
        case "charged": charged
        case "caption": Double(caption)
        default: 0
        }
    }

    func array(_ key: String) -> [Double] {
        switch key {
        case "card": card
        case "shaft": shaft
        case "touch": touch
        case "sparks": sparks
        default: []
        }
    }
}

typealias SuperchargeCardFrame = SuperchargeCardState

enum SuperchargeCardSimulation {
    static func clamp(_ value: Double, low: Double = 0, high: Double = 1) -> Double {
        max(low, min(high, value))
    }

    static func charge(at time: Double) -> Double {
        let progress = clamp(time / SuperchargeCardConstants.chargeDuration)
        if progress <= SuperchargeCardConstants.chargeLinearEnd {
            return progress * SuperchargeCardConstants.chargeKnee / SuperchargeCardConstants.chargeLinearEnd
        }
        let tail = (progress - SuperchargeCardConstants.chargeLinearEnd) / (1 - SuperchargeCardConstants.chargeLinearEnd)
        return SuperchargeCardConstants.chargeKnee + (1 - SuperchargeCardConstants.chargeKnee) * (1 - pow(1 - tail, 2))
    }

    static func drainEase(_ time: Double) -> Double {
        let target = clamp(time)
        var low = 0.0
        var high = 1.0
        for _ in 0..<18 {
            let amount = (low + high) / 2
            let inverse = 1 - amount
            let x = 3 * inverse * inverse * amount * SuperchargeCardConstants.Drain.bezierX1
                + 3 * inverse * amount * amount * SuperchargeCardConstants.Drain.bezierX2
                + amount * amount * amount
            if x < target { low = amount } else { high = amount }
        }
        let amount = (low + high) / 2
        return 3 * (1 - amount) * amount * amount + amount * amount * amount
    }

    static func strikeDim(age: Double) -> Double {
        if age <= 0 || age >= SuperchargeCardConstants.Strike.leader { return 1 }
        if age >= SuperchargeCardConstants.Strike.dimDuration { return SuperchargeCardConstants.Strike.dim }
        let target = age / SuperchargeCardConstants.Strike.dimDuration
        var low = 0.0
        var high = 1.0
        for _ in 0..<18 {
            let amount = (low + high) / 2
            let inverse = 1 - amount
            let x = 3 * inverse * inverse * amount * 0.23 + 3 * inverse * amount * amount * 0.32 + amount * amount * amount
            if x < target { low = amount } else { high = amount }
        }
        let inverse = 1 - (low + high) / 2
        return 1 - (1 - SuperchargeCardConstants.Strike.dim) * (1 - inverse * inverse * inverse)
    }

    static func act(_ state: inout SuperchargeCardState, action: SuperchargeCardAction,
                    configuration: SuperchargeCardConfiguration) {
        state.events = 0
        if state.phase == .strike {
            if case .down = action {
                state.scriptPaused = true
                state.real = true
            }
            if case .up = action {
                state.real = false
                state.resumeAt = state.clock + SuperchargeCardConstants.Script.resume
            }
            if case .cancel = action {
                state.real = false
                state.resumeAt = state.clock + SuperchargeCardConstants.Script.resume
            }
            return
        }
        switch action {
        case let .down(x, y):
            state.scriptPaused = true
            state.real = true
            state.down = false
            state.silent = false
            state.automated = false
            let point = localPoint(state, x: x, y: y)
            if !hit(point.x, point.y) && (state.phase == .charging || state.phase == .primed) {
                setPhase(&state, .draining)
            }
            press(&state, x: point.x, y: point.y)
        case .activate:
            state.scriptPaused = true
            state.real = true
            state.down = false
            state.silent = false
            state.automated = false
            if state.phase == .charged {
                discharge(&state, configuration: configuration)
            } else {
                press(&state, x: 201, y: 392)
                state.automated = true
            }
            state.real = false
            state.resumeAt = state.clock + SuperchargeCardConstants.Script.resume
        case let .move(x, y):
            guard state.real && state.down else { return }
            let point = localPoint(state, x: x, y: y)
            state.travel = max(state.travel, hypot(point.x - state.downX, point.y - state.downY))
            move(&state, x: point.x, y: point.y)
        case .up:
            release(&state, configuration: configuration)
            state.real = false
            state.resumeAt = state.clock + SuperchargeCardConstants.Script.resume
        case .cancel:
            release(&state, configuration: configuration, cancelled: true)
            state.real = false
            state.resumeAt = state.clock + SuperchargeCardConstants.Script.resume
        }
    }

    static func step(_ state: inout SuperchargeCardState, seconds: Double,
                     configuration: SuperchargeCardConfiguration) {
        var remaining = max(0, min(seconds, 0.25))
        var events = 0
        repeat {
            let delta = min(remaining, SuperchargeCardConstants.maxFrame)
            stepBounded(&state, seconds: delta, configuration: configuration)
            events |= state.events
            remaining -= delta
        } while remaining > 1e-9
        state.events = events
    }

    private static func stepBounded(_ state: inout SuperchargeCardState, seconds: Double,
                                    configuration: SuperchargeCardConfiguration) {
        state.events = 0
        state.accumulator += clamp(seconds, low: 0, high: SuperchargeCardConstants.maxFrame)
        while state.accumulator + SuperchargeCardConstants.epsilon >= SuperchargeCardConstants.step {
            state.accumulator = max(0, state.accumulator - SuperchargeCardConstants.step)
            if state.accumulator < SuperchargeCardConstants.epsilon { state.accumulator = 0 }
            state.ticks += 1
            state.clock = Double(state.ticks) * SuperchargeCardConstants.step
            let previous = state.script
            if !state.scriptPaused && configuration.autoPlay && !configuration.reduced {
                state.script = ((state.script + SuperchargeCardConstants.step) / SuperchargeCardConstants.step).rounded() * SuperchargeCardConstants.step
            }
            physics(&state, delta: SuperchargeCardConstants.step, configuration: configuration)
            runScript(&state, previous: previous, configuration: configuration)
            electricity(&state, configuration: configuration)
            if !state.scriptPaused && configuration.autoPlay && !configuration.reduced
                && state.script + SuperchargeCardConstants.epsilon >= SuperchargeCardConstants.Script.period {
                let remainder = state.accumulator
                state = SuperchargeCardState(seed: state.initialSeed)
                state.accumulator = remainder
            }
        }
    }

    private static func random(_ state: inout SuperchargeCardState) -> Double {
        state.seed = state.seed &* 1_664_525 &+ 1_013_904_223
        return Double(state.seed) / 4_294_967_296
    }

    private static func emit(_ state: inout SuperchargeCardState, _ event: Int) {
        if !state.silent { state.events |= event }
    }

    private static func setPhase(_ state: inout SuperchargeCardState, _ phase: SuperchargeCardPhase) {
        state.phase = phase
        state.phaseTime = 0
    }

    private static func chargeTime(at energy: Double) -> Double {
        if energy <= SuperchargeCardConstants.chargeKnee {
            return SuperchargeCardConstants.chargeDuration * energy * SuperchargeCardConstants.chargeLinearEnd / SuperchargeCardConstants.chargeKnee
        }
        let tail = SuperchargeCardConstants.chargeLinearEnd
            + (1 - SuperchargeCardConstants.chargeLinearEnd)
            * (1 - sqrt(max(0, (1 - energy) / (1 - SuperchargeCardConstants.chargeKnee))))
        return SuperchargeCardConstants.chargeDuration * tail
    }

    private static func localPoint(_ state: SuperchargeCardState, x: Double, y: Double) -> SuperchargeCardPoint {
        let pivot = SuperchargeCardConstants.Layout.pivot
        let dx = x - pivot.x - state.card[0]
        let dy = y - pivot.y - state.card[1]
        let cosine = cos(state.card[2])
        let sine = sin(state.card[2])
        return SuperchargeCardPoint(
            x: pivot.x + (dx * cosine + dy * sine) / state.card[3],
            y: pivot.y + (-dx * sine + dy * cosine) / state.card[3]
        )
    }

    private static func move(_ state: inout SuperchargeCardState, x: Double, y: Double) {
        state.touch[0] = clamp(x, low: SuperchargeCardConstants.Layout.glassX + SuperchargeCardConstants.Touch.inset,
                               high: SuperchargeCardConstants.Layout.glassX + SuperchargeCardConstants.Layout.glassWidth - SuperchargeCardConstants.Touch.inset)
        state.touch[1] = clamp(y - state.sink, low: SuperchargeCardConstants.Layout.glassY + SuperchargeCardConstants.Touch.inset,
                               high: SuperchargeCardConstants.Touch.bottom)
    }

    private static func hit(_ x: Double, _ y: Double) -> Bool {
        let slop = SuperchargeCardConstants.Touch.hitSlop
        let glass = x >= SuperchargeCardConstants.Layout.glassX - slop
            && x <= SuperchargeCardConstants.Layout.glassX + SuperchargeCardConstants.Layout.glassWidth + slop
            && y >= SuperchargeCardConstants.Layout.glassY - slop
            && y <= SuperchargeCardConstants.Layout.glassY + SuperchargeCardConstants.Layout.glassHeight + slop
        let paper = x >= SuperchargeCardConstants.Layout.paperLeft - slop
            && x <= SuperchargeCardConstants.Layout.paperRight + slop
            && y >= SuperchargeCardConstants.Layout.paperTop - slop
            && y <= SuperchargeCardConstants.Layout.paperBottom + slop
        return glass || paper
    }

    private static func press(_ state: inout SuperchargeCardState, x: Double, y: Double) {
        if state.phase == .strike || !hit(x, y) { return }
        state.down = true
        state.downAt = state.clock
        state.downX = x
        state.downY = y
        state.travel = 0
        move(&state, x: x, y: y)
        state.filamentAt = -1
        if state.phase == .rest || state.phase == .draining || state.phase == .discharging {
            state.chargeTime = chargeTime(at: state.energy)
            state.tickTime = 0
            setPhase(&state, .charging)
        }
        emit(&state, SuperchargeCardConstants.Event.light)
    }

    private static func spawn(_ state: inout SuperchargeCardState, drain: Bool = false) {
        let count = drain
            ? SuperchargeCardConstants.Spark.drainCount
            : SuperchargeCardConstants.Spark.ringCount + SuperchargeCardConstants.Spark.boltCount + SuperchargeCardConstants.Spark.groundCount
        for index in 0..<count {
            let ring = drain || index < SuperchargeCardConstants.Spark.ringCount
            let ground = !drain && index >= SuperchargeCardConstants.Spark.ringCount + SuperchargeCardConstants.Spark.boltCount
            var theta = (ground ? Double.pi / 2 : -Double.pi / 2) + (random(&state) - 0.5) * .pi * 150 / 180
            if ring && random(&state) < 0.15 { theta = random(&state) * .pi }
            let speed = (SuperchargeCardConstants.Spark.minSpeed
                + pow(random(&state), 3) * (SuperchargeCardConstants.Spark.maxSpeed - SuperchargeCardConstants.Spark.minSpeed))
                * (drain ? 0.4 : 1)
            let life = SuperchargeCardConstants.Spark.minLife
                + random(&state) * (SuperchargeCardConstants.Spark.maxLife - SuperchargeCardConstants.Spark.minLife)
            let x = ring
                ? SuperchargeCardConstants.Layout.ring.x + (random(&state) - 0.5) * 14
                : ground ? state.geometry?.groundX ?? 200 : SuperchargeCardConstants.Layout.boltTop.x
            let y = ring
                ? SuperchargeCardConstants.Layout.ring.y - SuperchargeCardConstants.Layout.ringRadius + random(&state) * 6
                : ground ? SuperchargeCardConstants.Layout.paperBottom : SuperchargeCardConstants.Layout.boltTop.y
            let delay = drain ? 0 : random(&state) * SuperchargeCardConstants.Spark.birthSpan
            state.particles.append(contentsOf: [x, y, cos(theta) * speed, sin(theta) * speed, life, life, delay])
        }
    }

    private static func drainingGlyph(energy: Double, time: Double, reduced: Bool) -> Double {
        let base = max(SuperchargeCardConstants.Ember.base,
                       SuperchargeCardConstants.Drain.glyph * pow(energy, SuperchargeCardConstants.Drain.glyphPower))
        let flickers = SuperchargeCardConstants.Drain.flickers
        let dip = !reduced && ((time > flickers[0] && time < flickers[1]) || (time > flickers[2] && time < flickers[3]))
        return max(SuperchargeCardConstants.Ember.base, base * (dip ? SuperchargeCardConstants.Drain.flickerLevel : 1))
    }

    private static func discharge(_ state: inout SuperchargeCardState, configuration: SuperchargeCardConfiguration) {
        state.drainFrom = state.energy
        state.glowFrom = state.glow
        state.treeFrom = state.treeGlow
        state.ringFrom = state.ringLit
        state.typeFrom = state.charged
        setPhase(&state, .discharging)
        if !configuration.reduced { spawn(&state, drain: true) }
        emit(&state, SuperchargeCardConstants.Event.soft)
    }

    private static func release(_ state: inout SuperchargeCardState, configuration: SuperchargeCardConfiguration,
                                cancelled: Bool = false) {
        if !state.down { return }
        state.down = false
        if state.phase == .primed && !cancelled {
            setPhase(&state, .strike)
            state.caption = -1
            state.geometry = SuperchargeCardLightning.strikeGeometry(seed: state.seed)
            _ = random(&state)
            state.lightning = SuperchargeCardLightning.strikeFrame(state.geometry!, age: 0, reduced: configuration.reduced)
        } else if state.phase == .charging || state.phase == .primed {
            setPhase(&state, .draining)
            emit(&state, SuperchargeCardConstants.Event.soft)
        } else if state.phase == .charged && !cancelled
                    && state.clock - state.downAt <= SuperchargeCardConstants.Touch.tapTime
                    && state.travel <= SuperchargeCardConstants.Touch.tapTravel {
            discharge(&state, configuration: configuration)
        }
    }

    private static func spring(_ value: Double, velocity: Double, target: Double, stiffness: Double,
                               damping: Double, delta: Double) -> (value: Double, velocity: Double) {
        let nextVelocity = velocity + (-stiffness * (value - target) - damping * velocity) * delta
        return (value + nextVelocity * delta, nextVelocity)
    }

    private static func physics(_ state: inout SuperchargeCardState, delta: Double,
                                configuration: SuperchargeCardConfiguration) {
        let before = state.phaseTime
        state.phaseTime = ((state.phaseTime + delta) / SuperchargeCardConstants.step).rounded() * SuperchargeCardConstants.step
        let time = state.phaseTime
        let previousEnergy = state.energy
        state.dim = 1
        state.flash = 0
        state.pulse = -1
        state.shock = 0
        state.shaft = [240, 0]
        if state.phase == .charging || state.phase == .primed {
            state.chargeTime += delta
            state.energy = charge(at: state.chargeTime)
            state.glow = SuperchargeCardConstants.Charging.glow * state.energy
            state.glyph = SuperchargeCardConstants.Ember.base
                + (1 - SuperchargeCardConstants.Ember.base) * pow(state.energy, SuperchargeCardConstants.Charging.glyphPower)
            if !configuration.reduced && previousEnergy < SuperchargeCardConstants.Charging.flickerUntil
                && time.truncatingRemainder(dividingBy: SuperchargeCardConstants.Flicker.period) < SuperchargeCardConstants.Flicker.duration {
                state.glyph *= SuperchargeCardConstants.Flicker.depth
            }
            state.tickTime += delta
            let tick = SuperchargeCardConstants.Tick.slow
                + (SuperchargeCardConstants.Tick.fast - SuperchargeCardConstants.Tick.slow) * state.energy
            if state.tickTime >= tick && state.phase == .charging {
                state.tickTime -= tick
                emit(&state, previousEnergy < SuperchargeCardConstants.Tick.light
                     ? SuperchargeCardConstants.Event.selection
                     : previousEnergy < SuperchargeCardConstants.Tick.medium
                     ? SuperchargeCardConstants.Event.light : SuperchargeCardConstants.Event.medium)
            }
            if state.phase == .charging
                && state.chargeTime + SuperchargeCardConstants.epsilon >= SuperchargeCardConstants.chargeDuration {
                state.energy = 1
                setPhase(&state, .primed)
                emit(&state, SuperchargeCardConstants.Event.rigid)
            }
            if state.automated && state.phase == .primed {
                state.automated = false
                release(&state, configuration: configuration)
                state.resumeAt = state.clock + SuperchargeCardConstants.Script.resume
            }
        } else if state.phase == .draining {
            state.energy = max(0, state.energy - (SuperchargeCardConstants.Drain.rate
                + SuperchargeCardConstants.Drain.acceleration * (1 - state.energy)) * delta)
            state.glow = SuperchargeCardConstants.Charging.glow * state.energy
            state.glyph = drainingGlyph(energy: state.energy, time: time, reduced: configuration.reduced)
            if state.energy == 0 { setPhase(&state, .rest) }
        } else if state.phase == .strike {
            state.energy = 1
            let age = max(0, time - SuperchargeCardConstants.Strike.leader)
            state.dim = configuration.reduced ? 1 : strikeDim(age: time)
            if time < SuperchargeCardConstants.Strike.leader {
                state.glow = SuperchargeCardConstants.Charging.glow
                state.glyph = 1
            } else {
                if before + SuperchargeCardConstants.epsilon < SuperchargeCardConstants.Strike.leader
                    && time + SuperchargeCardConstants.epsilon >= SuperchargeCardConstants.Strike.leader {
                    emit(&state, SuperchargeCardConstants.Event.heavy | SuperchargeCardConstants.Event.success)
                    if !configuration.reduced {
                        state.drop = SuperchargeCardConstants.Strike.kick
                        state.dropVelocity = 0
                        state.rotation = SuperchargeCardConstants.Strike.rotation
                    }
                }
                state.flash = (configuration.reduced ? SuperchargeCardConstants.Reduced.flash : SuperchargeCardConstants.Strike.flash)
                    * exp(-max(0, age - SuperchargeCardConstants.Strike.flashHold) / SuperchargeCardConstants.Strike.flashTau)
                state.glow = clamp(age / SuperchargeCardConstants.Strike.glowRise)
                state.glyph = 1 + (SuperchargeCardConstants.Strike.glyph - 1)
                    * pow(1 - clamp(age / SuperchargeCardConstants.Strike.glyphCool), 2)
                state.glyphScale = configuration.reduced ? 1 : 1 + (SuperchargeCardConstants.Strike.glyphScale - 1)
                    * exp(-age / SuperchargeCardConstants.Strike.glyphScaleTau)
                state.tree = clamp(age / SuperchargeCardConstants.Strike.treeDuration)
                state.treeGlow = SuperchargeCardConstants.Strike.treeCold
                    + (SuperchargeCardConstants.Strike.treeHot - SuperchargeCardConstants.Strike.treeCold)
                    * exp(-age / SuperchargeCardConstants.Strike.treeTau)
                state.charged = clamp((time - SuperchargeCardConstants.Strike.stampAt) / SuperchargeCardConstants.Strike.stampDuration)
            }
            if time + SuperchargeCardConstants.epsilon >= SuperchargeCardConstants.Strike.ringAt {
                let ringAge = time - SuperchargeCardConstants.Strike.ringAt
                state.ringLit = 1 + (SuperchargeCardConstants.Strike.ringHot - 1)
                    * pow(1 - clamp(ringAge / SuperchargeCardConstants.Strike.ringSettle), 2)
                state.shock = configuration.reduced || ringAge >= SuperchargeCardConstants.Strike.shock
                    ? 0 : clamp(ringAge / SuperchargeCardConstants.Strike.shock)
                if before + SuperchargeCardConstants.epsilon < SuperchargeCardConstants.Strike.ringAt && !configuration.reduced {
                    spawn(&state)
                }
            }
            if time + SuperchargeCardConstants.epsilon >= SuperchargeCardConstants.Strike.end {
                setPhase(&state, .charged)
                state.glyph = 1
                state.glyphScale = 1
                state.charged = 1
                state.glow = 1
                state.flash = 0
            }
        } else if state.phase == .charged {
            state.energy = 1
            state.ringLit = 1
            state.glyph = 1
            state.glyphScale = 1
            state.tree = 1
            state.charged = 1
            state.glow = 1 + SuperchargeCardConstants.Charged.breath
                * sin(2 * .pi * time / SuperchargeCardConstants.Charged.period)
            state.treeGlow = SuperchargeCardConstants.Strike.treeCold
            let cycle = time.truncatingRemainder(dividingBy: SuperchargeCardConstants.Charged.pulsePeriod)
            state.pulse = configuration.reduced || cycle >= SuperchargeCardConstants.Charged.pulseDuration
                ? -1 : cycle / SuperchargeCardConstants.Charged.pulseDuration
        } else if state.phase == .discharging {
            state.energy = state.drainFrom * (1 - drainEase(time / SuperchargeCardConstants.Drain.energy))
            if time >= SuperchargeCardConstants.Drain.energy { state.energy = 0 }
            state.glow = state.glowFrom * (1 - clamp(time / SuperchargeCardConstants.Drain.glow))
            state.treeGlow = state.treeFrom * (1 - clamp(time / SuperchargeCardConstants.Drain.glow))
            if state.treeGlow == 0 { state.tree = 0 }
            state.ringLit = state.ringFrom * (1 - clamp(time / SuperchargeCardConstants.Drain.ring))
            state.charged = state.typeFrom * (1 - clamp(time / SuperchargeCardConstants.Drain.type))
            state.glyph = drainingGlyph(energy: state.energy, time: time, reduced: configuration.reduced)
            if time + SuperchargeCardConstants.epsilon >= SuperchargeCardConstants.Drain.end { setPhase(&state, .rest) }
        }
        if state.phase == .rest {
            state.energy = 0
            state.glow = 0
            state.ringLit = 0
            state.tree = 0
            state.treeGlow = 0
            state.charged = 0
            state.glyphScale = 1
            state.glyph = SuperchargeCardConstants.Ember.base + SuperchargeCardConstants.Ember.amplitude
                * sin(2 * .pi * state.clock / SuperchargeCardConstants.Ember.period)
            if !configuration.reduced && state.clock > SuperchargeCardConstants.Ember.tickPeriod
                && state.clock.truncatingRemainder(dividingBy: SuperchargeCardConstants.Ember.tickPeriod) < SuperchargeCardConstants.Ember.tickDuration {
                state.glyph = SuperchargeCardConstants.Ember.tickLevel
            }
        }
        state.ring = state.energy
        state.meter = state.energy
        state.caption = state.phase == .primed ? 1
            : state.phase == .strike ? (time >= SuperchargeCardConstants.Print.captionAt ? 2 : -1)
            : state.phase == .charged ? 2 : 0
        let held = state.down && (state.phase == .charging || state.phase == .primed || state.phase == .charged)
        state.touch[2] = clamp(state.touch[2] + (held ? delta / SuperchargeCardConstants.Touch.rise : -delta / SuperchargeCardConstants.Touch.fall))
        let striking = state.phase == .strike
        let anticipation = striking && time < SuperchargeCardConstants.Strike.leader
        let stiffness = held ? SuperchargeCardConstants.Press.stiffness
            : striking ? SuperchargeCardConstants.Strike.stiffness : SuperchargeCardConstants.Release.stiffness
        let damping = held ? SuperchargeCardConstants.Press.damping
            : striking ? SuperchargeCardConstants.Strike.damping : SuperchargeCardConstants.Release.damping
        let sink = spring(state.sink, velocity: state.sinkVelocity,
                          target: held ? state.phase == .charged ? SuperchargeCardConstants.Touch.holdSink : SuperchargeCardConstants.Press.sink : 0,
                          stiffness: stiffness, damping: damping, delta: delta)
        state.sink = sink.value
        state.sinkVelocity = sink.velocity
        let scale = spring(state.scale, velocity: state.scaleVelocity,
                           target: anticipation ? SuperchargeCardConstants.Strike.dipScale : held ? SuperchargeCardConstants.Press.scale : 1,
                           stiffness: stiffness, damping: damping, delta: delta)
        state.scale = scale.value
        state.scaleVelocity = scale.velocity
        let drop = spring(state.drop, velocity: state.dropVelocity,
                          target: anticipation ? SuperchargeCardConstants.Strike.drop : 0,
                          stiffness: stiffness, damping: damping, delta: delta)
        state.drop = configuration.reduced ? 0 : drop.value
        state.dropVelocity = configuration.reduced ? 0 : drop.velocity
        let rotation = spring(state.rotation, velocity: state.rotationVelocity, target: 0,
                              stiffness: stiffness, damping: damping, delta: delta)
        state.rotation = configuration.reduced ? 0 : rotation.value
        state.rotationVelocity = configuration.reduced ? 0 : rotation.velocity
        var shake = 0.0
        if !configuration.reduced && (state.phase == .charging || state.phase == .primed) {
            shake = SuperchargeCardConstants.Shake.base + SuperchargeCardConstants.Shake.gain * state.energy * state.energy
            let slot = Int(floor((state.clock + SuperchargeCardConstants.epsilon) / SuperchargeCardConstants.Shake.interval))
            if slot != state.shakeAt {
                state.shakeAt = slot
                state.shakeX = random(&state) * 2 - 1
                state.shakeY = random(&state) * 2 - 1
                state.shakeRotation = random(&state) * 2 - 1
            }
        }
        state.card = [
            state.shakeX * shake,
            state.drop + state.shakeY * shake,
            state.rotation + (shake != 0 ? state.shakeRotation * SuperchargeCardConstants.Shake.rotation * pow(state.energy, 2) : 0),
            state.scale
        ]
        if state.phase == .rest
            && abs(state.sink) + abs(state.sinkVelocity) + abs(state.scaleVelocity) < SuperchargeCardConstants.settleTolerance {
            state.sink = 0
            state.sinkVelocity = 0
            state.scaleVelocity = 0
            state.drop = 0
            state.dropVelocity = 0
            state.rotation = 0
            state.rotationVelocity = 0
            state.scale = 1
            state.card = [0, 0, 0, 1]
        }
        state.sparks = []
        var particles: [Double] = []
        for index in stride(from: 0, to: state.particles.count, by: 7) {
            let delay = max(0, state.particles[index + 6] - delta)
            if state.particles[index + 6] > 0 {
                particles.append(contentsOf: [
                    state.particles[index], state.particles[index + 1], state.particles[index + 2],
                    state.particles[index + 3], state.particles[index + 4], state.particles[index + 5], delay
                ])
                continue
            }
            let life = state.particles[index + 4] - delta
            if life <= 0 { continue }
            let velocityX = state.particles[index + 2] * exp(-SuperchargeCardConstants.Spark.drag * delta)
            let velocityY = state.particles[index + 3] * exp(-SuperchargeCardConstants.Spark.drag * delta)
                + SuperchargeCardConstants.Spark.gravity * delta
            let x = state.particles[index] + velocityX * delta
            let y = state.particles[index + 1] + velocityY * delta
            particles.append(contentsOf: [x, y, velocityX, velocityY, life, state.particles[index + 5], 0])
            state.sparks.append(contentsOf: [x, y, velocityX, velocityY, life / state.particles[index + 5]])
        }
        state.particles = particles
    }

    private static func electricity(_ state: inout SuperchargeCardState,
                                    configuration: SuperchargeCardConfiguration) {
        if state.phase == .strike, let geometry = state.geometry {
            state.lightning = SuperchargeCardLightning.strikeFrame(geometry, age: state.phaseTime, reduced: configuration.reduced)
            state.shaft = [geometry.meanX, state.lightning.level * SuperchargeCardConstants.Strike.shaft]
            return
        }
        let held = state.down && (state.phase == .charging || state.phase == .primed || state.phase == .charged)
        if held {
            let interval = state.energy > SuperchargeCardConstants.Filament.fastAt
                ? SuperchargeCardConstants.Filament.fastInterval : SuperchargeCardConstants.Filament.interval
            let fingerMoved = state.filamentX != state.touch[0]
                || state.filamentY != state.touch[1] || state.filamentEnergy != state.energy
            if state.filamentAt < 0 || (configuration.reduced && fingerMoved)
                || (!configuration.reduced && state.clock - state.filamentAt + SuperchargeCardConstants.epsilon >= interval) {
                if !configuration.reduced || state.filamentAt < 0 { _ = random(&state) }
                state.filamentAt = state.clock
                state.filamentX = state.touch[0]
                state.filamentY = state.touch[1]
                state.filamentEnergy = state.energy
                state.filament = SuperchargeCardLightning.filaments(
                    x: state.touch[0], y: state.touch[1], energy: state.energy,
                    seed: state.seed, charged: state.phase == .charged
                )
            }
        }
        let charged = state.phase == .charged
        let coronaPeriod = charged ? SuperchargeCardConstants.Corona.chargedPeriod : SuperchargeCardConstants.Corona.period
        if !configuration.reduced
            && (charged || ((state.phase == .charging || state.phase == .primed) && state.energy >= SuperchargeCardConstants.Corona.threshold))
            && state.clock - state.coronaAt >= coronaPeriod {
            state.coronaAt = state.clock
            state.coronaUntil = state.clock + SuperchargeCardConstants.Corona.lifeMin
                + random(&state) * (SuperchargeCardConstants.Corona.lifeMax - SuperchargeCardConstants.Corona.lifeMin)
            let count = charged ? 2 + Int(floor(random(&state) * 2))
                : state.phase == .primed ? 4 + Int(floor(random(&state) * 3))
                : 1 + Int(floor(state.energy * 4))
            state.coronaLines = SuperchargeCardLightning.corona(seed: state.seed, count: count)
        }
        let arcEligible = charged || ((state.phase == .charging || state.phase == .primed)
            && state.energy >= SuperchargeCardConstants.Arc.threshold)
        if !configuration.reduced && arcEligible && state.clock >= state.arcAt {
            let life = SuperchargeCardConstants.Arc.lifeMin
                + random(&state) * (SuperchargeCardConstants.Arc.lifeMax - SuperchargeCardConstants.Arc.lifeMin)
            state.arcUntil = state.clock + life
            let progress = clamp((state.energy - SuperchargeCardConstants.Arc.threshold) / (1 - SuperchargeCardConstants.Arc.threshold))
            state.arcAt = state.clock + (charged ? SuperchargeCardConstants.Arc.chargedPeriod
                : life + SuperchargeCardConstants.Arc.slowPause
                + (SuperchargeCardConstants.Arc.fastPause - SuperchargeCardConstants.Arc.slowPause) * progress)
            let count = charged ? 1 : state.phase == .primed ? 3 : state.energy >= 0.7 ? 2 : 1
            state.arc = SuperchargeCardLightning.rimArcs(seed: state.seed, energy: state.energy, count: count)
        }
        let activeArc = arcEligible && state.clock < state.arcUntil
        let activeCorona = state.clock < state.coronaUntil
        state.lightning = SuperchargeCardLightningFrame(
            branch: held && !activeArc ? state.filament.branch : [],
            twig: held ? state.filament.twig : [],
            level: held ? SuperchargeCardConstants.Filament.base + SuperchargeCardConstants.Filament.gain * state.energy
                : activeCorona ? 0.7 : 0
        )
        if activeArc {
            if held { state.lightning.twig += state.filament.branch }
            state.lightning.branch += state.arc.branch
            state.lightning.twig += state.arc.twig
            state.lightning.level = max(state.lightning.level, state.arc.level)
        }
        if activeCorona { state.lightning.twig += state.coronaLines }
    }

    private static func runScript(_ state: inout SuperchargeCardState, previous: Double,
                                  configuration: SuperchargeCardConfiguration) {
        if !configuration.autoPlay || configuration.reduced { return }
        if state.scriptPaused {
            if state.real || state.automated || state.clock < state.resumeAt
                || (state.phase != .rest && state.phase != .charged) { return }
            state.scriptPaused = false
            state.script = state.phase == .charged ? SuperchargeCardConstants.Script.tap : 0
            state.silent = true
            if state.phase == .charged {
                press(&state, x: 201, y: 380)
                release(&state, configuration: configuration)
            }
            return
        }
        state.silent = true
        let time = state.script
        if previous < SuperchargeCardConstants.Script.firstDown
            && time + SuperchargeCardConstants.epsilon >= SuperchargeCardConstants.Script.firstDown {
            press(&state, x: SuperchargeCardConstants.Script.firstPoint.x, y: SuperchargeCardConstants.Script.firstPoint.y)
        }
        if previous < SuperchargeCardConstants.Script.firstUp
            && time + SuperchargeCardConstants.epsilon >= SuperchargeCardConstants.Script.firstUp {
            release(&state, configuration: configuration)
        }
        if previous < SuperchargeCardConstants.Script.secondDown
            && time + SuperchargeCardConstants.epsilon >= SuperchargeCardConstants.Script.secondDown {
            press(&state, x: SuperchargeCardConstants.Script.secondPoint.x, y: SuperchargeCardConstants.Script.secondPoint.y)
        }
        if previous < SuperchargeCardConstants.Script.release
            && time + SuperchargeCardConstants.epsilon >= SuperchargeCardConstants.Script.release {
            release(&state, configuration: configuration)
        }
        if previous < SuperchargeCardConstants.Script.playDown
            && time + SuperchargeCardConstants.epsilon >= SuperchargeCardConstants.Script.playDown {
            press(&state, x: SuperchargeCardConstants.Script.playFrom.x, y: SuperchargeCardConstants.Script.playFrom.y)
        }
        if previous < SuperchargeCardConstants.Script.playUp
            && time + SuperchargeCardConstants.epsilon >= SuperchargeCardConstants.Script.playUp {
            release(&state, configuration: configuration)
        }
        if previous < SuperchargeCardConstants.Script.tap
            && time + SuperchargeCardConstants.epsilon >= SuperchargeCardConstants.Script.tap {
            press(&state, x: 201, y: 380)
            release(&state, configuration: configuration)
        }
        if state.down {
            let gliding = time >= SuperchargeCardConstants.Script.playDown
            let glide = clamp(time - SuperchargeCardConstants.Script.playDown)
            let x = gliding
                ? SuperchargeCardConstants.Script.playFrom.x + glide * (SuperchargeCardConstants.Script.playTo.x - SuperchargeCardConstants.Script.playFrom.x)
                : time < SuperchargeCardConstants.Script.secondDown ? SuperchargeCardConstants.Script.firstPoint.x : SuperchargeCardConstants.Script.secondPoint.x
            let y = gliding
                ? SuperchargeCardConstants.Script.playFrom.y + glide * (SuperchargeCardConstants.Script.playTo.y - SuperchargeCardConstants.Script.playFrom.y)
                : time < SuperchargeCardConstants.Script.secondDown ? SuperchargeCardConstants.Script.firstPoint.y : SuperchargeCardConstants.Script.secondPoint.y
            move(&state,
                 x: x + SuperchargeCardConstants.Script.wanderX * sin(2 * .pi * SuperchargeCardConstants.Script.frequencyX * time),
                 y: y + SuperchargeCardConstants.Script.wanderY * sin(2 * .pi * SuperchargeCardConstants.Script.frequencyY * time + 1))
            state.travel = gliding ? 150 : 30
        }
    }
}
