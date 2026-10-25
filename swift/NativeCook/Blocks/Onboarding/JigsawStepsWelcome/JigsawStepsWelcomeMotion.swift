import Foundation

/*
 Jigsaw Steps Welcome — the autoPlay timeline (loop = 10 s), pure functions of t.

   0.0 – 1.4   rest: twelve pieces scattered under the board, tilted up to ±26°.
   1.4 – 2.8   step 1, the corners: four pieces fly home one after another (0.2 s apart, 0.75 s
               each): lifted (scale up to 1.07, a 28 pt arc, the shadow growing and softening),
               ease-in-out across, the tilt unwinding with a 12 % overshoot, a 3 % squash as each
               lands, and a light running round its edges (0.35 s).
   3.2 – 4.6   step 2, the outer middles.          5.0 – 6.4   step 3, the middle row.
   6.4 – 7.2   the cut lines fade out and the photo is whole.
   7.0 – 8.2   the photo comes alive: a slow push-in (Ken Burns, 1 → 1.06) and a warm gleam.
   8.05 – 8.25 the whole photo hands back to the pieces, and 8.2 – 9.6 they scatter back to the
               pile (0.06 s apart, 0.7 s each, a lower arc).
   9.6 – 10    rest. The text above the button rolls to each step as it begins.
 */
enum JigsawStepsWelcomeMotion {
    static let loop: Double = 10
    static let stepAt: [Double] = [1.4, 3.2, 5.0]
    static let flight: Double = 0.75
    static let stagger: Double = 0.2
    static let seams = (6.4, 7.2)
    static let burns = (7.0, 8.2)
    static let handback = (8.05, 8.25)
    static let scatterAt: Double = 8.2
    static let scatterGap: Double = 0.06
    static let scatterLen: Double = 0.7
    static let arc: Double = 28
    static let count = JigsawStepsWelcomeLayout.count
    static let steps = JigsawStepsWelcomeLayout.steps

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let u = clamp01((x - a) / (b - a))
        return u * u * (3 - 2 * u)
    }
    static func easeInOutCubic(_ u: Double) -> Double {
        if u < 0.5 { return 4 * u * u * u }
        let v = -2 * u + 2
        return 1 - v * v * v / 2
    }
    static func easeOutBack(_ u: Double, _ c1: Double) -> Double {
        let c3 = c1 + 1, v = u - 1
        return 1 + c3 * v * v * v + c1 * v * v
    }

    /// When piece i starts its flight home: (step, order in step, start time).
    static func flight(of i: Int) -> (step: Int, order: Int, start: Double) {
        for (k, s) in steps.enumerated() {
            if let j = s.firstIndex(of: i) { return (k, j, stepAt[k] + Double(j) * stagger) }
        }
        return (0, 0, 0)
    }

    /// The order pieces were placed in (0…11), so the last placed leaves first.
    static func placeOrder(_ i: Int) -> Int {
        let f = flight(of: i)
        return f.step * 4 + f.order
    }

    struct Piece {
        var x: Double, y: Double, rot: Double, scale: Double
        var lift: Double
        var flash: Double
        var groove: Double
        /// 1 while it sits in the board (no edge, no shadow).
        var onBoard: Double
    }

    /// Piece i at loop time t, given its home and its scattered rest pose.
    static func piece(_ i: Int, _ t: Double, home: (Double, Double), scatter: (Double, Double, Double)) -> Piece {
        let f = flight(of: i)
        let u = seg(t, f.start, f.start + flight)
        let back = scatterAt + Double(count - 1 - placeOrder(i)) * scatterGap
        let v = seg(t, back, back + scatterLen)
        let e = easeInOutCubic(u) * (1 - easeInOutCubic(v))
        let x = scatter.0 + (home.0 - scatter.0) * e
        var y = scatter.1 + (home.1 - scatter.1) * e
        y -= arc * sin(.pi * u) + arc * 0.7 * sin(.pi * v)
        let rot = scatter.2 * (1 - easeOutBack(u, 1.7)) + scatter.2 * easeInOutCubic(v)
        let lift = sin(.pi * u) + sin(.pi * v)
        let landed = f.start + flight
        let squash = t > landed ? 0.03 * sin(.pi * seg(t, landed, landed + 0.18)) : 0
        let flash = t >= landed && t < landed + 0.35 ? 1 - seg(t, landed, landed + 0.35) : 0
        let groove = u >= 1 && v <= 0 ? 1 - smoothstep(seams.0, seams.1, t) : 0
        let on: Double = groove > 0 || (t > 6.4 && t < 8.2) ? 1 : 0
        return Piece(x: x, y: y, rot: rot, scale: 1 + 0.07 * lift - squash, lift: lift, flash: flash, groove: groove, onBoard: on)
    }

    /// The whole photo laid over the finished board: (opacity, Ken Burns scale, gleam position 0…1).
    static func whole(_ t: Double) -> (Double, Double, Double) {
        let a = smoothstep(seams.0, seams.1, t) * (1 - seg(t, handback.0, handback.1))
        let k = easeInOutCubic(seg(t, burns.0, burns.1))
        return (a, 1 + 0.06 * k, seg(t, 7.1, 8.0))
    }

    /// The text's roll position: 0 before the first step, k while step k plays, back to 0 at the end.
    static func textRoll(_ t: Double) -> Double {
        var r = 0.0
        for a in stepAt { r += smoothstep(a, a + 0.45, t) }
        return r - Double(stepAt.count) * smoothstep(scatterAt, scatterAt + 0.55, t)
    }

    /// Which piece flies in overlay slot j (drawn above everything) at t, or −1.
    static func slotPiece(_ j: Int, _ t: Double) -> Int {
        for (k, s) in steps.enumerated() {
            let a = stepAt[k] + Double(j) * stagger
            if t >= a && t < a + flight + 0.3 { return s[j] }
        }
        return -1
    }
}
