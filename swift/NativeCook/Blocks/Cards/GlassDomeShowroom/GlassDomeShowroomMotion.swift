import Foundation
import CoreGraphics

/// Time and touch model of the showroom. Everything on screen is a function of φ, the carousel's
/// position in cars (φ = 2 puts car 2 in the middle; it wraps every `count` cars), and φ is a
/// function of the clock.
enum GlassDomeShowroomMotion {
    static let loop = 10.0
    static let count = 5
    /// Distance (pt) between two jar centres; a drag of this length moves the carousel one car.
    static let spacing = 272.0
    /// Seconds after the last touch before the carousel springs back to the first car.
    static let idle = 2.0
    /// Duration of that return; the loop restarts at its end.
    static let back = 1.6
    /// Start of each step A→B, B→C, C→D, D→E, E→A in the loop.
    static let steps = [1.5, 3.2, 5.3, 7.0, 8.7]
    static let stepSettle = 0.85
    /// The loop likes car C (index 2) at this time.
    static let heartIndex = 2
    static let heartAt = 4.3

    // Layout in the 393-wide design space.
    static let centreX = 196.5
    static let jarWidth = 260.0
    static let jarHeight = 318.0
    static let jarRadius = 64.0
    static let carWidth = 248.0
    static let headlinePitch = 66.0

    static func floorY(height: Double, safeBottom: Double) -> Double { height - safeBottom - 136 }

    static func clamp(_ x: Double, _ lo: Double, _ hi: Double) -> Double { min(max(x, lo), hi) }

    /// Step response of a damped spring (response = period in s), from `from` with velocity
    /// `velocity` (units/s); exactly `to` after `settle` s.
    static func spring(_ t: Double, from: Double, to: Double, velocity: Double = 0,
                       response: Double, damping: Double, settle: Double) -> Double {
        if t <= 0 { return from }
        if t >= settle { return to }
        let w = 2 * Double.pi / response
        let zw = damping * w
        let wd = w * (1 - damping * damping).squareRoot()
        let a = from - to
        let b = (velocity + zw * a) / wd
        return to + exp(-zw * t) * (a * cos(wd * t) + b * sin(wd * t))
    }

    static func loopTime(_ time: Double) -> Double { time - floor(time / loop) * loop }

    /// The loop: rest on A, then one car every ~1.9 s, all the way round back to A.
    static func loopPhi(_ time: Double) -> Double {
        let t = loopTime(time)
        var phi = 0.0
        for (i, s) in steps.enumerated() where t >= s {
            phi = spring(t - s, from: Double(i), to: Double(i + 1), response: 0.55, damping: 0.8, settle: stepSettle)
        }
        return phi == Double(count) ? 0 : phi
    }

    /// d wrapped into [-count/2, count/2).
    static func wrap(_ d: Double) -> Double {
        let n = Double(count)
        let m = (d + n / 2) - floor((d + n / 2) / n) * n
        return m - n / 2
    }

    static func index(_ phi: Double) -> Int {
        let i = Int(phi.rounded()) % count
        return i < 0 ? i + count : i
    }

    struct Jar {
        let index: Int
        /// Signed distance from the middle, in cars.
        let d: Double
        let x: Double
        let scale: Double
    }

    /// The visible jars for φ, ordered back to front.
    static func jars(phi: Double) -> [Jar] {
        var out: [Jar] = []
        for i in 0..<count {
            let d = wrap(Double(i) - phi)
            let a = abs(d)
            let scale = 1 - 0.16 * min(a, 1) - 0.04 * max(a - 1, 0)
            let x = centreX + d * spacing
            if abs(x - centreX) > centreX + jarWidth * scale / 2 + 4 { continue }
            out.append(Jar(index: i, d: d, x: x, scale: scale))
        }
        return out.sorted { abs($0.d) > abs($1.d) }
    }

    /// The heart's centre in a jar's local space (origin at the floor line under the jar's middle).
    static let heartCentre = CGPoint(x: jarWidth / 2 - 24 - 19, y: 4 - jarHeight + 30 + 19)

    /// Scale of a heart popped `t` s ago.
    static func heartScale(_ t: Double) -> Double {
        if t < 0 || t > 1.2 { return 1 }
        return 1 + 0.6 * exp(-7 * t) * sin(11 * t)
    }
}

/// Touch state, read by the timeline; a plain class, so writing it never re-renders the view.
final class GlassDomeShowroomTouch {
    typealias M = GlassDomeShowroomMotion
    enum Feedback { case none, snap, like, unlike }

    var touching = false
    var live = 0.0
    /// Drag speed in cars per second while the finger is down.
    var dragVelocity = 0.0
    private var startPhi = 0.0
    private var downClock = 0.0
    private var lastClock = 0.0
    private var lastPhi = 0.0
    private var moved = 0.0
    private var detent = 0
    var release: Double?
    private var from = 0.0, velocity = 0.0, target = 0.0, home = 0.0
    var likes = [Bool](repeating: false, count: M.count)
    var popClock = -10.0
    var popIndex = -1

    /// φ at a clock time (seconds since the view appeared).
    func phi(_ clock: Double, autoPlay: Bool) -> Double {
        if touching { return live }
        if let r = release {
            let dt = clock - r
            if dt < M.idle {
                return M.spring(dt, from: from, to: target, velocity: velocity, response: 0.55, damping: 0.8, settle: 1.4)
            }
            let b = dt - M.idle
            if b < M.back { return M.spring(b, from: target, to: home, response: 0.8, damping: 0.8, settle: M.back) }
            return autoPlay ? home + M.loopPhi(b - M.back) : home
        }
        return autoPlay ? M.loopPhi(clock) : 0
    }

    /// The loop's own time when the loop is what moves the carousel, else nil.
    func loopTime(_ clock: Double, autoPlay: Bool) -> Double? {
        guard autoPlay, !touching else { return nil }
        if let r = release {
            let b = clock - r - M.idle - M.back
            return b >= 0 ? M.loopTime(b) : nil
        }
        return M.loopTime(clock)
    }

    /// Clock time after which nothing moves any more (outside autoPlay).
    var restsAfter: Double { max((release ?? 0) + M.idle + M.back, popClock + 1.2) + 0.2 }

    func liked(_ i: Int, clock: Double, autoPlay: Bool) -> Bool {
        if likes[i] { return true }
        if i == M.heartIndex, let t = loopTime(clock, autoPlay: autoPlay) { return t >= M.heartAt }
        return false
    }

    /// Seconds since car i's heart popped (large when it did not).
    func popAge(_ i: Int, clock: Double, autoPlay: Bool) -> Double {
        if i == popIndex, clock - popClock < 1.2 { return clock - popClock }
        if i == M.heartIndex, !likes[i], let t = loopTime(clock, autoPlay: autoPlay) { return t - M.heartAt }
        return 10
    }

    func begin(clock: Double, autoPlay: Bool) {
        let current = phi(clock, autoPlay: autoPlay)
        touching = true
        live = current
        startPhi = current
        downClock = clock
        lastClock = clock
        lastPhi = current
        dragVelocity = 0
        moved = 0
        detent = Int(current.rounded())
    }

    /// Returns true when the car in the middle changed (a detent haptic).
    func move(dx: Double, dy: Double, clock: Double) -> Bool {
        moved = max(moved, (dx * dx + dy * dy).squareRoot())
        guard moved >= 8 else { return false }
        live = startPhi - dx / M.spacing
        let dt = clock - lastClock
        if dt > 0.004 {
            dragVelocity = 0.6 * dragVelocity + 0.4 * (live - lastPhi) / dt
            lastClock = clock
            lastPhi = live
        }
        let d = Int(live.rounded())
        if d != detent { detent = d; return true }
        return false
    }

    /// `point` is in the design space; `floorY` is the floor line.
    func end(clock: Double, velocityX: Double, point: CGPoint, floorY: Double) -> Feedback {
        var feedback = Feedback.snap
        dragVelocity = 0
        if moved < 8 && clock - downClock < 0.5 {
            velocity = 0
            target = live.rounded()
            feedback = .none
            let px = Double(point.x), py = Double(point.y)
            for jar in M.jars(phi: live).reversed() {
                let s = jar.scale
                let h = M.heartCentre
                let hx = jar.x + Double(h.x) * s, hy = floorY + Double(h.y) * s
                if abs(jar.d) < 0.5, (px - hx) * (px - hx) + (py - hy) * (py - hy) < 30 * 30 {
                    likes[jar.index].toggle()
                    if likes[jar.index] {
                        popClock = clock
                        popIndex = jar.index
                        feedback = .like
                    } else {
                        if popIndex == jar.index { popIndex = -1 }
                        feedback = .unlike
                    }
                    break
                }
                let half = M.jarWidth * s / 2
                if px > jar.x - half, px < jar.x + half, py > floorY + (4 - M.jarHeight) * s, py < floorY + 40 {
                    if abs(jar.d) >= 0.5 {
                        target = (live + jar.d).rounded()
                        feedback = .snap
                    }
                    break
                }
            }
        } else {
            velocity = M.clamp(-velocityX / M.spacing, -12, 12)
            let nearest = live.rounded()
            target = M.clamp((live + 0.22 * velocity).rounded(), nearest - 2, nearest + 2)
        }
        from = live
        release = clock
        home = Double(M.count) * (target / Double(M.count)).rounded()
        touching = false
        return feedback
    }
}
