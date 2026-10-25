import Foundation

/*
 The cradle's motion, as one "folded" pendulum.

 An ideal Newton's cradle behaves like a single pendulum angle ψ folded at the vertical: while
 ψ < 0 the left group of n balls hangs at ψ (the rest at 0), while ψ > 0 the right group of n balls
 hangs at ψ. Each zero crossing is a collision: the momentum passes through the still balls and
 the same number leaves on the other side.

 autoPlay (loop = 8 s), every frame a pure function of t:
   0.0 – 1.2   rest; a fingertip fades in on the left ball from 1.0.
   1.2 – 2.0   the finger pulls ball 1 out to −15° (ease-in-out).
   2.0 – 6.0   released: ψ = −A(s)·cos(ωs), period 1.02 s, amplitude × 0.97 per collision
               (two collisions a period), so balls 1 and 5 take turns.
   6.0 – 7.4   the amplitude eases to 0 (ease-in-out), the swing dies down.
   7.4 – 8.0   rest. t = 8 is the frame at t = 0.

 Touch: the same folded pendulum, integrated (ψ'' = −ω²·sin ψ, a little air drag, velocity
 × 0.97 at every crossing), from the angle and angular velocity the finger leaves it with.
 */
enum NewtonCradleSwingMotion {
    static let loop = 8.0
    /// Pendulum period (s), and its angular frequency.
    static let period = 1.02
    static let omega = 2 * Double.pi / period
    /// The scripted pull, in radians.
    static let pull = 15 * Double.pi / 180
    /// Furthest a finger may pull before the rubber band, and the band's softness (radians).
    static let pullLimit = 16 * Double.pi / 180
    static let pullSoft = 2.5 * Double.pi / 180
    /// Amplitude kept at each collision.
    static let keep = 0.97
    static let balls = 5

    private static let pullStart = 1.2
    private static let release = 2.0
    private static let fadeStart = 6.0
    private static let fadeEnd = 7.4

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(max((t - a) / (b - a), 0), 1)
    }

    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func easeOut(_ x: Double) -> Double {
        1 - pow(1 - x, 3)
    }

    /// The loop's folded angle ψ at loop time t (radians, n = 1).
    static func loopPsi(_ t: Double) -> Double {
        if t < pullStart { return 0 }
        if t < release { return -pull * easeInOut(seg(t, pullStart, release)) }
        let s = t - release
        let amp = pull * pow(keep, 2 * s / period) * (1 - easeInOut(seg(t, fadeStart, fadeEnd)))
        return -amp * cos(omega * s)
    }

    /// Collisions so far in the loop (shown while the balls swing).
    static func loopHits(_ t: Double) -> Int {
        if t < release + period / 4 || t > fadeEnd - 0.35 { return 0 }
        return Int(floor((t - release - period / 4) / (period / 2))) + 1
    }

    /// 0 → 1 while the scripted fingertip is on the left ball.
    static func loopFinger(_ t: Double) -> Double {
        easeOut(seg(t, 1.0, 1.2)) * (1 - easeOut(seg(t, release, release + 0.22)))
    }

    /// How full the energy meter is (1 = the pull, 0 = still), for the loop.
    static func loopEnergy(_ t: Double) -> Double {
        if t < release { return easeInOut(seg(t, pullStart, release)) }
        let s = t - release
        return pow(keep, 2 * s / period) * (1 - easeInOut(seg(t, fadeStart, fadeEnd)))
    }

    /// Each ball's angle (radians) for a folded angle ψ and a group of n balls.
    static func ballAngles(_ psi: Double, _ n: Int) -> [Double] {
        var out = [Double](repeating: 0, count: balls)
        if psi < 0 {
            for i in 0..<n { out[i] = psi }
        } else if psi > 0 {
            for i in (balls - n)..<balls { out[i] = psi }
        }
        return out
    }

    /// Past pullLimit the pull gets stiffer: limit + soft·(1 − e^(−over/soft)).
    static func rubberBand(_ theta: Double) -> Double {
        let a = abs(theta)
        if a <= pullLimit { return theta }
        let over = a - pullLimit
        return (theta < 0 ? -1 : 1) * (pullLimit + pullSoft * (1 - exp(-over / pullSoft)))
    }

    /// The touch simulation. Returns true when a collision happened.
    static func step(_ sim: inout NewtonCradleSwingSim, _ dt: Double) -> Bool {
        var hit = false
        let sub = max(1, Int((dt / (1.0 / 240)).rounded(.up)))
        let h = dt / Double(sub)
        for _ in 0..<sub {
            let before = sim.psi
            sim.vel += -omega * omega * sin(sim.psi) * h
            sim.vel *= exp(-0.06 * h)
            sim.psi += sim.vel * h
            if (before < 0 && sim.psi >= 0) || (before > 0 && sim.psi <= 0) {
                sim.vel *= keep
                sim.hits += 1
                hit = true
            }
        }
        // Too small to see: stop.
        if sim.amplitude < 0.004 {
            sim.psi = 0
            sim.vel = 0
        }
        return hit
    }
}

/// ψ, ψ', the size of the moving group and the collisions so far.
struct NewtonCradleSwingSim {
    var psi = 0.0
    var vel = 0.0
    var n = 1
    var hits = 0

    /// The swing's amplitude (radians).
    var amplitude: Double {
        let w = vel / NewtonCradleSwingMotion.omega
        return (psi * psi + w * w).squareRoot()
    }
}
