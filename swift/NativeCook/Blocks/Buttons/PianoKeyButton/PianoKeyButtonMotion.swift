import Foundation

/*
 Piano Key Button — timing. Every frame of the self-playing loop is a pure function of the loop
 time t (seconds); under a finger the same pose is built from per-key springs and press times.

 loop = 8 s:
   0.0 – 1.3   rest (the poster: three ivory keys, streak 12 days)
   1.3 – 2.0   key 1 (C) down and up      2.1 – 2.8  key 2 (E)      2.9 – 3.6  key 3 (G)
   4.2         chord C + G down, held to 6.4; 5.2 – 5.6 the streak rolls 12 → 13 and the chip pulses
   6.4 – 8.0   release and settle; 7.0 – 7.4 the streak rolls back to 12 so 8.0 equals 0.0
*/
enum PianoKeyButtonMotion {
    static let loop = 8.0
    static let keyCount = 3

    /// (key, down, up) in loop seconds.
    static let script: [(Int, Double, Double)] = [
        (0, 1.3, 2.0),
        (1, 2.1, 2.8),
        (2, 2.9, 3.6),
        (0, 4.2, 6.4),
        (2, 4.2, 6.4),
    ]
    static let streakFrom = 12.0
    static let streakUp = (5.2, 5.6)
    static let streakBack = (7.0, 7.4)

    /// The key's spring: ω 40 rad/s, damping ratio 0.62 (≈ 8 % overshoot, settled in ~0.16 s).
    static let keyOmega = 40.0
    static let keyZeta = 0.62

    /// A ring wave lives this long (s); three rings leave each press 0.12 s apart.
    static let ringLife = 1.3
    static let ringDelay = 0.12
    /// After the last touch the loop waits this long, then eases back to rest in backFor.
    static let idleBeforeBack = 2.0
    static let backFor = 0.6

    struct Key {
        /// Seconds since this key last went down (large when it never did).
        var downAge = 99.0
        /// Seconds since it came back up, or -1 while it is held.
        var upAge = 99.0
        /// Key travel 0 (rest) … 1 (on the keybed), may overshoot a little.
        var depth = 0.0
        /// 0 = the CTA label shows, 1 = the note name shows.
        var label = 0.0
    }

    struct Pose {
        var keys = [Key](repeating: Key(), count: 3)
        /// Staff note heads lit 0…1: C, E, G, chord.
        var notes = [0.0, 0.0, 0.0, 0.0]
        /// The streak as a number that rests on whole days.
        var streak = 12.0
        /// Chip pulse 0…1.
        var pulse = 0.0
    }

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func easeOut(_ x: Double) -> Double {
        let u = 1 - clamp01(x)
        return 1 - u * u * u
    }

    static func easeInOut(_ x: Double) -> Double {
        let v = clamp01(x)
        if v < 0.5 { return 4 * v * v * v }
        let u = -2 * v + 2
        return 1 - u * u * u / 2
    }

    /// Reanimated's default withTiming curve (ease-in-out quad).
    static func easeInOutQuad(_ x: Double) -> Double {
        let v = clamp01(x)
        return v < 0.5 ? 2 * v * v : 1 - (-2 * v + 2) * (-2 * v + 2) / 2
    }

    /// Unit step response of an underdamped spring, 0 before τ = 0.
    static func springStep(_ tau: Double, _ omega: Double, _ zeta: Double) -> Double {
        if tau <= 0 { return 0 }
        let wd = omega * (1 - zeta * zeta).squareRoot()
        let e = exp(-zeta * omega * tau)
        return 1 - e * (cos(wd * tau) + (zeta * omega) / wd * sin(wd * tau))
    }

    /// The hammer behind a key: flicks up in 60 ms, falls back with one small bounce.
    static func hammerLift(_ downAge: Double) -> Double {
        if downAge < 0 || downAge > 1 { return 0 }
        if downAge < 0.06 { return easeOut(downAge / 0.06) }
        let a = downAge - 0.06
        return max(-0.12, exp(-a * 9) * cos(a * 20))
    }

    /// How hard the key's strings ring: decays while held, damped fast once the key is up.
    static func stringRing(_ k: Key) -> Double {
        if k.downAge > 6 { return 0 }
        let ring = exp(-k.downAge * 1.4)
        return k.upAge < 0 ? ring : ring * exp(-k.upAge * 9)
    }

    /// A soft glow on the key, strongest at the strike.
    static func keyGlow(_ k: Key) -> Double {
        if k.downAge > 6 { return 0 }
        let strike = exp(-k.downAge * 5) * 0.5
        let held = k.upAge < 0 ? 0.12 : 0.12 * exp(-k.upAge * 6)
        return strike + held
    }

    private static func labelAt(_ t: Double, _ down: Double, _ up: Double) -> Double {
        easeInOut(seg(t, down + 0.02, down + 0.26)) - easeInOut(seg(t, up + 0.35, up + 0.62))
    }

    /// The pose of the self-playing loop at loop time t.
    static func loopPose(_ t: Double) -> Pose {
        var p = Pose()
        var pulse = 0.0
        for (n, item) in script.enumerated() {
            let (key, down, up) = item
            // Superposed steps are the exact response of the linear spring to "down, then up".
            p.keys[key].depth += springStep(t - down, keyOmega, keyZeta) - springStep(t - up, keyOmega, keyZeta)
            p.keys[key].label += labelAt(t, down, up)
            if t >= down {
                p.keys[key].downAge = t - down
                p.keys[key].upAge = t >= up ? t - up : -1
                pulse = max(pulse, 0.3 * exp(-(t - down) * 6))
            }
            let light = seg(t, down, down + 0.08) * (1 - seg(t, up + 0.5, up + 1.1))
            let note = min(n, 3)
            p.notes[note] = max(p.notes[note], light)
        }
        p.streak = streakFrom + easeInOut(seg(t, streakUp.0, streakUp.1)) - easeInOut(seg(t, streakBack.0, streakBack.1))
        let q = seg(t, streakUp.0, streakUp.0 + 0.6)
        p.pulse = max(pulse, sin(.pi * q) * (1 - q * 0.4))
        return p
    }

    /// The resting pose (also what reduced motion shows).
    static func restPose() -> Pose { loopPose(0) }
}

/// A value easing from one number to another over a duration, sampled at any time.
struct PianoKeyButtonTween {
    var from = 0.0
    var to = 0.0
    var start = 0.0
    var duration = 0.0

    init(_ v: Double) {
        from = v
        to = v
    }

    func value(_ now: Double) -> Double {
        guard duration > 0 else { return to }
        let u = PianoKeyButtonMotion.easeInOutQuad((now - start) / duration)
        return from + (to - from) * u
    }

    mutating func go(_ target: Double, at now: Double, delay: Double = 0, duration d: Double) {
        from = value(now)
        to = target
        start = now + delay
        duration = d
    }
}

/// The hand-driven state: per-key springs and press times, stepped from the timeline.
final class PianoKeyButtonEngine {
    typealias M = PianoKeyButtonMotion

    enum Mode { case auto, touch, back }

    var mode = Mode.auto
    /// The loop's t = 0 in timeline seconds (nil until the first frame).
    var loopStart: Double? = nil
    var lastTouch = 0.0
    var backUntil = 0.0
    var lastStep: Double? = nil

    var depth = [0.0, 0.0, 0.0]
    var velocity = [0.0, 0.0, 0.0]
    var target = [0.0, 0.0, 0.0]
    var label = [PianoKeyButtonTween(0), PianoKeyButtonTween(0), PianoKeyButtonTween(0)]
    var downAt = [-99.0, -99.0, -99.0]
    var upAt = [-99.0, -99.0, -99.0]
    var fingers = [0, 0, 0]
    var owner: [Int: Int] = [:]
    var streak = PianoKeyButtonTween(M.streakFrom)
    var pulseAt = -99.0
    var chorded = false

    /// Advances the springs and the mode to `now`, then returns the pose to draw.
    func pose(at now: Double, auto: Bool, reduced: Bool) -> M.Pose {
        if loopStart == nil { loopStart = now }
        // A stalled frame (or a paused timeline) holds the loop where it was instead of skipping.
        if let last = lastStep, now - last > 0.25, mode == .auto, let start = loopStart {
            loopStart = start + (now - last)
        }
        stepSprings(to: now)
        switch mode {
        case .touch:
            if fingers.reduce(0, +) == 0 && now - lastTouch > M.idleBeforeBack {
                mode = .back
                backUntil = now + M.backFor
                streak.go(M.streakFrom, at: now, duration: M.backFor)
                chorded = false
            }
        case .back:
            if now >= backUntil {
                mode = .auto
                loopStart = now
            }
        case .auto:
            break
        }
        if mode != .auto { return touchPose(now) }
        if !auto { return M.restPose() }
        let t = (now - (loopStart ?? now)).truncatingRemainder(dividingBy: M.loop)
        return M.loopPose(max(0, t))
    }

    /// True while something still moves under the hand (the timeline may pause otherwise).
    func busy(_ now: Double) -> Bool {
        mode != .auto
    }

    private func stepSprings(to now: Double) {
        guard let last = lastStep else {
            lastStep = now
            return
        }
        var dt = min(0.05, max(0, now - last))
        lastStep = now
        let w = M.keyOmega, z = M.keyZeta
        while dt > 0 {
            let h = min(dt, 1.0 / 480)
            for i in 0..<M.keyCount {
                let a = -w * w * (depth[i] - target[i]) - 2 * z * w * velocity[i]
                velocity[i] += a * h
                depth[i] += velocity[i] * h
            }
            dt -= h
        }
    }

    private func touchPose(_ at: Double) -> M.Pose {
        var p = M.Pose()
        for i in 0..<M.keyCount {
            let held = upAt[i] < downAt[i]
            p.keys[i] = M.Key(downAge: at - downAt[i], upAge: held ? -1 : at - upAt[i], depth: depth[i], label: label[i].value(at))
            p.notes[i] = M.seg(at, downAt[i], downAt[i] + 0.08) * (held ? 1 : 1 - M.seg(at, upAt[i] + 0.5, upAt[i] + 1.1))
        }
        p.notes[3] = min(p.notes[0], p.notes[2])
        let q = (at - pulseAt) / 0.6
        var pulse = q > 0 && q < 1 ? sin(.pi * q) * (1 - q * 0.4) : 0
        for i in 0..<M.keyCount where at >= downAt[i] {
            pulse = max(pulse, 0.3 * exp(-(at - downAt[i]) * 6))
        }
        p.streak = streak.value(at)
        p.pulse = pulse
        return p
    }

    /// Hands the loop's pose to the springs, so a finger never makes anything jump.
    private func takeOver(_ at: Double, auto: Bool) {
        if mode == .touch { return }
        let p: M.Pose
        if mode == .back {
            p = touchPose(at)
        } else if auto, let start = loopStart {
            p = M.loopPose(max(0, (at - start).truncatingRemainder(dividingBy: M.loop)))
        } else {
            p = M.restPose()
        }
        // The loop's spring velocity, measured from the pose a moment earlier.
        var before: M.Pose? = nil
        if mode == .auto, auto, let start = loopStart {
            before = M.loopPose(max(0, (at - 0.001 - start).truncatingRemainder(dividingBy: M.loop)))
        }
        for i in 0..<M.keyCount {
            let k = p.keys[i]
            depth[i] = k.depth
            velocity[i] = before.map { (k.depth - $0.keys[i].depth) / 0.001 } ?? 0
            if abs(velocity[i]) > 200 { velocity[i] = 0 }
            target[i] = 0
            label[i] = PianoKeyButtonTween(k.label)
            if k.downAge < 50 {
                downAt[i] = at - k.downAge
                upAt[i] = k.upAge < 0 ? at - k.downAge - 1 : at - k.upAge
            } else {
                downAt[i] = -99
                upAt[i] = -99
            }
            // A key the loop holds down is let go now.
            if k.upAge < 0 {
                upAt[i] = at
                label[i].go(0, at: at, delay: 0.35, duration: 0.26)
            }
        }
        streak = PianoKeyButtonTween(p.streak)
        streak.go(p.streak.rounded(), at: at, duration: 0.3)
        mode = .touch
    }

    /// A finger landed on key i. Returns true when it completes the chord.
    func press(_ i: Int, at: Double, auto: Bool) -> Bool {
        takeOver(at, auto: auto)
        fingers[i] += 1
        lastTouch = at
        if fingers[i] > 1 { return false }
        downAt[i] = at
        target[i] = 1
        label[i].go(1, at: at, duration: 0.24)
        // C and G held together is the exercise's chord.
        if fingers[0] > 0 && fingers[2] > 0 && !chorded {
            chorded = true
            pulseAt = at
            streak.go(M.streakFrom + 1, at: at, delay: 0.2, duration: 0.4)
            return true
        }
        return false
    }

    func release(_ i: Int, at: Double) {
        lastTouch = at
        fingers[i] = max(0, fingers[i] - 1)
        if fingers[i] > 0 { return }
        upAt[i] = at
        target[i] = 0
        label[i].go(0, at: at, delay: 0.35, duration: 0.26)
    }
}
