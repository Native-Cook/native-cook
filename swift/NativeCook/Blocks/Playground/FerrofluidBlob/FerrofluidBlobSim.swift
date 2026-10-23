import Foundation

// The ferrofluid's magnets: up to two of them, each following a finger (or the autoPlay script)
// and carrying the springs the shader reads: how far the fluid has heaped up toward it, how tall
// its crown of spikes stands, and which way the spikes lean. Stepped in fixed substeps, a line-for-
// line port of the React Native simulation.
//
// Units: points and seconds. Positions are on the table ("ground"), not on the screen: the dish is
// seen from above at an angle, so a screen point maps to the ground through the camera in
// FerrofluidBlob.swift. The dish's centre is the ground origin.
enum FerrofluidBlobTuning {
    /// How many magnets (fingers) can raise a crown at once.
    static let magnets = 2

    // Fixed substep and the most frame time one frame may consume (a long hitch is cut short).
    static let substep = 1.0 / 120
    static let maxFrame = 1.0 / 30

    // The magnet glides after its finger on a nearly critical spring (rad/s, damping ratio), so the
    // crown trails a fast swipe a little and catches up without overshoot.
    static let followW = 15.0
    static let followZ = 0.86
    // Field: how far the fluid heaps up under the magnet (0…1). It rises over ~0.3 s.
    static let fieldW = 8.0
    static let fieldZ = 0.62
    // Spikes: their height (0…1, a little more during a pulse). Growing is well damped; once the
    // field is gone they melt back underdamped, so they wobble and briefly dimple below the surface.
    static let spikeW = 11.0
    static let spikeRiseZ = 0.5
    static let spikeFallZ = 0.2
    static let spikeMin = -0.35
    static let spikeMax = 1.7
    // The spikes lean against the magnet's motion, as if dragged through the fluid: leanPerSpeed pt
    // of tip offset per pt/s, at most leanMax pt, eased at leanRate per second.
    static let leanPerSpeed = 0.03
    static let leanMax = 9.0
    static let leanRate = 12.0
    // A tap pulses the magnet: the spikes get a kick of pulseSpike spike-heights per second and the
    // field a smaller one, and the magnet stays on for tapLinger s so the flinch can be seen.
    static let pulseSpike = 7.5
    static let pulseField = 2.2
    static let tapLinger = 0.42
    // A touch shorter than tapTime s that moves less than tapSlop pt is a tap.
    static let tapTime = 0.24
    static let tapSlop = 12.0

    // Events for haptics, read and cleared by the view each frame.
    static let eventRise = 1
    static let eventPulse = 2

    // Negative, so it can never meet a real finger's id (the touch listener counts up from 1).
    static let scriptID = -1000

    /// Seconds the script takes before it starts over.
    static let loop = 12.0
}

struct FerrofluidBlobMagnet {
    /// Touch id holding it, or -1. Scripted fingers use ids from scriptID up.
    var touch = -1
    var scripted = false
    /// Where its finger is, and how strong it wants to be (0…1).
    var tx = 0.0
    var ty = 0.0
    var power = 0.0
    /// Seconds it stays on after its finger lifts (after a tap).
    var linger = 0.0
    var downAt = 0.0
    var travel = 0.0
    var lastX = 0.0
    var lastY = 0.0
    /// The magnet itself, gliding after the finger.
    var x = 0.0
    var y = 0.0
    var vx = 0.0
    var vy = 0.0
    var field = 0.0
    var fieldV = 0.0
    var spike = 0.0
    var spikeV = 0.0
    var leanX = 0.0
    var leanY = 0.0
}

/// One autoPlay key: a scripted finger's place in dish radii from `t` on.
struct FerrofluidBlobKey {
    let t: Double
    let x: Double
    let y: Double
    let on: Bool
    let power: Double
}

final class FerrofluidBlobSim {
    private typealias T = FerrofluidBlobTuning

    var m = Array(repeating: FerrofluidBlobMagnet(), count: FerrofluidBlobTuning.magnets)
    var time = 0.0
    let reduced: Bool
    var events = 0
    var eventsByUser = false

    init(reduced: Bool) {
        self.reduced = reduced
    }

    /// One step of a damped spring toward `target`: returns (value, velocity).
    private static func springStep(_ value: Double, _ velocity: Double, _ target: Double, _ w: Double, _ z: Double, _ h: Double) -> (Double, Double) {
        let a = w * w * (target - value) - 2 * z * w * velocity
        let v = velocity + a * h
        return (value + v * h, v)
    }

    /// The magnet a new finger takes: a free one, preferring the one whose crown has melted most.
    private func freeMagnet() -> Int {
        var best = -1
        var bestLeft = Double.infinity
        for i in m.indices {
            let g = m[i]
            if g.touch != -1 { continue }
            let left = abs(g.field) + abs(g.spike) + (g.linger > 0 ? 2 : 0)
            if left < bestLeft {
                bestLeft = left
                best = i
            }
        }
        return best
    }

    func touchDown(_ id: Int, _ x: Double, _ y: Double, scripted: Bool, power: Double) {
        let i = freeMagnet()
        if i < 0 { return }
        var g = m[i]
        // A magnet whose crown has melted starts right under the finger instead of gliding over.
        if abs(g.field) + abs(g.spike) < 0.08 && g.linger <= 0 {
            g.x = x
            g.y = y
            g.vx = 0
            g.vy = 0
            g.leanX = 0
            g.leanY = 0
        }
        g.touch = id
        g.scripted = scripted
        g.tx = x
        g.ty = y
        g.power = power
        g.linger = 0
        g.downAt = time
        g.travel = 0
        g.lastX = x
        g.lastY = y
        m[i] = g
        if !scripted {
            events |= T.eventRise
            eventsByUser = true
        }
    }

    func touchMove(_ id: Int, _ x: Double, _ y: Double, power: Double) {
        for i in m.indices where m[i].touch == id {
            m[i].travel += hypot(x - m[i].lastX, y - m[i].lastY)
            m[i].lastX = x
            m[i].lastY = y
            m[i].tx = x
            m[i].ty = y
            m[i].power = power
        }
    }

    /// Kicks one magnet's crown up: the flinch of a tap.
    private func pulse(_ i: Int, byUser: Bool) {
        m[i].spikeV += T.pulseSpike * (reduced ? 0.4 : 1)
        m[i].fieldV += T.pulseField
        m[i].linger = max(m[i].linger, T.tapLinger)
        events |= T.eventPulse
        if byUser { eventsByUser = true }
    }

    func touchUp(_ id: Int) {
        for i in m.indices where m[i].touch == id {
            let tap = time - m[i].downAt < T.tapTime && m[i].travel < T.tapSlop
            m[i].touch = -1
            if tap { pulse(i, byUser: !m[i].scripted) }
        }
    }

    /// A scripted tap on whichever magnet a scripted finger holds.
    func pulseScripted(_ id: Int) {
        for i in m.indices where m[i].touch == id {
            pulse(i, byUser: false)
        }
    }

    /// A real finger takes over: the script's fingers let go where they are.
    func cancelScripted() {
        for i in m.indices where m[i].scripted && m[i].touch != -1 {
            m[i].touch = -1
        }
    }

    /// Whether a real finger rests on the dish.
    var userHolding: Bool {
        m.contains { $0.touch != -1 && !$0.scripted }
    }

    func step(_ frame: Double) {
        let dt = min(frame, T.maxFrame)
        let n = max(1, Int(ceil(dt / T.substep - 1e-6)))
        let h = dt / Double(n)
        let calm = reduced
        for _ in 0..<n {
            time += h
            for i in m.indices {
                var g = m[i]
                if g.linger > 0 { g.linger = max(0, g.linger - h) }
                let on = g.touch != -1 || g.linger > 0
                let power = on ? g.power : 0
                if g.touch != -1 {
                    if calm {
                        g.x = g.tx
                        g.y = g.ty
                        g.vx = 0
                        g.vy = 0
                    } else {
                        (g.x, g.vx) = Self.springStep(g.x, g.vx, g.tx, T.followW, T.followZ, h)
                        (g.y, g.vy) = Self.springStep(g.y, g.vy, g.ty, T.followW, T.followZ, h)
                    }
                } else {
                    // Let go, the magnet coasts to a stop where it was.
                    let keep = exp(-8 * h)
                    g.vx *= keep
                    g.vy *= keep
                    g.x += g.vx * h
                    g.y += g.vy * h
                }
                (g.field, g.fieldV) = Self.springStep(g.field, g.fieldV, power, T.fieldW, calm ? 1 : T.fieldZ, h)
                let z = calm ? 1 : power > g.spike ? T.spikeRiseZ : T.spikeFallZ
                let (sp, spv) = Self.springStep(g.spike, g.spikeV, power, T.spikeW, z, h)
                g.spike = min(T.spikeMax, max(T.spikeMin, sp))
                g.spikeV = sp == g.spike ? spv : 0
                // Lean against the motion, eased.
                var lx = -g.vx * T.leanPerSpeed
                var ly = -g.vy * T.leanPerSpeed
                let len = hypot(lx, ly)
                if len > T.leanMax {
                    lx *= T.leanMax / len
                    ly *= T.leanMax / len
                }
                let e = 1 - exp(-T.leanRate * h)
                g.leanX += (lx - g.leanX) * e
                g.leanY += (ly - g.leanY) * e
                m[i] = g
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // autoPlay. Two scripted fingers, placed in units of the dish's radius from its centre (ground
    // coordinates; negative y is the far side). A key holds the finger's place from its time on and
    // eases (smoothstep) into the next key; `on` keys put the finger down, off keys lift it.

    /// The first magnet sweeps an arc over the far side, flinches, glides to the near right and lifts.
    private static let keysA: [FerrofluidBlobKey] = [
        .init(t: 0, x: -0.46, y: 0.06, on: false, power: 0),
        .init(t: 0.9, x: -0.46, y: 0.06, on: true, power: 0.85),
        .init(t: 1.6, x: -0.42, y: -0.08, on: true, power: 1),
        .init(t: 2.5, x: -0.18, y: -0.3, on: true, power: 1),
        .init(t: 3.4, x: 0.16, y: -0.3, on: true, power: 1),
        .init(t: 4.3, x: 0.38, y: -0.06, on: true, power: 1),
        .init(t: 5.6, x: 0.38, y: -0.06, on: true, power: 1),
        .init(t: 6.8, x: 0.3, y: 0.22, on: true, power: 1),
        .init(t: 8.4, x: 0.22, y: 0.16, on: true, power: 1),
        .init(t: 9.6, x: 0.22, y: 0.16, on: false, power: 0),
    ]
    /// The second comes down on the left while the first is still there: two crowns.
    private static let keysB: [FerrofluidBlobKey] = [
        .init(t: 0, x: -0.34, y: 0.1, on: false, power: 0),
        .init(t: 6.6, x: -0.34, y: 0.1, on: true, power: 0.9),
        .init(t: 7.6, x: -0.3, y: -0.02, on: true, power: 1),
        .init(t: 8.6, x: -0.22, y: 0.12, on: true, power: 1),
        .init(t: 10.3, x: -0.22, y: 0.12, on: false, power: 0),
    ]
    /// Taps: (time, finger).
    private static let pulses: [(Double, Int)] = [(5.0, 0), (8.9, 0), (9.1, 1)]

    private static func smooth01(_ p: Double) -> Double {
        let t = min(1, max(0, p))
        return t * t * (3 - 2 * t)
    }

    /// Where a scripted finger is at time t: (x, y, on, power), x and y in dish radii.
    private static func keyAt(_ keys: [FerrofluidBlobKey], _ t: Double) -> (Double, Double, Bool, Double) {
        var i = 0
        while i + 1 < keys.count && keys[i + 1].t <= t { i += 1 }
        let a = keys[i]
        if i + 1 >= keys.count { return (a.x, a.y, a.on, a.power) }
        let b = keys[i + 1]
        let e = smooth01((t - a.t) / (b.t - a.t))
        return (a.x + (b.x - a.x) * e, a.y + (b.y - a.y) * e, a.on, a.power + (b.power - a.power) * e)
    }

    /// True when the loop's clock went past `at` between `before` and `now` (wrapping at the loop).
    private static func passed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        now >= before ? before < at && now >= at : at > before || at <= now
    }

    /// Drives the scripted fingers from the loop's clock, which moved from `before` to `now`.
    /// `radius` is the dish's radius in points.
    func script(before: Double, now: Double, radius: Double) {
        let tracks = [Self.keysA, Self.keysB]
        for f in tracks.indices {
            let (x, y, on, power) = Self.keyAt(tracks[f], now)
            let id = T.scriptID + f
            let held = m.contains { $0.touch == id }
            let px = x * radius
            let py = y * radius
            if on && !held {
                touchDown(id, px, py, scripted: true, power: power)
            } else if on {
                touchMove(id, px, py, power: power)
            } else if held {
                touchMove(id, px, py, power: power)
                // A scripted lift is never a tap; taps are listed in `pulses`.
                for i in m.indices where m[i].touch == id { m[i].touch = -1 }
            }
        }
        for (at, finger) in Self.pulses where Self.passed(before, now, at) {
            pulseScripted(T.scriptID + finger)
        }
    }
}
