import Foundation

/*
 Matchstick Strike Button — timing and the match's physics. Every frame of the self-playing loop
 is a pure function of the loop time t; under a finger a small simulation (Sim) is stepped from
 the timeline and read through the same Pose.

 loop = 8 s:
   0.0 – 1.3   rest (the poster: a fresh match lying above the box, "Strike to start" printed in it)
   1.3 – 1.7   the match lifts and moves to the right end of the striker (it pulls back 8 pt at 1.62)
   1.7 – 2.2   strike: the head drags right → left across the strip, sparks fly back and fall
   2.2 – 2.6   flare: a fireball of 1.8× size, a sulphur puff, then a steady teardrop flame
   2.6 – 2.9   the match swings down under the CTA      2.9 – 4.4  it glides under the letters,
               which ignite left → right as the flame passes (white flash, then a gold glow)
   4.4 – 5.8   hold: the flame flickers, the whole screen warms
   5.8 – 6.15  a gust: the flame leans hard and goes out; smoke curls up from 5.95
   6.0 – 6.9   the letters cool back to print       6.8 – 7.2  the burnt match goes back to rest
   7.2 – 7.6   it drops behind the box                7.6 – 8.0  a fresh one rises from the box
*/
enum MatchstickStrikeButtonMotion {
    static let loop = 8.0
    static let idleBeforeBack = 2.0
    static let backFor = 0.9
    /// A swipe on the strip lights the match above this speed (pt/s) once it has run this far (pt).
    static let strikeSpeed = 650.0
    static let strikeRun = 56.0
    static let maxSparks = 32

    /// Layout the motion needs, in screen points.
    struct Geo: Equatable {
        var rest: CGPoint
        var strikeFrom: CGPoint
        var strikeTo: CGPoint
        var glideFrom: CGPoint
        var glideTo: CGPoint
        var strikerTop: Double
        var strikerBottom: Double
        var strikerLeft: Double
        var strikerRight: Double
        var boxTop: Double
        /// Letter centres (x) and the CTA's baseline.
        var letters: [Double]
        var baseline: Double
        var matchLength: Double
    }

    struct Match {
        var x = 0.0
        var y = 0.0
        /// Direction from the head to the tail, degrees (0 = tail to the right).
        var angle = 0.0
        /// 0 lying on the table … 1 held up (bigger, softer shadow).
        var lift = 0.0
        /// 0 fresh red head … 1 charred head.
        var burnt = 0.0
        /// How far the stick has charred below the head, 0 … 1.
        var char = 0.0
        var alpha = 1.0
        /// 1 = drawn over the box, 0 = behind it.
        var front = 1.0
    }

    struct Spark {
        var x: Double, y: Double, vx: Double, vy: Double, age: Double, life: Double
    }

    /// A column of smoke: where it starts, its age and strength.
    struct Smoke {
        var x = 0.0, y = 0.0, age = -1.0, strength = 0.0
    }

    struct Pose {
        var match = Match()
        /// A fresh match fading in at rest (only while easing back after a touch).
        var spare = 0.0
        /// Flame: size 0 … 1, flare 0 … 1, lean -1 … 1.
        var lit = 0.0
        var flare = 0.0
        var lean = 0.0
        /// Warm light over the scene.
        var light = 0.0
        var sparks: [Spark] = []
        /// Smoke: the sulphur puff, then the blown-out curl.
        var puff = Smoke()
        var curl = Smoke()
        /// Heat per letter: 0 printed, 1 glowing gold, above 1 the white flash of catching.
        var heat: [Double] = []
        /// A scrape on the strip: x from, x to, darkness.
        var scrape = (0.0, 0.0, 0.0)
        /// Flicker phase (radians).
        var phase = 0.0
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
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func hash(_ n: Double) -> Double {
        let s = sin(n * 12.9898 + 4.1414) * 43758.5453
        return s - floor(s)
    }

    /// Where the head is during the loop (no lean or flame, just the path).
    static func headAt(_ t: Double, _ g: Geo) -> (x: Double, y: Double, angle: Double, lift: Double) {
        if t < 1.3 { return (g.rest.x, g.rest.y, 0, 0) }
        if t < 1.7 {
            // Lift to the strip's right end, with a short pull-back before the strike (anticipation).
            let k = easeInOut(seg(t, 1.3, 1.62))
            let back = sin(.pi * seg(t, 1.56, 1.7)) * 8
            return (mix(g.rest.x, g.strikeFrom.x, k) + back, mix(g.rest.y, g.strikeFrom.y, k) - sin(.pi * k) * 26, mix(0, -22, k), k)
        }
        if t < 2.2 {
            let k = easeInOut(seg(t, 1.7, 2.2))
            return (mix(g.strikeFrom.x, g.strikeTo.x, k), g.strikeFrom.y + sin(.pi * k) * 1.5, -22, 1)
        }
        if t < 2.6 {
            // The flare pulls the match up off the strip.
            let k = easeOut(seg(t, 2.2, 2.6))
            return (g.strikeTo.x, g.strikeTo.y - 14 * k, -22 - 6 * k, 1)
        }
        if t < 2.9 {
            let k = easeInOut(seg(t, 2.6, 2.9))
            return (mix(g.strikeTo.x, g.glideFrom.x, k), mix(g.strikeTo.y - 14, g.glideFrom.y, k), mix(-28, 28, k), 1)
        }
        if t < 6.8 {
            let k = easeInOut(seg(t, 2.9, 4.4))
            return (mix(g.glideFrom.x, g.glideTo.x, k), g.glideFrom.y - sin(.pi * k) * 4, 28, 1)
        }
        if t < 7.2 {
            let k = easeInOut(seg(t, 6.8, 7.2))
            return (mix(g.glideTo.x, g.rest.x, k), mix(g.glideTo.y, g.rest.y, k) - sin(.pi * k) * 30, mix(28, 0, k), 1 - k)
        }
        if t < 7.6 {
            let k = easeInOut(seg(t, 7.2, 7.6))
            return (g.rest.x, g.rest.y + 74 * k, 0, 0)
        }
        let k = easeOut(seg(t, 7.6, 8.0))
        return (g.rest.x, g.rest.y + 74 * (1 - k), 0, 0)
    }

    /// When each letter catches in the loop: the head passes under its centre.
    static func igniteTimes(_ g: Geo) -> [Double] {
        g.letters.map { x in
            var lo = 2.9, hi = 4.4
            for _ in 0..<24 {
                let m = (lo + hi) / 2
                if headAt(m, g).x < x - 4 { lo = m } else { hi = m }
            }
            return hi
        }
    }

    /// A letter's heat from the moment it caught (and, when cooling, the moment it started to).
    static func heatOf(_ age: Double, _ coolAge: Double) -> Double {
        if age < 0 { return 0 }
        let h = easeOut(age / 0.22) + exp(-age * 7) * 0.6 * clamp01(age / 0.04)
        return coolAge < 0 ? h : h * (1 - easeInOut(coolAge / 0.8))
    }

    static let sparksAuto = 26

    /// The sparks the loop's strike throws: each a pure function of t and its index.
    private static func loopSparks(_ t: Double, _ g: Geo) -> [Spark] {
        if t < 1.7 || t > 3.0 { return [] }
        var out: [Spark] = []
        for i in 0..<sparksAuto {
            let fi = Double(i)
            let born = 1.78 + 0.4 * fi / Double(sparksAuto)
            let life = 0.28 + 0.3 * hash(fi * 3.1)
            let age = t - born
            if age < 0 || age > life { continue }
            let at = headAt(born, g)
            // Thrown back against the stroke (to the right) and up, then falling.
            let ang = (-150 + 120 * hash(fi + 7.7)) * .pi / 180
            let sp = 140 + 300 * hash(fi * 1.9 + 2.2)
            out.append(Spark(x: at.x - 6, y: at.y, vx: -cos(ang) * sp, vy: sin(ang) * sp, age: age, life: life))
        }
        return out
    }

    /// The pose of the self-playing loop at loop time t.
    static func loopPose(_ t: Double, _ g: Geo, _ ignite: [Double]) -> Pose {
        let h = headAt(t, g)
        // Lean against the motion: a finite difference of the path (it is smooth).
        let h2 = headAt(max(0, t - 0.04), g)
        var lean = max(-1, min(1, -(h.x - h2.x) / 0.04 / 520))
        let flare = t > 2.12 && t < 2.8 ? exp(-pow((t - 2.26) / 0.13, 2)) : 0
        var lit = easeOut(seg(t, 2.14, 2.5))
        let out = seg(t, 5.8, 6.15)
        lit *= 1 - out * out
        lean = lean * (1 - out) - out * 0.9
        if t >= 6.15 { lit = 0 }
        var p = Pose()
        p.heat = ignite.indices.map { heatOf(t - ignite[$0], t - (6.0 + 0.03 * Double($0))) }
        p.sparks = loopSparks(t, g)
        let burnt = t < 7.6 ? easeOut(seg(t, 2.2, 2.7)) : 0
        let char = t < 7.6 ? seg(t, 2.6, 6.0) : 0
        p.match = Match(x: h.x, y: h.y, angle: h.angle, lift: h.lift, burnt: burnt, char: char, alpha: 1, front: t >= 7.2 && t < 8 ? 0 : 1)
        p.lit = lit
        p.flare = flare
        p.lean = lean
        p.light = lit * 0.85 + flare * 0.9
        p.puff = Smoke(x: g.strikeTo.x - 2, y: g.strikeTo.y - 6, age: t - 2.2, strength: t > 2.2 ? 1 - seg(t, 2.6, 3.8) : 0)
        p.curl = Smoke(x: g.glideTo.x, y: g.glideTo.y - 8, age: t - 5.95, strength: t > 5.95 ? 1 - seg(t, 7.0, 7.8) : 0)
        p.scrape = (g.strikeTo.x, g.strikeFrom.x, t > 1.75 && t < 7.6 ? 0.45 * seg(t, 1.75, 2.2) * (1 - seg(t, 6.4, 7.6)) : 0)
        p.phase = 2 * .pi * t / loop
        return p
    }
}

/// The finger's simulation, stepped from the timeline.
final class MatchstickStrikeButtonSim {
    typealias M = MatchstickStrikeButtonMotion

    var x = 0.0, y = 0.0, angle = 0.0, lift = 0.0, burnt = 0.0
    var litAt = -1.0, outAt = -1.0
    var lean = 0.0, vx = 0.0, run = 0.0
    var sparks: [M.Spark] = []
    /// x from, x to, time of the scrape.
    var scrape = (0.0, 0.0, -99.0)
    var ignite: [Double]
    var cool = -1.0
    var backAt = -1.0
    var back: M.Match? = nil
    var phase = 0.0
    /// Where the match caught (the sulphur puff rises from there).
    var px = 0.0, py = 0.0

    init(letters: Int) {
        ignite = [Double](repeating: -1, count: letters)
    }

    /// The pose under (or after) a finger at clock time `now`.
    func pose(_ now: Double, _ g: M.Geo) -> M.Pose {
        var lit = 0.0, flare = 0.0
        if litAt >= 0 {
            let a = now - litAt
            lit = M.easeOut(a / 0.36)
            flare = exp(-pow((a - 0.12) / 0.13, 2))
        }
        var lean = self.lean
        if outAt >= 0 {
            let o = M.seg(now, outAt, outAt + 0.3)
            lit *= 1 - o * o
            lean = lean * (1 - o) - 0.9 * o
            flare = 0
        }
        var p = M.Pose()
        p.heat = ignite.map { $0 < 0 ? 0 : M.heatOf(now - $0, cool < 0 ? -1 : now - cool) }
        p.sparks = sparks.compactMap { s in
            let age = now - s.age
            return age >= 0 && age < s.life ? M.Spark(x: s.x, y: s.y, vx: s.vx, vy: s.vy, age: age, life: s.life) : nil
        }
        var match = M.Match(x: x, y: y, angle: angle, lift: lift, burnt: burnt, char: litAt >= 0 ? M.clamp01((now - litAt) / 3.4) : 0, alpha: 1, front: 1)
        var spare = 0.0
        if backAt >= 0, let b = back {
            // Easing back: the used match drifts toward rest and fades, a fresh one fades in there.
            let k = M.seg(now, backAt, backAt + M.backFor)
            let m = M.easeInOut(M.seg(now, backAt + 0.2, backAt + 0.7))
            match = b
            match.x = M.mix(b.x, g.rest.x, m)
            match.y = M.mix(b.y, g.rest.y, m)
            match.angle = M.mix(b.angle, 0, m)
            match.lift = b.lift * (1 - m)
            match.alpha = 1 - m
            spare = M.easeInOut(M.seg(k, 0.5, 1))
        }
        let sa = litAt >= 0 ? now - litAt : -1
        let oa = outAt >= 0 ? now - outAt : -1
        p.match = match
        p.spare = spare
        p.lit = lit
        p.flare = flare
        p.lean = lean
        p.light = lit * 0.85 + flare * 0.9
        p.puff = M.Smoke(x: px, y: py, age: sa, strength: sa >= 0 ? 1 - M.seg(sa, 0.4, 1.6) : 0)
        p.curl = M.Smoke(x: back?.x ?? match.x, y: (back?.y ?? match.y) - 8, age: oa, strength: oa >= 0 ? 1 - M.seg(oa, 0.9, 1.6) : 0)
        p.scrape = (scrape.0, scrape.1, scrape.2 < -50 ? 0 : 0.55 * (1 - M.seg(now, scrape.2 + 0.5, scrape.2 + 3)))
        p.phase = phase
        return p
    }

    func addSpark(_ x: Double, _ y: Double, _ dir: Double, _ at: Double) {
        let n = Double(sparks.count)
        let ang = (-150 + 120 * M.hash(n + at * 7.3)) * .pi / 180
        let sp = 140 + 300 * M.hash(n * 1.9 + at)
        sparks.append(M.Spark(x: x, y: y, vx: -dir * cos(ang) * sp, vy: sin(ang) * sp, age: at, life: 0.28 + 0.3 * M.hash(n * 3.1 + at)))
        if sparks.count > M.maxSparks { sparks.removeFirst() }
    }
}

/// The mode of the block and the clock: the loop plays, a finger plays, or the screen eases back.
final class MatchstickStrikeButtonEngine {
    typealias M = MatchstickStrikeButtonMotion

    enum Mode { case auto, touch, back }

    var mode = Mode.auto
    var loopStart: Double? = nil
    var lastFrame: Double? = nil
    var lastTouch = 0.0
    var finger = false
    var sim = MatchstickStrikeButtonSim(letters: 15)
    /// The finger's grab offset from the head, and the last drag sample (for the stroke speed).
    var grab = (0.0, 0.0)
    var lastDrag: (x: Double, t: Double)? = nil
    var lastPose = M.Pose()

    func pose(at now: Double, geo: M.Geo, ignite: [Double], auto: Bool) -> M.Pose {
        if loopStart == nil { loopStart = now }
        let gap = lastFrame.map { now - $0 } ?? 0
        // A stalled frame (or a paused timeline) holds the loop where it was instead of skipping.
        if gap > 0.25, mode == .auto, let s = loopStart { loopStart = s + gap }
        let dt = min(max(gap, 0), 1.0 / 20)
        lastFrame = now
        if mode != .auto {
            let s = sim
            // Held, the match rises off the table and turns: tail up while striking, down once lit.
            let lit = s.litAt >= 0 && s.outAt < 0
            let lift = finger || s.lift > 0.01 ? 1.0 : 0.0
            s.lift += (lift - s.lift) * (1 - exp(-dt * 10))
            let angle = lit ? 28.0 : -22.0
            s.angle += (angle - s.angle) * (1 - exp(-dt * 6))
            s.vx *= exp(-dt * 6)
            let lean = max(-1, min(1, -s.vx / 520))
            s.lean += (lean - s.lean) * (1 - exp(-dt * 12))
            if s.litAt >= 0 { s.burnt = M.easeOut((now - s.litAt) / 0.4) }
            s.phase = 2 * .pi * now / M.loop
            if mode == .touch && !finger && now - lastTouch > M.idleBeforeBack {
                mode = .back
                s.backAt = now
                s.back = s.pose(now, geo).match
                if s.litAt >= 0 && s.outAt < 0 { s.outAt = now }
                s.cool = now
            } else if mode == .back && now >= s.backAt + M.backFor {
                sim = MatchstickStrikeButtonSim(letters: 15)
                mode = .auto
                loopStart = now
            }
        }
        let p: M.Pose
        if mode != .auto {
            p = sim.pose(now, geo)
        } else if !auto {
            p = M.loopPose(0, geo, ignite)
        } else {
            p = M.loopPose(max(0, (now - (loopStart ?? now)).truncatingRemainder(dividingBy: M.loop)), geo, ignite)
        }
        lastPose = p
        return p
    }

    /// Hands whatever the loop shows to the simulation, so a finger never makes anything jump.
    func takeOver(_ at: Double, _ g: M.Geo) {
        let p = lastPose
        let s = MatchstickStrikeButtonSim(letters: 15)
        if mode == .auto && p.match.front > 0.5 && p.match.alpha > 0.99 {
            s.x = p.match.x
            s.y = p.match.y
            s.angle = p.match.angle
            s.lift = p.match.lift
            s.burnt = p.match.burnt
            if p.lit > 0.05 {
                s.litAt = at - 1
                s.px = p.match.x
                s.py = p.match.y
            } else if p.match.burnt > 0.5 {
                // A spent match: swap it for a fresh one at rest.
                s.x = g.rest.x
                s.y = g.rest.y
                s.angle = 0
                s.lift = 0
                s.burnt = 0
            }
            for i in 0..<min(p.heat.count, s.ignite.count) where p.heat[i] > 0.02 { s.ignite[i] = at - 0.5 }
            if p.lit < 0.05 && p.heat.contains(where: { $0 > 0.02 }) { s.cool = at }
        } else if mode == .touch {
            return
        } else {
            s.x = g.rest.x
            s.y = g.rest.y
        }
        sim = s
        mode = .touch
    }
}
