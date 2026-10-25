import Foundation

/*
 Compass Needle Nav — the needle in its liquid and the pages swinging round the compass.

 The needle is an underdamped spring (ω 7 rad/s, ζ 0.4: it swings 25 % past a new heading and
 settles in about 1.4 s, as a needle floating in damping fluid does). The pages hang on a wheel
 centred on the compass, one every 90°; the wheel follows the same target critically damped
 (ω 5.5 rad/s), so a page swings in from the side its tab sits on, without wobbling.

 loop = 8 s: rest on N (Map) 0 – 1.2; to E (Trips) at 1.2, settled by 2.6; hold; to S (Gear) at
 3.4, settled by 4.8; hold; on round through W to N at 5.8 (a half turn), settled by 7.2; 7.4 – 7.9
 the last hundredth of a degree is eased out so 8.0 equals 0.0; rest.
*/
enum CompassNeedleNavMotion {
    static let loop = 8.0
    static let needleOmega = 7.0
    static let needleZeta = 0.4
    static let wheelOmega = 5.5
    static let idleBeforeBack = 2.0
    static let backFor = 0.9

    /// (time, degrees added to the heading).
    static let steps: [(Double, Double)] = [(1.2, 90), (3.4, 90), (5.8, 180)]

    struct Pose {
        /// Needle heading and the page wheel's angle, degrees (continuous, 0 = N, clockwise).
        var needle = 0.0
        var wheel = 0.0
        /// Needle angular velocity, degrees per second (moves the bubble).
        var spin = 0.0
        /// The finger's angle on the bezel while dragging, or nil.
        var finger: Double? = nil
        /// The heading being steered to (lights its mark on the bezel).
        var target = 0.0
    }

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func easeInOut(_ x: Double) -> Double {
        let v = clamp01(x)
        if v < 0.5 { return 4 * v * v * v }
        let u = -2 * v + 2
        return 1 - u * u * u / 2
    }

    /// Wraps degrees to -180 … 180.
    static func wrap(_ d: Double) -> Double { d - 360 * round(d / 360) }

    /// Rounds half up, as JavaScript's Math.round does (the React Native version's rounding).
    static func round(_ x: Double) -> Double { floor(x + 0.5) }

    private static func under(_ tau: Double) -> Double {
        if tau <= 0 { return 0 }
        let w = needleOmega, z = needleZeta
        let wd = w * (1 - z * z).squareRoot()
        return 1 - exp(-z * w * tau) * (cos(wd * tau) + z * w / wd * sin(wd * tau))
    }

    private static func critical(_ tau: Double) -> Double {
        if tau <= 0 { return 0 }
        let w = wheelOmega
        return 1 - exp(-w * tau) * (1 + w * tau)
    }

    private static func headingAt(_ t: Double) -> (needle: Double, wheel: Double, target: Double) {
        var needle = 0.0, wheel = 0.0, target = 0.0
        for (at, add) in steps {
            needle += add * under(t - at)
            wheel += add * critical(t - at)
            if t >= at { target += add }
        }
        // Ease out the spring's last residue so the loop closes exactly on 360°.
        let k = easeInOut((t - 7.4) / 0.5)
        return (needle + (360 - needle) * k, wheel + (360 - wheel) * k, target)
    }

    /// The pose of the self-playing loop at loop time t.
    static func loopPose(_ t: Double) -> Pose {
        let h = headingAt(t)
        let h2 = headingAt(max(0, t - 1.0 / 60))
        return Pose(needle: h.needle, wheel: h.wheel, spin: (h.needle - h2.needle) * 60, finger: nil, target: h.target)
    }
}

/// The finger's simulation and the block's mode, stepped from the timeline.
final class CompassNeedleNavEngine {
    typealias M = CompassNeedleNavMotion

    enum Mode { case auto, touch, back }

    var mode = Mode.auto
    var loopStart: Double? = nil
    var lastFrame: Double? = nil
    var lastTouch = 0.0
    var dragging = false

    var needle = 0.0, vel = 0.0
    var wheel = 0.0, wheelVel = 0.0
    var target = 0.0
    var finger: Double? = nil
    /// Easing back: start time, and where it started from.
    var backAt = -1.0
    var from = (0.0, 0.0)
    var lastPose = M.Pose()
    /// The loop time of the last frame (drives the needle's bob).
    var bobTime = 0.0

    func reset() {
        needle = 0; vel = 0; wheel = 0; wheelVel = 0; target = 0
        finger = nil; backAt = -1; from = (0, 0)
    }

    /// Steps both springs toward the target (fixed 1/240 s substeps, so it is stable at any rate).
    private func step(_ dt: Double) {
        let n = max(1, Int((dt * 240).rounded(.up)))
        let h = dt / Double(n)
        let w = M.needleOmega, z = M.needleZeta, ww = M.wheelOmega
        for _ in 0..<n {
            vel += (-w * w * (needle - target) - 2 * z * w * vel) * h
            needle += vel * h
            wheelVel += (-ww * ww * (wheel - target) - 2 * ww * wheelVel) * h
            wheel += wheelVel * h
        }
    }

    func pose(at now: Double, auto: Bool, reduced: Bool, still: Double) -> M.Pose {
        if loopStart == nil { loopStart = now }
        let gap = lastFrame.map { now - $0 } ?? 0
        // A stalled frame (or a paused timeline) holds the loop where it was instead of skipping.
        if gap > 0.25, mode == .auto, let s = loopStart { loopStart = s + gap }
        let dt = min(max(gap, 0), 1.0 / 20)
        lastFrame = now
        if mode == .touch {
            step(dt)
            if !dragging && now - lastTouch > M.idleBeforeBack {
                from = (needle, wheel)
                target = 360 * M.round(wheel / 360)
                backAt = now
                mode = .back
            }
        } else if mode == .back && now >= backAt + M.backFor {
            reset()
            mode = .auto
            loopStart = now
        }
        let p: M.Pose
        if reduced {
            p = M.Pose(needle: still, wheel: still, spin: 0, finger: nil, target: still)
        } else if mode == .back {
            let k = M.easeInOut((now - backAt) / M.backFor)
            p = M.Pose(needle: from.0 + (target - from.0) * k, wheel: from.1 + (target - from.1) * k, spin: 0, finger: nil, target: target)
        } else if mode == .touch {
            p = M.Pose(needle: needle, wheel: wheel, spin: vel, finger: finger, target: target)
        } else if !auto {
            p = M.loopPose(0)
        } else {
            let t = max(0, (now - (loopStart ?? now)).truncatingRemainder(dividingBy: M.loop))
            p = M.loopPose(t)
        }
        bobTime = mode == .auto ? max(0, (now - (loopStart ?? now)).truncatingRemainder(dividingBy: M.loop)) : now
        lastPose = p
        return p
    }

    func takeOver() {
        if mode == .touch { return }
        let p = lastPose
        reset()
        needle = p.needle
        wheel = p.wheel
        vel = p.spin
        target = p.target
        mode = .touch
    }
}
