import Foundation

/*
 Tablecloth Pull Trick — the autoPlay timeline (loop = 9 s) as one pose per time, a port of
 clothMotion.ts.

   0.0 – 1.4   rest: the cloth lies on the table, its hem hanging over the front edge in soft folds,
               swaying a little (whole-loop sines).
   1.4 – 1.75  grab: an anticipation tug (the hem pulled 10 pt, eased back to 2 pt).
   1.75 – 2.3  the yank (0.55 s): the pull runs as u^1.6 until the whole cloth is over the edge; a
               26 pt wave travels through it toward the front, the hanging part whips down and
               toward the viewer, ghost copies and speed streaks trail it. As its back edge passes
               under each dish, that dish hops 8–12 pt and then jiggles (a damped 5 Hz sine).
   2.6 – 5.0   a "Clean pull" badge; the counter rolls 3 → 4 (2.6 – 2.9).
   5.0 – 7.4   the cloth is thrown back on: it billows down from above the table (from 480 pt,
               landing at 6.4 s; while above the dishes it is drawn over them), the hem swings down
               over the edge (6.1 – 6.7), the air deflates with a settle; the dishes ease home
               (5.2 – 7.0); the counter rolls back (7.4 – 7.7).
   7.4 – 9.0   rest.
 */

struct TableclothPullTrickPose {
    /// Pull (pt), its speed (pt/s), ripple amplitude (pt), stream of the hanging part (0 … 1).
    var d = 0.0
    var speed = 0.0
    var wave = 0.0
    var fling = 0.0
    /// Throw-back: height above the table, shift toward the back, air under the middle, hem angle.
    var lift = 0.0
    var back = 0.0
    var air = 0.0
    var hang = 1.0
    /// Common dish drift toward the viewer (pt); the yank's age (s, < 0 = none), start, strength.
    var slide = 0.0
    var yankAge = -1.0
    var yankFrom = 0.0
    var jig = 0.0
    /// Common glass lean (degrees), counter value (rolls), badge (0 … 1), loop time for idle sways.
    var tip = 0.0
    var count = 3.0
    var badge = 0.0
    var time = 0.0
}

enum TableclothPullTrickMotion {
    static let loop = 9.0
    /// Table depth (world pt), the hem's drop below the edge, and the cloth's whole length.
    static let depth = 320.0
    static let drop = 92.0
    static let clothLen = depth + drop
    /// Pull at which the whole cloth has left the screen; the yank's length (s).
    static let out = clothLen + 300
    static let yank = 0.55
    /// Length of a throw-back (s).
    static let throwBackTime = 2.4

    static func clamp(_ v: Double, _ a: Double, _ b: Double) -> Double { min(max(v, a), b) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }
    static func easeOut(_ x: Double) -> Double { 1 - (1 - x) * (1 - x) * (1 - x) }

    static func easeInOut(_ x: Double) -> Double {
        if x < 0.5 { return 4 * x * x * x }
        let y = -2 * x + 2
        return 1 - y * y * y / 2
    }

    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// A bell 0 → 1 → 0 over [a, b].
    static func bell(_ t: Double, _ a: Double, _ b: Double) -> Double { sin(.pi * seg(t, a, b)) }

    /// A damped sine after s ≥ 0, windowed to exactly zero at `span`.
    static func ring(_ s: Double, _ decay: Double, _ w: Double, _ span: Double) -> Double {
        if s <= 0 || s >= span { return 0 }
        let k = 1 - s / span
        return exp(-decay * s) * sin(w * s) * k * k
    }

    /// The pull during a yank that started at `from`, `age` seconds ago: (d, speed).
    static func yankPull(_ from: Double, _ age: Double) -> (Double, Double) {
        let u = clamp(age / yank, 0, 1)
        let d = from + (out - from) * pow(u, 1.6)
        let speed = u > 0 && u < 1 ? (out - from) * 1.6 * pow(u, 0.6) / yank : 0
        return (d, speed)
    }

    /// Seconds into a yank (from `from`) when the cloth's back edge passes under table depth y.
    static func passTime(_ y: Double, _ from: Double) -> Double {
        let need = clamp((depth - y + 24 - from) / (out - from), 0, 1)
        return yank * pow(need, 1 / 1.6)
    }

    /// The cloth thrown back on, r seconds into the throw-back.
    static func throwBack(_ r: Double) -> (lift: Double, back: Double, air: Double, hang: Double, wave: Double) {
        let fall = easeInOut(seg(r, 0, 1.4))
        let air = 42 * (0.55 + 0.45 * sin(.pi * seg(r, 0, 0.9))) * (1 - seg(r, 1.0, 1.55)) + 10 * ring(r - 1.4, 4, 11, 1.0)
        let hang = 0.38 + 0.62 * easeOut(seg(r, 1.1, 1.7)) + 0.1 * ring(r - 1.7, 5, 12, 0.7)
        return (480 * (1 - fall), 150 * (1 - fall), air, hang, 16 * sin(.pi * seg(r, 0, 1.4)))
    }

    static func restPose(_ time: Double, _ count: Double) -> TableclothPullTrickPose {
        var p = TableclothPullTrickPose()
        p.count = count
        p.time = time
        return p
    }

    static func autoPose(_ t: Double) -> TableclothPullTrickPose {
        var p = restPose(t, 3)
        if t >= 1.4 && t < 1.75 {
            p.d = 10 * easeOut(seg(t, 1.4, 1.62)) - 8 * easeInOut(seg(t, 1.62, 1.75))
        } else if t >= 1.75 && t < 5.0 {
            let y = yankPull(2, t - 1.75)
            p.d = y.0
            p.speed = y.1
        } else if t >= 5.0 && t < 5.0 + throwBackTime {
            let b = throwBack(t - 5.0)
            p.lift = b.lift
            p.back = b.back
            p.air = b.air
            p.hang = b.hang
            p.wave = b.wave
        }
        if t >= 1.75 && t < 2.6 {
            p.wave = 26 * bell(t, 1.78, 2.45)
            p.fling = 0.85 * bell(t, 1.85, 2.6)
        }
        if t >= 1.75 && t < 4.0 {
            p.yankAge = t - 1.75
            p.yankFrom = 2
            p.jig = 1
        }
        p.slide = 4 * easeOut(seg(t, 1.8, 2.2)) * (1 - easeInOut(seg(t, 5.2, 7.0)))
        p.count = 3 + easeInOut(seg(t, 2.6, 2.9)) - easeInOut(seg(t, 7.4, 7.7))
        p.badge = easeOut(seg(t, 2.6, 2.9)) * (1 - easeInOut(seg(t, 4.6, 5.0)))
        return p
    }

    /// A dish's yank response at table depth y: (dx, dy, spin (rad), lean (deg), hop (pt)).
    static func jiggle(_ p: TableclothPullTrickPose, _ i: Int, _ y: Double) -> (Double, Double, Double, Double, Double) {
        if p.yankAge < 0 || p.jig <= 0 { return (0, 0, 0, 0, 0) }
        let a = p.yankAge - passTime(y, p.yankFrom)
        if a <= 0 || a >= 1.4 { return (0, 0, 0, 0, 0) }
        let h1 = hash(Double(i + 1))
        let h2 = hash(Double(i + 7))
        let k = 1 - a / 1.4
        let env = exp(-3.2 * a) * k * k * p.jig
        let w = 2 * Double.pi * (4.6 + h1 * 1.2) * a
        let hop = (8 + 4 * h2) * sin(.pi * clamp(a / 0.22, 0, 1)) * min(p.jig, 1.4)
        return (
            2.4 * (h1 - 0.5) * env * sin(w + h2 * 6),
            -3 * h2 * env * abs(sin(w * 0.5)),
            0.12 * (h2 - 0.5) * env * sin(w * 0.8 + 1),
            9 * (0.6 + h1 * 0.4) * env * sin(w + h2 * 3),
            hop
        )
    }
}
