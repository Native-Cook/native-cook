import Foundation

/// Floating Letters Stir — where every letter is, as a pure function of the loop time, plus the small live
/// simulation a finger drives. A port of the React Native `floatingLettersMotion.ts`.
///
/// LOOP = 10 s
///   0.00 – 0.80   rest: the phrase readable, letters bobbing 1–2 pt on the ambient swell.
///   0.80 – 1.20   the ghost finger lands left of the phrase (fade 0.4 s, scale 1.25 → 1).
///   1.20 – 4.80   the stir: 1.6 clockwise turns of an elliptical spiral around the phrase (ease-in-out sine),
///                 a ring every 0.5 s (8 rings, each alive 2.8 s, front at 150 pt/s). Each letter is carried by
///                 the Gaussian-weighted integral of the finger's velocity near it (sigma 70 pt, up to 130 pt)
///                 plus 16–48 pt outward, eased out over 1.6 s, then keeps turning with the vortex and spins.
///   4.80 – 5.10   the finger lifts.
///   6.30 – 8.35   home: an underdamped spring per letter (zeta 0.62, omega 6.5 rad/s), 60 ms apart.
///   8.35 – 10.0   rest.
enum FloatingLettersStirMotion {
    static let loop: Double = 10

    static let ghostIn = 0.8
    static let stirA = 1.2
    static let stirB = 4.8
    static let ghostOut = 5.1
    static let stirTurns = 1.6
    private static let stirPhi0 = Double.pi * 1.1
    private static let stirR0 = 1.02
    private static let stirR1 = 0.6

    static let pushSigma = 70.0
    static let pushGain = 0.55
    static let pushMax = 130.0
    static let pushSpread = 16.0
    private static let pushLead = 0.35
    private static let pushRise = 1.6

    static let returnAt = 6.3
    static let returnStagger = 0.06
    private static let zeta = 0.62
    private static let omega = 6.5
    private static let settled = 2.0

    static let ripples = 8
    static let rippleT0 = 1.25
    static let rippleEvery = 0.5
    static let rippleLife = 2.8
    static let rippleSpeed = 150.0
    static let rippleLambda = 34.0
    static let rippleWidth = 30.0
    private static let rippleK = 2 * Double.pi / rippleLambda

    /// Letters ride the rings: lift in pt per unit height, tilt in rad per unit slope.
    static let bobLift = 3.0
    static let bobTilt = 0.05

    private static let tau = Double.pi * 2

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { let u = 1 - x; return 1 - u * u * u }
    static func easeInOutSine(_ x: Double) -> Double { 0.5 - 0.5 * cos(.pi * x) }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// Unit step response of the return spring (0 → 1, a little past 1 on the way).
    static func springStep(_ tau: Double) -> Double {
        if tau <= 0 { return 0 }
        if tau >= settled { return 1 }
        let root = (1 - zeta * zeta).squareRoot()
        let wd = omega * root
        let e = exp(-zeta * omega * tau)
        return 1 - e * (cos(wd * tau) + (zeta / root) * sin(wd * tau))
    }

    /// The phrase box the stir circles: centre and half extents, and the screen.
    struct Geo { var cx, cy, hw, hh, W, H: Double }
    struct Home { var x, y: Double }
    /// What the scripted stir does to one letter: its carry (pt), when the finger passed, its spin target (rad).
    struct Push { var px, py, tc, spin: Double }
    struct Pose { var dx = 0.0, dy = 0.0, rot = 0.0, scale = 1.0 }

    /// Where the ghost finger is at loop time t.
    static func stirPos(_ t: Double, _ geo: Geo) -> (x: Double, y: Double) {
        let u = easeInOutSine(seg(t, stirA, stirB))
        let phi = stirPhi0 + tau * stirTurns * u
        let r = stirR0 + (stirR1 - stirR0) * u
        return (geo.cx + geo.hw * r * cos(phi), geo.cy + geo.hh * r * sin(phi))
    }

    /// How visible the ghost finger is (0…1).
    static func ghostAt(_ t: Double) -> Double {
        seg(t, ghostIn, stirA) * (1 - seg(t, stirB, ghostOut))
    }

    /// Integrates the scripted finger's velocity near each home. Runs once per layout.
    static func makePushes(_ homes: [Home], _ geo: Geo) -> [Push] {
        let samples = 180
        let step = (stirB - stirA) / Double(samples)
        var out: [Push] = []
        for (n, home) in homes.enumerated() {
            let i = Double(n)
            var sx = 0.0, sy = 0.0, sw = 0.0, st = 0.0
            for s in 0..<samples {
                let t0 = stirA + step * Double(s)
                let a = stirPos(t0, geo)
                let b = stirPos(t0 + step, geo)
                let mx = (a.x + b.x) / 2 - home.x
                let my = (a.y + b.y) / 2 - home.y
                let w = exp(-(mx * mx + my * my) / (2 * pushSigma * pushSigma))
                let dx = b.x - a.x
                let dy = b.y - a.y
                sx += dx * w
                sy += dy * w
                let len = hypot(dx, dy) * w
                sw += len
                st += len * (t0 + step / 2)
            }
            let raw = hypot(sx, sy) * pushGain
            let mag = pushMax * (1 - exp(-raw / pushMax))
            let k = raw > 0.001 ? mag / (raw / pushGain) : 0
            let side = hash(i * 5.31 + 2) > 0.22 ? 1.0 : -0.45
            // Water spreads what it carries: a little outward from the middle of the phrase, so letters part.
            let ox = home.x - geo.cx
            let oy = home.y - geo.cy
            let ol = max(1, hypot(ox, oy))
            let spread = (pushSpread + pushSpread * 2 * hash(i * 3.9 + 6)) * min(1, mag / 50)
            out.append(Push(
                px: sx * k + (ox / ol) * spread,
                py: sy * k + (oy / ol) * spread * 0.8,
                tc: sw > 0.001 ? st / sw : (stirA + stirB) / 2,
                spin: side * (0.3 + 0.8 * hash(i * 7.77 + 3)) * min(1, mag / 60)))
        }
        return out
    }

    /// Offset, rotation and scale of letter i at loop time t, relative to its home.
    static func autoOffset(_ t: Double, _ n: Int, _ push: Push) -> Pose {
        let i = Double(n)
        let start = push.tc - pushLead
        if t <= start { return Pose() }
        let h1 = hash(i * 3.17 + 1)
        let h2 = hash(i * 9.13 + 4)
        let ret = returnAt + returnStagger * i
        let tau = min(t, ret) - start
        let e = easeOutCubic(clamp01(tau / pushRise))
        // The carry keeps turning with the vortex; a small eddy rides on it.
        let turn = (0.1 + 0.12 * h1) * tau
        let c = cos(turn), s = sin(turn)
        let eddyR = 5 + 7 * h2
        let eddyA = h1 * Self.tau + (0.9 + 0.7 * h2) * tau
        let ox = e * (push.px * c - push.py * s) + e * eddyR * (cos(eddyA) - cos(h1 * Self.tau))
        let oy = e * (push.px * s + push.py * c) + e * eddyR * (sin(eddyA) - sin(h1 * Self.tau))
        let rot = push.spin * (1 - exp(-tau / 1.4)) + 0.06 * e * sin(1.7 * tau + i)
        let sc = 1 + 0.03 * e * sin(2.1 * tau + i * 1.3)
        if t < ret { return Pose(dx: ox, dy: oy, rot: rot, scale: sc) }

        let k = 1 - springStep(t - ret)
        let a = 0.5 * k
        let ca = cos(a), sa = sin(a)
        return Pose(dx: k * (ox * ca - oy * sa), dy: k * (ox * sa + oy * ca), rot: k * rot, scale: 1 + k * (sc - 1))
    }

    /// The ambient swell under letter i at ambient time a (period LOOP): dy (pt) and tilt (rad).
    static func swellAt(_ a: Double, _ n: Int) -> (dy: Double, tilt: Double) {
        let i = Double(n)
        let p = tau * a / loop
        let f1 = hash(i * 2.11 + 9) * tau
        let f2 = hash(i * 4.57 + 11) * tau
        return (1.3 * sin(p + f1) + 0.7 * sin(2 * p + f2), 0.012 * sin(p + f2))
    }

    /// Rings on the water, as a flat list of `ripples` × [x, y, age (s), strength]; strength 0 = unused.
    static func autoRipples(_ t: Double, _ geo: Geo, on: Bool) -> [Double] {
        var out = [Double](repeating: 0, count: ripples * 4)
        guard on else { return out }
        for k in 0..<ripples {
            let born = rippleT0 + rippleEvery * Double(k)
            let age = t - born
            if age > 0 && age < rippleLife {
                let p = stirPos(born, geo)
                out[k * 4] = p.x
                out[k * 4 + 1] = p.y
                out[k * 4 + 2] = age
                out[k * 4 + 3] = 0.6 + 0.4 * hash(Double(k) * 6.3 + 17)
            }
        }
        return out
    }

    /// Height and slope (divided by the wave number) of the rings at a point.
    static func ripplesAt(_ x: Double, _ y: Double, _ rips: [Double]) -> (h: Double, gx: Double, gy: Double) {
        var h = 0.0, gx = 0.0, gy = 0.0
        for k in 0..<ripples {
            let amp = rips[k * 4 + 3]
            if amp <= 0 { continue }
            let age = rips[k * 4 + 2]
            let dx = x - rips[k * 4]
            let dy = y - rips[k * 4 + 1]
            let d = max(0.001, hypot(dx, dy))
            let off = d - rippleSpeed * age
            if off > rippleWidth * 2.5 || off < -rippleWidth * 2.5 { continue }
            let env = amp * seg(age, 0, 0.15) * pow(1 - clamp01(age / rippleLife), 1.5)
            let g = exp(-(off * off) / (rippleWidth * rippleWidth))
            let sn = sin(off * rippleK), cs = cos(off * rippleK)
            let dh = env * g * (cs - ((2 * off) / (rippleWidth * rippleWidth * rippleK)) * sn)
            h += env * g * sn
            gx += dh * dx / d
            gy += dh * dy / d
        }
        return (h, gx, gy)
    }

    // MARK: - Under a real finger: a small live simulation

    /// Per letter: offset, velocity, rotation and its speed.
    struct Sim {
        var x, y, vx, vy, r, vr: [Double]
        init(_ n: Int) {
            let z = [Double](repeating: 0, count: n)
            x = z; y = z; vx = z; vy = z; r = z; vr = z
        }
    }

    /// The finger: where it is, how fast it moves (pt/s), and whether it is down.
    struct Finger { var x = 0.0, y = 0.0, vx = 0.0, vy = 0.0, on = false }

    private static let fingerSigma = 95.0
    private static let fingerMax = 1800.0
    private static let couple = 9.0
    private static let drag = 2.2
    private static let softK = 1.2
    private static let firmK = 42.0
    private static let edge = 28.0
    private static let edgeK = 30.0

    /// One step of the live water. While stirring the letters follow the water the finger moves (velocity
    /// coupling, Gaussian of 95 pt), drift against drag and feel only a faint pull home; with `settle` the pull
    /// is a critically damped spring. Past the screen's margins a spring pushes back (the rubber band).
    static func stepSim(_ s: inout Sim, _ homes: [Home], _ finger: Finger, _ geo: Geo, _ dt: Double, settle: Bool) {
        let k = settle ? firmK : softK
        let c = settle ? 2 * firmK.squareRoot() : drag
        let sp = hypot(finger.vx, finger.vy)
        let cap = sp > fingerMax ? fingerMax / sp : 1
        let fvx = finger.vx * cap, fvy = finger.vy * cap
        for i in homes.indices {
            let X = homes[i].x + s.x[i]
            let Y = homes[i].y + s.y[i]
            var ax = -k * s.x[i] - c * s.vx[i]
            var ay = -k * s.y[i] - c * s.vy[i]
            var ar = -(settle ? firmK : 0.4) * s.r[i] - (settle ? 2 * firmK.squareRoot() : 1.6) * s.vr[i]
            if finger.on && sp > 20 {
                let dx = X - finger.x
                let dy = Y - finger.y
                let near = exp(-(dx * dx + dy * dy) / (2 * fingerSigma * fingerSigma))
                let pull = couple * (0.04 + near)
                ax += (fvx - s.vx[i]) * pull
                ay += (fvy - s.vy[i]) * pull
                // The water turns around the finger's path: letters beside it are spun by its curl.
                ar += ((dx * fvy - dy * fvx) / 5200) * near
            }
            if !settle {
                if X < edge { ax += (edge - X) * edgeK }
                if X > geo.W - edge { ax -= (X - (geo.W - edge)) * edgeK }
                if Y < edge * 2 { ay += (edge * 2 - Y) * edgeK }
                if Y > geo.H - edge * 2 { ay -= (Y - (geo.H - edge * 2)) * edgeK }
            }
            s.vx[i] += ax * dt
            s.vy[i] += ay * dt
            s.vr[i] += ar * dt
            s.x[i] += s.vx[i] * dt
            s.y[i] += s.vy[i] * dt
            s.r[i] += s.vr[i] * dt
        }
    }

    /// How far from rest the live water is (pt-like units).
    static func simEnergy(_ s: Sim) -> Double {
        var e = 0.0
        for i in s.x.indices {
            e = max(e, abs(s.x[i]) + abs(s.y[i]) + (abs(s.vx[i]) + abs(s.vy[i])) * 0.05 + abs(s.r[i]) * 30)
        }
        return e
    }
}
