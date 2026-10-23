import Foundation

struct DayOneMedalPoint: Codable, Equatable {
    var x = 0.0
    var y = 0.0
}

struct DayOneMedalConfig {
    var reduced = false
    var centre = DayOneMedalPoint(x: 201, y: 240)
    var half = 156.0
    var pivot = DayOneMedalPoint(x: 201, y: -40)
    var engravePath: [[Double]] = []
}

enum DayOneMedalTurn: String, Codable {
    case rest, drag, coast, settle, coin, wobble
}

enum DayOneMedalMorph: String, Codable {
    case idle, prepare, running, copy, commit
}

struct DayOneMedalState: Codable {
    var forged = false
    var wearing = false
    var engraved = false
    var day = 0
    var mode = DayOneMedalTurn.rest
    var clock = 0.0
    var base = 0.0
    var yaw = 0.0
    var shown = 0.0
    var velocity = 0.0
    var spin = 0.0
    var pitch = 0.035
    var roll = 0.0
    var target = 0.0
    var stiffness = 110.0
    var damping = 13.0
    var sway = 0.0
    var swayPhase = 0.0
    var idle = 0.0
    var hand = false
    var silent = false
    var down = false
    var blankTouch = false
    var downAt = 0.0
    var travel = 0.0
    var dx = 0.0
    var dy = 0.0
    var fingerX = 0.0
    var fingerY = 0.0
    var pressed = 0.0
    var pressFrom = 0.0
    var pressTarget = 0.0
    var pressTime = 0.0
    var fade = 1.0
    var fadeTime = -1.0
    var charge = 0.0
    var chargeVelocity = 0.0
    var hold = false
    var holdTime = 0.0
    var holdStart = 0.0
    var forgeTicks = 0.0
    var heat = 0.0
    var stamp = 0.0
    var struck = -10.0
    var recoil = 0.0
    var recoilVelocity = 0.0
    var sweep = 1.15
    var sweepTime = 0.62
    var wobbleTime = 0.0
    var wobblePhase = 0.0
    var wobbleFrequency = 0.0
    var lastTick = -10.0
    var lightX = 0.0
    var lightY = 0.0
    var lightVX = 0.0
    var lightVY = 0.0
    var worn = 0.0
    var wearStart = 0.0
    var wearTime = 0.0
    var caught = false
    var theta = 0.0
    var thetaVelocity = 0.0
    var pullStart = 0.0
    var drop = 0.0
    var dropVelocity = 0.0
    var offsetX = 0.0
    var offsetY = 0.0
    var hangX = 0.0
    var hangVelocity = 0.0
    var nudge = 0.0
    var nudgeVelocity = 0.0
    var engrave = 0.0
    var engraveTime = 0.0
    var engraveLine = -1
    var cutting = false
    var morph = 0.0
    var morphTime = 0.0
    var morphPhase = DayOneMedalMorph.idle
    var from = 0
    var to = 0
    var revision = 0
    var events = 0
}

enum DayOneMedalAction {
    case touch(Double, Double)
    case move(Double, Double, Double, Double)
    case release(Double, Double, Bool)
    case day(Int)
    case textures(Int)
    case enter, flip, lap, wear, reset, forge, flick, drift
}

/// The source's 1/120-second integration, deadlines and completion tolerances.
/// Functions return a new value; views never mutate the simulation halfway through a frame.
enum DayOneMedalPhysics {
    static let rad = Double.pi / 180
    static let epsilon = 1e-9
    static let lines = [(0.05, 0.34), (0.36, 0.44), (0.48, 0.74), (0.78, 1.0)]
    static func clamp(_ x: Double, _ low: Double, _ high: Double) -> Double { max(low, min(high, x)) }
    static func nearestFace(_ x: Double) -> Double { floor(x / .pi + 0.5) * .pi }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2 }

    static func oscillator(_ position: Double, _ velocity: Double, _ target: Double, _ k: Double, _ c: Double, _ dt: Double) -> (position: Double, velocity: Double) {
        let x = position - target
        let decay = c / 2
        let w = sqrt(max(epsilon, k - decay * decay))
        let envelope = exp(-decay * dt)
        let cosine = cos(w * dt)
        let sine = sin(w * dt)
        return (target + envelope * (x * cosine + (velocity + decay * x) * sine / w),
                envelope * (velocity * cosine - (decay * velocity + k * x) * sine / w))
    }

    static func critical(_ position: Double, _ velocity: Double, _ target: Double, _ dt: Double) -> (position: Double, velocity: Double) {
        let x = position - target
        let b = velocity + 12 * x
        let decay = exp(-12 * dt)
        return (target + (x + b * dt) * decay, (velocity - 12 * b * dt) * decay)
    }

    static func sound(_ s: inout DayOneMedalState, _ event: Int) {
        if !s.silent { s.events |= event }
    }

    static func absorb(_ s: inout DayOneMedalState) {
        s.base = s.yaw
        s.sway = 0
        s.swayPhase = 0
        s.idle = 0
    }

    static func spring(_ s: inout DayOneMedalState, _ goal: Double, _ k: Double, _ c: Double, _ kick: Double, _ hand: Bool) {
        absorb(&s)
        s.target = goal
        s.stiffness = k
        s.damping = c
        s.velocity += kick
        s.hand = hand
        s.mode = .settle
    }

    static func land(_ s: inout DayOneMedalState, _ reduced: Bool, coin: Bool = false) {
        s.base = s.target
        s.velocity = 0
        s.mode = .rest
        s.idle = 0
        s.wobbleFrequency = 0
        if !reduced && s.heat < 0.05 { s.sweepTime = 0 }
        if coin { sound(&s, 16) } else if s.hand { sound(&s, 2) }
        s.hand = false
    }

    static func strike(_ s: inout DayOneMedalState, _ reduced: Bool) {
        s.forged = true
        s.hold = false
        s.charge = 0
        s.chargeVelocity = 0
        s.struck = s.clock
        s.recoil = reduced ? 0 : -9 * rad
        s.recoilVelocity = 0
        s.events |= 128
        sound(&s, 4 | 8)
        if !s.down { s.mode = .rest }
    }

    static func flip(_ s: inout DayOneMedalState, _ config: DayOneMedalConfig, _ hand: Bool, drift: Bool = false) {
        guard s.forged else { return }
        let goal = nearestFace(s.base) + .pi
        if config.reduced {
            absorb(&s)
            s.target = goal
            s.velocity = 0
            s.fadeTime = 0
            s.mode = .rest
            if hand { sound(&s, 2) }
        } else {
            spring(&s, goal, drift ? 26 : 110, drift ? 8 : 13, drift ? 0 : 220 * rad, hand)
            if s.caught { s.thetaVelocity += 0.14 }
        }
    }

    static func changeDay(_ s: inout DayOneMedalState, _ delta: Int, _ config: DayOneMedalConfig) {
        guard s.forged && s.morphPhase == .idle else { return }
        let day = max(0, min(2, s.day + delta))
        if day == s.day {
            if !config.reduced { s.nudgeVelocity = -Double(delta) * 5 * sqrt(110) }
            sound(&s, 64)
            return
        }
        s.from = s.day
        s.to = day
        s.morph = 0
        s.morphTime = 0
        s.morphPhase = .prepare
        s.revision += 1
        s.events |= 256
        sound(&s, 32)
    }

    static func releaseTurn(_ s: inout DayOneMedalState, _ vx: Double, _ config: DayOneMedalConfig) {
        let velocity = clamp(vx * 0.6 * rad, -1800 * rad, 1800 * rad)
        s.velocity = velocity
        s.hand = true
        if config.reduced {
            s.target = nearestFace(s.base + velocity * 0.22)
            s.velocity = 0
            s.fadeTime = 0
            s.mode = .rest
        } else if abs(velocity) > 1400 * rad {
            s.mode = .coin
        } else if abs(velocity) < 260 * rad {
            spring(&s, nearestFace(s.base + velocity * 0.22), 110, 13, 0, true)
        } else { s.mode = .coast }
    }

    static func pendulumLength(_ config: DayOneMedalConfig) -> Double { max(80, config.centre.y - config.pivot.y) }
    static func pendulumLimit(_ config: DayOneMedalConfig) -> Double {
        let reach = max(0, config.centre.x - config.half * 190 / 355 - 24)
        return min(28 * rad, asin(clamp(reach / (pendulumLength(config) + config.half + 10), 0, 1)))
    }

    static func act(_ state: DayOneMedalState, _ action: DayOneMedalAction, _ config: DayOneMedalConfig) -> DayOneMedalState {
        var s = state
        s.events = 0
        switch action {
        case .reset:
            var fresh = DayOneMedalState()
            fresh.clock = s.clock
            fresh.revision = s.revision + 1
            fresh.events = 128 | 256
            return fresh
        case .textures(let revision):
            guard revision == s.revision else { return s }
            if s.morphPhase == .prepare || s.morphPhase == .copy { s.morphPhase = .running }
            else if s.morphPhase == .commit {
                s.morph = 0
                s.morphPhase = .idle
            }
        case .touch(let x, let y):
            absorb(&s)
            s.mode = .drag
            s.velocity = 0
            s.wobbleFrequency = 0
            s.fadeTime = -1
            s.fade = 1
            s.down = true
            s.blankTouch = !s.forged
            s.downAt = s.clock
            s.travel = 0
            s.dx = 0
            s.dy = 0
            s.fingerX = x
            s.fingerY = y
            s.pullStart = s.theta
            s.thetaVelocity = 0
            s.hand = true
            s.silent = false
            s.hold = !s.forged
            s.holdTime = 0
            s.holdStart = s.charge
            s.forgeTicks = 0
        case .move(let x, let y, let dx, let dy):
            guard s.down else { return s }
            s.fingerX = x
            s.fingerY = y
            s.dx += dx
            s.dy += dy
            s.travel += abs(dx) + abs(dy)
            if s.blankTouch {
                if s.travel >= 8 { s.hold = false }
            } else if s.caught && !config.reduced {
                let angle = s.pullStart + s.dx / pendulumLength(config)
                let limit = pendulumLimit(config)
                s.theta = limit * tanh(angle / max(epsilon, limit))
            } else if !s.wearing && !config.reduced {
                s.base += dx * 0.6 * rad
                s.yaw = s.base
            }
        case .release(let vx, let vy, let cancelled):
            guard s.down else { return s }
            s.down = false
            s.hold = false
            if s.blankTouch {
                if !s.forged && !cancelled && s.charge >= 0.85 { strike(&s, config.reduced) }
                s.mode = .rest
                s.hand = false
            } else {
                let vertical = abs(s.dy) > abs(s.dx) || (abs(vy) > 500 && abs(vy) > abs(vx))
                let swipe = vertical && (abs(s.dy) > 36 || abs(vy) > 500)
                if !cancelled && swipe {
                    changeDay(&s, (abs(s.dy) > 36 ? s.dy : vy) < 0 ? 1 : -1, config)
                    releaseTurn(&s, 0, config)
                } else if !cancelled && s.travel < 8 && s.clock - s.downAt < 0.35 {
                    flip(&s, config, true)
                } else if s.wearing {
                    if !config.reduced && s.caught && !cancelled {
                        let frequency = sqrt(980 / pendulumLength(config))
                        let limit = pendulumLimit(config)
                        let remaining = frequency * sqrt(max(0, limit * limit - s.theta * s.theta))
                        s.thetaVelocity = clamp(vx / pendulumLength(config), -remaining, remaining)
                    }
                    releaseTurn(&s, 0, config)
                } else { releaseTurn(&s, cancelled ? 0 : vx, config) }
            }
        case .day(let delta):
            s.silent = false
            changeDay(&s, delta, config)
        case .wear:
            guard s.forged else { return s }
            s.silent = false
            s.wearing.toggle()
            s.wearStart = s.worn
            s.wearTime = 0
            s.events |= 128
            if !s.wearing { s.caught = false }
        case .forge:
            guard !s.forged && !s.down else { return s }
            absorb(&s)
            s.mode = .rest
            s.silent = true
            s.hold = true
            s.holdTime = 0
            s.holdStart = s.charge
        case .flick:
            guard s.forged && !s.down else { return s }
            s.silent = true
            absorb(&s)
            if s.caught {
                if !config.reduced { s.thetaVelocity += 0.34 * 2 }
            } else { releaseTurn(&s, (1600 * rad) / (0.6 * rad), config) }
        case .drift:
            guard !s.down else { return s }
            s.silent = true
            flip(&s, config, false, drift: true)
        case .flip, .lap:
            guard s.forged && !s.down else { return s }
            s.silent = false
            if case .flip = action { flip(&s, config, true) }
            else if config.reduced {
                absorb(&s)
                s.target = nearestFace(s.base)
                s.velocity = 0
                s.fadeTime = 0
                s.mode = .rest
                sound(&s, 2)
            } else { spring(&s, nearestFace(s.base) + 2 * .pi, 38, 10.5, 520 * rad, true) }
        case .enter:
            s.down = false
            s.hold = false
            s.blankTouch = false
            s.charge = 0
            s.chargeVelocity = 0
            s.fade = 1
            s.fadeTime = -1
            s.silent = false
            absorb(&s)
            s.base = 0
            s.yaw = 0
            s.shown = 0
            s.target = 0
            s.velocity = 0
            s.spin = 0
            s.roll = 0
            s.pitch = 0.035
            s.mode = .rest
            s.idle = 0
        }
        return s
    }

    static func engravingAt(_ time: Double) -> (progress: Double, line: Int, cutting: Bool) {
        let cutTotal = 2.4 - 3 * 0.12
        let weight = lines.reduce(0) { $0 + $1.1 - $1.0 }
        var start = 0.0
        for (i, line) in lines.enumerated() {
            let (low, high) = line
            let duration = cutTotal * (high - low) / weight
            if time < start + duration { return (low + (high - low) * clamp((time - start) / duration, 0, 1), i, true) }
            if time < start + duration + 0.12 && i < 3 { return (high, i, false) }
            start += duration + 0.12
        }
        return (1, 3, false)
    }

    static func stepTurn(_ s: inout DayOneMedalState, _ dt: Double, _ reduced: Bool) {
        let before = s.base
        if s.mode == .coast || s.mode == .coin {
            let coin = s.mode == .coin
            s.velocity *= exp(-dt / (coin ? 1.6 : 0.85))
            s.base += s.velocity * dt
            if abs(s.velocity) < (coin ? 420 : 260) * rad {
                s.target = nearestFace(s.base + (coin ? 0 : s.velocity * 0.22))
                s.stiffness = 110
                s.damping = 13
                s.mode = coin ? .wobble : .settle
                s.wobbleTime = 0
                s.wobblePhase = 0
            }
        } else if s.mode == .settle || s.mode == .wobble {
            s.velocity += (-s.stiffness * (s.base - s.target) - s.damping * s.velocity) * dt
            s.base += s.velocity * dt
            if s.mode == .settle && abs(s.velocity) < rad && abs(s.base - s.target) < 0.08 * rad { land(&s, reduced) }
        }
        if s.hand && floor((s.base + .pi / 2) / .pi) != floor((before + .pi / 2) / .pi) && s.clock - s.lastTick > 0.05 {
            s.lastTick = s.clock
            sound(&s, 1)
        }
        if s.mode == .rest && !reduced && !s.hold {
            s.idle += dt
            let goal = s.idle > 0.9 ? 1.0 : 0.0
            s.sway += (goal - s.sway) * (1 - exp(-dt / 1.1))
            s.swayPhase += dt * 2 * .pi / 5.2
        }
        s.yaw = s.base + s.sway * 8 * rad * sin(s.swayPhase)
        s.pitch = 0.035 + (reduced ? 0 : s.sway * 0.03 * cos(s.swayPhase * 2))
        s.roll = 0
        if s.mode == .wobble {
            s.wobbleTime += dt
            let progress = clamp(s.wobbleTime / 1.3, 0, 1)
            let previous = s.wobblePhase
            s.wobbleFrequency = 2.5 + (9 - 2.5) * progress
            s.wobblePhase += 2 * .pi * s.wobbleFrequency * dt
            let taper = 1 - easeInOut(clamp((progress - 0.8) / (1 - 0.8), 0, 1))
            let onset = easeInOut(clamp(s.wobbleTime / 0.15, 0, 1))
            let amplitude = onset * 14 * rad * pow(2.5 / s.wobbleFrequency, 2) * taper
            s.pitch += amplitude * sin(s.wobblePhase)
            s.roll += amplitude * cos(s.wobblePhase)
            if floor((s.wobblePhase + .pi / 2) / .pi) != floor((previous + .pi / 2) / .pi) && s.clock - s.lastTick >= 1 / 30 {
                s.lastTick = s.clock
                sound(&s, 1)
            }
            if progress >= 1 {
                land(&s, reduced, coin: true)
                s.yaw = s.base
                s.pitch = 0.035
                s.roll = 0
            }
        }
    }

    static func stepForge(_ s: inout DayOneMedalState, _ dt: Double, _ reduced: Bool) {
        if s.hold && !s.forged {
            s.holdTime += dt
            let progress = clamp(s.holdTime / 1.4, 0, 1)
            s.charge = s.holdStart + (1 - s.holdStart) * (1 - pow(1 - progress, 1.6))
            s.forgeTicks += dt * (4 + (14 - 4) * s.charge)
            if s.forgeTicks >= 1 {
                s.forgeTicks.formTruncatingRemainder(dividingBy: 1)
                sound(&s, 1)
            }
            if s.holdTime + epsilon >= 1.4 { strike(&s, reduced) }
        } else if !s.forged {
            let cool = oscillator(s.charge, s.chargeVelocity, 0, 150, 18, dt)
            s.charge = max(0, cool.position)
            s.chargeVelocity = s.charge == 0 ? 0 : cool.velocity
        }
        let age = s.clock - s.struck
        if s.forged {
            s.stamp = reduced ? 1 : easeOut(clamp(age / 0.26, 0, 1))
            s.heat = reduced ? 1 - clamp(age / 0.2, 0, 1)
                : age < 0.11 ? 1 - pow(1 - age / 0.11, 2)
                : pow(1 - clamp((age - 0.11) / 1.5, 0, 1), 3)
        }
        let recoil = oscillator(s.recoil, s.recoilVelocity, 0, 110, 13, dt)
        s.recoil = reduced ? 0 : recoil.position
        s.recoilVelocity = reduced ? 0 : recoil.velocity
        s.pitch += s.recoil
    }

    static func stepWear(_ s: inout DayOneMedalState, _ dt: Double, _ config: DayOneMedalConfig) {
        s.wearTime += dt
        let duration = config.reduced ? 0.2 : s.wearing ? 0.7 : 0.45
        s.worn = s.wearStart + ((s.wearing ? 1 : 0) - s.wearStart) * clamp(s.wearTime / duration, 0, 1)
        if s.wearing && !s.caught && s.worn >= 0.55 {
            s.caught = true
            if !config.reduced { s.thetaVelocity += 0.34 }
            s.pullStart = s.theta - s.dx / pendulumLength(config)
        }
        let drop = oscillator(s.drop, s.dropVelocity, s.caught ? 10 : 0, 110, 13, dt)
        s.drop = config.reduced ? 0 : drop.position
        s.dropVelocity = config.reduced ? 0 : drop.velocity
        if config.reduced {
            s.theta = 0
            s.thetaVelocity = 0
        } else if !s.caught || !s.down {
            let swing = oscillator(s.theta, s.thetaVelocity, 0, s.caught ? 980 / pendulumLength(config) : 110, s.caught ? 0.5 : 13, dt)
            let limit = pendulumLimit(config)
            s.theta = clamp(swing.position, -limit, limit)
            s.thetaVelocity = abs(swing.position) > limit ? 0 : swing.velocity
        }
        let length = pendulumLength(config) + s.drop
        let hang = oscillator(s.hangX, s.hangVelocity, s.caught ? config.pivot.x - config.centre.x : 0, 110, 13, dt)
        s.hangX = config.reduced ? 0 : hang.position
        s.hangVelocity = config.reduced ? 0 : hang.velocity
        s.offsetX = s.hangX + length * sin(s.theta)
        s.offsetY = s.drop + length * (cos(s.theta) - 1)
        s.roll += s.theta
        let nudge = oscillator(s.nudge, s.nudgeVelocity, 0, 110, 13, dt)
        s.nudge = nudge.position
        s.nudgeVelocity = nudge.velocity
        s.offsetY += config.reduced ? 0 : s.nudge
        if !config.reduced && s.charge > 0 {
            let fast = s.clock * 2 * .pi * 31
            let slow = s.clock * 2 * .pi * 19
            let shake = (1 - 0.3) * sin(fast) + 0.3 * sin(slow)
            let shakeY = cos(fast) * sin(slow)
            let magnitude = max(1, hypot(shake, shakeY))
            s.offsetX += 1.5 * s.charge * shake / magnitude
            s.offsetY += 1.5 * s.charge * shakeY / magnitude
            s.roll += 0.6 * rad * s.charge * shake
        }
    }

    static func stepEngrave(_ s: inout DayOneMedalState, _ dt: Double, _ reduced: Bool) {
        s.cutting = false
        let back = abs(Int(floor(s.base / .pi + 0.5)) % 2) == 1
        guard s.forged && !s.engraved && back && s.mode == .rest && s.fadeTime < 0 else { return }
        s.engraveTime += dt
        let cut = engravingAt(s.engraveTime)
        s.engrave = reduced ? clamp(s.engraveTime / 0.4, 0, 1) : cut.progress
        s.cutting = !reduced && cut.cutting
        if s.engraveLine != cut.line {
            s.engraveLine = cut.line
            sound(&s, 1)
        }
        if s.engraveTime + epsilon >= (reduced ? 0.4 : 2.4) {
            s.engrave = 1
            s.engraved = true
            s.cutting = false
            s.events |= 128
        }
    }

    static func stepMorph(_ s: inout DayOneMedalState, _ dt: Double, _ reduced: Bool) {
        guard s.morphPhase == .running else { return }
        s.morphTime += dt
        let progress = clamp(s.morphTime / (reduced ? 0.25 : 0.9), 0, 1)
        s.morph = reduced ? (progress < 0.5 ? 0 : 1) : easeInOut(progress)
        if reduced { s.fade = abs(2 * progress - 1) }
        if s.day != s.to && s.morph >= 0.5 {
            s.morph = reduced ? 1 : 0.5
            if reduced { s.fade = 0 }
            s.morphTime = (reduced ? 0.25 : 0.9) * 0.5
            s.day = s.to
            s.morphPhase = .copy
            s.revision += 1
            s.events |= 128 | 256
        }
        if !reduced { s.heat = max(s.heat, 0.55 * sin(.pi * progress)) }
        if progress + epsilon >= 1 {
            s.morph = 1
            s.day = s.to
            s.from = s.to
            s.morphPhase = .commit
            s.revision += 1
            s.events |= 256 | 128
            sound(&s, 4)
        }
    }

    static func step(_ state: DayOneMedalState, _ seconds: Double, _ config: DayOneMedalConfig) -> DayOneMedalState {
        var s = state
        s.events = 0
        let elapsed = clamp(seconds, 0, 0.034)
        let shown = s.shown
        var remaining = elapsed
        while remaining > epsilon {
            let dt = min(1 / 120, remaining)
            remaining -= dt
            s.clock += dt
            stepTurn(&s, dt, config.reduced)
            stepForge(&s, dt, config.reduced)
            stepWear(&s, dt, config)
            stepEngrave(&s, dt, config.reduced)
            stepMorph(&s, dt, config.reduced)
            if s.fadeTime >= 0 {
                s.fadeTime += dt
                if s.fadeTime < 0.1 { s.fade = 1 - s.fadeTime / 0.1 }
                else {
                    s.base = s.target
                    s.yaw = s.target
                    s.fade = clamp((s.fadeTime - 0.1) / 0.14, 0, 1)
                    if s.fade >= 1 { s.fadeTime = -1 }
                }
            }
            let pressTarget = s.down || s.hold ? 1.0 : 0.0
            if pressTarget != s.pressTarget {
                s.pressFrom = s.pressed
                s.pressTarget = pressTarget
                s.pressTime = 0
            }
            s.pressTime += dt
            let progress = clamp(s.pressTime / (pressTarget != 0 ? 0.12 : 0.18), 0, 1)
            s.pressed = s.pressFrom + (pressTarget - s.pressFrom) * easeInOut(progress)
            var lx = s.down ? (s.fingerX - config.centre.x - s.offsetX) / 180 : 0
            var ly = s.down ? (config.centre.y + s.offsetY - s.fingerY) / 180 : 0
            let magnitude = max(1, hypot(lx, ly))
            lx /= magnitude
            ly /= magnitude
            let lightX = critical(s.lightX, s.lightVX, lx, dt)
            let lightY = critical(s.lightY, s.lightVY, ly, dt)
            s.lightX = lightX.position
            s.lightY = lightY.position
            s.lightVX = lightX.velocity
            s.lightVY = lightY.velocity
            s.sweepTime = min(0.62, s.sweepTime + dt)
            s.sweep = -1.15 + 2.3 * easeInOut(s.sweepTime / 0.62)
        }
        if elapsed > 0 {
            s.spin += ((s.yaw - shown) / elapsed - s.spin) * (1 - exp(-elapsed / 0.06))
            s.shown = s.yaw
        }
        return s
    }

    static func rotation(_ yaw: Double, _ pitch: Double, _ roll: Double) -> [Double] {
        let cy = cos(yaw), sy = sin(yaw)
        let cp = cos(pitch), sp = sin(pitch)
        let cr = cos(roll), sr = sin(roll)
        return [cr * cy - sr * sp * sy, sr * cy + cr * sp * sy, -cp * sy,
                -sr * cp, cr * cp, sp, cr * sy + sr * sp * cy, sr * sy - cr * sp * cy, cp * cy]
    }

    static func sparkSpot(_ s: DayOneMedalState, _ config: DayOneMedalConfig) -> (point: DayOneMedalPoint, on: Double) {
        var u = 0.5
        var v = 0.78 + (0.22 - 0.78) * s.charge
        let path = config.engravePath
        if s.cutting && !path.isEmpty {
            var next = 0
            while next < path.count - 1 && path[next][0] < s.engrave { next += 1 }
            let a = path[max(0, next - 1)]
            let b = path[next]
            let t = clamp((s.engrave - a[0]) / max(epsilon, b[0] - a[0]), 0, 1)
            u = a[1] + (b[1] - a[1]) * t
            v = a[2] + (b[2] - a[2]) * t
        }
        let on = s.cutting ? 0.5 : s.charge
        return (DayOneMedalPoint(x: config.centre.x + s.offsetX + (u - 0.5) * 2 * config.half * 190 / 355,
                                y: config.centre.y + s.offsetY + (v - 0.5) * 2 * config.half),
                s.cutting && path.isEmpty ? 0 : on)
    }
}
