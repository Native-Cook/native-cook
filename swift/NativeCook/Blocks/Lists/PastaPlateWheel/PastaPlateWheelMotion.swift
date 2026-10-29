import Foundation
import CoreGraphics

/// Time and touch model of the wheel. Everything on screen is a function of φ, the wheel's
/// position in plate steps (φ = 2 puts dish 2 at the front), and φ is a function of the clock.
enum PastaPlateWheelMotion {
    static let loop = 8.0
    /// Seconds after the last touch before the wheel springs back to the first dish.
    static let idle = 2.0
    /// Duration of that return; the loop restarts at its end.
    static let back = 1.6
    /// Drag distance (pt) that turns the wheel by one plate.
    static let stepPoints = 160.0
    static let count = 8
    static let centreX = 14.0
    static let orbit = 226.0
    static let discRadius = 242.0

    static func clamp(_ x: Double, _ lo: Double, _ hi: Double) -> Double { min(max(x, lo), hi) }

    /// Step response of a damped spring (response = period in s), from `from` with velocity `velocity`
    /// (units/s); exactly `to` after `settle` s.
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

    /// The loop: rest on A, turn to B, hold, turn to C, hold, turn back to A, rest.
    static func loopPhi(_ time: Double) -> Double {
        let t = time - floor(time / loop) * loop
        if t < 1.0 { return 0 }
        if t < 2.9 { return spring(t - 1.0, from: 0, to: 1, response: 0.6, damping: 0.7, settle: 1.5) }
        if t < 4.8 { return spring(t - 2.9, from: 1, to: 2, response: 0.6, damping: 0.7, settle: 1.5) }
        return spring(t - 4.8, from: 2, to: 0, response: 0.8, damping: 0.78, settle: 1.6)
    }

    /// d wrapped into [-count/2, count/2).
    static func wrap(_ d: Double) -> Double {
        let n = Double(count)
        let m = (d + n / 2) - floor((d + n / 2) / n) * n
        return m - n / 2
    }

    struct Plate {
        let index: Int
        let x: Double
        let y: Double
        let size: Double
        /// 1 at the front, falling off with the distance in steps.
        let front: Double
        /// Food rotation (radians): the food rolls three times as fast as the wheel turns.
        let spin: Double
    }

    /// The eight plates for φ, ordered back to front.
    static func plates(phi: Double, cy: Double, popIndex: Int = -1, pop: Double = 1) -> [Plate] {
        var out: [Plate] = []
        for i in 0..<count {
            let d = wrap(Double(i) - phi)
            let theta = d * Double.pi / 4
            let f = exp(-2.2 * d * d)
            var size = 84 + 80 * exp(-1.1 * d * d)
            if i == popIndex { size *= pop }
            let r = orbit + 22 * f
            out.append(Plate(index: i, x: centreX + r * cos(theta), y: cy + r * sin(theta), size: size, front: f, spin: 3 * theta))
        }
        return out.sorted { $0.front < $1.front }
    }

    /// Scale of a plate popped by a tap, t s after the tap.
    static func pop(_ t: Double) -> Double {
        if t < 0 || t > 1.2 { return 1 }
        return 1 + 0.1 * exp(-5 * t) * sin(13 * t)
    }

    static func centreY(safeTop: Double, height: Double, safeBottom: Double) -> Double {
        (safeTop + 60 + infoTop(height: height, safeBottom: safeBottom)) / 2
    }

    static func infoTop(height: Double, safeBottom: Double) -> Double { height - safeBottom - 160 }
}

/// Touch state, read by the timeline; a plain class, so writing it never re-renders the view.
final class PastaPlateWheelTouch {
    typealias M = PastaPlateWheelMotion
    enum Feedback { case none, detent, snap, pop }

    var touching = false
    var live = 0.0
    private var startPhi = 0.0
    private var downClock = 0.0
    private var moved = 0.0
    private var detent = 0
    var release: Double?
    private var from = 0.0, velocity = 0.0, target = 0.0, home = 0.0
    var popClock = -10.0
    var popIndex = -1

    /// φ at a clock time (seconds since the view appeared).
    func phi(_ clock: Double, autoPlay: Bool) -> Double {
        if touching { return live }
        if let r = release {
            let dt = clock - r
            if dt < M.idle {
                return M.spring(dt, from: from, to: target, velocity: velocity, response: 0.55, damping: 0.78, settle: 1.6)
            }
            let b = dt - M.idle
            if b < M.back { return M.spring(b, from: target, to: home, response: 0.8, damping: 0.78, settle: M.back) }
            return autoPlay ? home + M.loopPhi(b - M.back) : home
        }
        return autoPlay ? M.loopPhi(clock) : 0
    }

    /// Clock time after which nothing moves any more (outside autoPlay).
    var restsAfter: Double { (release ?? 0) + M.idle + M.back + 0.2 }

    func begin(clock: Double, autoPlay: Bool) {
        let current = phi(clock, autoPlay: autoPlay)
        touching = true
        live = current
        startPhi = current
        downClock = clock
        moved = 0
        detent = Int(current.rounded())
    }

    /// Returns true when the nearest plate changed (a detent haptic).
    func move(dx: Double, dy: Double) -> Bool {
        moved = max(moved, (dx * dx + dy * dy).squareRoot())
        guard moved >= 8 else { return false }
        live = startPhi - dy / M.stepPoints
        let d = Int(live.rounded())
        if d != detent { detent = d; return true }
        return false
    }

    func end(clock: Double, velocityY: Double, point: CGPoint, cy: Double) -> Feedback {
        var feedback = Feedback.snap
        if moved < 8 && clock - downClock < 0.5 {
            velocity = 0
            target = live.rounded()
            let hit = M.plates(phi: live, cy: cy).reversed().first { p in
                let dx = p.x - Double(point.x), dy = p.y - Double(point.y)
                return (dx * dx + dy * dy).squareRoot() < p.size / 2
            }
            if let hit {
                let delta = M.wrap(Double(hit.index) - live)
                if abs(delta) < 0.5 {
                    popClock = clock
                    popIndex = hit.index
                    feedback = .pop
                } else {
                    target = (live + delta).rounded()
                }
            } else {
                feedback = .none
            }
        } else {
            velocity = M.clamp(-velocityY / M.stepPoints, -12, 12)
            let nearest = live.rounded()
            target = M.clamp((live + 0.22 * velocity).rounded(), nearest - 3, nearest + 3)
        }
        from = live
        release = clock
        home = 8 * (target / 8).rounded()
        touching = false
        return feedback
    }
}
