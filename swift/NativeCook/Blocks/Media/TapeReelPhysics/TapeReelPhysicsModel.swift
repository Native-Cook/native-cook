import Foundation

/// The tape's mechanics, all in closed form so the self-playing loop is exact:
/// the reels' radii and angles are functions of how much tape has been played,
/// the slack tape is a damped spring driven by steps, and the plucked span is a sum of string modes.
enum TapeReelPhysicsModel {
    // MARK: Reels

    /// Seconds of tape on a side, and where the loop rests (12:41).
    static let total = 1800.0, home = 761.0
    /// Pack radii (design points): an empty hub's 20, a full reel's 50; r_supply² + r_takeup² stays constant.
    static let rMin = 20.0, rMax = 50.0
    /// Points of tape that pass the head per second of play (sets how fast the hubs turn).
    static let feed = 37.0
    static var area: Double { (rMax * rMax - rMin * rMin) / total }

    static func takeUp(_ s: Double) -> Double { sqrt(rMin * rMin + area * s) }
    static func supply(_ s: Double) -> Double { sqrt(rMax * rMax - area * s) }
    /// The hubs' angles (radians, clockwise) after s seconds of tape: ∫ feed ds / r, which is closed form.
    static func takeUpAngle(_ s: Double) -> Double { 2 * feed / area * (takeUp(s) - rMin) }
    static func supplyAngle(_ s: Double) -> Double { 2 * feed / area * (rMax - supply(s)) }
    /// Angular speed of a pack of radius r for a tape speed v (seconds of tape per second).
    static func spin(_ v: Double, _ r: Double) -> Double { feed * v / r }

    // MARK: Transport

    enum Mode: Int { case stop, play, ff, rew, pause, seek }
    static func moving(_ m: Mode) -> Bool { m == .play || m == .ff || m == .rew || m == .seek }

    /// From `start` on, the transport is in `mode` with the tape at `s`; a seek glides to `to` in `duration`.
    struct Segment { var start: Double, mode: Mode, s: Double, to: Double = 0, duration: Double = 0 }

    static func easeInOut(_ x: Double) -> Double {
        let u = min(max(x, 0), 1)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }
    static func easeInOutSlope(_ x: Double) -> Double {
        let u = min(max(x, 0), 1)
        return u < 0.5 ? 12 * u * u : 3 * pow(-2 * u + 2, 2)
    }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let u = min(max((x - a) / (b - a), 0), 1)
        return u * u * (3 - 2 * u)
    }

    /// Tape position and speed τ seconds into a segment. Play runs at 1×, wind at 320×, each
    /// reaching its speed with a linear ramp (0.3 s, 0.35 s); stop and pause hold the tape.
    static func position(_ g: Segment, _ tau: Double) -> (s: Double, v: Double) {
        switch g.mode {
        case .stop, .pause: return (g.s, 0)
        case .seek:
            let u = tau / g.duration
            if u >= 1 { return (g.to, 0) }
            return (g.s + (g.to - g.s) * easeInOut(u), (g.to - g.s) * easeInOutSlope(u) / g.duration)
        case .play, .ff, .rew:
            let speed = g.mode == .play ? 1.0 : g.mode == .ff ? 320 : -320
            let ramp = g.mode == .play ? 0.3 : 0.35
            let d = tau < ramp ? speed * tau * tau / (2 * ramp) : speed * (tau - ramp / 2)
            let v = tau < ramp ? speed * tau / ramp : speed
            let s = g.s + d
            if s <= 0 { return (0, 0) }
            if s >= total { return (total, 0) }
            return (s, v)
        }
    }

    // MARK: Slack

    /// How far the slack tape bows out when the transport stops (points), and its spring.
    static let slack = 7.0, gamma = 5.0, omega = 16.0

    /// A unit step response of the damped spring: 0 at the step, settling at 1 with one overshoot.
    static func step(_ tau: Double) -> Double {
        guard tau > 0 else { return 0 }
        return 1 - exp(-gamma * tau) * (cos(omega * tau) + gamma / omega * sin(omega * tau))
    }

    // MARK: The span between the guides

    /// Pulled at x (0…1 of the span) by d points: a triangle. Let go: the triangle's first four
    /// string modes, each ringing and dying away (fundamental 5 Hz).
    static func pulled(_ u: Double, x: Double, d: Double) -> Double {
        u < x ? d * u / x : d * (1 - u) / (1 - x)
    }
    static func ringing(_ u: Double, x: Double, d: Double, tau: Double) -> Double {
        var y = 0.0
        for n in 1...4 {
            let k = Double(n)
            let a = 2 * d / (k * k * .pi * .pi * x * (1 - x)) * sin(k * .pi * x)
            y += a * sin(k * .pi * u) * cos(2 * .pi * 5 * k * tau) * exp(-(2.5 + 0.8 * k) * tau)
        }
        return y
    }
    /// The hum of the span while the tape winds fast (a ≤ 1).
    static func hum(_ u: Double, a: Double, clock: Double) -> Double {
        a * (1.2 * sin(.pi * u) * sin(2 * .pi * 18 * clock) + 0.6 * sin(3 * .pi * u) * sin(2 * .pi * 27 * clock + 1))
    }

    /// A pluck: pulled at x by d now, or let go from d at `releasedAt`.
    struct Pluck { var x: Double, d: Double, releasedAt: Double? }

    // MARK: The self-playing loop

    static let loop = 9.0

    /// 0.6 play · 1.6–1.95 a finger pulls the tape 22 pt at 38 % · 2.1 lets go · 3.2 fast forward ·
    /// 4.6 stop · 5.2 rewind back to 12:41 in 2.2 s · 7.4 stop · rest to 9.0.
    static let script: [Segment] = {
        var g: [Segment] = [Segment(start: 0, mode: .stop, s: home)]
        g.append(Segment(start: 0.6, mode: .play, s: home))
        let atFF = position(g[1], 3.2 - 0.6).s
        g.append(Segment(start: 3.2, mode: .ff, s: atFF))
        let atStop = position(g[2], 4.6 - 3.2).s
        g.append(Segment(start: 4.6, mode: .stop, s: atStop))
        g.append(Segment(start: 5.2, mode: .seek, s: atStop, to: home, duration: 2.2))
        g.append(Segment(start: 7.4, mode: .stop, s: home))
        return g
    }()
    static let scriptSteps: [(Double, Double)] = [(0.6, -slack), (4.6, slack), (5.2, -slack), (7.4, slack)]
    static func scriptPluck(_ t: Double) -> Pluck? {
        if t < 1.6 { return nil }
        if t < 2.1 { return Pluck(x: 0.38, d: 22 * (1 - pow(1 - min((t - 1.6) / 0.35, 1), 3)), releasedAt: nil) }
        return Pluck(x: 0.38, d: 22, releasedAt: 2.1)
    }

    // MARK: A moment

    struct Frame {
        var s = home, v = 0.0
        var mode = Mode.stop
        /// The key shown pressed (index into the key row), or nil.
        var key: Int? = nil
        var sag = slack
        var pluck: Pluck? = nil
        /// The span's hum while winding (0…1) and the meters' level envelope (0…1).
        var hum = 0.0, meters = 0.0
        /// A scripted finger on the tape: x on the span, d below it, and its opacity.
        var finger: (x: Double, d: Double, alpha: Double)? = nil
    }

    /// Evaluates a timeline (segments, slack steps on a base) at time t.
    static func frame(at t: Double, segments g: [Segment], sagBase: Double, steps: [(Double, Double)], pluck: Pluck?) -> Frame {
        var f = Frame()
        var i = 0
        for (k, seg) in g.enumerated() where seg.start <= t { i = k }
        let seg = g[i], tau = t - seg.start
        (f.s, f.v) = position(seg, tau)
        f.mode = seg.mode == .seek ? .rew : seg.mode
        let previous = i > 0 ? g[i - 1].mode : nil
        switch seg.mode {
        case .play: f.key = 1
        case .ff: f.key = 2
        case .rew, .seek: f.key = 0
        case .pause: f.key = 3
        case .stop: f.key = (previous != nil && previous != .stop && tau < 0.2) ? 4 : nil
        }
        f.sag = sagBase
        for (at, delta) in steps { f.sag += delta * step(t - at) }
        f.pluck = pluck
        f.hum = min(1, abs(f.v) / 200)
        if seg.mode == .play {
            f.meters = smoothstep(0, 0.3, tau)
        } else if previous == .play {
            f.meters = 1 - smoothstep(0, 0.3, tau)
        }
        return f
    }

    static func auto(_ t: Double) -> Frame {
        var f = frame(at: t, segments: script, sagBase: slack, steps: scriptSteps, pluck: scriptPluck(t))
        if t >= 1.45 && t < 2.35 {
            let p = scriptPluck(t)
            f.finger = (0.38, p?.releasedAt == nil ? (p?.d ?? 0) : 22,
                        smoothstep(1.45, 1.6, t) * (1 - smoothstep(2.1, 2.35, t)))
        }
        return f
    }

    /// The span's displacement at u (0…1) for a frame, with time `t` on the frame's clock.
    static func span(_ u: Double, _ f: Frame, t: Double, still: Bool) -> Double {
        var y = 0.0
        if let p = f.pluck {
            if let r = p.releasedAt {
                if !still { y += ringing(u, x: p.x, d: p.d, tau: t - r) }
            } else {
                y += pulled(u, x: p.x, d: p.d)
            }
        }
        if !still { y += hum(u, a: f.hum, clock: t) }
        return y
    }

    /// The meters: a level 0…1 per channel, from a fixed mix of sines.
    static func level(_ t: Double, channel: Int) -> Double {
        let p = Double(channel) * 0.9
        return 0.55 + 0.2 * sin(2 * .pi * 1.7 * t + p) + 0.12 * sin(2 * .pi * 4.3 * t + 1.1 + p) + 0.08 * sin(2 * .pi * 7.9 * t + 2.3 + 2 * p)
    }

    static func clock(_ s: Double) -> String {
        let n = Int(s.rounded(.down))
        return String(format: "%02d:%02d", n / 60, n % 60)
    }
}
