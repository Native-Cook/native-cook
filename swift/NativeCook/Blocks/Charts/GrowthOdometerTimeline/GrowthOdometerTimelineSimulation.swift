import Foundation

/// All positions are months, digit angles are cells, and time is in seconds.
struct GrowthOdometerTimelineSimulation {
    static let dt = 1.0 / 240
    static let values = [3000.0, 3280, 3640, 4150, 4560, 5000, 5720, 6510, 7280, 8050, 9150, 9640, 10000]
    static let months = ["Mar 2024", "Apr 2024", "May 2024", "Jun 2024", "July 2024", "Aug 2024", "Sept 2024", "Oct 2024", "Nov 2024", "Dec 2024", "Jan 2025", "Feb 2025", "Mar 2025"]
    static let fullMonths = ["March 2024", "April 2024", "May 2024", "June 2024", "July 2024", "August 2024", "September 2024", "October 2024", "November 2024", "December 2024", "January 2025", "February 2025", "March 2025"]

    enum Mode { case idle, held, drag, coast, goTo, auto }

    struct Drum {
        var theta: Double
        var omega = 0.0
        var rate = 0.0
        var direction = 1.0
        var previous: Double
        var wraps = 0.0
        var spinning = false
        var runout = -1.0

        mutating func step(target: Double, index: Int, landing: Bool) {
            let dt = GrowthOdometerTimelineSimulation.dt
            let stiffness = [900.0, 640, 420, 300, 190][index]
            let damping = [0.50, 0.50, 0.48, 0.45, 0.42][index]
            let maxSpeed = [14.0, 11, 8.5, 6, 4][index]
            let delta = target - previous
            previous = target
            rate += (abs(delta) / dt - rate) * (1 - exp(-dt / 0.06))
            if abs(delta) > 1e-8 { direction = delta > 0 ? 1 : -1 }
            if !landing && rate > 0.9 * maxSpeed {
                runout = -1
                spinning = true
                omega += (direction * maxSpeed - omega) * (1 - exp(-dt / 0.08))
            } else {
                if spinning && index < 4 {
                    if runout < 0 { runout = index < 3 ? Double(index) * 0.15 : 0 }
                    if runout > 0 {
                        runout = max(0, runout - dt)
                        omega += (direction * maxSpeed - omega) * (1 - exp(-dt / 0.08))
                        theta += omega * dt
                        return
                    }
                    let runway = 0.5 + (index < 3 ? 1.5 * Double(index) : 0)
                    wraps = direction > 0 ? ceil((theta + runway - target) / 10) : floor((theta - runway - target) / 10)
                }
                let error = target + 10 * wraps - theta
                spinning = false
                omega += (stiffness * error - 2 * damping * sqrt(stiffness) * omega) * dt
                if abs(error) < 0.002 && abs(omega) < 0.02 {
                    theta = target + 10 * wraps
                    omega = 0
                    return
                }
            }
            theta += omega * dt
        }
    }

    var p = 0.0
    var velocity = 0.0
    var mode = Mode.idle
    var target = 0.0
    var drums: [Drum]
    var clock = 0.0
    var steps = 0
    var accumulator = 0.0
    var loopStep = 0
    var auto: Bool
    var reduced: Bool
    var paused = false
    var returning = false
    var lastTouch = -100.0
    var raw0 = 0.0
    var grabTarget = 0.0
    var grip = 0.0
    var gripVelocity = 0.0
    var down = false
    var pressed = false
    var press = 0.0
    var pressVelocity = 0.0
    var touchStart = 0.0
    var gripFrom = 0.0
    var halo = 0.0
    var phase = 0.0
    var heroStart = -100.0
    var armed = true
    var tickStart = -100.0
    var month = 0
    var announced = 0
    var events = 0
    var lastHaptic = -100.0
    var endLatch = 0
    var reducedFrom = 0.0
    var reducedStart = 0.0
    var displayMonth = 0
    var reducedMonth = 0
    var fadeStart = -100.0
    var haloFrom = 0.0

    init(autoPlay: Bool = false, reduced: Bool = false) {
        auto = autoPlay && !reduced
        self.reduced = reduced
        drums = (0..<5).map { index in
            let target = Self.drumTarget(3000, index)
            return Drum(theta: target, previous: target)
        }
    }

    static func clamp(_ value: Double, _ low: Double, _ high: Double) -> Double {
        max(low, min(high, value))
    }

    static func smooth(_ low: Double, _ high: Double, _ value: Double) -> Double {
        let t = clamp((value - low) / (high - low), 0, 1)
        return t * t * (3 - 2 * t)
    }

    static func value(at position: Double) -> Double {
        let scaled = clamp(position, 0, 12)
        let index = min(11, Int(floor(scaled)))
        return values[index] + (values[index + 1] - values[index]) * (scaled - Double(index))
    }

    static func drumTarget(_ value: Double, _ index: Int) -> Double {
        if index == 0 { return value }
        let scaled = value / pow(10, Double(index))
        let whole = floor(scaled)
        let carry = [0.0, 0.1, 0.02, 0.004, 0.0012][index]
        return whole + smooth(1 - carry, 1, scaled - whole)
    }

    static func rubber(_ raw: Double) -> Double {
        if raw < 0 { return -1.2 * (1 - 1 / (-raw * 0.55 / 1.2 + 1)) }
        if raw > 12 { return 12 + 1.2 * (1 - 1 / ((raw - 12) * 0.55 / 1.2 + 1)) }
        return raw - 0.3 * sin(.pi * 2 * raw) / (.pi * 2)
    }

    static func inverseRubber(_ position: Double) -> Double {
        if position < 0 || position > 12 {
            let excess = min(1.199, position < 0 ? -position : position - 12)
            let raw = excess / (0.55 * (1 - excess / 1.2))
            return position < 0 ? -raw : 12 + raw
        }
        var raw = position
        for _ in 0..<3 {
            raw -= (rubber(raw) - position) / (1 - 0.3 * cos(.pi * 2 * raw))
        }
        return raw
    }

    mutating func goTo(_ destination: Double) {
        target = Self.clamp(destination, 0, 12)
        mode = .goTo
        reducedFrom = p
        reducedStart = clock
    }

    mutating func touch() {
        phase = chevronPhase
        grabTarget = mode == .goTo ? target : p.rounded()
        paused = true
        returning = false
        lastTouch = clock
        raw0 = Self.inverseRubber(p)
        mode = .held
        velocity = 0
        down = true
        touchStart = clock
        gripFrom = grip
    }

    mutating func drag(translationY: Double, velocityY: Double = 0) {
        p = Self.rubber(raw0 - translationY / 64)
        velocity = Self.clamp(-velocityY / 64, -22, 22)
        mode = .drag
        lastTouch = clock
    }

    mutating func release(velocityY: Double) {
        down = false
        lastTouch = clock
        if mode == .drag || mode == .held {
            velocity = mode == .held ? 0 : Self.clamp(-velocityY / 64, -22, 22)
            if reduced { goTo(p.rounded()) } else { mode = .coast }
        }
    }

    mutating func nextMonth(direction: Double = 1, rewind: Bool = true, fromGrab: Bool = false) {
        phase = chevronPhase
        let from = fromGrab || down ? grabTarget : mode == .goTo ? target : p.rounded()
        paused = true
        returning = false
        lastTouch = clock
        goTo(rewind && from >= 12 ? 0 : from + direction)
    }

    var chevronPhase: Double {
        if reduced { return 0 }
        if !auto || paused { return phase }
        let time = Double(loopStep) * Self.dt
        return 0.9 * time + 0.6 * p + 0.2 * Self.smooth(9.6, 11.3, time)
    }

    var time: Double { reduced ? 0 : auto && !paused ? Double(loopStep) * Self.dt : clock }
    var heroAge: Double { clock - heroStart }
    var flash: Double { reduced ? 1 - Self.smooth(0, 0.2, heroAge) : Self.envelope(heroAge, decay: 0.35) }
    var surge: Double { reduced ? flash * 0.25 : Self.envelope(heroAge, decay: 0.55) }
    var tick: Double { 1 - Self.smooth(0, 0.16, clock - tickStart) }
    var numberAlpha: Double {
        if !reduced { return 1 }
        let age = clock - fadeStart
        return 1 - 0.7 * (age < 0.08 ? Self.smooth(0, 0.08, age) : 1 - Self.smooth(0.08, 0.16, age))
    }

    static func envelope(_ age: Double, decay: Double) -> Double {
        if age < 0 || age > 4 { return 0 }
        if age < 0.14 { return 1 - pow(1 - age / 0.14, 3) }
        return age < 0.2 ? 1 : exp(-(age - 0.2) / decay)
    }

    private mutating func autoStep() {
        let time = Double(loopStep) * Self.dt
        if time < 0.8 {
            p = 0
            velocity = 0
            mode = .auto
        } else if time < 6.158333333 {
            let elapsed = time - 0.8
            let raw = elapsed < 0.35 ? 2.2 * elapsed * elapsed / 0.7 : 2.2 * (elapsed - 0.175)
            if raw < 11.4 {
                p = raw - 0.04 * sin(.pi * 2 * raw) / (.pi * 2)
                velocity = min(2.2, 2.2 * elapsed / 0.35) * (1 - 0.04 * cos(.pi * 2 * raw))
                mode = .auto
            } else if mode == .auto { goTo(12) }
        }
        if time >= 6.158333333 && time < 9.6 && mode == .auto { goTo(12) }
        if loopStep == 2304 { goTo(0) }
    }

    private mutating func wheelStep() {
        guard mode == .goTo || mode == .coast else { return }
        if reduced {
            let progress = Self.smooth(0, 0.18, clock - reducedStart)
            p = reducedFrom + (target - reducedFrom) * progress
            velocity = 0
            if progress == 1 { mode = .idle }
            return
        }
        var acceleration: Double
        if mode == .goTo {
            acceleration = -210 * (p - target) - 18 * velocity
        } else {
            let speed = abs(velocity)
            let damping = 1.9 + 17 * (1 - Self.smooth(1.2, 3.5, speed))
            let detent = 260 * (1 - 0.75 * Self.smooth(2, 9, speed))
            acceleration = -damping * velocity - detent * sin(.pi * 2 * p) / (.pi * 2)
            if p > 12 { acceleration += -420 * min(1, p - 12) - 37 * velocity }
            if p < 0 { acceleration += -420 * max(-1, p) - 37 * velocity }
        }
        let maxSpeed = mode == .goTo ? 11.0 : 22.0
        velocity = Self.clamp(velocity + acceleration * Self.dt, -maxSpeed, maxSpeed)
        p += velocity * Self.dt
        let stop = mode == .goTo ? target : Self.clamp(p.rounded(), 0, 12)
        if abs(velocity) < 0.02 && abs(p - stop) < 0.002 {
            p = stop
            velocity = 0
            mode = .idle
        }
    }

    mutating func advance(_ delta: Double) {
        events = 0
        accumulator += Self.clamp(delta, 0, 0.05)
        while accumulator >= Self.dt - 1e-10 {
            accumulator -= Self.dt
            substep()
        }
    }

    private mutating func substep() {
        let dt = Self.dt
        steps += 1
        clock = Double(steps) * dt
        let playing = auto && !paused
        if playing {
            loopStep += 1
            autoStep()
        }
        wheelStep()
        if auto && paused && !down && mode == .idle && clock - lastTouch >= 3.5 {
            if !returning {
                returning = true
                goTo(0)
            } else {
                paused = false
                returning = false
                loopStep = 0
            }
        }
        if p < 11.5 { armed = true }
        let hero = armed && p >= 11.999
        if hero {
            heroStart = clock
            armed = false
            if !playing { events |= 4 }
        }
        let currentMonth = Int(Self.clamp(p.rounded(), 0, 12))
        if currentMonth != month {
            month = currentMonth
            tickStart = clock
            if !playing && !hero && month != 12 && clock - lastHaptic >= 0.028 {
                events |= 1
                lastHaptic = clock
            }
        }
        let end = p > 12 ? 1 : p < 0 ? -1 : 0
        if end != 0 && end != endLatch && velocity * Double(end) > 1.5 && !playing && !hero { events |= 2 }
        endLatch = end
        if mode == .idle && announced != month {
            announced = month
            events |= 8
        }
        if down {
            let value = Self.clamp((clock - touchStart) / 0.12, 0, 1)
            var progress = value
            for _ in 0..<5 {
                let remaining = 1 - progress
                let x = 0.69 * remaining * remaining * progress + 0.96 * remaining * progress * progress + progress * progress * progress
                let derivative = 0.69 * remaining * remaining + 0.54 * remaining * progress + 2.04 * progress * progress
                progress = Self.clamp(progress - (x - value) / max(0.001, derivative), 0, 1)
            }
            grip = gripFrom + (1 - gripFrom) * (1 - pow(1 - progress, 3))
            gripVelocity = 0
        } else {
            gripVelocity += (-260 * grip - 24 * gripVelocity) * dt
            grip += gripVelocity * dt
        }
        pressVelocity += (300 * ((pressed ? 1 : 0) - press) - 26 * pressVelocity) * dt
        press += pressVelocity * dt
        if !reduced { phase += (0.9 + 0.6 * velocity) * dt }
        if reduced && month != reducedMonth {
            reducedMonth = month
            fadeStart = clock
            haloFrom = halo
        }
        if reduced && clock - fadeStart >= 0.08 { displayMonth = reducedMonth }
        let value = Self.value(at: reduced ? Double(displayMonth) : p)
        let landing = !down && !armed && target == 12 && (mode == .goTo || mode == .idle)
        let drumValue = landing && !reduced ? 10000 : value
        for index in 0..<5 {
            let target = Self.drumTarget(drumValue, index)
            if reduced {
                drums[index].theta = target
                drums[index].omega = 0
                drums[index].previous = target
            } else {
                drums[index].step(target: target, index: index, landing: landing)
            }
        }
        let haloLevel = ((reduced ? Self.value(at: Double(reducedMonth)) : value) - 3000) / 7000
        if reduced {
            halo = haloFrom + (haloLevel - haloFrom) * Self.smooth(0, 0.24, clock - fadeStart)
        } else {
            halo += (haloLevel - halo) * (1 - exp(-dt / 0.09))
        }
        if haloLevel == 0 && halo < 0.00001 { halo = 0 }
        if playing && loopStep >= 2880 {
            var clean = Self(autoPlay: true)
            clean.steps = steps
            clean.clock = clock
            clean.accumulator = accumulator
            clean.announced = announced
            clean.events = events
            self = clean
        }
    }
}
