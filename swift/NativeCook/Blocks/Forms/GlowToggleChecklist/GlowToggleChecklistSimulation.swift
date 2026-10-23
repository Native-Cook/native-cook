import Foundation

struct GlowToggleChecklistTouch {
    var control: Int
    var down: Bool
    var dx = 0.0
    var velocity = 0.0
    var k = 1.0
    var cancel = false
    var activate = false
}

struct GlowToggleChecklistKnob {
    var on = false
    var lead = 0.0
    var trail = 0.0
    var leadV = 0.0
    var trailV = 0.0
    var hold = 0.0
    var linearFrom = 0.0
    var linearAge = 0.12
}

struct GlowToggleChecklistEvents {
    var changed = false
    var cap = false
    var onMask = 0
    var offMask = 0
    var success = false
    var reset = false
    var paused = false
    var resumed = false
}

/// Pure fixed-step state, including the demo finger. All times are simulation seconds.
struct GlowToggleChecklistSimulation {
    static let fixedDT = 1.0 / 240
    static let period = 10.4
    var knobs = Array(repeating: GlowToggleChecklistKnob(), count: 4)
    var clock = 0.0
    var present = 0.0
    var letters = Array(repeating: 0.0, count: 7)
    var open = 0.0
    var openTarget = 0.0
    var openFrom = 0.0
    var openAge = 1.0
    var press = 0.0
    var sum = 0.0
    var fill = 0.0
    var fillV = 0.0
    var hue = 0.0
    var hueV = 0.0
    var tip = 0.0
    var tipV = 0.0
    var surge = 0.0
    var surgeAge = -1.0
    var flash = 0.0
    var glare = -0.3
    var glareAge = -1.0
    var glareDelay = -1.0
    var surgeDelay = -1.0
    var introAge = -1.0
    var outroAge = -1.0
    var still: Bool
    var opacity: Double
    var fadeAge = -1.0
    var fadeFrom = 0.0
    var fadeKind = 0
    var fadeTarget = 0.0
    var autoPlay: Bool
    var started = false
    var loopTime = 9.25
    var loopTick = 2220
    var paused = false
    var idle = 0.0
    var remainder = 0.0
    var finger = -2
    var fingerReal = false
    var realDown = false
    var start = 0.0
    var moved = 0.0
    var drag = false
    var lastDx = 0.0
    var fingerVelocity = 0.0
    var events = GlowToggleChecklistEvents()

    init(autoPlay: Bool = false, still: Bool = false) {
        self.autoPlay = autoPlay
        self.still = still
        opacity = still ? 0 : 1
    }

    static func unit(_ n: Double) -> Double { max(0, min(1, n)) }

    static func bezier(_ t: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if t <= 0 { return 0 }
        if t >= 1 { return 1 }
        var low = 0.0
        var high = 1.0
        var u = t
        for _ in 0..<18 {
            u = (low + high) / 2
            let v = 1 - u
            let x = 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u
            if x < t { low = u } else { high = u }
        }
        let v = 1 - u
        return 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u
    }

    static func demoTouch(_ t: Double, still: Bool) -> GlowToggleChecklistTouch {
        var control = -2
        if (t >= 0.9 && t < 1.06) || (t >= 7.5 && t < 7.64) { control = -1 }
        if t >= 2 && t < 2.12 { control = 0 }
        if t >= 2.8 && t < 2.92 { control = 1 }
        if t >= 3.6 && t < 3.72 { control = 2 }
        if t >= 5.5 && t < 5.62 { control = 3 }
        if !still && t >= 4.4 && t < 4.98 {
            let p = min(1, (t - 4.4) / 0.42)
            return .init(control: 3, down: true, dx: 20 * 0.46 * (1 - pow(1 - p, 3)),
                         velocity: p < 1 ? 20 * 0.46 * 3 * pow(1 - p, 2) / 0.42 : 0)
        }
        return .init(control: control, down: control != -2)
    }

    mutating func clearEvents() { events = GlowToggleChecklistEvents() }

    mutating func fireGlare() {
        if !still { glareAge = 0 }
    }

    mutating func fade(_ kind: Int, _ target: Double) {
        fadeAge = 0
        fadeFrom = opacity
        fadeKind = kind
        fadeTarget = target
    }

    mutating func intro() {
        clock = 0
        introAge = 0
        outroAge = -1
        glareDelay = 0.1
        if still { fade(2, 1) } else { present = 0.08 }
    }

    mutating func outro() {
        outroAge = 0
        introAge = -1
        if still { fade(2, 0) }
    }

    mutating func resetHidden() {
        knobs = Array(repeating: GlowToggleChecklistKnob(), count: 4)
        sum = 0
        fill = 0
        fillV = 0
        hue = 0
        hueV = 0
        tip = 0
        tipV = 0
        surge = 0
        surgeAge = -1
        surgeDelay = -1
        flash = 0
        press = 0
        glare = -0.3
        glareAge = -1
        glareDelay = -1
        open = 0
        openTarget = 0
        openFrom = 0
        openAge = 1
        present = 0
        introAge = -1
        outroAge = -1
        finger = -2
        events.changed = true
        events.reset = true
    }

    mutating func commit(_ i: Int, on: Bool, real: Bool) {
        knobs[i].linearFrom = knobs[i].lead
        knobs[i].linearAge = 0
        if knobs[i].on == on { return }
        knobs[i].on = on
        let count = knobs.filter(\.on).count
        events.changed = true
        if on && count == 4 {
            surgeDelay = 0.06
            if real { events.success = true }
        } else if on {
            flash = max(flash, 0.35)
            if real { events.onMask |= 1 << i }
        } else {
            if count == 3 {
                flash = 0.25
                surgeDelay = -1
            }
            if real { events.offMask |= 1 << i }
        }
    }

    mutating func toggleCap(real: Bool) {
        openTarget = 1 - openTarget
        openFrom = open
        openAge = 0
        if still { fade(1, openTarget) }
        events.changed = true
        if real { events.cap = true }
    }

    static func rubber(_ n: Double) -> Double {
        if n < 0 { return -0.08 * (1 - exp(n / 0.08)) }
        if n > 1 { return 1 + 0.08 * (1 - exp(-(n - 1) / 0.08)) }
        return n
    }

    mutating func cancelFinger() {
        if finger >= 0 {
            knobs[finger].linearFrom = knobs[finger].lead
            knobs[finger].linearAge = 0
        }
        finger = -2
        fingerVelocity = 0
    }

    mutating func input(_ touch: GlowToggleChecklistTouch, real: Bool) {
        if real {
            if !paused && autoPlay {
                cancelFinger()
                paused = true
                events.paused = true
            }
            realDown = touch.down
            idle = 0
        }
        if touch.cancel {
            cancelFinger()
            return
        }
        if touch.activate {
            if present < 1 { return }
            if touch.control == -1 { toggleCap(real: real) }
            else if touch.control >= 0 && open >= 0.98 {
                commit(touch.control, on: !knobs[touch.control].on, real: real)
            }
            return
        }
        if touch.down && finger == -2 {
            if present < 1 || (touch.control >= 0 && open < 0.98) { return }
            finger = touch.control
            fingerReal = real
            start = touch.control >= 0 ? knobs[touch.control].lead : 0
            moved = 0
            drag = false
            lastDx = 0
        }
        if finger == -2 || real != fingerReal { return }
        moved = max(moved, abs(touch.dx))
        if finger >= 0 {
            let index = finger
            drag = drag || moved >= 6
            if moved > 0 {
                let position = start + touch.dx / (20 * touch.k)
                knobs[index].lead = still ? Self.unit(position) : Self.rubber(position)
                knobs[index].leadV = touch.velocity / (20 * touch.k)
                if still {
                    knobs[index].trail = knobs[index].lead
                    knobs[index].trailV = knobs[index].leadV
                }
            }
            if !touch.down {
                let on = drag ? knobs[index].lead + 0.1 * knobs[index].leadV > 0.5 : !knobs[index].on
                commit(index, on: on, real: real)
                finger = -2
            }
        } else if !touch.down {
            toggleCap(real: real)
            finger = -2
        }
        lastDx = touch.dx
        fingerVelocity = touch.velocity
    }

    mutating func advance(_ h: Double) {
        if present > 0 { clock += h }
        if glareDelay >= 0 {
            glareDelay -= h
            if glareDelay <= 1e-9 {
                glareDelay = -1
                fireGlare()
            }
        }
        if surgeDelay >= 0 {
            surgeDelay -= h
            if surgeDelay <= 1e-9 {
                surgeDelay = -1
                flash = 1
                if !still {
                    surgeAge = 0
                    glareDelay = 0.08
                }
            }
        }
        if surgeAge >= 0 {
            surgeAge += h
            surge = surgeAge / 0.95
            if surgeAge >= 0.95 {
                surgeAge = -1
                surge = 0
            }
        }
        flash *= exp(-h / 0.28)
        if glareAge >= 0 {
            glareAge += h
            glare = -0.3 + 1.6 * Self.bezier(glareAge / 0.7, 0.77, 0, 0.175, 1)
            if glareAge >= 0.7 {
                glareAge = -1
                glare = -0.3
            }
        }
        if !still {
            if introAge >= 0 {
                introAge += h
                present = 0.08 + 0.92 * Self.bezier(introAge / 0.52, 0.23, 1, 0.32, 1)
                for i in 0..<7 {
                    letters[i] = Self.bezier((introAge - 0.055 * Double(i)) / 0.22, 0.23, 1, 0.32, 1)
                }
                if introAge >= 0.55 { introAge = -1 }
            }
            if outroAge >= 0 {
                outroAge += h
                present = 1 - Self.bezier(outroAge / 0.38, 0.77, 0, 0.175, 1)
                if outroAge >= 0.38 { outroAge = -1 }
            }
            if open != openTarget {
                openAge += h
                let duration = openTarget > 0 ? 0.56 : 0.44
                let p = openTarget > 0 ? Self.bezier(openAge / duration, 0.23, 1, 0.32, 1)
                    : Self.bezier(openAge / duration, 0.32, 0.72, 0, 1)
                open = openFrom + (openTarget - openFrom) * p
                if openAge >= duration {
                    open = openTarget
                    if open == 0 { fireGlare() }
                }
            }
            press += ((finger == -1 ? 1 : 0) - press) * (1 - exp(-h / 0.055))
        } else if fadeAge >= 0 {
            let before = fadeAge
            fadeAge += h
            if fadeAge < 0.12 { opacity = fadeFrom * (1 - fadeAge / 0.12) }
            else {
                if before < 0.12 {
                    if fadeKind == 1 { open = fadeTarget }
                    else {
                        present = fadeTarget
                        letters = Array(repeating: 1, count: 7)
                    }
                }
                opacity = Self.unit((fadeAge - 0.12) / 0.18)
                if fadeAge >= 0.3 { fadeAge = -1 }
            }
        }
        sum = 0
        for i in 0..<4 {
            var knob = knobs[i]
            let held = finger == i
            knob.hold += ((held ? 1 : 0) - knob.hold) * (1 - exp(-h / (held ? 0.07 : 0.12)))
            if still {
                if !(held && moved > 0) {
                    knob.linearAge = min(0.12, knob.linearAge + h)
                    knob.lead = knob.linearFrom + ((knob.on ? 1 : 0) - knob.linearFrom) * knob.linearAge / 0.12
                    knob.trail = knob.lead
                    knob.leadV = 0
                    knob.trailV = 0
                }
            } else {
                if !(held && moved > 0) {
                    knob.leadV += (900 * ((knob.on ? 1 : 0) - knob.lead) - 2 * 0.82 * 30 * knob.leadV) * h
                    knob.lead += knob.leadV * h
                }
                let w = held && moved > 0 ? 22.0 : 17.0
                let z = held && moved > 0 ? 0.8 : 0.72
                let target = held && moved > 0 ? knob.lead : knob.on ? 1.0 : 0.0
                knob.trailV += (w * w * (target - knob.trail) - 2 * z * w * knob.trailV) * h
                knob.trail += knob.trailV * h
                knob.lead = max(-0.08, min(1.08, knob.lead))
                knob.trail = max(-0.08, min(1.08, knob.trail))
            }
            sum += Self.unit((knob.lead + knob.trail) / 2)
            knobs[i] = knob
        }
        fillV += (256 * (sum - fill) - 32 * fillV) * h
        fill += fillV * h
        hueV += (49 * (sum - hue) - 14 * hueV) * h
        hue += hueV * h
        let p = Self.unit((sum - 3.1) / 0.85)
        let target = p * p * (3 - 2 * p)
        tipV += (121 * (target - tip) - 22 * (still ? 1 : 0.38) * tipV) * h
        tip += tipV * h
    }

    mutating func step(touch: GlowToggleChecklistTouch? = nil, dt: Double) {
        if !started {
            started = true
            if !autoPlay { intro() }
        }
        if let touch { input(touch, real: true) }
        remainder += max(0, min(dt, 1.0 / 30))
        while remainder + 1e-10 >= Self.fixedDT {
            remainder = max(0, remainder - Self.fixedDT)
            if autoPlay && paused {
                if !realDown { idle += Self.fixedDT }
                if idle + 1e-9 >= 3.5 {
                    paused = false
                    idle = 0
                    events.resumed = true
                    loopTick = Int(((openTarget > 0 || open > 0 ? 7.5 : 8.7) / Self.fixedDT).rounded())
                }
            }
            if autoPlay && !paused {
                loopTime = Double(loopTick) * Self.fixedDT
                if loopTick == 36 { fireGlare() }
                if loopTick == 2088 { outro() }
                if loopTick == 2208 { resetHidden() }
                if loopTick == 2232 { intro() }
                var touch = Self.demoTouch(loopTime, still: still)
                if touch.down || finger != -2 {
                    if !touch.down {
                        touch.control = finger
                        touch.dx = lastDx
                        touch.velocity = 0
                    }
                    input(touch, real: false)
                }
                loopTick = (loopTick + 1) % 2496
                loopTime = Double(loopTick) * Self.fixedDT
            }
            advance(Self.fixedDT)
        }
    }
}
