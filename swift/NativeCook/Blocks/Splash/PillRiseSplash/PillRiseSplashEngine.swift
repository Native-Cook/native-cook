import SwiftUI
import UIKit

enum PillRiseSplashMetrics {
    // The capsule: `pillWidth` wide, its top a half circle. The splash's progress `s` runs 0 (hidden
    // under the bottom edge) → `peek` (a dark capsule holding the button) → 0.48 (risen to 30% of the
    // screen) → 0.68 (touching the top, a little wider) → 1 (the whole screen).
    static let pillWidth = 68.0
    static let peek = 0.12
    static let buttonRadius = 24.0
    // How far above the home indicator the button sits while the capsule peeks.
    static let peekLift = 64.0
    // Drag: a full screen of travel times this share moves `s` from 0 to 1. A release commits when
    // it would carry `s` past `commitAt` within `throwTime` s.
    static let dragSpan = 0.85
    static let commitAt = 0.42
    static let throwTime = 0.22
    static let dragSlop = 6.0
    static let tapMax = 0.4
    static let ctaTapMax = 0.6
    // Splash timings, s.
    static let peekTime = 0.52
    static let riseTime = 1.5
    static let boardDelay = 0.12
    static let boardTime = 1.4
    static let exitTime = 0.65
    static let logoTime = 0.52

    // Jar drag: pulled further than `rubber` pt, a jar gives less and less. Released, it springs
    // home with the finger's speed (stiffness `jarK`, damping `jarC` per unit mass: it wobbles).
    static let rubber = 160.0
    static let jarK = 220.0
    static let jarC = 13.0
    static let jarGrab = 90.0
    // The line the coins appear on, below the progress bar.
    static let coinLine = 34.0
    static let coinDX = [-14.0, 11, -7, 16, -10]

    // autoPlay script (s). One loop starts and ends on the resting splash.
    static let loop = 11.0
    static let peekAt = 0.6
    static let riseAt = 1.4
    static let coinAt = [4.5, 5.25, 5.95, 6.7, 7.4]
    static let coinJar = [0, 2, 1, 0, 2]
    static let pressAt = 9.0
    static let exitAt = 9.15
    // A touch pauses the script for `userHold` s; it resumes at the matching point of the loop.
    static let userHold = 4.0
    static let resumeSplash = peekAt - 0.2
    static let resumeBoard = 8.2

    static let headlineFont = "Futura-CondensedExtraBold"
    static let headlineSize = 42.0
    static let headlineLine = 41.0

    // The jar stage is laid out for a 402 × 470 pt box and scaled to the room left.
    static let stageRefWidth = 402.0
    static let stageRefHeight = 470.0

    // An invented mark: two slanted bars passing each other, like a transfer.
    static let markSize = 64.0
    static let markScale = 1.35
    static let markSkew = -0.22

    static let ctaHeight = 56.0
    static let ctaLeft = 20.0

    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))
    static let riseEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.6, y: 0), endControlPoint: UnitPoint(x: 0.3, y: 1))
    static let exitEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.77, y: 0), endControlPoint: UnitPoint(x: 0.175, y: 1))

    static func seg(_ v: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (v - a) / (b - a))) }

    /// Past `limit`, a pull gives less and less.
    static func rubber(_ d: Double, _ limit: Double) -> Double {
        let a = abs(d)
        let sign: Double = d > 0 ? 1 : d < 0 ? -1 : 0
        return sign * (a < limit ? a : limit + (a - limit) * 0.3)
    }

    /// Piecewise linear interpolation, clamped at both ends, like Reanimated's `interpolate`.
    static func lerp(_ x: Double, _ input: [Double], _ output: [Double]) -> Double {
        if x <= input[0] { return output[0] }
        for i in 1..<input.count where x <= input[i] {
            let p = (x - input[i - 1]) / (input[i] - input[i - 1])
            return output[i - 1] + (output[i] - output[i - 1]) * p
        }
        return output[output.count - 1]
    }

    /// Reanimated's default timing curve, ease in and out (quadratic).
    static func standard(_ p: Double) -> Double { p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2 }
}

/// Reanimated's `withSpring` with a duration and a damping ratio, stepped a frame at a time: the
/// stiffness is solved so the spring's energy falls to the threshold after 1.5 × the duration.
struct PillRiseSplashSpring {
    let target: Double
    let dampingRatio: Double
    let clamp: Bool
    private(set) var velocity: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let initialEnergy: Double
    private static let energyThreshold = 6e-9

    init(from value: Double, to target: Double, velocity: Double, duration: Double, dampingRatio: Double, clamp: Bool) {
        self.target = target
        self.dampingRatio = dampingRatio
        self.clamp = clamp
        var v = velocity
        // Velocity that would carry the first frame away from the target is dropped.
        if (target > value && v < 0) || (target < value && v > 0) { v = 0 }
        self.velocity = v
        let x0 = value - target
        let zeta = dampingRatio
        let settling = duration * 1.5
        func energy(_ x: Double, _ v: Double, _ k: Double) -> Double { 0.5 * k * x * x + 0.5 * v * v }
        func difference(_ k: Double) -> Double {
            let w = sqrt(k) * zeta
            let xt = (x0 + (v + x0 * w) * settling) * exp(-w * settling)
            let vt = (x0 + (v + x0 * w) * settling) * exp(-w * settling) * -w + (v + x0 * w) * exp(-w * settling)
            return energy(xt, vt, k) / energy(x0, v, k) - Self.energyThreshold
        }
        var low = Double.ulpOfOne
        var high = 8e3
        let rising = difference(high) >= difference(low)
        var k = (low + high) / 2
        var left = 100
        while abs(difference(k)) > Self.energyThreshold * 1e-3 && left > 0 {
            left -= 1
            if (difference(k) < 0) == rising { low = k } else { high = k }
            k = (low + high) / 2
        }
        if !k.isFinite { k = 170 }
        stiffness = k
        omega0 = sqrt(k)
        omega1 = zeta < 1 ? omega0 * sqrt(1 - zeta * zeta) : 0
        initialEnergy = energy(x0, v, k)
    }

    /// Moves `value` on by `dt` seconds; true once the spring has come to rest on its target.
    mutating func step(_ value: inout Double, dt: Double) -> Bool {
        if initialEnergy == 0 || !initialEnergy.isFinite {
            value = target
            return true
        }
        let start = value
        let zeta = dampingRatio
        let x0 = value - target
        let v0 = velocity
        if zeta < 1 {
            let sin1 = sin(omega1 * dt)
            let cos1 = cos(omega1 * dt)
            let envelope = exp(-zeta * omega0 * dt)
            let frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            value = target + frag
            velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        } else {
            let envelope = exp(-omega0 * dt)
            value = target + envelope * (x0 + (v0 + omega0 * x0) * dt)
            velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
        }
        // Overshoot clamped: crossing the target ends the spring on it.
        if clamp && (start - target) * (value - target) < 0 {
            value = target
            velocity = 0
            return true
        }
        let now = 0.5 * stiffness * (target - value) * (target - value) + 0.5 * velocity * velocity
        if now / initialEnergy <= Self.energyThreshold {
            value = target
            velocity = 0
            return true
        }
        return false
    }
}

/// A number driven like a Reanimated shared value: set outright, or animated by a (delayed) timing
/// curve or a spring, stepped by the block's clock. A finished animation hands back its callback.
struct PillRiseSplashValue {
    private enum Motion {
        case spring(PillRiseSplashSpring)
        case timing(from: Double?, to: Double, start: Double, duration: Double, curve: (Double) -> Double)
    }

    private(set) var value: Double
    private var motion: Motion?
    private var done: (() -> Void)?

    init(_ value: Double) { self.value = value }

    var isAnimating: Bool { motion != nil }

    /// Puts the value somewhere at once; whatever was moving it stops, and its callback never runs.
    mutating func set(_ value: Double) {
        self.value = value
        motion = nil
        done = nil
    }

    mutating func stop() {
        motion = nil
        done = nil
    }

    /// A timing that starts after `delay` s from wherever the value is by then.
    mutating func timing(to target: Double, duration: Double, delay: Double = 0, now: Double,
                         curve: @escaping (Double) -> Double, then: (() -> Void)? = nil) {
        motion = .timing(from: delay > 0 ? nil : value, to: target, start: now + delay, duration: duration, curve: curve)
        done = then
    }

    mutating func spring(to target: Double, velocity: Double, duration: Double, dampingRatio: Double,
                         clamp: Bool = false, then: (() -> Void)? = nil) {
        motion = .spring(PillRiseSplashSpring(from: value, to: target, velocity: velocity, duration: duration,
                                              dampingRatio: dampingRatio, clamp: clamp))
        done = then
    }

    /// Steps the value to block time `now`; returns the callback of an animation that just finished.
    mutating func step(dt: Double, now: Double) -> (() -> Void)? {
        guard let motion else { return nil }
        var finished = false
        switch motion {
        case .spring(var spring):
            var next = value
            finished = spring.step(&next, dt: dt)
            value = next
            self.motion = finished ? nil : .spring(spring)
        case let .timing(from, to, start, duration, curve):
            if now < start { return nil }
            let origin = from ?? value
            if from == nil { self.motion = .timing(from: origin, to: to, start: start, duration: duration, curve: curve) }
            let p = duration <= 0 ? 1 : min(1, max(0, (now - start) / duration))
            value = origin + (to - origin) * curve(p)
            if p >= 1 {
                finished = true
                self.motion = nil
            }
        }
        guard finished else { return nil }
        let callback = done
        done = nil
        return callback
    }
}

/// A coin in flight: when it appeared (block time, s), the jar it falls into and its sideways offset.
struct PillRiseSplashCoin {
    var t0: Double
    var jar: Int
    var dx: Double
    var user: Bool

    static let none = PillRiseSplashCoin(t0: -100, jar: 0, dx: 0, user: false)
}

/// Where everything sits for a screen size: the splash's capsule and the board, bottom up.
struct PillRiseSplashLayout: Equatable {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double
    var lines: Int

    private typealias M = PillRiseSplashMetrics

    var ctaTop: Double { height - max(bottom, 16) - 8 - M.ctaHeight }
    var ctaWidth: Double { width - M.ctaLeft * 2 }
    var linkTop: Double { ctaTop - 52 }
    var headlineTop: Double { linkTop - 18 - M.headlineLine * Double(lines) - 6 }
    var barTop: Double { top + 14 }
    var stageTop: Double { barTop + 24 }
    var stageBottom: Double { headlineTop - 16 }
    var stageMid: Double { (stageTop + stageBottom) / 2 }
    var k: Double { min(width / M.stageRefWidth, (stageBottom - stageTop) / M.stageRefHeight, 1.25) }
    var peekTop: Double { height - max(bottom, 12) - M.peekLift - M.buttonRadius - 10 }
    var ctaRect: CGRect { CGRect(x: M.ctaLeft, y: ctaTop, width: ctaWidth, height: M.ctaHeight) }
}

/// Everything one frame draws.
struct PillRiseSplashFrame {
    var layout: PillRiseSplashLayout
    var s: Double
    var board: Double
    var exit: Double
    var logoIn: Double
    var press: Double
    var time: Double
    var coins: [PillRiseSplashCoin]
    var offsetX: [Double]
    var offsetY: [Double]
    var speedX: [Double]
    var landed: [Double]
    var reducedMotion: Bool
}

struct PillRiseSplashSettings {
    var autoPlay: Bool
    var reducedMotion: Bool
    var layout: PillRiseSplashLayout
    var onGetStarted: (() -> Void)?
}

/// The splash's state, stepped once a frame like the RN frame callback, and the touches that drive it.
@MainActor
final class PillRiseSplashEngine {
    private typealias M = PillRiseSplashMetrics

    // Splash progress (see `M.peek`), board entrance, the way back, and the mark's own fade.
    private var s = PillRiseSplashValue(0)
    private var board = PillRiseSplashValue(0)
    private var exit = PillRiseSplashValue(0)
    private var logoIn = PillRiseSplashValue(1)
    private var press = PillRiseSplashValue(0)
    // 0 splash, 1 opening or board, 2 on the way back.
    private var phase = 0
    // Seconds since the block appeared (drives the floating and the coins), and the autoPlay clock.
    private var time = 0.0
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var coins = Array(repeating: PillRiseSplashCoin.none, count: PillRiseSplashJars.coinSlots)
    private var nextSlot = 0
    private var offsetX = [0.0, 0, 0]
    private var offsetY = [0.0, 0, 0]
    private var speedX = [0.0, 0, 0]
    private var speedY = [0.0, 0, 0]
    private var landed = [-100.0, -100, -100]

    private var lastDate: Date?
    private var started = false
    private var settings = PillRiseSplashSettings(autoPlay: false, reducedMotion: false,
                                                  layout: PillRiseSplashLayout(width: 1, height: 1, top: 0, bottom: 0, lines: 3),
                                                  onGetStarted: nil)
    private var jars: [PillRiseSplashPlacedJar] = []
    private var jarLayout: PillRiseSplashLayout?

    // The touch under way: 0 undecided, 1 the capsule, 2 a jar, 3 nothing.
    private var touchStart: Date?
    private var panning = false
    private var panMode = 0
    private var grabbed = -1
    private var grabFrom = (s: 0.0, x: 0.0, y: 0.0)
    private var onCta = false

    private let haptic = UIImpactFeedbackGenerator(style: .light)

    private var reduced: Bool { settings.reducedMotion }

    /// The jars placed for the current layout.
    func placedJars(for layout: PillRiseSplashLayout) -> [PillRiseSplashPlacedJar] {
        if jarLayout != layout {
            jarLayout = layout
            jars = PillRiseSplashJars.place(layout: layout)
        }
        return jars
    }

    func step(to date: Date, settings next: PillRiseSplashSettings) -> PillRiseSplashFrame {
        let auto = next.autoPlay && !next.reducedMotion
        if !started || auto != (settings.autoPlay && !settings.reducedMotion) {
            settings = next
            started = true
            looping = auto
            clock = 0
            hold = 0
            if !auto { peek(delay: 0.7) }
        }
        settings = next
        let jars = placedJars(for: next.layout)

        let dt = min(lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60, 0.064)
        lastDate = date
        if dt > 0 { advance(dt, jars: jars) }

        return PillRiseSplashFrame(
            layout: next.layout, s: s.value, board: board.value, exit: exit.value, logoIn: logoIn.value,
            press: press.value, time: time, coins: coins, offsetX: offsetX, offsetY: offsetY, speedX: speedX,
            landed: landed, reducedMotion: reduced
        )
    }

    private func advance(_ dt: Double, jars: [PillRiseSplashPlacedJar]) {
        let before = time
        let now = before + dt
        time = now

        var callbacks: [() -> Void] = []
        if let c = s.step(dt: dt, now: now) { callbacks.append(c) }
        if let c = board.step(dt: dt, now: now) { callbacks.append(c) }
        if let c = exit.step(dt: dt, now: now) { callbacks.append(c) }
        if let c = logoIn.step(dt: dt, now: now) { callbacks.append(c) }
        if let c = press.step(dt: dt, now: now) { callbacks.append(c) }
        callbacks.forEach { $0() }

        // A coin passing through a rim rings its jar; one a finger dropped gets a haptic too.
        for coin in coins where coin.t0 >= 0 && coin.jar < jars.count {
            let at = coin.t0 + PillRiseSplashJars.landingTime(jars[coin.jar].drop)
            if before < at && now >= at {
                landed[coin.jar] = at
                if coin.user { haptic.impactOccurred() }
            }
        }

        // Jars not under a finger spring home, carrying the speed they were let go with.
        for i in offsetX.indices where i != grabbed {
            if abs(offsetX[i]) + abs(offsetY[i]) + abs(speedX[i]) + abs(speedY[i]) < 0.05 { continue }
            speedX[i] += (-M.jarK * offsetX[i] - M.jarC * speedX[i]) * dt
            speedY[i] += (-M.jarK * offsetY[i] - M.jarC * speedY[i]) * dt
            offsetX[i] += speedX[i] * dt
            offsetY[i] += speedY[i] * dt
        }

        // The script.
        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping {
                clock = phase == 0 ? M.resumeSplash : phase == 1 ? M.resumeBoard : M.exitAt + 0.01
            }
            return
        }
        guard looping else { return }
        let t0 = clock
        let t = (t0 + dt).truncatingRemainder(dividingBy: M.loop)
        clock = t
        func passed(_ at: Double) -> Bool { (t0 < at && t >= at) || (t < t0 && (at > t0 || at <= t)) }
        if passed(M.peekAt) { peek(delay: 0) }
        if passed(M.riseAt) { commit(user: false, velocity: 0) }
        for i in M.coinAt.indices where passed(M.coinAt[i]) && phase == 1 && board.value > 0.9 {
            drop(jar: M.coinJar[i], user: false)
        }
        if passed(M.pressAt) && phase == 1 {
            press.timing(to: 1, duration: 0.11, now: now, curve: M.standard) { [weak self] in
                guard let self else { return }
                self.press.timing(to: 0, duration: 0.2, now: self.time, curve: M.standard)
            }
        }
        if passed(M.exitAt) { leave(user: false) }
    }

    // MARK: - Moves

    private func peek(delay: Double) {
        guard phase == 0 else { return }
        s.timing(to: M.peek, duration: reduced ? 0 : M.peekTime, delay: delay, now: time, curve: M.easeOut.value(at:))
    }

    private func startBoard() {
        board.timing(to: 1, duration: reduced ? 0.5 : M.boardTime, delay: M.boardDelay, now: time, curve: { $0 })
    }

    /// Opens the capsule into the board. `velocity` is the finger's, in units of `s` per second.
    private func commit(user: Bool, velocity: Double) {
        guard phase == 0 else { return }
        phase = 1
        if user { haptic.impactOccurred() }
        let next: () -> Void = { [weak self] in self?.startBoard() }
        if reduced {
            s.timing(to: 1, duration: 0.42, now: time, curve: M.standard, then: next)
        } else if user {
            s.spring(to: 1, velocity: velocity, duration: 1.3, dampingRatio: 1, clamp: true, then: next)
        } else {
            s.timing(to: 1, duration: M.riseTime, now: time, curve: M.riseEase.value(at:), then: next)
        }
    }

    /// Back to the resting splash: everything resets under the full-screen lime.
    private func reset() {
        s.set(0)
        board.set(0)
        exit.set(0)
        phase = 0
        coins = Array(repeating: .none, count: PillRiseSplashJars.coinSlots)
        offsetX = [0, 0, 0]
        offsetY = [0, 0, 0]
        speedX = [0, 0, 0]
        speedY = [0, 0, 0]
        logoIn.set(0)
        logoIn.timing(to: 1, duration: M.logoTime, now: time, curve: M.easeOut.value(at:))
        if !looping || hold > 0 { peek(delay: M.logoTime + 0.4) }
    }

    /// The button grows into a full-screen lime and the splash comes back.
    private func leave(user: Bool) {
        guard phase == 1, board.value >= 0.9 else { return }
        phase = 2
        if user {
            haptic.impactOccurred()
            settings.onGetStarted?()
        }
        exit.timing(to: 1, duration: reduced ? 0.3 : M.exitTime, now: time,
                    curve: reduced ? M.standard : M.exitEase.value(at:)) { [weak self] in self?.reset() }
    }

    /// Drops a coin into jar `jar`.
    private func drop(jar: Int, user: Bool) {
        let slot = nextSlot
        nextSlot = (slot + 1) % PillRiseSplashJars.coinSlots
        coins[slot] = PillRiseSplashCoin(t0: time, jar: jar, dx: M.coinDX[slot], user: user)
    }

    /// The jar nearest a point on screen, or -1 when none is within reach.
    private func jarAt(_ point: CGPoint, reach: Double) -> Int {
        var best = -1
        var bestD = reach
        for (i, jar) in jars.enumerated() {
            let d = hypot(point.x - (jar.x + offsetX[i]), point.y - (jar.mid + offsetY[i]))
            if d < bestD {
                bestD = d
                best = i
            }
        }
        return best
    }

    // MARK: - Touches

    // One recogniser stands in for the race of a pan (after 6 pt of travel) and a tap (a touch lifted
    // within 400 ms that never travelled that far), plus the button's own tap.
    func touchChanged(_ value: DragGesture.Value) {
        let layout = settings.layout
        if touchStart == nil {
            touchStart = value.time
            panning = false
            panMode = 0
            onCta = phase == 1 && layout.ctaRect.contains(value.startLocation)
            if onCta { press.timing(to: 1, duration: 0.11, now: time, curve: M.standard) }
        }
        if !panning && hypot(value.translation.width, value.translation.height) >= M.dragSlop {
            panning = true
            panStart(at: value.location)
        }
        if panning { panUpdate(value) }
    }

    func touchEnded(_ value: DragGesture.Value) {
        let held = touchStart.map { value.time.timeIntervalSince($0) } ?? 0
        if panning {
            panEnd(velocity: value.velocity)
        } else if onCta {
            if held <= M.ctaTapMax {
                hold = M.userHold
                leave(user: true)
            }
        } else if held <= M.tapMax {
            tap(at: value.location)
        }
        if onCta { press.timing(to: 0, duration: 0.18, now: time, curve: M.standard) }
        touchStart = nil
        panning = false
        panMode = 0
        grabbed = -1
        onCta = false
    }

    private func panStart(at point: CGPoint) {
        hold = M.userHold
        // The button's tap fails once the finger travels.
        if onCta {
            onCta = false
            press.timing(to: 0, duration: 0.18, now: time, curve: M.standard)
        }
        if phase == 0 && !reduced {
            s.stop()
            grabFrom = (s.value, 0, 0)
            panMode = 1
        } else if phase == 1 && board.value > 0.9 {
            let j = jarAt(point, reach: M.jarGrab * settings.layout.k + 20)
            guard j >= 0 else { return }
            grabbed = j
            grabFrom = (0, offsetX[j], offsetY[j])
            panMode = 2
        }
    }

    private func panUpdate(_ value: DragGesture.Value) {
        if panMode == 1 {
            let raw = grabFrom.s - value.translation.height / (settings.layout.height * M.dragSpan)
            // Below the resting peek the capsule resists; it never tears off the bottom edge.
            s.set(raw < M.peek ? M.peek - M.rubber(M.peek - raw, 0) * 0.35 : min(raw, 0.97))
        } else if panMode == 2, offsetX.indices.contains(grabbed) {
            let j = grabbed
            offsetX[j] = M.rubber(grabFrom.x + value.translation.width, M.rubber)
            offsetY[j] = M.rubber(grabFrom.y + value.translation.height, M.rubber)
            // The finger's speed tilts the jar while it is carried.
            speedX[j] = value.velocity.width
        }
    }

    /// A touch the system cancelled: a drag lets go where it is, a tap never happened.
    func touchLost() {
        guard touchStart != nil else { return }
        if panning { panEnd(velocity: .zero) }
        if onCta { press.timing(to: 0, duration: 0.18, now: time, curve: M.standard) }
        touchStart = nil
        panning = false
        panMode = 0
        grabbed = -1
        onCta = false
    }

    private func panEnd(velocity fling: CGSize) {
        if panMode == 1 {
            let velocity = -fling.height / (settings.layout.height * M.dragSpan)
            if s.value + velocity * M.throwTime > M.commitAt {
                commit(user: true, velocity: velocity)
            } else {
                s.spring(to: M.peek, velocity: velocity, duration: 0.45, dampingRatio: 0.8)
            }
        } else if panMode == 2, speedX.indices.contains(grabbed) {
            speedX[grabbed] = fling.width
            speedY[grabbed] = fling.height
        }
    }

    private func tap(at point: CGPoint) {
        hold = M.userHold
        let layout = settings.layout
        if phase == 0 {
            commit(user: true, velocity: 0)
        } else if phase == 1 && board.value > 0.9 && point.y > layout.stageTop && point.y < layout.stageBottom {
            let j = jarAt(point, reach: 1000)
            if j >= 0 { drop(jar: j, user: true) }
        }
    }
}
