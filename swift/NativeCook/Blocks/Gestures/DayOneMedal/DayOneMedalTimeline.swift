import Foundation

enum DayOneMedalEvent: String, Codable, CaseIterable {
    case reset, arrived, struck, cooled, landed, back, front, engraved, caught, unworn, milestone, closed
}

enum DayOneMedalCommand {
    case reset, open, forge, turn, flick, wear, next, close
}

struct DayOneMedalDemoStep {
    var action: DayOneMedalCommand?
    var event: DayOneMedalEvent?
    var dwell = 0.0
}

struct DayOneMedalDemoClock {
    static let steps: [DayOneMedalDemoStep] = [
        .init(action: .reset, event: .reset),
        .init(action: .open, event: .arrived, dwell: 0.65),
        .init(action: .forge, event: .struck),
        .init(event: .cooled, dwell: 1),
        .init(action: .turn, event: .back),
        .init(event: .engraved, dwell: 0.7),
        .init(action: .turn, event: .front, dwell: 0.9),
        .init(action: .flick, event: .landed, dwell: 0.7),
        .init(action: .wear, event: .caught, dwell: 1.4),
        .init(action: .flick, dwell: 3),
        .init(action: .wear, event: .unworn, dwell: 0.6),
        .init(action: .next, event: .milestone, dwell: 1.4),
        .init(action: .next, event: .milestone, dwell: 1.6),
        .init(action: .close, event: .closed, dwell: 0.9),
    ]
    var index = -1
    var running = false
    var waiting: DayOneMedalEvent?
    var deadline = Double.infinity
    var watchdog = Double.infinity
    var seen = Set<DayOneMedalEvent>()

    mutating func start(_ now: Double) {
        stop()
        running = true
        index = -1
        deadline = now
    }

    mutating func stop() {
        running = false
        waiting = nil
        deadline = .infinity
        watchdog = .infinity
    }

    mutating func signal(_ event: DayOneMedalEvent, _ now: Double) {
        guard running else { return }
        seen.insert(event)
        if waiting == event { complete(now) }
    }

    mutating func complete(_ now: Double) {
        waiting = nil
        watchdog = .infinity
        deadline = now + Self.steps[index].dwell
    }

    mutating func advance(_ now: Double) -> DayOneMedalCommand? {
        guard running else { return nil }
        if now >= watchdog {
            stop()
            return .close
        }
        guard now + 1e-10 >= deadline else { return nil }
        deadline = .infinity
        index = (index + 1) % Self.steps.count
        let step = Self.steps[index]
        waiting = step.event
        watchdog = now + 60
        if step.action != nil { seen.removeAll() }
        if step.event == nil || step.event.map({ seen.contains($0) }) == true { complete(now) }
        return step.action
    }

    static func completions(_ previous: DayOneMedalState, _ next: DayOneMedalState) -> [DayOneMedalEvent] {
        var events: [DayOneMedalEvent] = []
        if next.struck >= 0 && next.struck != previous.struck { events.append(.struck) }
        if next.forged && next.heat == 0 && previous.heat > 0 { events.append(.cooled) }
        if next.mode == .rest && previous.mode != .rest {
            events.append(.landed)
            events.append(abs(Int(floor(next.base / .pi + 0.5)) % 2) == 1 ? .back : .front)
        }
        if next.engraved && !previous.engraved { events.append(.engraved) }
        if next.caught && !previous.caught { events.append(.caught) }
        if next.worn == 0 && previous.worn > 0 { events.append(.unworn) }
        if next.morphPhase == .idle && previous.morphPhase == .commit { events.append(.milestone) }
        return events
    }
}

struct DayOneMedalSpark {
    var born: Double
    var point: DayOneMedalPoint
    var velocity: DayOneMedalPoint
    var life: Double
}

struct DayOneMedalEngine {
    var state = DayOneMedalState()
    var ribbon = DayOneMedalRibbonState()
    var config = DayOneMedalConfig()
    var demo = DayOneMedalDemoClock()
    var sparks: [DayOneMedalSpark] = []
    var elapsed = 0.0
    var opened = false
    var present = 0.0
    var drag = 0.0
    var dragging = false
    var dragVelocity = 0.0
    var dragOmega = 30.0
    var dragEnergy = 1.0
    var dragDismissTime = -1.0
    var dragDismissFrom = 0.0
    var autoplay = false
    var resumeAt = Double.infinity
    var restartAt = Double.infinity
    var transitionStart = -1.0
    var transitionFrom = 0.0
    var transitionTo = 0.0
    var transitionDuration = 0.5
    var events = 0
    var completed: [DayOneMedalEvent] = []
    var headerAlpha = 1.0
    var headerFrom = 1.0
    var headerTo = 1.0
    var headerTime = 0.0
    var sparkBudget = 0.0
    var sparkSerial = 0
    var rotation: [Double] { DayOneMedalPhysics.rotation(state.yaw, state.pitch, state.roll) }

    mutating func signal(_ event: DayOneMedalEvent) {
        completed.append(event)
        demo.signal(event, elapsed)
    }

    mutating func publish(_ next: DayOneMedalState) {
        let previous = state
        state = next
        if previous.wearing != next.wearing {
            headerFrom = headerAlpha
            headerTo = next.wearing ? 0 : 1
            headerTime = elapsed
        }
        events |= next.events
        for event in DayOneMedalDemoClock.completions(previous, next) { signal(event) }
        if next.events & 256 != 0 {
            // Text and the preloaded texture pair change in this native frame's transaction.
            publish(DayOneMedalPhysics.act(state, .textures(state.revision), config))
        }
    }

    mutating func act(_ action: DayOneMedalAction) {
        publish(DayOneMedalPhysics.act(state, action, config))
        if case .reset = action { signal(.reset) }
    }

    mutating func show() {
        guard !opened else { return }
        opened = true
        act(.enter)
        drag = 0
        transitionFrom = present
        transitionTo = 1
        transitionStart = elapsed
        transitionDuration = config.reduced ? 0.16 : 0.5
    }

    mutating func hide() {
        guard opened else { return }
        state.down = false
        state.hold = false
        transitionFrom = present
        transitionTo = 0
        transitionStart = elapsed
        transitionDuration = config.reduced ? 0.14 : 0.38
    }

    mutating func touched() {
        demo.stop()
        restartAt = .infinity
        resumeAt = autoplay && !config.reduced ? elapsed + 3.5 : .infinity
    }

    mutating func command(_ command: DayOneMedalCommand) {
        switch command {
        case .reset: act(.reset)
        case .open: show()
        case .forge: act(.forge)
        case .turn: act(.drift)
        case .flick: act(.flick)
        case .wear: act(.wear)
        case .next: act(.day(1))
        case .close: hide()
        }
    }

    mutating func releaseHeader(velocity: Double, dismiss: Bool) {
        dragging = false
        if dismiss {
            hide()
            dragDismissTime = elapsed
            dragDismissFrom = drag
            return
        }
        dragVelocity = velocity
        var low = Double.ulpOfOne
        var high = 8000.0
        for _ in 0..<100 {
            let stiffness = (low + high) / 2
            let omega = sqrt(stiffness / 4)
            let b = velocity + drag * omega
            let end = (drag + b * 0.45) * exp(-omega * 0.45)
            let endVelocity = -end * omega + b * exp(-omega * 0.45)
            let initialEnergy = 2 * velocity * velocity + 0.5 * stiffness * drag * drag
            let finalEnergy = 2 * endVelocity * endVelocity + 0.5 * stiffness * end * end
            let difference = finalEnergy / max(initialEnergy, 1e-30) - 6e-9
            if abs(difference) < 6e-12 {
                low = stiffness
                high = stiffness
                break
            }
            if difference > 0 { low = stiffness } else { high = stiffness }
        }
        dragOmega = sqrt((low + high) / 8)
        dragEnergy = max(1e-30, 2 * (velocity * velocity + dragOmega * dragOmega * drag * drag))
    }

    static func sheetEase(_ x: Double) -> Double {
        var low = 0.0
        var high = 1.0
        for _ in 0..<18 {
            let t = (low + high) / 2
            let bx = 3 * (1 - t) * (1 - t) * t * 0.32 + t * t * t
            if bx < x { low = t } else { high = t }
        }
        let t = (low + high) / 2
        return 3 * (1 - t) * (1 - t) * t * 0.72 + 3 * (1 - t) * t * t + t * t * t
    }

    mutating func tick(_ dt: Double, scale: Double) {
        elapsed += dt
        completed.removeAll(keepingCapacity: true)
        if elapsed >= resumeAt && !state.down {
            resumeAt = .infinity
            if opened {
                hide()
                restartAt = elapsed + (config.reduced ? 0.14 : 0.38)
            } else { demo.start(elapsed) }
        }
        if elapsed >= restartAt && !opened {
            restartAt = .infinity
            demo.start(elapsed)
        }
        if let command = demo.advance(elapsed) { self.command(command) }
        if transitionStart >= 0 {
            let progress = elapsed - transitionStart + 1e-9 >= transitionDuration ? 1 : min(1, (elapsed - transitionStart) / transitionDuration)
            present = transitionFrom + (transitionTo - transitionFrom) * Self.sheetEase(progress)
            if progress >= 1 {
                present = transitionTo
                transitionStart = -1
                if present == 1 { signal(.arrived) }
                else {
                    opened = false
                    signal(.closed)
                }
            }
        }
        let previousClock = state.clock
        if present == 1 && opened && transitionStart < 0 { publish(DayOneMedalPhysics.step(state, dt, config)) }
        if !dragging {
            if dragDismissTime >= 0 {
                let progress = min(1, (elapsed - dragDismissTime) / 0.38)
                drag = dragDismissFrom * (1 - DayOneMedalPhysics.easeInOut(progress))
                if progress >= 1 { dragDismissTime = -1 }
            } else if drag != 0 || dragVelocity != 0 {
                let time = min(dt, 0.064)
                let b = dragVelocity + dragOmega * drag
                let envelope = exp(-dragOmega * time)
                drag = (drag + b * time) * envelope
                dragVelocity = (dragVelocity - dragOmega * b * time) * envelope
                let energy = (dragVelocity * dragVelocity + dragOmega * dragOmega * drag * drag) * 2
                if energy / dragEnergy < 6e-9 {
                    drag = 0
                    dragVelocity = 0
                }
            }
        }
        headerAlpha = headerFrom + (headerTo - headerFrom) * DayOneMedalPhysics.easeInOut(min(1, max(0, (elapsed - headerTime) / 0.2)))
        let matrix = rotation
        let attach = DayOneMedalPoint(x: config.centre.x + state.offsetX + config.half * matrix[3],
                                      y: config.centre.y + state.offsetY - config.half * matrix[4])
        ribbon = DayOneMedalRibbonPhysics.step(ribbon, state.clock - previousClock, state.worn * present, config.pivot, attach, scale, config.reduced)
        let spot = DayOneMedalPhysics.sparkSpot(state, config)
        let strength = config.reduced ? 0 : spot.on
        sparks.removeAll { state.clock - $0.born >= $0.life }
        sparkBudget = strength > 0.01 ? sparkBudget + (state.clock - previousClock) * (3 + 19 * strength) : 0
        while sparkBudget >= 1 && sparks.count < 14 {
            sparkBudget -= 1
            sparkSerial += 1
            let seed = sin(Double(sparkSerial) * 127.1) * 43758.5453
            let r = seed - floor(seed)
            let angle = -.pi * (0.12 + 0.76 * r)
            let speed = (45 + r * 95) * scale
            sparks.append(DayOneMedalSpark(born: state.clock, point: spot.point,
                velocity: DayOneMedalPoint(x: cos(angle) * speed, y: sin(angle) * speed), life: 0.22 + r * 0.18))
        }
    }
}

struct DayOneMedalRandom {
    var value: UInt32 = 20261013
    mutating func next() -> Double {
        value = value &* 1664525 &+ 1013904223
        return Double(value) / 4294967296
    }
}

enum DayOneMedalLeafSeed {
    static func make(count: Int, width: Double, height: Double, half: Double, centreY: Double, ratio: Double, centreX: Double) -> [Float] {
        var random = DayOneMedalRandom()
        var data: [Float] = []
        let pointsPerUnit = half / ratio
        for index in 0..<count {
            let depth = random.next()
            let z = index < 4 ? 1.45 + depth * 0.2 : (depth - 0.5) * 1.7
            let perspective = (6.9 - z) / 6.9
            let phase = random.next() * .pi * 2
            let population = random.next()
            let size = population < 0.1 ? 8 + random.next() * 3 : population < 0.16 ? 3 + random.next() * 2 : 4 + random.next() * 4
            let x = ((random.next() - 0.5) * width * 1.15 + width / 2 - centreX) / half * perspective
            let y = (Double(index) < Double(count) * 0.55 ? -1.1 + random.next() * 2.2 : centreY / half + 0.20 + random.next() * height / half * 0.9) * perspective
            let seed = random.next()
            let shapeHeight = size * (0.65 + random.next() * 0.3) / pointsPerUnit
            let sign = random.next() < 0.5 ? -1.0 : 1.0
            let spin = sign * (1.2 + 3.4 * random.next())
            let fall = (62 + 88 * random.next()) * (0.7 + 0.5 * depth) / pointsPerUnit
            let drift = (6 + 14 * random.next()) / pointsPerUnit
            let rate = 0.6 + 1.3 * random.next()
            let tumble = 2.2 + 4.6 * random.next()
            data += [x, y, z, seed, 0, 0, 0, 0, size / pointsPerUnit, shapeHeight, phase, spin, fall, drift, rate, tumble, 0, 0, 0, 0].map(Float.init)
        }
        return data
    }
}
