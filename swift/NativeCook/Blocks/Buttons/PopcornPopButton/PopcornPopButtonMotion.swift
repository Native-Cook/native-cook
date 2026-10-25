import Foundation

/*
 Popcorn Pop Button — the motion. Every frame is a pure function of a small "story" (when each
 kernel popped, when it was tipped into the bowl, when the lid lifted, when the label rolled), so
 the self-playing loop and the finger share one drawing: the loop fills the story from the
 timeline, a finger fills it from the simulation in the component.

 autoPlay (loop = 8 s):
   0.0 – 1.2   rest: 22 kernels in the copper pan, glass lid on, "Start the movie".
   1.2 – 2.6   a press (scale 0.97 in 0.1 s): the pan heats (glow, thermometer arc, kernels jitter).
   2.6 – 4.6   popping: one by one, slow, then fast, then slow (rank q -> 2.6 + 2 s * (q + 0.9 sin 2πq / 2π)).
   4.6 – 5.0   the heap lifts the lid; it rides up on the popcorn (ease-out back).
   5.0 – 5.35  the label rolls to "Enjoy the show"; the press lets go at 4.8.
   6.4 – 8.0   the pan tips into the bowl on the movie card (staggered arcs), kernels drop back in,
               the lid settles, the label rolls back. 8.0 is the same frame as 0.0.
 */
enum PopcornPopButtonMotion {
    static let loop = 8.0
    static let n = 22
    /// Corns needed before a release counts (and before the lid lifts under a finger).
    static let enough = 15
    static let never = 1e9

    struct Geo: Equatable {
        var w: Double
        var h: Double
        /// The round button.
        var bx: Double
        var by: Double
        var D: Double
        /// The pan's opening (an ellipse) and its front wall height.
        var px: Double
        var py: Double
        var rx: Double
        var ry: Double
        var wall: Double
        /// The lid's dome height.
        var dome: Double
        /// The bowl's mouth on the movie card.
        var bowlX: Double
        var bowlY: Double
        var cardX: Double
        var cardY: Double
        var cardW: Double
        var cardH: Double
        var footerY: Double
        var arcR: Double
    }

    /// Everything a frame is drawn from. Times are on one clock (loop time, or the frame clock).
    struct Story {
        var now = 0.0
        var popAt = [Double](repeating: PopcornPopButtonMotion.never, count: PopcornPopButtonMotion.n)
        var goneAt = [Double](repeating: PopcornPopButtonMotion.never, count: PopcornPopButtonMotion.n)
        var emptyFor = 0.9
        var lidAt = PopcornPopButtonMotion.never
        var lidBack = PopcornPopButtonMotion.never
        var labelAt = PopcornPopButtonMotion.never
        var labelOff = PopcornPopButtonMotion.never
        var heat = 0.0
        var press = 0.0
    }

    struct Kernel { var x, y, rot, alpha: Double; var v: Int }
    struct Corn { var x, y, rot, sx, sy: Double; var v: Int }
    struct Flying { var x, y, rot, scale: Double; var v: Int }
    struct Fleck { var x, y, alpha: Double }

    struct Pose {
        var heat = 0.0
        var press = 0.0
        var shakeX = 0.0
        var shakeY = 0.0
        var kernels: [Kernel] = []
        /// Corns in the pan, back to front.
        var corns: [Corn] = []
        /// Corns on their way to the bowl.
        var flying: [Flying] = []
        var flecks: [Fleck] = []
        var lidX = 0.0
        var lidY = 0.0
        var lidRot = 0.0
        var label = 0.0
        var bowl = 0.0
    }

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double {
        let k = 1 - clamp01(x)
        return 1 - k * k * k
    }
    static func easeInOutCubic(_ x: Double) -> Double {
        let k = clamp01(x)
        if k < 0.5 { return 4 * k * k * k }
        let m = -2 * k + 2
        return 1 - m * m * m / 2
    }
    static func easeOutBack(_ x: Double) -> Double {
        let k = clamp01(x) - 1
        let c = 1.9
        return 1 + (c + 1) * k * k * k + c * k * k
    }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// The pop order: rank of each kernel (a seeded shuffle).
    static let rank: [Int] = {
        let idx = (0..<n).sorted { hash(Double($0) * 3.7 + 1) < hash(Double($1) * 3.7 + 1) }
        var out = [Int](repeating: 0, count: n)
        for r in 0..<n { out[idx[r]] = r }
        return out
    }()

    /// Slow, fast, slow: the share of the popping time at which rank q pops.
    static func popCurve(_ q: Double) -> Double { q + 0.9 * sin(2 * .pi * q) / (2 * .pi) }

    static let autoPop: [Double] = (0..<n).map { 2.6 + 2 * popCurve(Double(rank[$0]) / Double(n - 1)) }

    /// Under a finger: the cooking needed before rank r pops.
    static func cookNeeded(_ r: Int) -> Double { 0.05 + 1.25 * popCurve(Double(r) / Double(n - 1)) }

    /// Kernel spots on the pan's floor, in pan units (-1..1).
    static let spot: [Double] = {
        var s: [Double] = []
        for i in 0..<n {
            let d = Double(i)
            let r = 0.74 * sqrt((d + 0.5) / Double(n)) * (0.88 + 0.2 * hash(d + 61))
            let a = d * 2.39996 + 0.3 + (hash(d + 67) - 0.5) * 0.5
            s.append(r * cos(a))
            s.append(r * sin(a) * 0.92)
        }
        return s
    }()

    /// The corns' places in the heap by rank: 14 on the floor, then 8 piled on top.
    static let heap: [Double] = {
        var h: [Double] = []
        for r in 0..<n {
            if r < 14 {
                let rr = 0.78 * sqrt((Double(r) + 0.5) / 14)
                let a = Double(r) * 2.39996 + 0.9
                h.append(contentsOf: [rr * cos(a), rr * sin(a), 0.004 + (1 - rr) * 0.02])
            } else {
                let j = Double(r - 14)
                let rr = 0.5 * sqrt((j + 0.5) / 8)
                let a = j * 2.39996 + 2.1
                h.append(contentsOf: [rr * cos(a), rr * sin(a) - 0.12, 0.05 + (1 - rr) * 0.035])
            }
        }
        return h
    }()

    /// Corn draw order (by rank): the floor layer back to front, then the pile back to front.
    static let order: [Int] = (0..<n).sorted {
        (($0 < 14 ? 0 : 1000) + heap[$0 * 3 + 1]) < (($1 < 14 ? 0 : 1000) + heap[$1 * 3 + 1])
    }

    /// Kernel at each rank.
    static let byRank: [Int] = {
        var out = [Int](repeating: 0, count: n)
        for i in 0..<n { out[rank[i]] = i }
        return out
    }()

    static func autoHeat(_ t: Double) -> Double {
        let up = 0.5 - 0.5 * cos(.pi * seg(t, 1.2, 2.6))
        return up * (1 - easeInOutCubic(seg(t, 4.6, 5.6)))
    }

    static func autoPress(_ t: Double) -> Double {
        easeOutCubic(seg(t, 1.2, 1.3)) * (1 - easeOutCubic(seg(t, 4.8, 4.95)))
    }

    /// The loop's story at loop time t.
    static func autoStory(_ t: Double) -> Story {
        var s = Story()
        s.now = t
        s.popAt = autoPop
        s.goneAt = [Double](repeating: 6.4, count: n)
        s.emptyFor = 1.6
        s.lidAt = 4.6
        s.lidBack = 6.4
        s.labelAt = 5.0
        s.labelOff = 6.4
        s.heat = autoHeat(t)
        s.press = autoPress(t)
        return s
    }

    /// A corn's resting angle in the heap.
    static func heapRot(_ r: Int) -> Double { (hash(Double(r) + 101) - 0.5) * 140 }

    /// Where a corn sits in the heap (screen pt).
    static func heapAt(_ g: Geo, _ r: Int) -> (Double, Double) {
        (g.px + heap[r * 3] * g.rx * 0.86,
         g.py + heap[r * 3 + 1] * g.ry * 0.8 - heap[r * 3 + 2] * g.D - g.D * 0.012)
    }

    static func makePose(_ s: Story, _ g: Geo) -> Pose {
        let now = s.now
        let heat = clamp01(s.heat)
        let D = s.emptyFor
        var p = Pose()
        p.kernels.reserveCapacity(n)
        var corns: [(rank: Int, corn: Corn)] = []
        let jig = heat * heat * g.D * 0.006
        var bowl = 0.0
        let N = Double(n)
        for i in 0..<n {
            let di = Double(i)
            let r = rank[i]
            let dr = Double(r)
            let sx = g.px + spot[i * 2] * g.rx
            let sy = g.py + spot[i * 2 + 1] * g.ry
            let pop = s.popAt[i]
            let gone = s.goneAt[i]
            let goneCorn = pop <= now && gone <= now && pop < gone
            // The kernel: on the floor until it pops, jittering with the heat; dropped back in after the bowl.
            var showKernel = pop > now
            var dropY = 0.0
            var alpha = 1.0
            if goneCorn {
                let e = now - gone - D * (0.31 + 0.31 * dr / N)
                let dur = D * 0.22
                if e >= 0 {
                    showKernel = true
                    let k = clamp01(e / dur)
                    dropY = -g.D * 0.2 * (1 - k * k)
                    if k >= 1 { dropY = -g.D * 0.012 * max(0, sin(.pi * clamp01((e - dur) / 0.12))) }
                    alpha = clamp01(k / 0.3)
                }
            }
            if showKernel {
                let k = 40 + floor(hash(di + 9) * 20)
                let ph = hash(di + 4) * 6.283
                let jx = jig * sin(2 * .pi * k * now / loop + ph)
                let jy = jig * 0.7 * sin(2 * .pi * (k + 7) * now / loop + ph * 1.7)
                p.kernels.append(Kernel(x: sx + jx, y: sy + jy + dropY, rot: hash(di + 2) * 360 + jx * 8, alpha: alpha, v: i % 4))
            }
            if pop > now { continue }
            let tau = now - pop
            // Hull flecks thrown out as it bursts.
            if tau < 0.4 {
                for f in 0..<3 {
                    let df = Double(f)
                    let a = -Double.pi / 2 + (hash(di * 7 + df) - 0.5) * 2.6
                    let v = g.D * (0.35 + 0.4 * hash(di * 5 + df + 2))
                    p.flecks.append(Fleck(x: sx + cos(a) * v * tau, y: sy + sin(a) * v * tau + g.D * 1.4 * tau * tau, alpha: 1 - tau / 0.4))
                }
            }
            let hp = heapAt(g, r)
            if goneCorn {
                // Into the bowl: an arc up and over, shrinking, staggered by rank.
                let e = now - gone - D * (0.34 * dr / N)
                if e < 0 {
                    corns.append((r, Corn(x: hp.0, y: hp.1, rot: heapRot(r), sx: 1, sy: 1, v: r % 6)))
                    continue
                }
                let k = clamp01(e / (D * 0.28))
                if k >= 1 {
                    let late = e - D * 0.28
                    if late < 0.25 { bowl = max(bowl, sin(.pi * late / 0.25)) }
                    continue
                }
                let u = easeInOutCubic(k)
                let mx = (hp.0 + g.bowlX) / 2
                let my = min(hp.1, g.bowlY) - g.D * 0.32
                let x = (1 - u) * (1 - u) * hp.0 + 2 * (1 - u) * u * mx + u * u * g.bowlX
                let y = (1 - u) * (1 - u) * hp.1 + 2 * (1 - u) * u * my + u * u * g.bowlY
                p.flying.append(Flying(x: x, y: y, rot: heapRot(r) + 220 * u, scale: 1 - 0.5 * u, v: r % 6))
                continue
            }
            // In flight from the kernel's spot to its place in the heap, then a little bounce.
            let T = 0.4 + 0.14 * hash(di + 31)
            let lidOn = pop < s.lidAt || s.lidBack > s.lidAt
            let H = g.D * (lidOn ? 0.07 + 0.04 * hash(di + 17) : 0.16 + 0.08 * hash(di + 17))
            let u = clamp01(tau / T)
            var x = sx + (hp.0 - sx) * u
            var y = sy + (hp.1 - sy) * u - H * 4 * u * (1 - u)
            var q = 0.2 * exp(-tau / 0.09) * cos(2 * .pi * tau / 0.16)
            if tau > T {
                let l = tau - T
                y -= g.D * 0.02 * abs(sin(.pi * l / 0.16)) * exp(-l / 0.11)
                q += 0.14 * exp(-l / 0.06)
                x = hp.0
            }
            let grow = 0.35 + 0.65 * easeOutBack(tau / 0.14)
            let spin = (hash(di + 51) > 0.5 ? 1.0 : -1.0) * (200 + 220 * hash(di + 57))
            let rot = heapRot(r) + spin * (1 - easeOutCubic(u))
            corns.append((r, Corn(x: x, y: y, rot: rot, sx: grow * (1 + q), sy: grow * (1 - q), v: r % 6)))
        }
        // Back to front by heap order (the floor layer first).
        for want in order {
            if let c = corns.first(where: { $0.rank == want }) { p.corns.append(c.corn) }
        }
        // The lid: rattled by pops underneath, then lifted onto the heap, then lowered back.
        var lidX = 0.0
        var lidY = 0.0
        var lidRot = 0.0
        for i in 0..<n {
            let tau = now - s.popAt[i] - 0.05
            if tau >= 0 && tau < 0.12 && s.popAt[i] < s.lidAt {
                lidY -= g.D * 0.012 * sin(.pi * tau / 0.12)
                lidRot += (hash(Double(i) + 77) - 0.5) * 3 * sin(.pi * tau / 0.12)
            }
        }
        let lift = easeOutBack(seg(now, s.lidAt, s.lidAt + 0.45))
        let down = s.lidBack >= s.lidAt ? easeInOutCubic(seg(now, s.lidBack + D * 0.55, s.lidBack + D * 0.83)) : 0
        let perched = lift * (1 - down)
        lidX += g.D * 0.05 * perched
        lidY += -g.D * 0.1 * perched
        lidRot += -9 * perched
        if s.lidBack >= s.lidAt { lidY -= g.D * 0.008 * sin(.pi * seg(now, s.lidBack + D * 0.83, s.lidBack + D * 0.95)) }
        let on = easeInOutCubic(seg(now, s.labelAt, s.labelAt + 0.35))
        let off = s.labelOff >= s.labelAt ? easeInOutCubic(seg(now, s.labelOff + D * 0.62, s.labelOff + D * 0.84)) : 0
        let shake = heat * heat * g.D * 0.0025
        p.heat = heat
        p.press = clamp01(s.press)
        p.shakeX = shake * sin(2 * .pi * 47 * now / loop)
        p.shakeY = shake * 0.6 * sin(2 * .pi * 61 * now / loop + 1)
        p.lidX = lidX
        p.lidY = lidY
        p.lidRot = lidRot
        p.label = on * (1 - off)
        p.bowl = bowl
        return p
    }
}
