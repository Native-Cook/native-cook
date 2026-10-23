import SwiftUI
import UIKit

enum CurtainSplitWelcomeMetrics {
    static let titleSize: CGFloat = 40
    static let titleLeading: CGFloat = 44
    static let indexSize: CGFloat = 56
    static let placeSize: CGFloat = 11
    static let placeTracking: CGFloat = 2.2 // pt between letters

    // The blinds: six vertical strips. On the way to the next scene the even strips slide up and
    // the odd ones down, each starting `stagger` of the transition after the one to its left, and
    // while they move they part by up to `gap` pt and darken, so the next scene shows between them.
    static let strips = 6
    static let stagger = 0.09
    static let gap = 7.0
    static let shade = 0.4
    static let stripEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.62, y: 0), endControlPoint: UnitPoint(x: 0.22, y: 1))
    // The scene underneath starts a little larger and darker, and settles as it is uncovered.
    static let underScale = 0.08
    static let underDim = 0.55

    // Dragging: a full transition takes this share of the screen's height of finger travel.
    static let dragShare = 0.55
    static let dragSlop: CGFloat = 10
    static let throwTime = 0.25 // s of release speed added before choosing the scene
    static let flick = 1.1 // scenes per second: a release this fast turns the page whatever the distance
    static let releaseDuration = 0.65
    static let releaseRatio = 1.0
    static let turnTime = 1.65 // s, the arrow and autoPlay
    static let turnEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.25, y: 1))
    static let tapMaxDuration = 0.45

    // Layout, in pt.
    static let side: CGFloat = 28
    static let button: CGFloat = 60

    // autoPlay: one turn every `period` seconds, the first after a rest, so a loop of all five
    // scenes starts and ends on the same picture.
    static let period = 3.0
    static let firstTurn = 1.3
    static let userHold = 3.5

    /// How far strip `k` has gone, 0 at rest and 1 out of the screen, for a transition at `t`.
    static func stripAt(_ t: Double, _ k: Int) -> Double {
        let p = min(1, max(0, (t - Double(k) * stagger) / (1 - Double(strips - 1) * stagger)))
        return stripEase.value(at: p)
    }
}

struct CurtainSplitWelcomeLayout: Equatable {
    var width: CGFloat
    var height: CGFloat
    var top: CGFloat
    var bottom: CGFloat
    var indexY: CGFloat
    var placeY: CGFloat
    var line1Y: CGFloat
    var line2Y: CGFloat

    init(width: CGFloat, height: CGFloat, top: CGFloat, bottom: CGFloat) {
        typealias M = CurtainSplitWelcomeMetrics
        self.width = width
        self.height = height
        self.top = top
        self.bottom = bottom
        line2Y = height - bottom - 34
        line1Y = line2Y - M.titleLeading
        indexY = top + 30 + M.indexSize * 0.72
        placeY = line1Y - M.titleSize * 0.72 - 22
    }
}

// MARK: - Animations

/// Reanimated's `withSpring` with a duration and a damping ratio (and, if asked, no overshoot),
/// stepped a frame at a time: the stiffness is solved so the spring's energy falls to the threshold
/// after 1.5 × the duration, and each frame moves along the exact solution from where the last one
/// left it.
struct CurtainSplitWelcomeSpring {
    let target: Double
    private(set) var velocity: Double
    private let zeta: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let initialEnergy: Double
    private let start: Double
    private let clamped: Bool
    private static let energyThreshold = 6e-9

    init(from value: Double, to target: Double, velocity: Double, duration: Double, dampingRatio: Double, overshootClamping: Bool) {
        self.target = target
        clamped = overshootClamping
        var v = velocity
        // Velocity that would carry the first frame away from the target is dropped.
        if (target > value && v < 0) || (target < value && v > 0) { v = 0 }
        self.velocity = v
        let x0 = value - target
        start = x0
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
        self.zeta = zeta
        omega0 = sqrt(k)
        omega1 = zeta < 1 ? omega0 * sqrt(1 - zeta * zeta) : 0
        // Reanimated measures the starting energy with the speed it was given.
        initialEnergy = energy(x0, velocity, k)
    }

    /// Moves `value` on by `dt` seconds; true once the spring has come to rest on its target.
    mutating func step(_ value: inout Double, dt: Double) -> Bool {
        if initialEnergy == 0 || !initialEnergy.isFinite {
            value = target
            return true
        }
        let x0 = value - target
        let v0 = velocity
        let t = dt
        if zeta < 1 {
            let sin1 = sin(omega1 * t)
            let cos1 = cos(omega1 * t)
            let envelope = exp(-zeta * omega0 * t)
            let frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            value = target + frag
            velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        } else {
            let envelope = exp(-omega0 * t)
            value = target + envelope * (x0 + (v0 + omega0 * x0) * t)
            velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
        }
        if clamped {
            // No overshoot: the spring stops once it would pass its target.
            let left = start >= 0 ? target : target + start
            let right = left + abs(start)
            if value < left || value > right {
                value = target
                velocity = 0
                return true
            }
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

/// Reanimated's `withTiming`, stepped a frame at a time.
struct CurtainSplitWelcomeTiming {
    let from: Double
    let to: Double
    let duration: Double
    let curve: (Double) -> Double
    var time = 0.0

    /// Reanimated's default timing curve, an ease-in-out quad.
    static func inOutQuad(_ t: Double) -> Double {
        t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }

    mutating func step(_ value: inout Double, dt: Double) -> Bool {
        time += dt
        let p = min(1, time / duration)
        value = from + (to - from) * curve(p)
        return p >= 1
    }
}

// MARK: - The engine

struct CurtainSplitWelcomeFrame {
    /// Where we are, in scenes: the whole part is the scene on top, the rest how far its strips
    /// have gone.
    var position: Double
    var press: Double
}

/// The scenes' state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class CurtainSplitWelcomeEngine {
    private typealias M = CurtainSplitWelcomeMetrics

    private enum Motion {
        case spring(CurtainSplitWelcomeSpring)
        case timing(CurtainSplitWelcomeTiming)
    }

    let count: Int
    // It only grows or shrinks by one per turn and is folded back into one cycle.
    private var position = 0.0
    private var motion: Motion?
    private var press = 0.0
    private var pressMotion: CurtainSplitWelcomeTiming?
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var reduced = false
    private var lastDate: Date?
    private var lastAuto: Bool?
    private(set) var layout: CurtainSplitWelcomeLayout?
    private(set) var photos: [UIImage?]
    var onChange: ((Int) -> Void)?

    // The touch under way.
    private enum Touch { case pending, panning }
    private var touch: Touch?
    private var touchStart: Date?
    private var dragFrom = 0.0

    private let impact = UIImpactFeedbackGenerator(style: .light)

    init(count: Int) {
        self.count = count
        photos = Array(repeating: nil, count: count)
    }

    private var loop: Double { M.period * Double(count) }

    func wrap(_ n: Int) -> Int { ((n % count) + count) % count }

    func photoArrived(_ image: UIImage, at index: Int) {
        guard photos.indices.contains(index) else { return }
        photos[index] = image
    }

    func step(to date: Date, layout: CurtainSplitWelcomeLayout, autoPlay: Bool, reduced: Bool) -> CurtainSplitWelcomeFrame {
        self.layout = layout
        self.reduced = reduced
        if lastAuto != autoPlay {
            lastAuto = autoPlay
            looping = autoPlay
            clock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(max(raw, 0), 0.064)
        if dt > 0 {
            frameCallback(dt: dt)
            stepMotion(dt: dt)
        }
        // The picture turns the position into an index with Int(), which traps on NaN or infinity.
        if !position.isFinite {
            position = 0
            motion = nil
        }
        return CurtainSplitWelcomeFrame(position: position, press: press)
    }

    private func frameCallback(dt: Double) {
        if hold > 0 {
            hold -= dt
            if hold <= 0 { clock = 0 }
            return
        }
        guard looping else { return }
        let before = clock
        let now = (before + dt).truncatingRemainder(dividingBy: loop)
        clock = now
        for k in 0..<count where crossed(before, now, M.firstTurn + Double(k) * M.period) {
            turn(1)
        }
    }

    private func stepMotion(dt: Double) {
        switch motion {
        case .spring(var spring)?:
            motion = spring.step(&position, dt: dt) ? nil : .spring(spring)
        case .timing(var timing)?:
            motion = timing.step(&position, dt: dt) ? nil : .timing(timing)
        case nil:
            break
        }
        if var timing = pressMotion {
            pressMotion = timing.step(&press, dt: dt) ? nil : timing
        }
    }

    /// True when the loop clock went past `at` between two frames (it may have wrapped).
    private func crossed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        if now >= before { return before < at && now >= at }
        return at > before || at <= now
    }

    /// JavaScript's Math.round: halves go up.
    private func round(_ x: Double) -> Double { floor(x + 0.5) }

    /// Folds the position back into the first cycle when it rests on a whole scene.
    private func fold() {
        if abs(position - round(position)) < 0.001 { position = Double(wrap(Int(round(position)))) }
    }

    private func turn(_ step: Double) {
        fold()
        let target = round(position) + step
        motion = .timing(CurtainSplitWelcomeTiming(from: position, to: target, duration: reduced ? 0.45 : M.turnTime) { M.turnEase.value(at: $0) })
    }

    private func pressTo(_ value: Double, _ duration: Double) {
        pressMotion = CurtainSplitWelcomeTiming(from: press, to: value, duration: duration, curve: CurtainSplitWelcomeTiming.inOutQuad)
    }

    private func commit(_ index: Int) {
        impact.impactOccurred()
        onChange?(index)
    }

    private func onButton(_ point: CGPoint) -> Bool {
        guard let l = layout else { return false }
        return point.x > l.width - M.side - M.button - 10 && point.y > l.height - l.bottom - 30 - M.button - 10
    }

    // MARK: Gestures

    // One recogniser stands in for the race of a pan (after 10 pt up or down) and a tap (a touch
    // lifted within 450 ms that never turned into a pan).
    func touchMoved(_ value: DragGesture.Value) {
        // The drag divides by the height, so not before the first real layout.
        guard let l = layout, l.height > 0 else { return }
        if touch == nil {
            touch = .pending
            touchStart = value.time
            hold = M.userHold
            if onButton(value.startLocation) { pressTo(1, 0.1) }
        }
        let dy = value.translation.height
        if touch == .pending && abs(dy) >= M.dragSlop {
            touch = .panning
            pressTo(0, 0.18)
            motion = nil
            fold()
            dragFrom = position
        }
        if touch == .panning {
            hold = M.userHold
            let moved = -Double(dy) / (Double(l.height) * M.dragShare)
            // One scene per drag, either way.
            position = max(round(dragFrom) - 1, min(round(dragFrom) + 1, dragFrom + moved))
        }
    }

    func touchEnded(_ value: DragGesture.Value) {
        defer {
            touch = nil
            touchStart = nil
        }
        guard let l = layout, l.height > 0 else { return }
        if touch == .panning {
            release(velocity: -Double(value.velocity.height) / (Double(l.height) * M.dragShare))
            return
        }
        pressTo(0, 0.18)
        guard touch == .pending, let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration,
              onButton(value.location) else { return }
        turn(1)
        commit(wrap(Int(round(position)) + 1))
    }

    /// A touch the system cancelled: a drag lets go where it is, a tap never happened.
    func touchLost() {
        guard let state = touch else { return }
        touch = nil
        touchStart = nil
        pressTo(0, 0.18)
        if state == .panning { release(velocity: 0) }
    }

    private func release(velocity: Double) {
        let from = round(dragFrom)
        let p = position
        // A flick goes on to the next whole scene in its direction; a slow release to the nearest.
        var thrown = round(p + velocity * M.throwTime)
        if velocity > M.flick { thrown = floor(p + 0.001) + 1 }
        if velocity < -M.flick { thrown = ceil(p - 0.001) - 1 }
        let target = max(from - 1, min(from + 1, thrown))
        motion = .spring(CurtainSplitWelcomeSpring(
            from: position, to: target, velocity: velocity,
            duration: M.releaseDuration, dampingRatio: M.releaseRatio, overshootClamping: true
        ))
        if target != from { commit(wrap(Int(target))) }
    }
}
