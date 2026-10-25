import Foundation

/*
 Music Box Player — the motion. The cylinder's angle is the only clock: pins ride on it, a pin
 that passes the comb's tips plucks its tooth (the tip bends back as the pin nears, then rings,
 decaying), and each pluck lets a note float up. The crank turns 4 times per turn of the cylinder,
 the governor's fan 40 times.

 autoPlay (loop = 8 s):
   0.0 – 1.2   rest: pins at the start of the song, playhead at 0:00.
   1.2 – 6.4   the crank turns: speeds up in 0.4 s, steady, then slows to a stop over the last 1 s
               (the cylinder makes exactly one turn, so the song plays once: 8 bars of 3/4).
   6.15        the last chord; it rings until 7.0.
   7.0 – 7.6   the playhead rolls back to 0:00. 8.0 is the same frame as 0.0.
 */
enum MusicBoxPlayerMotion {
    static let loop = 8.0
    static let teeth = 18
    /// Beats in one turn of the cylinder.
    static let slots = 24.5
    /// Pin angle where the comb's tips are (0 faces the viewer, +90° is the bottom silhouette).
    static let contact = 55 * Double.pi / 180
    static let crankRatio = 4.0
    static let songSeconds = 52
    static let push = 0.2

    /// Beat and tooth of every pin.
    static let pins: [(beat: Double, tooth: Int)] = {
        let melody = [9, 11, 13, 14, 13, 11, 12, 10, 9, 11, 9, 8, 9, 11, 13, 16, 14, 13, 12, 11, 10, 9]
        let bass = [2, 4, 0, 3, 2, 5, 1, 2]
        var out: [(Double, Int)] = []
        for (b, j) in melody.enumerated() { out.append((Double(b), j)) }
        for (k, j) in bass.enumerated() { out.append((Double(k * 3), j)) }
        // The final chord, late in the turn, while the crank slows.
        out.append(contentsOf: [(23, 9), (23, 14), (23, 2)])
        return out
    }()
    static var pinCount: Int { pins.count }

    /// A pin's angle on the cylinder at rest (the cylinder at angle 0).
    static func pinAngle(_ p: Int) -> Double { contact - 2 * .pi * (pins[p].beat + 0.5) / slots }

    private static func smooth(_ x: Double) -> Double {
        let k = min(1, max(0, x))
        return k * k * (3 - 2 * k)
    }

    /// The crank's share of one turn done at loop time t, tabulated (0.01 s).
    static let turns: [Double] = {
        let n = 801
        var v: [Double] = []
        for i in 0..<n {
            let t = Double(i) / Double(n - 1) * loop
            v.append(smooth((t - 1.2) / 0.4) * (1 - smooth((t - 5.4) / 1.0)))
        }
        var s: [Double] = [0]
        for i in 1..<n { s.append(s[i - 1] + (v[i] + v[i - 1]) / 2) }
        let total = s[n - 1]
        return s.map { $0 / total }
    }()

    static func turnsAt(_ t: Double) -> Double {
        let f = min(loop, max(0, t)) / loop * Double(turns.count - 1)
        let i = min(turns.count - 2, Int(floor(f)))
        return turns[i] + (turns[i + 1] - turns[i]) * (f - Double(i))
    }

    /// When each pin plucks in the loop (the inverse of turnsAt).
    static let plucks: [Double] = pins.map { pin in
        let want = (pin.beat + 0.5) / slots
        var i = 0
        while i < turns.count - 1 && turns[i + 1] < want { i += 1 }
        let f = turns[i + 1] - turns[i] > 1e-9 ? (want - turns[i]) / (turns[i + 1] - turns[i]) : 0
        return (Double(i) + f) / Double(turns.count - 1) * loop
    }

    struct Geo: Equatable {
        var w: Double
        var h: Double
        /// Cylinder: left, right, centre y, radius. Comb tips y, base y. Crank hub, arm.
        var cylX0: Double
        var cylX1: Double
        var cylY: Double
        var cylR: Double
        var tipY: Double
        var combY: Double
        var hubX: Double
        var hubY: Double
        var arm: Double
        var fanX: Double
        var fanY: Double
        var barX: Double
        var barY: Double
        var barW: Double
        var s: Double
        var boxX: Double
        var boxW: Double
        var lidTop: Double
        var rimY: Double
        var frontY: Double
        var frontH: Double
        var x0: Double
        var x1: Double
        var panelY: Double
        var panelH: Double
    }

    static func toothX(_ g: Geo, _ j: Int) -> Double {
        let m = 12.0
        return g.cylX0 + m + (g.cylX1 - g.cylX0 - m * 2) * (Double(j) + 0.5) / Double(teeth)
    }

    struct Note { var x, y, scale, alpha, tilt: Double }

    struct Pose {
        /// Cylinder angle (rad), crank angle, fan angle.
        var angle: Double
        var crank: Double
        var fan: Double
        /// Per tooth: tip offset (pt), glow 0..1.
        var tips: [Double]
        var glow: [Double]
        var notes: [Note]
        var progress: Double
    }

    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    static func posMod(_ x: Double, _ k: Double) -> Double {
        let r = x.truncatingRemainder(dividingBy: k)
        return r < 0 ? r + k : r
    }

    /// How far (rad) pin p still has to go to reach the comb at cylinder angle a; in 0..2π.
    static func toGo(_ p: Int, _ a: Double) -> Double {
        posMod(contact - (pinAngle(p) + a), 2 * .pi)
    }

    /// Tooth j rings e seconds after its pluck.
    static func ring(_ j: Int, _ e: Double) -> Double {
        if e < 0 || e > 1.8 { return 0 }
        let f = 7 + Double(teeth - j) * 0.35
        return 2.6 * exp(-e / 0.42) * cos(2 * .pi * f * e)
    }

    /// One floating note, e seconds after the pluck of a tooth at x.
    static func note(_ g: Geo, _ x: Double, _ e: Double, _ seed: Double) -> Note? {
        if e < 0 || e > 1.7 { return nil }
        let y = g.tipY - 14 - 118 * e + 8 * e * e
        let sway = 10 * sin(2 * .pi * (0.7 * e + hash(seed))) * min(1, e * 2)
        let a = min(1, e / 0.12) * (1 - max(0, (e - 1.1) / 0.6))
        return Note(x: x + sway + (hash(seed + 3) - 0.5) * 30 * e, y: y, scale: 0.75 + 0.35 * min(1, e / 0.4), alpha: a, tilt: (hash(seed + 5) - 0.5) * 30)
    }

    /// A frame from the cylinder angle `a` (rad, unwrapped), the crank angle, the time, each tooth's
    /// last pluck (`last[j]`, -1 for none) and the recent plucks (tooth, time).
    static func poseFrom(_ g: Geo, _ a: Double, _ crank: Double, _ now: Double, _ last: [Double], _ recent: [(Int, Double)], _ progress: Double) -> Pose {
        var tips: [Double] = []
        var glow: [Double] = []
        tips.reserveCapacity(teeth)
        glow.reserveCapacity(teeth)
        for j in 0..<teeth {
            let e = last[j] >= 0 ? now - last[j] : -1
            var off = ring(j, e)
            // A pin closing in pushes the tip away from the cylinder before it lets go.
            for p in 0..<pinCount where pins[p].tooth == j {
                let d = toGo(p, a)
                if d < push { off += 3 * (1 - d / push) }
            }
            tips.append(off)
            glow.append(e >= 0 && e < 1.5 ? exp(-e / 0.5) : 0)
        }
        var notes: [Note] = []
        for (j, at) in recent {
            if let n = note(g, toothX(g, j), now - at, Double(j) * 7 + at * 3.1) { notes.append(n) }
        }
        return Pose(angle: a, crank: crank, fan: a * 40, tips: tips, glow: glow, notes: notes, progress: progress)
    }

    static func loopPose(_ g: Geo, _ t: Double) -> Pose {
        let tr = turnsAt(t)
        let a = tr * 2 * .pi
        var last = [Double](repeating: -1, count: teeth)
        var recent: [(Int, Double)] = []
        for p in 0..<pinCount {
            let tp = plucks[p]
            if tp <= t {
                let j = pins[p].tooth
                if tp > last[j] { last[j] = tp }
                if t - tp < 1.7 { recent.append((j, tp)) }
            }
        }
        let back = min(1, max(0, (t - 7.0) / 0.6))
        let progress = tr * (1 - back * back * (3 - 2 * back))
        return poseFrom(g, a, a * crankRatio, t, last, recent, progress)
    }

    /// Pins that pass the comb between cylinder angles a0 and a1 (a1 > a0).
    static func passed(_ a0: Double, _ a1: Double) -> [Int] {
        (0..<pinCount).filter { p in
            let d = toGo(p, a0)
            return d > 0 && d <= a1 - a0
        }
    }
}
