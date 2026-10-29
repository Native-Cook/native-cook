import Foundation

/// Timing and touch model of Moka Flood Welcome. Every loop value is a pure function of the loop
/// time, so the loop's last frame equals its first.
enum MokaFloodWelcomeMotion {
    static let loop = 9.5

    static let restAngle = -6.0 // degrees, upright pot leaning a little
    static let pourAngle = -50.0 // degrees, spout down-left
    static let gravity = 2200.0 // pt/s², the stream's head and tail
    static let pourThreshold = -36.0 // the pot pours past this angle

    /// One falling stream from the spout: how far its head has fallen, how far its broken tail has
    /// fallen (0 while attached) and its width share.
    struct Stream {
        var head: Double
        var tail = 0.0
        var width = 1.0
    }

    /// What the art needs for one frame.
    struct Frame {
        var angle = restAngle
        /// Coffee poured: 0 empty glass, 1 at the rim, 1–1.75 dome, spills and puddle, 1.75–3 the flood rises
        /// from the bottom of the screen to above its top.
        var volume = 0.0
        /// While set, the flood drains: its surface sweeps from above the top (0) to below the
        /// bottom (1) and the glass, dome and spills are gone.
        var drain: Double?
        var title = 0.0 // 0–1, the three handwritten lines (see `lineProgress`)
        var sub = 0.0 // the subtitle's entrance
        var stream: Stream?
        var time = 0.0 // seconds for waves and bubbles
        var slosh = 0.0 // meniscus wobble share
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

    /// A released spring's remaining share of its start value (zero start velocity).
    static func springDecay(_ tau: Double, damping z: Double, response: Double) -> Double {
        if tau <= 0 { return 1 }
        let w = 2 * Double.pi / response
        let wd = w * sqrt(1 - z * z)
        return exp(-z * w * tau) * (cos(wd * tau) + z * w / wd * sin(wd * tau))
    }

    static func springStep(_ tau: Double, damping z: Double, response: Double) -> Double {
        tau <= 0 ? 0 : 1 - springDecay(tau, damping: z, response: response)
    }

    static func hash(_ x: Double) -> Double {
        let s = sin(x * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// Write progress of title line `i` (0, 1, 2) for a title value 0–1.
    static func lineProgress(_ title: Double, _ i: Int) -> Double {
        easeOutCubic(clamp((title - 0.29 * Double(i)) / 0.42))
    }

    // MARK: The loop (P = 9.5 s)

    static let streamAt = 1.2
    static let fillAt = 1.72
    static let breakAt = 5.4
    static let drainAt = 8.1
    static let drainEnd = 9.0

    /// Rest 0–1 s → the pot tilts on a spring (1.0) and pours (1.2) → the glass fills (1.72–3.6) →
    /// domes and spills (3.6–4.7) → the flood rises (4.7–6.3) while the pot rights itself (5.15) and
    /// the stream breaks (5.4) → the title is written (6.35–7.35) → the flood drains (8.05–8.95) →
    /// rest.
    static func loopVolume(_ t: Double) -> Double {
        if t < fillAt { return 0 }
        if t < 3.6 { return easeInOutSine(seg(t, fillAt, 3.6)) }
        if t < 4.7 { return 1 + 0.75 * seg(t, 3.6, 4.7) }
        return 1.75 + 1.25 * easeInOutSine(seg(t, 4.7, 6.5))
    }

    static func loopFrame(_ t: Double) -> Frame {
        var f = Frame()
        f.time = t
        let up = springStep(t - 1.0, damping: 0.62, response: 0.6)
        let back = springStep(t - 5.15, damping: 0.6, response: 0.6)
        let anticipation = 4 * sin(Double.pi * seg(t, 0.85, 1.05))
        f.angle = restAngle + (pourAngle - restAngle) * (up - back) + anticipation
        if t >= streamAt {
            let tau = t - streamAt
            var s = Stream(head: 0.5 * gravity * tau * tau)
            s.width = 1 - 0.65 * easeInCubic(seg(t, 5.1, breakAt))
            if t > breakAt { s.tail = 0.5 * gravity * (t - breakAt) * (t - breakAt) }
            if s.tail < 1200 { f.stream = s }
        }
        f.slosh = seg(t, fillAt, fillAt + 0.25) * (1 - seg(t, breakAt, 6.0))
        if t < drainEnd {
            f.volume = loopVolume(t)
            f.title = seg(t, 6.55, 7.55)
            f.sub = easeOutCubic(seg(t, 7.25, 7.65))
            if t >= drainAt { f.drain = easeInOutCubic(seg(t, drainAt, drainEnd)) }
        }
        return f
    }
}

/// The finger: hold to tilt the pot and pour, drag left or down to pour harder; release to let it
/// settle. Two seconds after the last touch the coffee drains and the loop starts again from rest.
final class MokaFloodWelcomeTouch {
    typealias M = MokaFloodWelcomeMotion

    static let idle = 2.0
    static let drainTime = 0.9

    private(set) var touching = false
    private var base = 0.0 // clock at loop time 0
    private var lastClock = -100.0 // last touch event
    private var lastStep = -1.0
    private var angle = M.restAngle
    private var spin = 0.0 // deg/s
    private var volume = 0.0 // 0…4: 3–4 writes the title
    private var drag = 0.0
    private var streamStart: Double?
    private var streamBreak: Double?
    private var drainSweep = false
    private(set) var hapticDue: [Double] = []

    var restsAfter: Double { lastClock + Self.idle + Self.drainTime }

    /// Loop time, or nil while the finger owns the scene.
    func loopTime(_ clock: Double, autoPlay: Bool) -> Double? {
        if touching || clock < restsAfter { return nil }
        if !autoPlay { return 0 }
        if lastClock > -50 { base = restsAfter }
        return M.mod(clock - base, M.loop)
    }

    func frame(_ clock: Double, autoPlay: Bool) -> M.Frame {
        if let t = loopTime(clock, autoPlay: autoPlay) { return autoPlay ? M.loopFrame(t) : M.Frame() }
        step(clock)
        var f = M.Frame()
        f.time = clock
        f.angle = angle
        f.volume = min(volume, 3)
        f.title = M.clamp(volume - 3)
        f.sub = M.easeOutCubic(M.clamp((f.title - 0.7) / 0.3))
        if let start = streamStart {
            let tau = clock - start
            var s = M.Stream(head: 0.5 * M.gravity * tau * tau)
            if let b = streamBreak {
                let k = clock - b
                s.width = 1 - 0.65 * M.easeInCubic(M.clamp(k / 0.22))
                if k > 0.22 { s.tail = 0.5 * M.gravity * (k - 0.22) * (k - 0.22) }
            }
            if s.tail < 1200 { f.stream = s }
        }
        f.slosh = f.stream != nil ? 1 : 0.4
        let drainStart = lastClock + Self.idle
        if !touching, clock > drainStart {
            let d = M.easeInOutCubic(M.clamp((clock - drainStart) / Self.drainTime))
            if drainSweep { f.drain = d } else { f.volume *= 1 - d }
        }
        return f
    }

    /// Integrates the pot's spring and the poured volume (touch only; the loop is closed-form).
    private func step(_ clock: Double) {
        let dt = M.clamp(clock - lastStep, 0, 1.0 / 30)
        lastStep = clock
        guard dt > 0 else { return }
        let target = touching ? M.restAngle + (M.pourAngle - M.restAngle) * (0.85 + 0.25 * drag) : M.restAngle
        let w = 2 * Double.pi / 0.6
        let z = touching ? 0.62 : 0.6
        var sub = 0.0
        while sub < dt { // fixed sub-steps keep the spring stable
            let h = min(1.0 / 240, dt - sub)
            spin += (-w * w * (angle - target) - 2 * z * w * spin) * h
            angle += spin * h
            sub += h
        }
        let pouring = angle < M.pourThreshold
        if pouring, streamStart == nil || streamBreak != nil {
            streamStart = clock
            streamBreak = nil
        } else if !pouring, streamStart != nil, streamBreak == nil {
            streamBreak = clock
        }
        if pouring, let start = streamStart, clock - start > 0.4 {
            let before = volume
            volume = min(4, volume + dt * (0.45 + 0.6 * drag))
            for mark in [0.0, 1.0, 3.0] where before <= mark && volume > mark {
                hapticDue.append(clock)
            }
        }
    }

    func begin(_ clock: Double, autoPlay: Bool) {
        if !touching {
            if let t = loopTime(clock, autoPlay: autoPlay), autoPlay {
                // Carry the loop as it is on screen into the touch.
                let f = M.loopFrame(t)
                angle = f.angle
                spin = 0
                volume = f.drain != nil ? 0 : (f.title > 0 ? 3 + f.title : f.volume)
                if t >= M.streamAt, t < M.breakAt {
                    streamStart = clock - (t - M.streamAt)
                    streamBreak = nil
                } else {
                    streamStart = nil
                    streamBreak = nil
                }
            } else if clock > lastClock + Self.idle {
                // Touched while the last pour drains: start again from what is left.
                let d = M.easeInOutCubic(M.clamp((clock - lastClock - Self.idle) / Self.drainTime))
                volume = drainSweep ? (d > 0.5 ? 0 : volume) : volume * (1 - d)
            }
            lastStep = clock
        }
        touching = true
        drag = 0
        lastClock = clock
    }

    /// `dx`, `dy`: the finger's travel in pt; left or down tilts the pot further.
    func move(_ clock: Double, dx: Double, dy: Double) {
        guard touching else { return }
        drag = M.clamp((-dx + dy) / 160)
        lastClock = clock
    }

    func end(_ clock: Double) {
        guard touching else { return }
        step(clock)
        touching = false
        drag = 0
        lastClock = clock
        drainSweep = volume >= 2.9
    }

    /// True once per due haptic.
    func takeHaptic(_ clock: Double) -> Bool {
        guard hapticDue.contains(where: { $0 <= clock }) else { return false }
        hapticDue.removeAll { $0 <= clock }
        return true
    }
}
