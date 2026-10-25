import Foundation

/*
 The abacus: geometry, the scripted loop, and the bead physics under the finger.

 Five rods (Food, Transit, Fun, Home, Other), ten beads each. A bead counts when it sits on the
 right ("logged") side; each rod's bead has a price, so the rod's amount is count × price and the
 header total is their sum ($386.40 at rest).

 autoPlay (loop = 8 s), every bead a pure function of t:
   0.0 – 1.3   rest.
   1.3 – 5.0   six flicks: a fingertip lands on the left side of a bead, pushes it 3 pt (80 ms), and
               flicks: the bead glides right with friction (an exponential profile, 0.3 s), still
               moving when it hits the stack, and bounces back off it (4.5 pt, decaying in ~0.3 s).
               Food ×3 (1.40, 1.95, 2.50), Fun ×2 (3.15, 3.70), Other ×1 (4.35): $386.40 → $469.00.
   5.0 – 6.2   hold on the totals.
   6.0 – 6.25  "Undo today" is pressed.
   6.2 – 7.5   the logged beads glide back (ease-in-out, 0.6 s, rods 120 ms apart) and bounce
               off the left stack.
   7.5 – 8.0   rest.

 Touch: beads slide on their rod with friction (4/s), collide (restitution 0.5, momentum swaps
 between equal masses), bounce off the frame (0.35), and below 45 pt/s a spring settles each to
 the nearer of its two slots. A grabbed bead follows the finger and pushes the beads in front.
 */
enum AbacusExpenseCounterMotion {
    static let loop = 8.0
    static let rods = 5
    static let beads = 10
    static let categories = ["Food", "Transit", "Fun", "Home", "Other"]
    /// Cents per bead, beads logged at rest, and each category's weekly budget in cents.
    static let prices = [1600, 640, 1500, 4500, 460]
    static let restCounts = [6, 5, 4, 4, 4]
    static let budgets = [16000, 6000, 12000, 20000, 4000]
    static let weekBudget = 52000

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(max((t - a) / (b - a), 0), 1)
    }

    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func easeOut(_ x: Double) -> Double {
        1 - pow(1 - x, 3)
    }

    /// [rod, bead, flick time, undo time]
    private static let flicks: [(Int, Int, Double, Double)] = [
        (0, 3, 1.4, 6.2),
        (0, 2, 1.95, 6.2),
        (0, 1, 2.5, 6.2),
        (2, 5, 3.15, 6.32),
        (2, 4, 3.7, 6.32),
        (4, 5, 4.35, 6.44),
    ]
    private static let push = 3.0
    private static let pushFor = 0.08
    private static let glideFor = 0.3
    private static let glideK = 2.2
    private static let undoFor = 0.6

    /// A bounce off a stack: |sin| arcs decaying, 0 → peak → 0, positive.
    private static func bounce(_ tau: Double, _ size: Double) -> Double {
        if tau <= 0 || tau > 0.6 { return 0 }
        return size * exp(-tau / 0.07) * abs(sin(Double.pi * tau / 0.11))
    }

    /// Every bead's x at loop time t.
    static func loopBeads(_ g: AbacusExpenseCounterGeo, _ t: Double, _ out: inout [Double]) {
        g.restBeads(&out)
        for (r, j, tf, tu) in flicks {
            let L = g.slotL(j)
            let R = g.slotR(j)
            var x = L
            if t >= tf && t < tf + pushFor {
                x = L + push * easeOut(seg(t, tf, tf + pushFor))
            } else if t >= tf + pushFor {
                let u = seg(t, tf + pushFor, tf + pushFor + glideFor)
                let f = (1 - exp(-glideK * u)) / (1 - exp(-glideK))
                x = L + push + (R - L - push) * f
                if u >= 1 { x = R - bounce(t - (tf + pushFor + glideFor), 4.5) }
            }
            if t >= tu { x = R - (R - L) * easeInOut(seg(t, tu, tu + undoFor)) + bounce(t - tu - undoFor, 3) }
            out[r * beads + j] = x
        }
    }

    /// The scripted fingertip: (x, y, alpha).
    static func loopFinger(_ g: AbacusExpenseCounterGeo, _ t: Double) -> (Double, Double, Double) {
        for (r, j, tf, _) in flicks {
            if t < tf - 0.26 || t > tf + 0.3 { continue }
            let contactX = g.slotL(j) - g.bw / 2 - 8
            let y = g.rodY[r]
            let inn = easeOut(seg(t, tf - 0.26, tf))
            var x = contactX - 14 * (1 - inn)
            var yy = y - 18 * (1 - inn)
            if t >= tf { x += push * easeOut(seg(t, tf, tf + pushFor)) }
            // The flick: the finger carries on 30 pt to the right and lifts away.
            let o = easeOut(seg(t, tf + pushFor, tf + 0.3))
            x += 30 * o
            yy -= 6 * o
            return (x, yy, inn * (1 - o))
        }
        return (0, 0, 0)
    }

    /// How pressed the "Undo today" chip looks in the loop (0–1).
    static func loopUndoPress(_ t: Double) -> Double {
        easeOut(seg(t, 6.0, 6.08)) * (1 - easeInOut(seg(t, 6.14, 6.3)))
    }

    private static let friction = 4.0
    private static let settleBelow = 45.0
    private static let settleK = 260.0
    private static let restitution = 0.5
    private static let wall = 0.35
    private static let hitSpeed = 60.0

    /// One step of the beads under touch. `grab` is (rod, bead, target x) or nil. Returns the
    /// strongest collision speed of the step (0 = none).
    static func stepBeads(
        _ g: AbacusExpenseCounterGeo, _ x: inout [Double], _ v: inout [Double],
        grab: (Int, Int, Double)?, dt: Double
    ) -> Double {
        var hit = 0.0
        let sub = max(1, Int((dt * 240).rounded(.up)))
        let h = dt / Double(sub)
        let c = 2 * 0.8 * sqrt(settleK)
        for s in 0..<sub {
            for r in 0..<rods {
                let base = r * beads
                let held = grab.map { $0.0 == r ? $0.1 : -1 } ?? -1
                for j in 0..<beads {
                    let i = base + j
                    if j == held, let grab {
                        // Spread the finger's move over the frame's substeps, so the bead's speed is real.
                        let nx = x[i] + (grab.2 - x[i]) / Double(sub - s)
                        v[i] = max(-2500, min(2500, (nx - x[i]) / h))
                        x[i] = nx
                        continue
                    }
                    v[i] *= exp(-friction * h)
                    if abs(v[i]) < settleBelow {
                        let L = g.slotL(j)
                        let R = g.slotR(j)
                        let slot = abs(x[i] - L) < abs(x[i] - R) ? L : R
                        v[i] += (settleK * (slot - x[i]) - c * v[i]) * h
                    }
                    x[i] += v[i] * h
                }
                for _ in 0..<3 {
                    for j in 0..<(beads - 1) {
                        let a = base + j
                        let b = a + 1
                        let overlap = g.bw - (x[b] - x[a])
                        if overlap <= 0 { continue }
                        if j == held {
                            x[b] += overlap
                            if v[b] < v[a] { v[b] = v[a] }
                        } else if j + 1 == held {
                            x[a] -= overlap
                            if v[a] > v[b] { v[a] = v[b] }
                        } else {
                            x[a] -= overlap / 2
                            x[b] += overlap / 2
                            let rel = v[a] - v[b]
                            if rel > 0 {
                                let mean = (v[a] + v[b]) / 2
                                v[a] = mean - restitution * rel / 2
                                v[b] = mean + restitution * rel / 2
                                if rel > hitSpeed && rel > hit { hit = rel }
                            }
                        }
                    }
                }
                // Each bead stays where the frame leaves room for its neighbours; then the chain is
                // straightened both ways, so nothing overlaps and nothing passes through the frame.
                for j in 0..<beads {
                    let i = base + j
                    let lo = g.slotL(j)
                    let hi = g.slotR(j)
                    if x[i] < lo {
                        x[i] = lo
                        if v[i] < 0 {
                            if j == 0 && -v[i] > hitSpeed && -v[i] > hit { hit = -v[i] }
                            v[i] = j == 0 ? -v[i] * wall : 0
                        }
                    } else if x[i] > hi {
                        x[i] = hi
                        if v[i] > 0 {
                            if j == beads - 1 && v[i] > hitSpeed && v[i] > hit { hit = v[i] }
                            v[i] = j == beads - 1 ? -v[i] * wall : 0
                        }
                    }
                }
                for j in 1..<beads where x[base + j] - x[base + j - 1] < g.bw {
                    x[base + j] = x[base + j - 1] + g.bw
                }
                for j in stride(from: beads - 2, through: 0, by: -1) where x[base + j + 1] - x[base + j] < g.bw {
                    x[base + j] = x[base + j + 1] - g.bw
                }
            }
        }
        return hit
    }

    /// Whether every bead has come to rest in a slot.
    static func settled(_ g: AbacusExpenseCounterGeo, _ x: [Double], _ v: [Double]) -> Bool {
        for r in 0..<rods {
            for j in 0..<beads {
                let i = r * beads + j
                if abs(v[i]) > 1 { return false }
                let d = min(abs(x[i] - g.slotL(j)), abs(x[i] - g.slotR(j)))
                if d > 0.3 { return false }
            }
        }
        return true
    }

    /// Logged beads per rod for positions x.
    static func countBeads(_ g: AbacusExpenseCounterGeo, _ x: [Double]) -> [Int] {
        (0..<rods).map { r in
            (0..<beads).reduce(0) { n, j in x[r * beads + j] > (g.slotL(j) + g.slotR(j)) / 2 ? n + 1 : n }
        }
    }
}

struct AbacusExpenseCounterGeo: Equatable {
    let w: Double
    let h: Double
    let boardX: Double
    let boardY: Double
    let boardW: Double
    let boardH: Double
    let frame: Double
    let band: Double
    /// Rod y (bead centres), and the inner ends of the rods.
    let rodY: [Double]
    let innerL: Double
    let innerR: Double
    let bw: Double
    let bh: Double

    init(w: Double, h: Double, boardY: Double, reserveBelow: Double) {
        let rods = AbacusExpenseCounterMotion.rods
        let frame = 16.0
        let band = min(64, max(52, (h - boardY - 2 * frame - reserveBelow) / Double(rods)))
        let bh: Double = band >= 60 ? 30 : 27
        self.w = w
        self.h = h
        self.frame = frame
        self.boardY = boardY
        self.band = band
        self.bh = bh
        boardX = 16
        boardW = w - 32
        innerL = 16 + frame + 4
        innerR = 16 + (w - 32) - frame - 4
        bw = min(26, (innerR - innerL - 66) / Double(AbacusExpenseCounterMotion.beads))
        rodY = (0..<rods).map { r in boardY + frame + Double(r) * band + band - bh / 2 - 6 }
        boardH = 2 * frame + Double(rods) * band
    }

    func slotL(_ j: Int) -> Double {
        innerL + Double(j) * bw + bw / 2
    }

    func slotR(_ j: Int) -> Double {
        innerR - Double(AbacusExpenseCounterMotion.beads - 1 - j) * bw - bw / 2
    }

    /// Rest positions of every bead, rod by rod.
    func restBeads(_ out: inout [Double]) {
        let beads = AbacusExpenseCounterMotion.beads
        if out.count != AbacusExpenseCounterMotion.rods * beads {
            out = Array(repeating: 0, count: AbacusExpenseCounterMotion.rods * beads)
        }
        for r in 0..<AbacusExpenseCounterMotion.rods {
            let first = beads - AbacusExpenseCounterMotion.restCounts[r]
            for j in 0..<beads { out[r * beads + j] = j >= first ? slotR(j) : slotL(j) }
        }
    }

    func rest() -> [Double] {
        var out: [Double] = []
        restBeads(&out)
        return out
    }
}
