import SwiftUI
import UIKit

enum PatternLockInputMetrics {
    // A "Z": along the top, down the diagonal, along the bottom.
    static let patternZ = [0, 1, 2, 4, 6, 7, 8]
    // The wrong guess autoPlay draws first: an "L".
    static let patternL = [0, 3, 6, 7, 8]
    static let minDots = 4

    // Grid (pt): dots `spacingShare` of the width apart, at most `spacingMax`; the grid's centre
    // at `gridAt` of the height.
    static let spacingShare = 0.27
    static let spacingMax = 110.0
    static let gridAt = 0.53
    // A finger within `hit` of a dot's centre (share of the spacing) picks it up.
    static let hit = 0.34

    // The line: three strokes, a wide soft glow, a narrower one and a bright core.
    static let glowWidth = 16.0
    static let coreWidth = 3.5
    // The open end follows the finger on a spring (stiffness, damping ratio), so it lags and
    // stretches; a dot it snaps onto springs from where the finger was to its centre, overshooting.
    static let tailStiffness = 650.0
    static let tailDamping = 0.72
    static let snapStiffness = 420.0
    static let snapDamping = 0.42
    // The open end bows against its motion by this much per pt/s, at most `bowMax` pt.
    static let bow = 0.04
    static let bowMax = 46.0

    // A wrong pattern: the grid shakes sideways like a struck spring, `shake` pt at `shakeHz`,
    // dying away at `shakeDecay` (1/s) over `shakeTime` s; the red path holds for `wrongHold` s
    // and fades over `fadeTime` s.
    static let shake = 18.0
    static let shakeHz = 6.0
    static let shakeDecay = 4.0
    static let shakeTime = 1.1
    static let wrongHold = 0.65
    static let fadeTime = 0.3
    // The right pattern: the path pulses, the dots collapse into the centre, the padlock forms and
    // springs open with a check; `resetAfter` s after release the grid comes back.
    static let pulseIn = 0.14
    static let pulseOut = 0.34
    static let collapseAt = 0.38
    static let collapseSpring = (duration: 0.62, ratio: 0.82)
    static let lockAt = 0.76
    static let lockSpring = (duration: 0.7, ratio: 0.68)
    static let openAt = 1.38
    static let openSpring = (duration: 0.52, ratio: 0.5)
    static let checkAt = 1.48
    static let checkTime = 0.34
    static let resetAfter = 3.0
    static let expandSpring = (duration: 0.7, ratio: 0.8)
    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))

    // autoPlay loop (seconds): the "L" is drawn and rejected, then the "Z" unlocks and the grid
    // comes back, at rest, before the loop starts over. Each stroke between dots takes `segment` s.
    static let loop = 10.0
    static let wrongAt = 0.6
    static let rightAt = 3.7
    static let segment = 0.27
    static let liftAfter = 0.12
    // A touch pauses the loop this long; then it starts over from the empty grid.
    static let userHold = 4.0

    static func clamp(_ x: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, x)) }

    /// Reanimated's default timing curve, ease in and out (quadratic).
    static func inOutQuad(_ t: Double) -> Double { t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2 }
    static func out(_ t: Double) -> Double { easeOut.value(at: t) }

    static func passed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        now >= before ? before < at && now >= at : at > before || at <= now
    }

    /// The dot halfway between two dots, if the line between them crosses one, else nil.
    static func between(_ a: Int, _ b: Int) -> Int? {
        let ra = a / 3, ca = a % 3, rb = b / 3, cb = b % 3
        if (ra + rb) % 2 != 0 || (ca + cb) % 2 != 0 { return nil }
        return (ra + rb) / 2 * 3 + (ca + cb) / 2
    }

    /// A spring step for a 2D point (semi-implicit Euler), as the RN frame callback takes it.
    static func springStep(_ s: SIMD4<Double>, to goal: CGPoint, stiffness k: Double, ratio: Double, dt: Double) -> SIMD4<Double> {
        let c = 2 * sqrt(k) * ratio
        let vx = s.z + (k * (goal.x - s.x) - c * s.z) * dt
        let vy = s.w + (k * (goal.y - s.y) - c * s.w) * dt
        return SIMD4(s.x + vx * dt, s.y + vy * dt, vx, vy)
    }
}

/// Where the grid sits on this screen.
struct PatternLockInputGrid: Equatable {
    let spacing: Double
    let centre: CGPoint
    let dots: [CGPoint]
    let width: Double
    let height: Double

    init(width: Double, height: Double) {
        typealias M = PatternLockInputMetrics
        self.width = width
        self.height = height
        spacing = min(M.spacingMax, width * M.spacingShare)
        centre = CGPoint(x: width / 2, y: height * M.gridAt)
        var dots: [CGPoint] = []
        for k in 0..<9 {
            let column = Double(k % 3 - 1)
            let row = Double(k / 3 - 1)
            dots.append(CGPoint(x: centre.x + column * spacing, y: centre.y + row * spacing))
        }
        self.dots = dots
    }

    /// Where the virtual finger is at `u` (0…1) along a pattern: each stroke eased into its dot.
    func along(_ pattern: [Int], _ u: Double) -> CGPoint {
        // A pattern of fewer than two dots, or one off the grid, has no strokes to follow.
        guard pattern.count >= 2, pattern.allSatisfy({ dots.indices.contains($0) }), u.isFinite else { return centre }
        let strokes = pattern.count - 1
        let at = PatternLockInputMetrics.clamp(u, 0, 1) * Double(strokes)
        let k = min(strokes - 1, Int(floor(at)))
        let f = at - Double(k)
        let eased = 0.45 * f + 0.55 * f * f * (3 - 2 * f)
        let a = dots[pattern[k]], b = dots[pattern[k + 1]]
        return CGPoint(x: a.x + (b.x - a.x) * eased, y: a.y + (b.y - a.y) * eased)
    }
}

/// Reanimated's `withSpring` with a duration and a damping ratio: the stiffness is solved so the
/// spring's energy falls to the threshold after 1.5 × the duration, and each frame moves along the
/// exact solution from where the last one left it.
struct PatternLockInputSpring {
    let target: Double
    let dampingRatio: Double
    private(set) var velocity: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let initialEnergy: Double
    private static let energyThreshold = 6e-9

    init(from value: Double, to target: Double, velocity: Double, duration: Double, dampingRatio: Double) {
        self.target = target
        self.dampingRatio = dampingRatio
        self.velocity = velocity
        let x0 = value - target
        let v = velocity
        let zeta = dampingRatio
        let settling = duration * 1.5
        func energy(_ x: Double, _ v: Double, _ k: Double) -> Double { 0.5 * k * x * x + 0.5 * v * v }
        func difference(_ k: Double) -> Double {
            let w = sqrt(k) * zeta
            let xt = (x0 + (v + x0 * w) * settling) * exp(-w * settling)
            let vt = (x0 + (v + x0 * w) * settling) * exp(-w * settling) * -w + (v + x0 * w) * exp(-w * settling)
            return energy(xt, vt, k) / energy(x0, v, k) - Self.energyThreshold
        }
        // Bisection, as Reanimated solves it.
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
        initialEnergy = energy(x0, velocity, k)
    }

    /// Moves `value` on by `dt` seconds; true once the spring has come to rest on its target.
    mutating func step(_ value: inout Double, dt: Double) -> Bool {
        if initialEnergy == 0 || !initialEnergy.isFinite {
            value = target
            return true
        }
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
        let now = 0.5 * stiffness * (target - value) * (target - value) + 0.5 * velocity * velocity
        if now / initialEnergy <= Self.energyThreshold {
            value = target
            velocity = 0
            return true
        }
        return false
    }
}

/// A number driven like a Reanimated shared value: set outright, or animated by timings and
/// duration springs, each optionally delayed. As with `withDelay`, whatever was moving the value
/// keeps moving it until the delay is up; a callback runs when an animation finishes unreplaced.
struct PatternLockInputValue {
    enum Step {
        case timing(to: Double, duration: Double, curve: (Double) -> Double)
        case spring(to: Double, duration: Double, ratio: Double)
    }

    private enum Motion {
        case timing(from: Double, to: Double, t: Double, duration: Double, curve: (Double) -> Double)
        case spring(PatternLockInputSpring)
    }

    private struct Entry {
        let start: Double
        var steps: [Step]
        let done: (() -> Void)?
    }

    private(set) var value: Double
    private var motion: Motion?
    private var current: Entry?
    private var pending: [Entry] = []

    init(_ value: Double) { self.value = value }

    mutating func set(_ next: Double) {
        value = next
        motion = nil
        current = nil
        pending = []
    }

    /// Starts `steps` (a sequence) after `delay` s from `now`; `done` runs if they finish unreplaced.
    mutating func animate(_ steps: [Step], delay: Double = 0, now: Double, done: (() -> Void)? = nil) {
        let start = now + delay
        // Animations due later than this one are replaced by it; earlier ones run until it starts.
        pending.removeAll { $0.start >= start }
        pending.append(Entry(start: start, steps: steps, done: done))
        if delay <= 0 { begin(pending.removeLast()) }
    }

    private mutating func begin(_ entry: Entry) {
        pending.removeAll { $0.start <= entry.start }
        current = entry
        next()
    }

    private mutating func next() {
        guard var entry = current, !entry.steps.isEmpty else {
            motion = nil
            return
        }
        switch entry.steps.removeFirst() {
        case let .timing(to, duration, curve):
            motion = .timing(from: value, to: to, t: 0, duration: duration, curve: curve)
        case let .spring(to, duration, ratio):
            motion = .spring(PatternLockInputSpring(from: value, to: to, velocity: 0, duration: duration, dampingRatio: ratio))
        }
        current = entry
    }

    /// Advances to `now` by `dt` s; returns the callback of an animation that just finished.
    mutating func step(now: Double, dt: Double) -> (() -> Void)? {
        if let first = pending.min(by: { $0.start < $1.start }), now >= first.start {
            begin(first)
        }
        guard let m = motion else { return nil }
        var finished = false
        switch m {
        case let .timing(from, to, t, duration, curve):
            let elapsed = t + dt
            let p = duration > 0 ? min(1, elapsed / duration) : 1
            value = from + (to - from) * curve(p)
            finished = p >= 1
            motion = .timing(from: from, to: to, t: elapsed, duration: duration, curve: curve)
        case var .spring(spring):
            finished = spring.step(&value, dt: dt)
            motion = .spring(spring)
        }
        guard finished else { return nil }
        if let entry = current, !entry.steps.isEmpty {
            next()
            return nil
        }
        let done = current?.done
        current = nil
        motion = nil
        return done
    }
}

enum PatternLockInputStatus {
    case idle, wrong, right

    var copy: String {
        switch self {
        case .idle: "Connect at least four dots"
        case .wrong: "Wrong pattern. Try again."
        case .right: "Unlocked"
        }
    }
}

struct PatternLockInputSettings {
    var grid: PatternLockInputGrid
    var pattern: [Int]
    var autoPlay: Bool
    var reducedMotion: Bool
    var onUnlock: (() -> Void)?
}

/// The lock's state, stepped once a frame like the RN frame callback, and the gesture that drives it.
@MainActor
final class PatternLockInputEngine {
    private typealias M = PatternLockInputMetrics

    private(set) var now = 0.0
    private(set) var selected: [Int] = []
    /// When each dot was hit (s, on `now`), or nil if it is not in the pattern.
    private(set) var hits = [Double?](repeating: nil, count: 9)
    // 0: taking input, 1: showing a wrong pattern, 2: unlocking.
    private(set) var stage = 0
    private(set) var drawing = false
    private(set) var finger = CGPoint.zero
    private(set) var tail = SIMD4<Double>(0, 0, 0, 0)
    private(set) var snap = SIMD4<Double>(0, 0, 0, 0)
    private(set) var wrong = PatternLockInputValue(0)
    private(set) var right = PatternLockInputValue(0)
    private(set) var shake = 0.0
    private var shakeAt = -10.0
    private(set) var lineAlpha = PatternLockInputValue(1)
    private(set) var pulse = PatternLockInputValue(0)
    private(set) var collapse = PatternLockInputValue(0)
    private(set) var lock = PatternLockInputValue(0)
    private(set) var open = PatternLockInputValue(0)
    private(set) var check = PatternLockInputValue(0)
    private(set) var glow = PatternLockInputValue(0)
    private(set) var ghostAlpha = PatternLockInputValue(0)
    private var ghost: Int?
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    // The status line, and the one fading out under it.
    private(set) var status = (copy: PatternLockInputStatus.idle, at: -10.0)
    private(set) var oldStatus: (copy: PatternLockInputStatus, at: Double)?
    private var touching = false

    private let selection = UISelectionFeedbackGenerator()
    private let notify = UINotificationFeedbackGenerator()

    func step(to date: Date, settings: PatternLockInputSettings) {
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            clock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = M.clamp(raw, 0, 1.0 / 30)
        guard dt > 0 else { return }
        now += dt
        stepValues(dt)
        let grid = settings.grid

        // autoPlay: a fixed script on a clock, paused for a while by any touch.
        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping { clock = stage == 0 ? 0 : M.rightAt + Double(M.patternZ.count - 1) * M.segment + 1 }
        } else if looping {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            let strokes = [(at: M.wrongAt, dots: M.patternL), (at: M.rightAt, dots: settings.pattern)]
            for (s, stroke) in strokes.enumerated() where stroke.dots.count >= 2 {
                let length = Double(stroke.dots.count - 1) * M.segment
                if M.passed(before, t, stroke.at) && stage == 0 {
                    ghost = s
                    ghostAlpha.animate([.timing(to: 1, duration: 0.14, curve: M.inOutQuad)], now: now)
                    begin(grid.along(stroke.dots, 0), user: false, settings)
                }
                if ghost == s {
                    move(grid.along(stroke.dots, (t - stroke.at) / length), user: false, settings)
                    if t >= stroke.at + length + M.liftAfter {
                        ghost = nil
                        ghostAlpha.animate([.timing(to: 0, duration: 0.18, curve: M.inOutQuad)], now: now)
                        release(user: false, settings)
                    }
                }
            }
        }

        // The wrong-pattern shake: a struck spring dying away.
        let since = now - shakeAt
        shake = since >= 0 && since < M.shakeTime ? M.shake * exp(-since * M.shakeDecay) * sin(since * M.shakeHz * .pi * 2) : 0

        // The open end chases the finger (or, once let go, falls back onto the last dot).
        let last = selected.last
        let goal = drawing || last == nil || !grid.dots.indices.contains(last ?? -1) ? finger : grid.dots[last ?? 0]
        tail = settings.reducedMotion ? SIMD4(goal.x, goal.y, 0, 0) : M.springStep(tail, to: goal, stiffness: M.tailStiffness, ratio: M.tailDamping, dt: dt)
        if snap != .zero {
            let next = M.springStep(snap, to: .zero, stiffness: M.snapStiffness, ratio: M.snapDamping, dt: dt)
            let still = abs(next.x) + abs(next.y) < 0.05 && abs(next.z) + abs(next.w) < 1
            snap = still ? .zero : next
        }
    }

    private func stepValues(_ dt: Double) {
        var callbacks: [() -> Void] = []
        func run(_ value: inout PatternLockInputValue) {
            if let done = value.step(now: now, dt: dt) { callbacks.append(done) }
        }
        run(&wrong)
        run(&right)
        run(&lineAlpha)
        run(&pulse)
        run(&collapse)
        run(&lock)
        run(&open)
        run(&check)
        run(&glow)
        run(&ghostAlpha)
        callbacks.forEach { $0() }
    }

    private func setStatus(_ copy: PatternLockInputStatus) {
        guard copy != status.copy else { return }
        oldStatus = (status.copy, now)
        status = (copy, now)
    }

    private func clearPattern() {
        selected = []
        hits = Array(repeating: nil, count: 9)
        lineAlpha.set(1)
        glow.animate([.timing(to: 0, duration: 0.4, curve: M.inOutQuad)], now: now)
    }

    /// Adds a dot to the pattern, with the dot it jumped over first if it skipped one.
    private func pick(_ k: Int, user: Bool, _ settings: PatternLockInputSettings) {
        guard !selected.contains(k), settings.grid.dots.indices.contains(k) else { return }
        if let last = selected.last, let middle = M.between(last, k), !selected.contains(middle) {
            selected.append(middle)
            hits[middle] = now - 0.04
        }
        selected.append(k)
        hits[k] = now
        // The line's end snaps from where the finger is onto the dot, and springs home.
        let dot = settings.grid.dots[k]
        snap = settings.reducedMotion ? .zero : SIMD4(finger.x - dot.x, finger.y - dot.y, 0, 0)
        glow.animate([.timing(to: Double(selected.count) / 9, duration: 0.26, curve: M.out)], now: now)
        if user { selection.selectionChanged() }
    }

    /// Picks up any dot the finger passes over between two points.
    private func sweep(from a: CGPoint, to b: CGPoint, user: Bool, _ settings: PatternLockInputSettings) {
        let grid = settings.grid
        let distance = hypot(b.x - a.x, b.y - a.y)
        // A point that is not a number (never from a finger) picks nothing; a huge jump is capped.
        guard distance.isFinite else { return }
        let steps = max(1, Int(min(ceil(distance / 6), 2000)))
        let reach = grid.spacing * M.hit
        for s in 1...steps {
            let x = a.x + (b.x - a.x) * Double(s) / Double(steps)
            let y = a.y + (b.y - a.y) * Double(s) / Double(steps)
            for k in 0..<9 where hypot(x - grid.dots[k].x, y - grid.dots[k].y) < reach {
                pick(k, user: user, settings)
            }
        }
    }

    private func fail(user: Bool, _ settings: PatternLockInputSettings) {
        stage = 1
        wrong.animate([.timing(to: 1, duration: 0.16, curve: M.inOutQuad)], now: now)
        if !settings.reducedMotion { shakeAt = now }
        lineAlpha.animate([.timing(to: 0, duration: M.fadeTime, curve: M.inOutQuad)], delay: M.wrongHold, now: now) { [unowned self] in
            clearPattern()
            wrong.animate([.timing(to: 0, duration: 0.24, curve: M.inOutQuad)], now: now)
            stage = 0
            setStatus(.idle)
        }
        setStatus(.wrong)
        if user { notify.notificationOccurred(.error) }
    }

    private func succeed(user: Bool, _ settings: PatternLockInputSettings) {
        stage = 2
        right.animate([.timing(to: 1, duration: 0.2, curve: M.inOutQuad)], now: now)
        pulse.animate([.timing(to: 1, duration: M.pulseIn, curve: M.out), .timing(to: 0, duration: M.pulseOut, curve: M.inOutQuad)], now: now)
        if settings.reducedMotion {
            collapse.animate([.timing(to: 1, duration: 0.3, curve: M.inOutQuad)], delay: M.collapseAt, now: now)
            lock.animate([.timing(to: 1, duration: 0.3, curve: M.inOutQuad)], delay: M.lockAt, now: now)
            check.animate([.timing(to: 1, duration: M.checkTime, curve: M.inOutQuad)], delay: M.checkAt, now: now)
        } else {
            collapse.animate([.spring(to: 1, duration: M.collapseSpring.duration, ratio: M.collapseSpring.ratio)], delay: M.collapseAt, now: now)
            lock.animate([.spring(to: 1, duration: M.lockSpring.duration, ratio: M.lockSpring.ratio)], delay: M.lockAt, now: now)
            open.animate([.spring(to: 1, duration: M.openSpring.duration, ratio: M.openSpring.ratio)], delay: M.openAt, now: now)
            check.animate([.timing(to: 1, duration: M.checkTime, curve: M.out)], delay: M.checkAt, now: now)
        }
        // Then everything goes back the way it came, and the grid takes input again.
        open.animate([.timing(to: 0, duration: 0.2, curve: M.inOutQuad)], delay: M.resetAfter, now: now)
        check.animate([.timing(to: 0, duration: 0.2, curve: M.inOutQuad)], delay: M.resetAfter, now: now)
        lock.animate([.timing(to: 0, duration: 0.28, curve: M.inOutQuad)], delay: M.resetAfter, now: now) { [unowned self] in
            clearPattern()
            right.animate([.timing(to: 0, duration: 0.3, curve: M.inOutQuad)], now: now)
            if settings.reducedMotion {
                collapse.animate([.timing(to: 0, duration: 0.3, curve: M.inOutQuad)], now: now)
            } else {
                collapse.animate([.spring(to: 0, duration: M.expandSpring.duration, ratio: M.expandSpring.ratio)], now: now)
            }
            stage = 0
            setStatus(.idle)
        }
        setStatus(.right)
        // autoPlay unlocks from the timeline's body: the owner hears about it once the frame is done.
        let unlocked = settings.onUnlock
        DispatchQueue.main.async { unlocked?() }
        if user { notify.notificationOccurred(.success) }
    }

    private func release(user: Bool, _ settings: PatternLockInputSettings) {
        guard drawing else { return }
        drawing = false
        guard !selected.isEmpty else { return }
        if selected.count >= M.minDots && selected == settings.pattern { succeed(user: user, settings) } else { fail(user: user, settings) }
    }

    private func begin(_ point: CGPoint, user: Bool, _ settings: PatternLockInputSettings) {
        guard stage == 0 else { return }
        drawing = true
        finger = point
        tail = SIMD4(point.x, point.y, 0, 0)
        sweep(from: point, to: point, user: user, settings)
    }

    private func move(_ point: CGPoint, user: Bool, _ settings: PatternLockInputSettings) {
        guard drawing else { return }
        let from = finger
        finger = point
        sweep(from: from, to: point, user: user, settings)
    }

    private func touched() {
        hold = M.userHold
        if ghost != nil {
            // A virtual stroke in progress is dropped.
            ghost = nil
            ghostAlpha.animate([.timing(to: 0, duration: 0.15, curve: M.inOutQuad)], now: now)
            if drawing {
                drawing = false
                clearPattern()
            }
        }
    }

    // MARK: Gesture

    func touchMoved(_ value: DragGesture.Value, settings: PatternLockInputSettings) {
        if !touching {
            touching = true
            touched()
            begin(value.startLocation, user: true, settings)
        }
        move(value.location, user: true, settings)
    }

    func touchEnded(settings: PatternLockInputSettings) {
        touching = false
        release(user: true, settings)
    }

    /// A touch the system took away (a DragGesture then never ends): the pattern is let go as if
    /// the finger had lifted, like the RN pan's onFinalize. Nothing happens when it already ended.
    func touchCancelled(settings: PatternLockInputSettings) {
        guard touching else { return }
        touchEnded(settings: settings)
    }
}
