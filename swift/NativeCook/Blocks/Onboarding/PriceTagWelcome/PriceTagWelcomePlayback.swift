import Foundation

/// The engine of motion.ts: autoPlay script, touch, the way home. One instance per screen.
@MainActor
final class PriceTagWelcomePlayback {
    private typealias Motion = PriceTagWelcomeMotion
    private enum Mode { case automatic, live, returning }

    private struct Engine {
        var mode: Mode
        var autoPlay: Bool
        var still: Bool
        var autoStart: Double
        var last: Double
        var lastTouch: Double
        var x: Double
        var velocity: Double
        var acceleration: Double
        var dragging: Bool
        var dragOrigin: Double
        var dragTranslation: Double
        var theta: [Double]
        var angularVelocity: [Double]
        var frozeAt: [Double]
        var thawAt: [Double]
        var press: [Double]
        var pressTarget: [Double]
        var homeAt: Double
        var homeFrom: Double
        var homeTo: Double
        var homeDuration: Double
    }

    private var engine = PriceTagWelcomePlayback.makeEngine(now: 0, autoPlay: false, still: false)
    private(set) var latestFrame = PriceTagWelcomeMotion.Frame.rest
    /// Whether the last toggle froze (true) or thawed (false), for the haptic.
    private(set) var lastFroze = false

    func start(autoPlay: Bool, reduced: Bool, at now: Double) {
        engine = Self.makeEngine(now: now, autoPlay: autoPlay, still: reduced)
        latestFrame = reduced ? .rest : (autoPlay ? Motion.frame(at: 0) : .rest)
    }

    /// Live Reduce Motion / screen reader and autoPlay changes (setAutoPlay in motion.ts).
    func configure(autoPlay: Bool, reduced: Bool, at now: Double) {
        if reduced {
            var frozeAt = Self.minusOnes
            for index in 0..<Motion.itemCount where isFrozen(index) { frozeAt[index] = now - 1 }
            engine = Self.makeEngine(now: now, autoPlay: autoPlay, still: true)
            engine.mode = .live
            engine.frozeAt = frozeAt
            latestFrame = makeLiveFrame(moving: false, at: now)
            return
        }
        if autoPlay && engine.mode == .live && !engine.dragging {
            engine.autoPlay = true
            engine.still = false
            engine.lastTouch = now
        } else if !autoPlay && engine.mode == .automatic {
            advance(to: now)
            takeOver(at: now)
            engine.autoPlay = false
            engine.still = false
        } else {
            engine.autoPlay = autoPlay
            engine.still = false
        }
    }

    func foreground(at now: Double) {
        engine.last = now
        engine.dragging = false
        if engine.mode == .automatic {
            engine.autoStart = now
            latestFrame = Motion.frame(at: 0)
        }
    }

    func sample(at now: Double) -> PriceTagWelcomeMotion.Frame {
        advance(to: now)
        return latestFrame
    }

    @discardableResult
    func touchDown(x: Double, y: Double, at now: Double) -> Int {
        advance(to: now)
        let hit = Motion.hitItem(x: x, y: y, offset: latestFrame.x)
        takeOver(at: now)
        engine.velocity = 0
        engine.pressTarget = Self.zeros
        if hit >= 0 { engine.pressTarget[hit] = 1 }
        engine.lastTouch = now
        return hit
    }

    func beginDrag(at now: Double) {
        advance(to: now)
        takeOver(at: now)
        engine.dragging = true
        engine.dragOrigin = engine.x
        engine.dragTranslation = 0
        engine.velocity = 0
        engine.pressTarget = Self.zeros
        engine.lastTouch = now
    }

    func moveDrag(translation: Double, at now: Double) {
        engine.dragTranslation = translation
        engine.lastTouch = now
    }

    func endDrag(velocity: Double, at now: Double) {
        engine.dragging = false
        engine.velocity = velocity
        engine.lastTouch = now
        engine.pressTarget = Self.zeros
    }

    @discardableResult
    func tap(x: Double, y: Double, at now: Double) -> Int {
        advance(to: now)
        let hit = Motion.hitItem(x: x, y: y, offset: engine.x)
        guard hit >= 0 else {
            cancelTouch(at: now)
            return -1
        }
        toggle(index: hit, at: now)
        return hit
    }

    func accessibilityToggle(index: Int, at now: Double) {
        advance(to: now)
        toggle(index: index, at: now)
        advance(to: now)
    }

    func cancelTouch(at now: Double) {
        engine.pressTarget = Self.zeros
        engine.lastTouch = now
    }

    // MARK: engine

    private static var zeros: [Double] { Array(repeating: 0, count: Motion.itemCount) }
    private static var minusOnes: [Double] { Array(repeating: -1, count: Motion.itemCount) }

    private static func makeEngine(now: Double, autoPlay: Bool, still: Bool) -> Engine {
        Engine(
            mode: autoPlay && !still ? .automatic : .live,
            autoPlay: autoPlay, still: still,
            autoStart: now, last: now, lastTouch: -1e9,
            x: 0, velocity: 0, acceleration: 0,
            dragging: false, dragOrigin: 0, dragTranslation: 0,
            theta: zeros, angularVelocity: zeros,
            frozeAt: minusOnes, thawAt: minusOnes,
            press: zeros, pressTarget: zeros,
            homeAt: -1, homeFrom: 0, homeTo: 0, homeDuration: 0
        )
    }

    private func isFrozen(_ index: Int) -> Bool {
        engine.frozeAt[index] >= 0 && engine.thawAt[index] < 0
    }

    private func advance(to now: Double) {
        if engine.mode == .automatic {
            let time = Motion.modulo(now - engine.autoStart, Motion.loop)
            engine.last = now
            latestFrame = Motion.frame(at: time)
            return
        }

        let delta = min(max(now - engine.last, 0), 1 / 20)
        engine.last = now

        // A thawed item is done once its melt has run.
        var heavy = false
        for index in 0..<Motion.itemCount {
            let limit = engine.still ? Motion.stillFade : Motion.Freeze.melt
            if engine.thawAt[index] >= 0 && now - engine.thawAt[index] >= limit {
                engine.frozeAt[index] = -1
                engine.thawAt[index] = -1
            }
            if isFrozen(index) { heavy = true }
        }

        // Row: finger, momentum (heavier while something is frozen), or the way home.
        let oldVelocity = engine.velocity
        if engine.dragging {
            let x = engine.dragOrigin + engine.dragTranslation
            let measured = delta > 0 ? (x - engine.x) / delta : engine.velocity
            let filter = delta > 0 ? 1 - exp(-delta / 0.03) : 0
            engine.velocity = Motion.lerp(engine.velocity, measured, filter)
            engine.x = x
        } else if engine.mode == .returning {
            let progress = Motion.segment(now, engine.homeAt, engine.homeAt + engine.homeDuration)
            let x = Motion.lerp(engine.homeFrom, engine.homeTo, Motion.easeInOut(progress))
            engine.velocity = delta > 0 ? (x - engine.x) / delta : 0
            engine.x = x
        } else if engine.velocity != 0 {
            let tau = heavy ? Motion.decayTau * Motion.Freeze.drag : Motion.decayTau
            engine.velocity = abs(engine.velocity) < 4 ? 0 : engine.velocity * exp(-delta / tau)
            engine.x += engine.velocity * delta
        }
        let rawAcceleration = delta > 0 ? (engine.velocity - oldVelocity) / delta : 0
        let accelerationFilter = delta > 0 ? 1 - exp(-delta / 0.025) : 0
        engine.acceleration = Motion.lerp(engine.acceleration, rawAcceleration, accelerationFilter)

        // Pendulums (held while frozen) and presses: fixed 1/240 s sub-steps.
        let stepCount = max(1, Int(ceil(delta * Motion.simulationHertz)))
        let fixedDelta = delta / Double(stepCount)
        for index in 0..<Motion.itemCount {
            let ft = engine.frozeAt[index] < 0 ? -1 : now - engine.frozeAt[index]
            let mt = engine.thawAt[index] < 0 ? -1 : now - engine.thawAt[index]
            if engine.still {
                engine.theta[index] = 0
                engine.angularVelocity[index] = 0
            } else if Motion.lockedBy(freeze: ft, thaw: mt) {
                engine.angularVelocity[index] = 0
            } else {
                for _ in 0..<stepCount {
                    let swing = Motion.swingStep(
                        theta: engine.theta[index], velocity: engine.angularVelocity[index],
                        index: index, acceleration: engine.acceleration, delta: fixedDelta
                    )
                    engine.theta[index] = swing.theta
                    engine.angularVelocity[index] = swing.velocity
                }
            }
            let target = engine.pressTarget[index]
            let duration = target > engine.press[index] ? Motion.Press.durationIn : Motion.Press.durationOut
            engine.press[index] = Motion.lerp(engine.press[index], target, 1 - exp(-delta * 3 / duration))
        }

        // Idle: after resumeAfter, ease home and thaw, then restart the loop from its first frame.
        if engine.autoPlay && !engine.still && engine.mode == .live && !engine.dragging
            && now - engine.lastTouch > Motion.resumeAfter && abs(engine.velocity) < 40 {
            let destination = (engine.x / PriceTagWelcomeTheme.period).rounded() * PriceTagWelcomeTheme.period
            engine.mode = .returning
            engine.homeAt = now
            engine.homeFrom = engine.x
            engine.homeTo = destination
            engine.homeDuration = min(
                Motion.Home.maximum,
                Motion.Home.base + Motion.Home.perPixel * abs(engine.x - destination)
            )
            engine.velocity = 0
        }
        if engine.mode == .returning {
            for index in 0..<Motion.itemCount
            where isFrozen(index) && now >= engine.homeAt + Motion.Home.thawStagger * Double(index) {
                engine.thawAt[index] = now
            }
        }
        if engine.mode == .returning && now >= engine.homeAt + engine.homeDuration {
            engine.x = 0
            engine.velocity = 0
            var settled = true
            for index in 0..<Motion.itemCount {
                if abs(engine.theta[index]) > 0.002 || abs(engine.angularVelocity[index]) > 0.02
                    || engine.frozeAt[index] >= 0 {
                    settled = false
                }
            }
            if settled {
                engine = Self.makeEngine(now: now, autoPlay: true, still: false)
                latestFrame = Motion.frame(at: 0)
                return
            }
        }

        latestFrame = makeLiveFrame(moving: isMoving, at: now)
    }

    private var isMoving: Bool {
        if engine.dragging || abs(engine.velocity) > 2 { return true }
        for index in 0..<Motion.itemCount {
            if abs(engine.theta[index]) > 0.0015 || abs(engine.angularVelocity[index]) > 0.01 { return true }
            if abs(engine.press[index] - engine.pressTarget[index]) > 0.002 { return true }
            if engine.frozeAt[index] >= 0 { return true }
        }
        return engine.mode != .live || (engine.autoPlay && !engine.still)
    }

    private func makeLiveFrame(moving: Bool, at now: Double) -> Motion.Frame {
        let light = Motion.glint(x: engine.x, velocity: engine.velocity)
        return Motion.Frame(
            x: engine.x,
            velocity: engine.velocity,
            theta: engine.theta.map(Motion.softDegrees),
            freezeTime: engine.frozeAt.map { $0 < 0 ? -1 : now - $0 },
            thawTime: engine.thawAt.map { $0 < 0 ? -1 : now - $0 },
            press: engine.press,
            clock: engine.still ? 0 : Motion.modulo(now, 1000),
            glint: light.progress,
            glintAlpha: light.alpha,
            moving: moving
        )
    }

    /// Leave autoPlay (or the way home) for touch: carry over every value on screen as it is.
    private func takeOver(at now: Double) {
        if engine.mode == .live {
            engine.lastTouch = now
            return
        }
        if engine.mode == .returning {
            engine.mode = .live
            engine.homeAt = -1
            engine.lastTouch = now
            return
        }
        let time = Motion.modulo(now - engine.autoStart, Motion.loop)
        for index in 0..<Motion.itemCount {
            let swing = Motion.scriptSwing(index: index, time: time)
            engine.theta[index] = swing.theta
            engine.angularVelocity[index] = swing.velocity
            let f = Motion.scriptFrost(index: index, time: time)
            engine.frozeAt[index] = f.freeze < 0 ? -1 : now - f.freeze
            engine.thawAt[index] = f.thaw < 0 ? -1 : now - f.thaw
            engine.press[index] = Motion.scriptPress(index: index, time: time)
        }
        engine.mode = .live
        engine.lastTouch = now
        engine.last = now
        engine.x = 0
        engine.velocity = 0
        engine.acceleration = 0
        engine.pressTarget = Self.zeros
    }

    /// A tap on item i: freeze its price, or thaw it (tapItem in motion.ts).
    private func toggle(index: Int, at now: Double) {
        takeOver(at: now)
        if isFrozen(index) {
            engine.thawAt[index] = now
            lastFroze = false
        } else if engine.thawAt[index] >= 0 && !engine.still {
            // Refrozen mid-thaw: the front restarts at the edge of the ice that is still there.
            let melt = Motion.clamp01((now - engine.thawAt[index]) / Motion.Freeze.melt)
            let q = Motion.clamp01(1 - (melt - 0.16) / 0.62)
            engine.frozeAt[index] = now - (1 - cbrt(1 - q)) * Motion.Freeze.grow
            engine.thawAt[index] = -1
            lastFroze = true
        } else {
            engine.frozeAt[index] = now
            engine.thawAt[index] = -1
            if !engine.still { engine.angularVelocity[index] += Motion.Freeze.kick }
            lastFroze = true
        }
        engine.pressTarget = Self.zeros
        engine.lastTouch = now
    }
}
