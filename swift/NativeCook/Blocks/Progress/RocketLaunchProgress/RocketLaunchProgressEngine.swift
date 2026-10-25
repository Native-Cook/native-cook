import Foundation
import SwiftUI

/// What a frame draws.
struct RocketLaunchProgressFrame {
    var t = 0.0
    var fuel = 0.0
    var moving = 0.0
    var spin = 0.0
    var rx = 0.0
    var ry = 0.0
    var rot = 0.0
    var on = 1.0
    var sx = 0.0
    var sy = 0.0
    var thr = 0.0
    var lift = 0.0
    var age = 0.0
    var fade = 0.0
    var trailA = 0.0
}

/*
 Rocket Launch Progress — the launch, stepped once a frame from the TimelineView (like the RN frame
 callback). In the loop everything is a function of the loop time; holding the pad fuels the tank
 by hand (2.4 s to full); a full tank hands the launch back to the loop from the countdown; 2 s
 after letting go of a part-full tank it drains (0.9 s) and the loop restarts at 0.
*/
@MainActor
final class RocketLaunchProgressEngine {
    private typealias M = RocketLaunchProgressMotion
    enum Mode { case auto, user, back }

    static let idle = 2.0
    static let returnTime = 0.9
    /// Holding fills the tank in this long.
    static let fillFor = 2.4

    var reduced = false
    /// 0: a tick (selection), 1: full (medium impact).
    var onFeel: ((Int) -> Void)?

    private(set) var frame = RocketLaunchProgressFrame()
    private var mode = Mode.auto
    private var clock = 0.0
    private var now = 0.0
    private var last: Date?
    private var lastTouch = 0.0
    private(set) var holding = false
    private var oneShot = false
    private var userFuel = 0.0
    private var returnAt = 0.0
    private var returnFuel = 0.0

    /// Whether the timeline must keep running outside autoPlay.
    var active: Bool { holding || oneShot || mode != .auto }

    func step(to date: Date, auto: Bool, layout L: RocketLaunchProgressLayout) -> RocketLaunchProgressFrame {
        let dt = min(max(last.map { date.timeIntervalSince($0) } ?? 0, 0), 0.05)
        last = date
        let at = now + dt
        now = at
        var t = 0.0
        var fuel = 0.0
        var moving = 0.0
        var spin = 0.0
        if mode == .auto {
            if reduced {
                // Still: the pad, or once fuelled by hand, the deployed screen.
                t = userFuel >= 100 ? M.doneAt + 0.3 : 0
                fuel = t > 0 ? 100 : 0
            } else if auto || oneShot {
                let next = clock + dt
                // Without autoPlay a hand-fuelled launch plays once and stops back on the pad.
                if !auto && next >= RocketLaunchProgress.loop { oneShot = false }
                t = next.truncatingRemainder(dividingBy: RocketLaunchProgress.loop)
                fuel = M.autoFuel(t)
            }
            clock = t
            moving = t > 1.4 && t < 4.6 ? 1 : 0
            spin = 2 * Double.pi * 6 * t / RocketLaunchProgress.loop
        } else {
            t = 0.5
            var u = userFuel
            if mode == .user && holding {
                let before = u
                u = min(100, u + 100 / Self.fillFor * dt)
                userFuel = u
                moving = 1
                for s in [25.0, 55, 85] where before < s && u >= s { onFeel?(0) }
                if u >= 100 {
                    // Full: the loop carries the launch on from the countdown.
                    holding = false
                    onFeel?(1)
                    mode = .auto
                    clock = M.countAt
                    oneShot = true
                }
            }
            if mode == .user && auto && !holding && at - lastTouch > Self.idle {
                returnAt = at
                returnFuel = u
                mode = .back
            }
            if mode == .back {
                let p = M.easeOutCubic((at - returnAt) / Self.returnTime)
                u = returnFuel * (1 - p)
                userFuel = u
                moving = 1
                if p >= 1 {
                    mode = .auto
                    clock = 0
                    userFuel = 0
                }
            }
            fuel = u
            spin = at * 5
        }

        let pose = reduced ? M.Pose() : M.rocketPose(t)
        let a = reduced ? 0 : M.shake(t)
        let w = 2 * Double.pi * t / RocketLaunchProgress.loop
        frame = RocketLaunchProgressFrame(
            t: t,
            fuel: fuel,
            moving: moving,
            spin: spin,
            rx: L.padX + pose.x,
            ry: L.baseY + pose.y,
            rot: pose.rot,
            on: reduced && t > M.liftAt ? 0 : pose.on,
            sx: a * 2.6 * (sin(37 * w) + 0.5 * sin(71 * w + 1)),
            sy: a * 2.2 * (sin(43 * w + 2) + 0.5 * sin(89 * w)),
            thr: reduced ? 0 : M.throttle(t),
            lift: M.clamp01((t - M.liftAt) / 1.2),
            age: max(0, t - M.igniteAt),
            fade: 1 - M.easeOutCubic((t - (M.doneAt - 0.2)) / 1.2),
            trailA: reduced ? 0 : M.clamp01((t - M.liftAt) / 0.15) * (1 - M.easeOutCubic((t - M.doneAt) / 0.8))
        )
        return frame
    }

    // MARK: - Touch

    private func touched() {
        lastTouch = now
        if mode == .user { return }
        if mode == .auto {
            // Mid-launch the loop keeps the stage; on the pad the finger takes the tank as it is.
            let t = clock
            if t >= M.countAt && t < M.rollAt + 0.6 { return }
            userFuel = t > M.rollAt ? 0 : M.autoFuel(t).rounded()
        }
        mode = .user
    }

    func press() {
        touched()
        if mode == .user { holding = true }
    }

    func release() {
        guard holding else { return }
        holding = false
        lastTouch = now
        // The gauge comes to rest on a whole number.
        userFuel = userFuel.rounded()
    }

    // MARK: - Paths

    /// The contrail: the nozzle's path since lift-off, a ribbon that widens with age.
    static func trail(_ t: Double, _ L: RocketLaunchProgressLayout) -> Path {
        var p = Path()
        let k = min(t, M.rollAt) - M.liftAt
        if k <= 0.02 { return p }
        let n = 28
        var left: [CGPoint] = []
        var right: [CGPoint] = []
        for j in 0...n {
            let kk = k * Double(j) / Double(n)
            let pose = M.rocketPose(M.liftAt + kk)
            let age = k - kk
            let w = (3 + 26 * age + 30 * max(0, t - M.doneAt)) * L.k
            let nx = cos(pose.rot)
            let ny = sin(pose.rot)
            let x = L.padX + pose.x
            let y = L.baseY + pose.y + 10 * L.k * min(1, age * 4)
            left.append(CGPoint(x: x - nx * w, y: y - ny * w))
            right.append(CGPoint(x: x + nx * w, y: y + ny * w))
        }
        p.move(to: left[0])
        for j in 1...n { p.addLine(to: left[j]) }
        for j in stride(from: n, through: 0, by: -1) { p.addLine(to: right[j]) }
        p.closeSubpath()
        return p
    }

    /// The fuel's surface: a level with a small wave that grows while it moves.
    static func slosh(_ level: Double, _ t: Double, _ moving: Double, _ tank: CGRect) -> Path {
        var p = Path()
        let y0 = tank.maxY - level * (tank.height - 4)
        let amp = 0.8 + 2.2 * moving
        p.move(to: CGPoint(x: tank.minX, y: tank.maxY))
        for j in 0...10 {
            let x = tank.minX + tank.width * Double(j) / 10
            p.addLine(to: CGPoint(x: x, y: y0 + amp * sin(Double(j) / 10 * Double.pi * 2 + 2 * Double.pi * 7 * t / RocketLaunchProgress.loop)))
        }
        p.addLine(to: CGPoint(x: tank.maxX, y: tank.maxY))
        p.closeSubpath()
        return p
    }
}
