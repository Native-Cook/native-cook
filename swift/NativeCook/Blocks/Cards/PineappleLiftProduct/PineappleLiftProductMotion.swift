import Foundation

/// Timing and touch model of Pineapple Lift Product. Every value is a pure function of the loop
/// time (autoPlay) or of the clock since a touch event, so the loop's last frame equals its first.
enum PineappleLiftProductMotion {
    static let loop = 7.0

    /// What the art needs for one frame.
    struct Frame {
        var open = 0.0 // the top half: 0 closed, 1 lifted up-right and tilted
        var rise = 0.0 // the bottle: 0 hidden in the fruit, 1 hovering above it
        var squash = 0.0 // pt the top half presses down (anticipation) or hops up (negative)
        var glow = 0.0 // warm glow behind the bottle
        var drift = 0.0 // hibiscus flowers drifting outward
        var sheen = -1.0 // a light band crossing the bottle, 0 → 1 (negative: none)
        var bursts: [(age: Double, strength: Double)] = []
    }

    // MARK: Curves

    static func mod(_ a: Double, _ b: Double) -> Double {
        let r = a.truncatingRemainder(dividingBy: b)
        return r < 0 ? r + b : r
    }

    static func clamp(_ v: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(max(v, lo), hi) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInCubic(_ x: Double) -> Double { x * x * x }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeInOutSine(_ x: Double) -> Double { -(cos(Double.pi * x) - 1) / 2 }
    static func smoothstep(_ x: Double) -> Double { x * x * (3 - 2 * x) }

    /// A released spring's remaining share of its start value (zero start velocity).
    static func springDecay(_ tau: Double, damping z: Double, response: Double) -> Double {
        if tau <= 0 { return 1 }
        let w = 2 * Double.pi / response
        let wd = w * sqrt(1 - z * z)
        return exp(-z * w * tau) * (cos(wd * tau) + z * w / wd * sin(wd * tau))
    }

    /// A spring stepping from 0 to 1.
    static func springStep(_ tau: Double, damping z: Double, response: Double) -> Double {
        tau <= 0 ? 0 : 1 - springDecay(tau, damping: z, response: response)
    }

    static func hash(_ x: Double) -> Double {
        let s = sin(x * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    // MARK: The loop (P = 7 s)

    static let burstAt = 1.45
    static let landAt = 5.75

    /// Rest 0–1 s → the top half presses, then lifts on a spring (1.15 s) → the bottle rises out of
    /// the fruit (1.3 s) in a ring of juice drops (1.45 s) → it hovers → sinks back (4.6–5.5 s) → the
    /// top closes (5.0–5.75 s) and lands with a small hop and a few drops → rest 6.3–7 s.
    static func loopFrame(_ t: Double) -> Frame {
        var f = Frame()
        let press = easeInOutSine(seg(t, 1.0, 1.15)) * (1 - easeOutCubic(seg(t, 1.15, 1.35)))
        let up = springStep(t - 1.15, damping: 0.62, response: 0.62)
        f.open = up * (1 - easeInCubic(seg(t, 5.0, 5.75)))
        let lift = springStep(t - 1.3, damping: 0.55, response: 0.7)
        f.rise = lift * (1 - easeInOutCubic(seg(t, 4.6, 5.5)))
        f.squash = 5 * press - hop(t - landAt) * (1 - smoothstep(seg(t, 6.2, 6.6)))
        f.glow = easeOutCubic(seg(t, 1.3, 2.2)) * (1 - easeInOutCubic(seg(t, 4.6, 5.4)))
        f.drift = easeOutCubic(seg(t, 1.3, 2.6)) * (1 - easeInOutCubic(seg(t, 4.6, 6.0)))
        if t > 2.7, t < 3.6 { f.sheen = easeInOutSine(seg(t, 2.7, 3.6)) }
        let a = t - burstAt
        if a >= 0, a < 1.9 { f.bursts.append((a, 1)) }
        let b = t - landAt
        if b >= 0, b < 1.2 { f.bursts.append((b, 0.45)) }
        return f
    }

    /// The landing hop of the top half, in pt.
    static func hop(_ tau: Double) -> Double {
        tau < 0 || tau > 0.9 ? 0 : 7 * exp(-7 * tau) * abs(sin(11 * tau))
    }

    // MARK: Juice drops

    static let dropCount = 54
    static let globCount = 7
    static let gravity = 1300.0

    struct Drop {
        var x: Double, y: Double // offset from the cut-face centre, pt
        var vx: Double, vy: Double // pt/s at this moment
        var r: Double
        var alpha: Double
    }

    /// Drops of one burst at `age` s; rim = the cut ellipse's radii in pt.
    static func drops(age: Double, strength: Double, rx: Double, ry: Double) -> [Drop] {
        var out: [Drop] = []
        let total = dropCount + globCount
        out.reserveCapacity(total)
        for i in 0..<total {
            let fi = Double(i)
            let glob = i >= dropCount
            let h1 = hash(fi * 1.37 + 0.11), h2 = hash(fi * 2.71 + 0.37)
            let h3 = hash(fi * 0.93 + 0.53), h4 = hash(fi * 3.17 + 0.71)
            let h5 = hash(fi * 1.91 + 0.29)
            let life = (glob ? 1.25 + 0.35 * h5 : 0.95 + 0.6 * h5) * (strength < 0.7 ? 0.72 : 1)
            if age > life { continue }
            let n = Double(glob ? globCount : dropCount)
            let k = glob ? fi - Double(dropCount) : fi
            let th = 2 * Double.pi * (k + 0.6 * h1) / n + (glob ? 0.4 : 0)
            let sx = cos(th) * rx * 0.92, sy = sin(th) * ry * 0.92
            let speed = strength * (glob ? 0.62 : 1)
            let vx0 = cos(th) * (70 + 190 * h2) * speed
            let vy0 = -(250 + 330 * h3) * speed * (0.8 + 0.2 * abs(cos(th)))
            let x = sx + vx0 * age
            let y = sy + vy0 * age + 0.5 * gravity * age * age
            let r = (glob ? 5.5 + 3.5 * h4 : 2.0 + 3.8 * h4 * h4) * (0.75 + 0.25 * strength)
            let fadeIn = min(1, age / 0.05)
            let fadeOut = 1 - pow(age / life, 3)
            out.append(Drop(x: x, y: y, vx: vx0, vy: vy0 + gravity * age, r: r, alpha: fadeIn * fadeOut))
        }
        return out
    }
}

/// The finger: drag up to lift (the bottle follows), release to spring back, tap for a hop and a
/// splash. Two seconds after the last touch the loop starts again from rest.
final class PineappleLiftProductTouch {
    typealias M = PineappleLiftProductMotion

    static let idle = 2.0
    static let dragSpan = 200.0 // pt of upward drag for open = 1

    private(set) var touching = false
    private var base = 0.0 // clock at loop time 0
    private var startOpen = 0.0
    private(set) var dragOpen = 0.0
    private var armed = true
    private var releaseClock = -100.0
    private var releaseOpen = 0.0
    private var releaseRise = 0.0
    private var landClock = -100.0
    private var tapClock = -100.0
    private var lastClock = -100.0 // last touch event (press, release or tap)
    private var bursts: [(clock: Double, strength: Double)] = []
    private(set) var hapticDue: [Double] = []

    var restsAfter: Double { lastClock + Self.idle }

    /// Loop time, or nil while the finger owns the scene.
    func loopTime(_ clock: Double, autoPlay: Bool) -> Double? {
        if touching || clock < lastClock + Self.idle { return nil }
        if !autoPlay { return 0 }
        if lastClock > -50 { base = lastClock + Self.idle }
        return M.mod(clock - base, M.loop)
    }

    func frame(_ clock: Double, autoPlay: Bool) -> M.Frame {
        if let t = loopTime(clock, autoPlay: autoPlay) { return autoPlay ? M.loopFrame(t) : M.Frame() }
        var f = M.Frame()
        let tap = tapPulse(clock - tapClock)
        if touching {
            f.open = dragOpen
            f.rise = max(0, dragOpen)
        } else {
            let tau = clock - releaseClock
            f.open = releaseOpen * M.springDecay(tau, damping: 0.78, response: 0.5) + tap
            f.rise = releaseRise * M.springDecay(tau, damping: 0.62, response: 0.62) + 0.55 * tap
        }
        f.squash = -M.hop(clock - landClock) * 0.8
        f.glow = M.clamp(f.rise)
        f.drift = M.clamp(f.open)
        for b in bursts where clock >= b.clock && clock - b.clock < 1.9 {
            f.bursts.append((clock - b.clock, b.strength))
        }
        return f
    }

    /// A quick hop of the top half for a tap: up and down in 0.42 s.
    private func tapPulse(_ tau: Double) -> Double {
        tau < 0 || tau > 0.42 ? 0 : 0.3 * sin(Double.pi * tau / 0.42)
    }

    func begin(_ clock: Double, autoPlay: Bool) {
        // Carry the scene as it is on screen into the drag.
        let now = frame(clock, autoPlay: autoPlay)
        bursts = now.bursts.map { (clock - $0.age, $0.strength) }
        startOpen = M.clamp(now.open, 0, 1.2)
        dragOpen = startOpen
        armed = startOpen < 0.55
        touching = true
        lastClock = clock
    }

    /// `dy` is the finger's vertical travel in pt (negative = up).
    func drag(_ clock: Double, dy: Double) {
        guard touching else { return }
        let raw = startOpen - dy / Self.dragSpan
        if raw > 1 {
            let over = raw - 1
            dragOpen = 1 + over / (1 + over / 0.35)
        } else if raw < 0 {
            dragOpen = raw / (1 - raw / 0.04)
        } else {
            dragOpen = raw
        }
        if armed, dragOpen > 0.55 {
            armed = false
            addBurst(clock, 1)
        } else if dragOpen < 0.3 {
            armed = true
        }
        lastClock = clock
    }

    func end(_ clock: Double, tapped: Bool) {
        guard touching else { return }
        touching = false
        releaseClock = clock
        releaseOpen = dragOpen
        releaseRise = max(0, dragOpen)
        lastClock = clock
        if tapped {
            tapClock = clock
            addBurst(clock + 0.08, 0.7)
            landClock = clock + 0.42
            hapticDue.append(clock + 0.42)
            lastClock = clock + 0.42
        } else if releaseOpen > 0.3 {
            // When the closing spring reaches the fruit: a hop, a few drops and a light tap.
            var tau = 0.0
            while tau < 1.5, releaseOpen * M.springDecay(tau, damping: 0.78, response: 0.5) > 0.03 { tau += 1.0 / 120 }
            landClock = clock + tau
            addBurst(landClock, 0.45)
        }
    }

    private func addBurst(_ clock: Double, _ strength: Double) {
        bursts.removeAll { clock - $0.clock > 2 }
        bursts.append((clock, strength))
        hapticDue.append(clock)
    }

    /// True once per due haptic.
    func takeHaptic(_ clock: Double) -> Bool {
        guard hapticDue.contains(where: { $0 <= clock }) else { return false }
        hapticDue.removeAll { $0 <= clock }
        return true
    }
}
