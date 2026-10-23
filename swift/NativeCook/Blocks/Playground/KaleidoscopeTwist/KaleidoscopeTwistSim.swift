import Foundation

// The object cell of the kaleidoscope: glass beads and shards loose in a round chamber (a port of
// the React Native block's kaleidoscopeSim.ts, step for step).
//
// The chamber turns with the user's hand (or the script), so gravity, seen from inside it, turns
// the other way and the pieces tumble. Each piece is a circle for the physics: gravity, a little
// air drag, then pairwise contacts (overlap pushed apart, an impulse with some bounce and friction)
// and the chamber's round wall, over `substeps` fixed substeps per tick. Shards also spin as they
// roll. Everything is plain arithmetic with a seeded random, so it runs the same, step for step, on
// any platform. The same clock drives the self-playing script: the turn, two shakes, the number of
// mirror segments and the iris that closes over the seam of the loop.

enum KaleidoscopeTwistConst {
    // One tick is 1/60 s; a slow frame runs at most `maxTicks` of them (a 50 ms cap).
    static let tick = 1.0 / 60
    static let maxTicks = 3
    static let substeps = 4
    static let passes = 2

    // The chamber's radius, in cell units (drawn about 1:1 with pt).
    static let cellR = 120.0
    // Gravity (pt/s²), air drag (1/s), bounce between pieces and on the wall, and friction. The
    // wall grips hard, so the pile is carried up the side as the chamber turns and then tumbles.
    static let gravity = 820.0
    static let air = 0.7
    static let bounce = 0.28
    static let wallBounce = 0.22
    static let friction = 0.3
    static let wallGrip = 0.3
    // Spin follows rolling at this rate (1/s).
    static let rollRate = 14.0

    // Pieces at the start, and the most a hand can add (the oldest added one goes first).
    static let startPieces = 52
    static let maxPieces = 64
    // A tapped bead grows in over this long (s).
    static let growTime = 0.35

    // Kinds of piece: a round glass bead, a flat shard, a tiny seed bead.
    static let bead = 0
    static let shard = 1
    static let seed = 2
    // Jewel tones the pieces are made in.
    static let tones = 8

    // The turn: free spin slows by this rate (1/s); under the script a spring (stiffness,
    // damping) follows the script's angle.
    static let spinDrag = 1.6
    static let turnStiffness = 26.0
    static let turnDamping = 10.2
    // Segment count: a spring towards the chosen even count.
    static let segStiffness = 90.0
    static let segDamping = 15.0
    static let segMin = 6.0
    static let segMax = 12.0
    // The iris opens at this rate (1/s) when nothing scripts it.
    static let irisRate = 3.2

    // autoPlay (seconds): the iris opens, the chamber turns 200°, is shaken, turns on to 420° with
    // the segments going 8 → 12 → 6 → 8, is shaken again, and the iris closes while it all resets.
    static let loop = 14.0
    static let irisOpen = 0.9
    static let irisCloseFrom = 12.9
    static let irisCloseTo = 13.5
    static let resetAt = 13.7
    // After a touch the script picks up here, past the iris opening.
    static let resumeAt = 1.0
    static let turnKeys: [(Double, Double)] = [(0, 0), (1.0, 0), (4.2, 200), (6.2, 200), (9.6, 420), (14, 420)]
    static let shakes = [4.6, 10.3]
    static let shakeSpeed = 300.0
    static let segKeys: [(Double, Double)] = [(5.2, 12), (8.4, 6), (11.2, 8)]
    static let segStart = 8.0
}

/// The chamber and everything in it, mutated in place one tick at a time.
final class KaleidoscopeTwistSim {
    private typealias K = KaleidoscopeTwistConst

    private struct Rest {
        var x: [Double], y: [Double], r: [Double], ang: [Double]
        var kind: [Int], tone: [Int], shape: [Int]
    }

    var count = 0
    var x: [Double] = []
    var y: [Double] = []
    var vx: [Double] = []
    var vy: [Double] = []
    var r: [Double] = []
    var ang: [Double] = []
    var spin: [Double] = []
    var kind: [Int] = []
    var tone: [Int] = []
    /// Shape seed of each piece (a shard's outline), and when it was added (s).
    var shape: [Int] = []
    var born: [Double] = []
    /// The settled start, restored at the loop's seam.
    private var rest = Rest(x: [], y: [], r: [], ang: [], kind: [], tone: [], shape: [])
    private(set) var restCount = 0
    var seed: UInt32
    var time = 0.0
    var clock = 0.0
    var hold = 0.0
    var looping: Bool
    var reduced: Bool
    /// The chamber's turn (rad, clockwise) and its speed.
    var angle = 0.0
    var angVel = 0.0
    /// The script turns the chamber relative to where it was when the script (re)started.
    var turnBase = 0.0
    var dragging = false
    /// Segment count, continuous while it morphs, its speed and the even count it settles on.
    var seg = K.segStart
    var segVel = 0.0
    var segTarget = K.segStart
    var pinching = false
    /// 0 = iris closed, 1 = open.
    var iris = 0.0
    /// Next slot an added piece replaces once the cell is full.
    var nextSlot = 0

    init(seed: Int, looping: Bool, reduced: Bool) {
        let s = UInt32(truncatingIfNeeded: seed)
        self.seed = s == 0 ? 1 : s
        self.looping = looping
        self.reduced = reduced
        // Scattered through the chamber, then left to fall and settle for three seconds.
        for i in 0..<K.startPieces {
            let kind = i % 4 == 3 ? K.shard : i % 5 == 1 ? K.seed : i % 7 == 2 ? K.shard : K.bead
            let a = random() * Double.pi * 2
            let d = random().squareRoot() * (K.cellR - 22)
            pushPiece(cos(a) * d, sin(a) * d, kind)
        }
        for _ in 0..<180 { physics() }
        for i in 0..<count {
            vx[i] = 0
            vy[i] = 0
            spin[i] = 0
        }
        rest = Rest(x: x, y: y, r: r, ang: ang, kind: kind, tone: tone, shape: shape)
        restCount = count
        nextSlot = count
    }

    /// A small, fast, seeded random in [0, 1): xorshift32 on the state's seed.
    func random() -> Double {
        var v = seed
        v ^= v << 13
        v ^= v >> 17
        v ^= v << 5
        seed = v
        return Double(v) / 4_294_967_296
    }

    /// Puts every piece back where it settled at the start, still.
    func restore() {
        count = restCount
        x = rest.x
        y = rest.y
        r = rest.r
        ang = rest.ang
        kind = rest.kind
        tone = rest.tone
        shape = rest.shape
        vx = [Double](repeating: 0, count: x.count)
        vy = [Double](repeating: 0, count: x.count)
        spin = [Double](repeating: 0, count: x.count)
        born = [Double](repeating: -10, count: x.count)
        angle = 0
        angVel = 0
        turnBase = 0
        seg = K.segStart
        segVel = 0
        segTarget = K.segStart
        nextSlot = restCount
    }

    private func pushPiece(_ px: Double, _ py: Double, _ k: Int) {
        let radius = k == K.bead ? 8 + random() * 7 : k == K.shard ? 10 + random() * 7 : 3.6 + random() * 1.6
        x.append(px)
        y.append(py)
        vx.append(0)
        vy.append(0)
        r.append(radius)
        ang.append(random() * Double.pi * 2)
        spin.append(0)
        kind.append(k)
        tone.append(Int((random() * Double(K.tones)).rounded(.down)))
        shape.append(Int((random() * 100_000).rounded(.down)))
        born.append(-10)
        count += 1
    }

    /// One tick of the pieces: substeps of gravity, drag, contacts and the wall.
    private func physics() {
        let h = K.tick / Double(K.substeps)
        let gx = K.gravity * sin(angle)
        let gy = K.gravity * cos(angle)
        let drag = 1 - K.air * h
        let n = count
        // Worked on as local buffers, handed back at the end (no copies: the fields are emptied).
        var x = self.x, y = self.y, vx = self.vx, vy = self.vy, spin = self.spin, ang = self.ang
        self.x = []; self.y = []; self.vx = []; self.vy = []; self.spin = []; self.ang = []
        let r = self.r
        defer {
            self.x = x; self.y = y; self.vx = vx; self.vy = vy; self.spin = spin; self.ang = ang
        }
        for _ in 0..<K.substeps {
            for i in 0..<n {
                vx[i] = (vx[i] + gx * h) * drag
                vy[i] = (vy[i] + gy * h) * drag
                x[i] += vx[i] * h
                y[i] += vy[i] * h
            }
            for pass in 0..<K.passes {
                for i in 0..<n {
                    var j = i + 1
                    while j < n {
                        defer { j += 1 }
                        let dx = x[j] - x[i]
                        let dy = y[j] - y[i]
                        let rr = r[i] + r[j]
                        let d2 = dx * dx + dy * dy
                        if d2 >= rr * rr || d2 < 1e-6 { continue }
                        let d = d2.squareRoot()
                        let nx = dx / d
                        let ny = dy / d
                        // Mass goes with area.
                        let mi = r[i] * r[i]
                        let mj = r[j] * r[j]
                        let wi = mj / (mi + mj)
                        let wj = mi / (mi + mj)
                        let overlap = rr - d
                        x[i] -= nx * overlap * wi
                        y[i] -= ny * overlap * wi
                        x[j] += nx * overlap * wj
                        y[j] += ny * overlap * wj
                        if pass > 0 { continue }
                        let rvx = vx[j] - vx[i]
                        let rvy = vy[j] - vy[i]
                        let vn = rvx * nx + rvy * ny
                        if vn >= 0 { continue }
                        let imp = -(1 + K.bounce) * vn
                        vx[i] -= nx * imp * wi
                        vy[i] -= ny * imp * wi
                        vx[j] += nx * imp * wj
                        vy[j] += ny * imp * wj
                        // Friction along the contact, at most `friction` of the push.
                        let tx = -ny
                        let ty = nx
                        let vt = rvx * tx + rvy * ty
                        let f = max(-K.friction * imp, min(K.friction * imp, vt * 0.5))
                        vx[i] += tx * f * wi
                        vy[i] += ty * f * wi
                        vx[j] -= tx * f * wj
                        vy[j] -= ty * f * wj
                    }
                }
                for i in 0..<n {
                    let d = hypot(x[i], y[i])
                    let lim = K.cellR - r[i]
                    if d <= lim || d < 1e-6 { continue }
                    let nx = x[i] / d
                    let ny = y[i] / d
                    x[i] = nx * lim
                    y[i] = ny * lim
                    let vn = vx[i] * nx + vy[i] * ny
                    if vn > 0 {
                        vx[i] -= (1 + K.wallBounce) * vn * nx
                        vy[i] -= (1 + K.wallBounce) * vn * ny
                    }
                    // Rolling along the wall: the spin follows the speed along it.
                    let vt = -vx[i] * ny + vy[i] * nx
                    vx[i] -= -ny * vt * K.wallGrip
                    vy[i] -= nx * vt * K.wallGrip
                    spin[i] += (vt / r[i] - spin[i]) * min(1, K.rollRate * h)
                }
            }
            for i in 0..<n { ang[i] += spin[i] * h }
        }
    }

    /// Drops a new piece at (px, py) in the chamber's frame; once full, it replaces the oldest added.
    func addPiece(_ px: Double, _ py: Double) {
        let d = hypot(px, py)
        let lim = K.cellR - 20
        let qx = d > lim ? px / d * lim : px
        let qy = d > lim ? py / d * lim : py
        let k = random() > 0.6 ? K.shard : K.bead
        if count < K.maxPieces {
            pushPiece(qx, qy, k)
            born[count - 1] = time
            return
        }
        let i = nextSlot
        nextSlot = i + 1 >= K.maxPieces ? restCount : i + 1
        x[i] = qx
        y[i] = qy
        vx[i] = 0
        vy[i] = 0
        kind[i] = k
        r[i] = k == K.bead ? 8 + random() * 7 : 10 + random() * 7
        tone[i] = Int((random() * Double(K.tones)).rounded(.down))
        shape[i] = Int((random() * 100_000).rounded(.down))
        born[i] = time
    }

    /// Gives every piece a random kick, mostly against gravity.
    func shake(_ speed: Double) {
        let gx = sin(angle)
        let gy = cos(angle)
        for i in 0..<count {
            let a = random() * Double.pi * 2
            let s = speed * (0.4 + 0.6 * random())
            vx[i] += cos(a) * s * 0.6 - gx * s
            vy[i] += sin(a) * s * 0.6 - gy * s
            spin[i] += (random() - 0.5) * 18
        }
    }

    /// Size of piece `i` now: added pieces grow in.
    func grownRadius(_ i: Int) -> Double {
        let age = (time - born[i]) / K.growTime
        if age >= 1 { return r[i] }
        let t = max(0, age)
        // An ease-out with a small overshoot.
        let s = 1 + 2.2 * pow(t - 1, 3) + 1.2 * pow(t - 1, 2)
        return r[i] * (0.55 + 0.45 * s)
    }

    private static func smooth(_ t: Double) -> Double {
        let u = max(0, min(1, t))
        return u * u * (3 - 2 * u)
    }

    /// The script's angle for the chamber at `t` (rad): eased between keys.
    private static func scriptAngle(_ t: Double) -> Double {
        // Past the reset the chamber is back at its start, hidden by the closed iris.
        if t >= K.resetAt { return 0 }
        for k in 1..<K.turnKeys.count {
            let (t1, a1) = K.turnKeys[k]
            if t <= t1 {
                let (t0, a0) = K.turnKeys[k - 1]
                return (a0 + (a1 - a0) * smooth((t - t0) / (t1 - t0))) * Double.pi / 180
            }
        }
        return K.turnKeys[K.turnKeys.count - 1].1 * Double.pi / 180
    }

    /// The script's iris at `t`: opens at the start of the loop, closes at its end.
    private static func scriptIris(_ t: Double) -> Double {
        if t < K.irisOpen { return 1 - pow(1 - t / K.irisOpen, 3) }
        if t < K.irisCloseFrom { return 1 }
        return 1 - smooth((t - K.irisCloseFrom) / (K.irisCloseTo - K.irisCloseFrom))
    }

    private static func crossed(_ before: Double, _ after: Double, _ at: Double) -> Bool {
        before < at && after >= at
    }

    /// Rounds like JavaScript's `Math.round` (halves up).
    static func jsRound(_ v: Double) -> Double { (v + 0.5).rounded(.down) }

    /// Sets the segment count the spring heads for (an even number in range).
    func setSegments(_ value: Double) {
        segTarget = max(K.segMin, min(K.segMax, Self.jsRound(value / 2) * 2))
    }

    /// One tick: the script, the chamber's turn, the segment spring, the iris, then the pieces.
    func step() {
        time += K.tick
        var scripted = false
        if hold > 0 {
            // A hand still on the glass keeps the script paused; the hold counts down once it lets go.
            if !dragging && !pinching { hold -= K.tick }
            if hold <= 0 {
                clock = K.resumeAt
                turnBase = angle - Self.scriptAngle(K.resumeAt)
            }
        } else if looping {
            scripted = true
            let before = clock
            var after = before + K.tick
            if after >= K.loop { after -= K.loop }
            for at in K.shakes where Self.crossed(before, after, at) && !reduced { shake(K.shakeSpeed) }
            for (at, count) in K.segKeys where Self.crossed(before, after, at) { setSegments(count) }
            if Self.crossed(before, after, K.resetAt) { restore() }
            clock = after
        }

        // The turn.
        if !dragging {
            if scripted {
                // The script's angle is measured from where it (re)started.
                let goal = turnBase + Self.scriptAngle(clock)
                let acc = K.turnStiffness * (goal - angle) - K.turnDamping * angVel
                angVel += acc * K.tick
            } else {
                angVel *= exp(-K.spinDrag * K.tick)
            }
            angle += angVel * K.tick
        }

        // The segments.
        if !pinching {
            if reduced {
                seg = segTarget
                segVel = 0
            } else {
                let acc = K.segStiffness * (segTarget - seg) - K.segDamping * segVel
                segVel += acc * K.tick
                seg += segVel * K.tick
            }
        }

        // The iris.
        iris = scripted ? Self.scriptIris(clock) : iris + (1 - iris) * (1 - exp(-K.irisRate * K.tick))

        physics()
    }
}
