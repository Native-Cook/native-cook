import SwiftUI
import UIKit

enum GlassRodOnboardingMetrics {
    // The rod: a glass cylinder lying across the screen, `rodRadius` pt either side of its centre
    // line. At rest a `peek` pt sliver of it waits at the right edge, breathing by `peekBreath` pt.
    static let rodRadius: Double = 46
    static let peek: Double = 18
    static let peekBreath: Double = 4
    static let peekPeriod = 2.6 // s
    // It leans with its own speed (rad per page/s) and with the finger's vertical travel (rad per
    // screen height), never past `maxTilt`; its colour fringes widen with speed.
    static let leanPerSpeed = 0.07
    static let tiltPerHeight = 0.55
    static let maxTilt = 0.2
    static let tiltFollow = 10.0 // 1/s
    static let chromaRest = 0.035
    static let chromaFast = 0.09

    // Paging: the photo behind the rod slides `photoShift` and the text `textShift` screen widths,
    // and the incoming photo settles from `incomingScale`.
    static let photoShift = 0.24
    static let textShift = 0.12
    static let incomingScale = 1.08

    // The finger: a page per rod travel, `rubber` pages at most past either end, and a release
    // that adds `project` s of the finger's speed.
    static let rubber = 0.18
    static let project = 0.22
    static let settleDuration = 0.62
    static let settleRatio = 0.86
    static let tiltDuration = 0.52
    static let tiltRatio = 0.7
    static let autoEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))
    static let autoPageTime = 1.4
    static let skipTime = 0.9
    static let reducedTime = 0.32
    static let activeOffset: Double = 10
    static let failOffset: Double = 24
    static let tapMaxDuration = 0.4

    // autoPlay, in seconds of a loop that rests on the first page: it pages forward at each of
    // `autoGo`, the last one wrapping from the third page round to the first, and presses the
    // button at `autoPress`.
    static let loop = 12.0
    static let autoGo = [0.8, 4.4, 8.4]
    static let autoPress = 7.4
    // Where the clock picks up when a touch's pause ends, by page.
    static let resumeAt = [0.0, 2.4, 5.9]
    static let userHold = 3.5

    // Dots: 8 pt, the current one stretched to 30 pt.
    static let dot: Double = 8
    static let dotActive: Double = 30
    static let dotGap: Double = 7
}

enum GlassRodOnboardingMath {
    static func clamp(_ x: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, x)) }

    static func smooth(_ x: Double) -> Double {
        let t = clamp(x, 0, 1)
        return t * t * (3 - 2 * t)
    }

    /// How far page `i` is from progress `p`, going round (the autoPlay wrap joins the ends).
    static func ringDistance(_ p: Double, _ i: Int, count: Int) -> Double {
        let n = Double(count)
        let raw = (p - Double(i)).truncatingRemainder(dividingBy: n)
        let d = abs((raw + n).truncatingRemainder(dividingBy: n))
        return min(d, n - d)
    }

    /// Resistance past either end: at most `rubber` pages, however far the finger goes.
    static func rubber(_ raw: Double, count: Int) -> Double {
        let r = GlassRodOnboardingMetrics.rubber
        if raw < 0 { return -r * (1 - exp(raw / r)) }
        let over = raw - Double(count - 1)
        if over > 0 { return Double(count - 1) + r * (1 - exp(-over / r)) }
        return raw
    }

    /// Which pages the rod is between: `a` on its left, `b` on its right, and how far it has come
    /// (0 at the right edge, 1 off the left). Past the last page `b` is the first page when the
    /// autoPlay wraps round, else the last page again; before the first, both are it.
    static func segment(_ p: Double, wrap: Bool, count: Int) -> (a: Int, b: Int, f: Double) {
        let n = Int(floor(p))
        let f = p - Double(n)
        let a = n < 0 ? 0 : n % count
        let b = n < 0 ? 0 : (n + 1 >= count ? (wrap ? (n + 1) % count : count - 1) : n + 1)
        return (a, b, f)
    }

    /// Reanimated's default timing curve, an ease-in-out quad.
    static func inOutQuad(_ t: Double) -> Double {
        t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }
}

// MARK: - Animations

/// Reanimated's `withSpring` with a duration and a damping ratio, stepped a frame at a time: the
/// stiffness is solved so the spring's energy falls to the threshold after 1.5 × the duration, and
/// each frame moves along the exact solution from where the last one left it.
struct GlassRodOnboardingSpring {
    let target: Double
    let duration: Double
    let dampingRatio: Double
    private(set) var velocity: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let initialEnergy: Double
    private static let energyThreshold = 6e-9

    init(from value: Double, to target: Double, velocity: Double, carried: Double, duration: Double, dampingRatio: Double) {
        self.target = target
        self.duration = duration
        self.dampingRatio = dampingRatio
        var v = carried + velocity
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
        if !k.isFinite || k <= 0 { k = 170 }
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
        let zeta = dampingRatio
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
        let now = 0.5 * stiffness * (target - value) * (target - value) + 0.5 * velocity * velocity
        if now / initialEnergy <= Self.energyThreshold || !value.isFinite {
            value = target
            velocity = 0
            return true
        }
        return false
    }
}

/// A number driven by one animation at a time, like a shared value: a spring or a timed move,
/// with a callback when it finishes.
struct GlassRodOnboardingValue {
    var value: Double
    private var spring: GlassRodOnboardingSpring?
    private var timingTo: Double?
    private var timingFrom = 0.0
    private var timingTime = 0.0
    private var timingDuration = 0.0
    private var curve: (Double) -> Double = GlassRodOnboardingMath.inOutQuad
    private var done: (() -> Void)?

    init(_ value: Double) { self.value = value }

    var moving: Bool { spring != nil || timingTo != nil }
    var velocity: Double { spring?.velocity ?? 0 }

    mutating func set(_ v: Double) {
        spring = nil
        timingTo = nil
        done = nil
        value = v
    }

    mutating func spring(to target: Double, duration: Double, dampingRatio: Double, velocity: Double = 0, then: (() -> Void)? = nil) {
        let carried = spring?.velocity ?? 0
        timingTo = nil
        done = then
        spring = GlassRodOnboardingSpring(from: value, to: target, velocity: velocity, carried: carried, duration: duration, dampingRatio: dampingRatio)
    }

    mutating func timing(to target: Double, duration: Double, curve: @escaping (Double) -> Double = GlassRodOnboardingMath.inOutQuad, then: (() -> Void)? = nil) {
        spring = nil
        timingTo = target
        timingFrom = value
        timingTime = 0
        timingDuration = max(duration, 0.001)
        self.curve = curve
        done = then
    }

    /// Moves the value on by `dt`; returns the finished move's callback for the caller to run.
    @discardableResult
    mutating func step(dt: Double) -> (() -> Void)? {
        if var running = spring {
            if running.step(&value, dt: dt) {
                spring = nil
                let callback = done
                done = nil
                return callback
            }
            spring = running
            return nil
        }
        if let to = timingTo {
            timingTime += dt
            let p = min(1, timingTime / timingDuration)
            value = timingFrom + (to - timingFrom) * curve(p)
            if p >= 1 {
                timingTo = nil
                let callback = done
                done = nil
                return callback
            }
        }
        return nil
    }
}

// MARK: - The engine

/// Everything one frame draws.
struct GlassRodOnboardingFrame {
    var size: CGSize
    var progress: Double
    var wrap: Bool
    var rodX: Double
    var pivotY: Double
    var angle: Double
    var chroma: Double
    var press: Double
}

/// The paging state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class GlassRodOnboardingEngine {
    private typealias M = GlassRodOnboardingMetrics
    private typealias X = GlassRodOnboardingMath

    let count: Int
    private(set) var progress = GlassRodOnboardingValue(0)
    private var fingerTilt = GlassRodOnboardingValue(0)
    private var pivotY = GlassRodOnboardingValue(0)
    private var press = GlassRodOnboardingValue(0)
    private var wrap = false
    private var peek = 1.0
    private var time = 0.0
    private var speed = 0.0
    private var lastProgress = 0.0
    private var lean = 0.0
    private var chroma = M.chromaRest
    private var dragFrom = 0.0
    private var dragging = false
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var reduced = false
    private var size = CGSize(width: 1, height: 1)
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var pivotPlaced = false

    // The touch under way.
    private enum Touch { case pending, panning, failed }
    private var touch: Touch?
    private var touchStart: Date?

    private let selection = UISelectionFeedbackGenerator()
    private let success = UINotificationFeedbackGenerator()

    init(count: Int) {
        self.count = max(1, count)
    }

    private var span: Double { Double(size.width) + M.rodRadius * 2 - M.peek }

    func step(to date: Date, size: CGSize, autoPlay: Bool, reduced: Bool) -> GlassRodOnboardingFrame {
        if size.width > 0 && size.height > 0 { self.size = size }
        self.reduced = reduced
        if !pivotPlaced {
            pivotPlaced = true
            pivotY.set(Double(self.size.height) / 2)
            peek = reduced ? 0 : 1
        }
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
            var finished: [() -> Void] = []
            for value in [\GlassRodOnboardingEngine.progress, \.fingerTilt, \.pivotY, \.press] as [ReferenceWritableKeyPath<GlassRodOnboardingEngine, GlassRodOnboardingValue>] {
                if let callback = self[keyPath: value].step(dt: dt) { finished.append(callback) }
            }
            finished.forEach { $0() }
            frameCallback(dt: dt)
        }
        return frame()
    }

    private func frame() -> GlassRodOnboardingFrame {
        let w = Double(size.width)
        let seg = X.segment(progress.value, wrap: wrap, count: count)
        let sliver = (M.peek + M.peekBreath * sin(time * .pi * 2 / M.peekPeriod)) * peek
        let rodX = reduced ? w + M.rodRadius * 4 : w + M.rodRadius - sliver - seg.f * (w + M.rodRadius * 2 - sliver)
        return GlassRodOnboardingFrame(
            size: size,
            progress: progress.value,
            wrap: wrap,
            rodX: rodX,
            pivotY: pivotY.value,
            angle: X.clamp(lean + fingerTilt.value, -M.maxTilt, M.maxTilt),
            chroma: chroma,
            press: press.value
        )
    }

    private func frameCallback(dt: Double) {
        time += dt
        // The rod's own speed, smoothed: it leans into its travel and its fringes widen.
        let p = progress.value
        let v = (p - lastProgress) / dt
        lastProgress = p
        let follow = 1 - exp(-dt * M.tiltFollow)
        speed += (v - speed) * follow
        lean += (X.clamp(speed * M.leanPerSpeed, -M.maxTilt, M.maxTilt) - lean) * follow
        chroma = M.chromaRest + (M.chromaFast - M.chromaRest) * X.clamp(abs(speed) / 2.5, 0, 1)
        // The sliver waits at the right edge while there is a page to go to.
        let resting = !dragging && abs(p - p.rounded()) < 0.002 && Int(p.rounded()) < count - 1
        let want = reduced ? 0.0 : (resting ? 1.0 : 0.0)
        peek += (want - peek) * (1 - exp(-dt * 6))

        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping {
                // Pick the script up from the page the finger left it on.
                let page = Int(X.clamp(progress.value.rounded(), 0, Double(count - 1)))
                if abs(progress.value - Double(page)) > 0.001 { goTo(Double(page), duration: 0.5) }
                clock = M.resumeAt[min(page, M.resumeAt.count - 1)]
            }
            return
        }
        guard looping else { return }
        let before = clock
        let now = before + dt
        for at in M.autoGo where before < at && now >= at {
            let from = progress.value.rounded()
            if Int(from) >= count - 1 { wrap = true }
            progress.timing(to: from + 1, duration: reduced ? M.reducedTime : M.autoPageTime, curve: { M.autoEase.value(at: $0) }) { [weak self] in
                guard let self else { return }
                // Round the wrap back to the first page: the same picture, so nothing moves.
                if self.progress.value >= Double(self.count) - 0.0001 {
                    self.progress.set(self.progress.value - Double(self.count))
                    self.lastProgress = self.progress.value
                    self.wrap = false
                }
            }
        }
        if before < M.autoPress && now >= M.autoPress { pressButton() }
        clock = now >= M.loop ? now - M.loop : now
    }

    private func goTo(_ target: Double, duration: Double) {
        progress.timing(to: target, duration: reduced ? M.reducedTime : duration, curve: { M.autoEase.value(at: $0) })
    }

    private func pressButton() {
        press.timing(to: 1, duration: 0.11, then: { [weak self] in
            self?.press.timing(to: 0, duration: 0.26)
        })
    }

    /// Brings a progress the autoPlay left past the last page (the wrap) back into range.
    private func settleWrap() -> Bool {
        // Only the autoPlay's wrap: a finger's rubber band past the last page is a different thing.
        let p = progress.value
        if wrap {
            // Seen as the step from before the first page to it: the same picture.
            let q = p > Double(count - 1) ? p - Double(count) : p
            wrap = false
            progress.set(q)
            lastProgress = q
            progress.spring(to: max(0, q.rounded()), duration: M.settleDuration, dampingRatio: M.settleRatio)
            return true
        }
        return false
    }

    /// Springs a page on or back; true when the page changed.
    @discardableResult
    func step(_ direction: Int) -> Bool {
        hold = M.userHold
        if settleWrap() { return false }
        let from = Int(progress.value.rounded())
        let target = Int(X.clamp(Double(from + direction), 0, Double(count - 1)))
        if target == from { return false }
        progress.spring(to: Double(target), duration: M.settleDuration, dampingRatio: M.settleRatio)
        selection.selectionChanged()
        return true
    }

    var isOnLastPage: Bool { Int(progress.value.rounded()) >= count - 1 && !wrap }

    func buttonTapped() {
        hold = M.userHold
        if isOnLastPage {
            success.notificationOccurred(.success)
        } else {
            step(1)
        }
    }

    func skip() {
        hold = M.userHold
        if isOnLastPage { return }
        wrap = false
        goTo(Double(count - 1), duration: M.skipTime)
        selection.selectionChanged()
    }

    // MARK: Touch

    func touchChanged(_ value: DragGesture.Value) {
        hold = M.userHold
        let h = Double(size.height)
        if touch == nil {
            touch = .pending
            touchStart = value.time
        }
        let dx = Double(value.translation.width)
        let dy = Double(value.translation.height)
        if touch == .pending {
            if abs(dy) > M.failOffset {
                touch = .failed
                return
            }
            guard abs(dx) > M.activeOffset else { return }
            touch = .panning
            if settleWrap() {
                touch = .failed
                return
            }
            progress.set(progress.value)
            dragFrom = progress.value
            dragging = true
            pivotY.spring(to: X.clamp(Double(value.location.y), h * 0.15, h * 0.85), duration: 0.26, dampingRatio: 1)
        }
        guard touch == .panning else { return }
        progress.set(X.rubber(dragFrom - dx / span, count: count))
        fingerTilt.set(X.clamp(dy / h * M.tiltPerHeight, -M.maxTilt, M.maxTilt))
    }

    func touchEnded(_ value: DragGesture.Value) {
        hold = M.userHold
        let state = touch
        let began = touchStart
        touch = nil
        touchStart = nil
        if state == .panning {
            let from = Int(dragFrom.rounded())
            let velocity = -Double(value.velocity.width) / span
            let projected = progress.value + velocity * M.project
            let target = Int(X.clamp(X.clamp(projected, Double(from - 1), Double(from + 1)).rounded(), 0, Double(count - 1)))
            progress.spring(to: Double(target), duration: M.settleDuration, dampingRatio: M.settleRatio, velocity: velocity)
            if target != from { selection.selectionChanged() }
        } else if state == .pending, let began, value.time.timeIntervalSince(began) <= M.tapMaxDuration {
            // Taps on the photo: the right 40% goes on, the left 25% goes back.
            let x = Double(value.location.x)
            let w = Double(size.width)
            if x > w * 0.6 { step(1) } else if x < w * 0.25 { step(-1) }
        }
        finalize()
    }

    /// A touch the system took away (or one already finished: then nothing happens).
    func touchCancelled() {
        guard touch != nil else { return }
        if touch == .panning {
            let target = X.clamp(progress.value.rounded(), 0, Double(count - 1))
            progress.spring(to: target, duration: M.settleDuration, dampingRatio: M.settleRatio)
        }
        touch = nil
        touchStart = nil
        finalize()
    }

    private func finalize() {
        dragging = false
        fingerTilt.spring(to: 0, duration: M.tiltDuration, dampingRatio: M.tiltRatio)
        pivotY.spring(to: Double(size.height) / 2, duration: M.tiltDuration, dampingRatio: M.tiltRatio)
    }
}
