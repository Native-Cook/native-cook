import Foundation

/*
 Slinky Stairs — the motion. The slinky is 40 rings. Each frame places every ring (centre and the
 direction of its long axis) from a pure function of the loop time: a ring is in the stack it is
 leaving, in the arch, or in the stack it is building. The arch is a cubic Bézier between the two
 stacks' tops whose end controls follow the end rings' axes, so the flipping end turns over smoothly.

 autoPlay (loop = 9 s):
   0.0 – 1.2   rest on the top step.
   1.2 – 6.4   three steps, 1.733 s each: tip over (30 %: the top end arcs over and lands on the next
               step, its axis turning 180°), pour (52 %: coils flow through the arch), flip (18 %: the
               last end leaves the old step and lands on top of the new stack). Ease-in-out per phase.
   6.4 – 7.0   settle on the bottom step (a damped squash, 5.5 Hz).
   7.0 – 8.4   lifted back to the top along an arc by an unseen hand holding the top ring: the coils
               hang and stretch (up to 1.9x) and swing behind the motion; set down with a squash.
   8.4 – 9.0   rest. 9.0 is the same frame as 0.0.
 */
enum SlinkyStairsMotion {
    static let loop = 9.0
    static let n = 40
    static let steps = 3
    static let walkFrom = 1.2
    static let stepFor = (6.4 - 1.2) / 3
    static let liftFrom = 7.0
    static let liftFor = 1.4
    /// Coils that live in the arch while it pours.
    static let arch = 12.0
    static let tip = 0.3
    static let pour = 0.82

    struct Geo: Equatable {
        var w: Double
        var h: Double
        /// Stack-bottom centre on each step (0 = top step).
        var baseX: [Double]
        var baseY: [Double]
        /// Ring radius (pt), stacked pitch (pt), and the camera's tilt (minor / major).
        var R: Double
        var pitch: Double
        var tilt: Double
        var tread: Double
        var riser: Double
        var stepY: [Double]
        var left: Double
        var panelY: Double
        var panelH: Double
        var deskEdge: Double
    }

    /// A ring: centre and the unit long axis (the short axis is screen-up, R * tilt).
    struct Ring { var x, y, nx, ny: Double }
    /// A contact shadow: centre, half width, alpha.
    struct Shadow { var x, y, r, alpha: Double }

    struct Pose {
        var rings: [Ring] = []
        var shadows: [Shadow] = []
        /// The label's position on its roll (0 Ready, 1 – 3 Step k, 4 Landed, 5 Ready again).
        var label = 0.0
    }

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOutCubic(_ x: Double) -> Double {
        let k = clamp01(x)
        if k < 0.5 { return 4 * k * k * k }
        let m = -2 * k + 2
        return 1 - m * m * m / 2
    }

    /// Point and tangent of a cubic Bézier at w.
    static func bez(_ ax: Double, _ ay: Double, _ bx: Double, _ by: Double, _ cx: Double, _ cy: Double, _ dx: Double, _ dy: Double, _ w: Double) -> (Double, Double, Double, Double) {
        let u = 1 - w
        let x = u * u * u * ax + 3 * u * u * w * bx + 3 * u * w * w * cx + w * w * w * dx
        let y = u * u * u * ay + 3 * u * u * w * by + 3 * u * w * w * cy + w * w * w * dy
        var tx = 3 * u * u * (bx - ax) + 6 * u * w * (cx - bx) + 3 * w * w * (dx - cx)
        var ty = 3 * u * u * (by - ay) + 6 * u * w * (cy - by) + 3 * w * w * (dy - cy)
        let l = hypot(tx, ty)
        if l < 1e-4 {
            tx = 0
            ty = -1
        } else {
            tx /= l
            ty /= l
        }
        return (x, y, tx, ty)
    }

    /// One ring whose axis (from the slinky's first end to its last) is (tx, ty).
    static func ring(_ out: inout [Ring], _ x: Double, _ y: Double, _ tx: Double, _ ty: Double) {
        // The long axis is perpendicular to the slinky's axis.
        out.append(Ring(x: x, y: y, nx: -ty, ny: tx))
    }

    static func stackRings(_ out: inout [Ring], _ x: Double, _ y: Double, _ count: Int, _ pitch: Double) {
        for i in 0..<count { ring(&out, x, y - Double(i) * pitch, 0, -1) }
    }

    /// The general walking slinky: `n1` coils stacked at A (first end at the bottom), `n2` coils
    /// stacked at B (last end at the bottom), and the rest in an arch between the given ends.
    static func walkRings(_ g: Geo, _ ax: Double, _ ay: Double, _ bx: Double, _ by: Double, _ n1: Double, _ n2: Double,
                          _ startX: Double, _ startY: Double, _ startDir: Double,
                          _ endX: Double, _ endY: Double, _ endDir: Double, _ out: inout [Ring]) {
        let p = g.pitch
        let N = Double(n)
        let archCount = N - n1 - n2
        let span = hypot(endX - startX, endY - startY)
        // Controls grow with the arch, so a short arch (an end just lifting) never loops back on itself.
        let h = max(1, min(170, span * 0.55 + archCount * 1.5))
        let sdx = cos(startDir), sdy = sin(startDir)
        let edx = cos(endDir), edy = sin(endDir)
        for i in 0..<n {
            let di = Double(i)
            if di < n1 - 0.5 {
                ring(&out, ax, ay - di * p, 0, -1)
            } else if di > N - 1 - n2 + 0.5 {
                ring(&out, bx, by - (N - 1 - di) * p, 0, -1)
            } else {
                let lo = max(0, n1 - 0.5)
                let hi = min(N - 1, N - 1 - n2 + 0.5)
                let w = hi - lo < 1e-3 ? 0.5 : clamp01((di - lo) / (hi - lo))
                let b = bez(startX, startY, startX + sdx * h, startY + sdy * h, endX - edx * h, endY - edy * h, endX, endY, w)
                ring(&out, b.0, b.1, b.2, b.3)
            }
        }
    }

    /// One step from step k to k + 1, u in 0..1.
    static func stepRings(_ g: Geo, _ k: Int, _ u: Double, _ out: inout [Ring]) {
        let p = g.pitch
        let N = Double(n)
        let ax = g.baseX[k], ay = g.baseY[k]
        let bx = g.baseX[k + 1], by = g.baseY[k + 1]
        let H = (N - 1) * p
        let up = -Double.pi / 2
        if u < tip {
            // The top end tips over and lands on the next step.
            let v = easeInOutCubic(u / tip)
            let nA = arch * min(1, v * 1.4)
            let n1 = N - nA
            let x0 = ax
            let y0 = ay - H
            let ex = x0 + (bx - x0) * (3 * (1 - v) * v * v * 0.95 + v * v * v) + (1 - v) * (1 - v) * v * 3 * g.tread * 0.25
            let q = 1 - v
            let ey = q * q * q * y0 + 3 * q * q * v * (y0 - g.riser * 0.9) + 3 * q * v * v * (by - g.riser * 1.25) + v * v * v * by
            walkRings(g, ax, ay, bx, by, n1, 0, ax, ay - (n1 - 1) * p, up, ex, ey, up + .pi * v, &out)
            return
        }
        if u < pour {
            // Coils pour through the arch.
            let v = easeInOutCubic((u - tip) / (pour - tip))
            let n1 = (N - arch) * (1 - v)
            let n2 = (N - arch) * v
            walkRings(g, ax, ay, bx, by, n1, n2, ax, ay - max(0, n1 - 1) * p, up, bx, by - max(0, n2 - 1) * p, .pi / 2, &out)
            return
        }
        // The first end leaves the old step and flips over onto the new stack.
        let v = easeInOutCubic((u - pour) / (1 - pour))
        let n2 = N - arch * (1 - v)
        let topB = by - (N - 1) * p
        let q = 1 - v
        let fx = q * q * q * ax + 3 * q * q * v * (ax + g.tread * 0.12) + 3 * q * v * v * (bx - g.tread * 0.35) + v * v * v * bx
        let fy = q * q * q * ay + 3 * q * q * v * (ay - g.riser * 0.55) + 3 * q * v * v * (topB - 34) + v * v * v * topB
        walkRings(g, ax, ay, bx, by, 0, n2, fx, fy, up + .pi * v, bx, by - max(0, n2 - 1) * p, .pi / 2, &out)
    }

    /// A stack squashing after a landing: e seconds since.
    static func squash(_ e: Double) -> Double {
        if e < 0 || e > 0.8 { return 1 }
        return 1 - 0.2 * exp(-e / 0.14) * cos(2 * .pi * 5.5 * e)
    }

    /// Lifted along an arc from step k to step 0 (v in 0..1): the coils hang from the top ring.
    static func liftRings(_ g: Geo, _ k: Int, _ v: Double, _ out: inout [Ring]) {
        let e = easeInOutCubic(v)
        let N = Double(n)
        let H = (N - 1) * g.pitch
        let x0 = g.baseX[k], y0 = g.baseY[k] - H
        let x1 = g.baseX[0], y1 = g.baseY[0] - H
        let apexY = min(y0, y1) - g.riser * 1.3
        let topX = x0 + (x1 - x0) * e
        let topY = (1 - e) * (1 - e) * y0 + 2 * (1 - e) * e * apexY + e * e * y1
        // Velocity of the hand (for the swing) from the curve's derivative.
        let de = 6 * v * (1 - v)
        let vx = (x1 - x0) * de
        let stretch = 1 + 0.9 * sin(.pi * e)
        for i in 0..<n {
            let d = (N - 1 - Double(i)) / (N - 1)
            let lag = -vx * 0.09 * d * d
            ring(&out, topX + lag, topY + (N - 1 - Double(i)) * g.pitch * stretch, lag * 0.004, -1)
        }
    }

    /// The finger holding the top ring at (px, py): the slinky stretches from its stack at step k.
    static func stretchRings(_ g: Geo, _ k: Int, _ px: Double, _ py: Double, _ out: inout [Ring]) {
        let bx = g.baseX[k], by = g.baseY[k]
        let L = hypot(px - bx, py - by)
        let N = Double(n)
        let H = (N - 1) * g.pitch
        if L <= H {
            // Squashed (or only leaning): a straight, tilted stack.
            let tx = L > 1e-3 ? (px - bx) / L : 0
            let ty = L > 1e-3 ? (py - by) / L : -1
            for i in 0..<n {
                let f = Double(i) / (N - 1)
                ring(&out, bx + (px - bx) * f, by + (py - by) * f, tx, ty)
            }
            return
        }
        let h = L * 0.32
        for i in 0..<n {
            let w = pow(Double(i) / (N - 1), 1.5)
            let b = bez(bx, by, bx, by - h, px, py + h, px, py, w)
            ring(&out, b.0, b.1, b.2, b.3)
        }
    }

    /// Step label value for the loop.
    static func loopLabel(_ t: Double) -> Double {
        var v = 0.0
        for s in 0..<steps {
            let a = walkFrom + Double(s) * stepFor
            v += easeInOutCubic(seg(t, a, a + 0.3))
        }
        v += easeInOutCubic(seg(t, 6.4, 6.7))
        v += easeInOutCubic(seg(t, liftFrom, liftFrom + 0.3))
        return v
    }

    static func loopPose(_ t: Double, _ g: Geo) -> Pose {
        var p = Pose()
        p.rings.reserveCapacity(n)
        if t < walkFrom {
            stackRings(&p.rings, g.baseX[0], g.baseY[0], n, g.pitch * squash(t + loop - 8.4))
            p.shadows.append(Shadow(x: g.baseX[0], y: g.baseY[0], r: g.R, alpha: 1))
        } else if t < 6.4 {
            let s = min(steps - 1, Int(floor((t - walkFrom) / stepFor)))
            let u = (t - walkFrom - Double(s) * stepFor) / stepFor
            stepRings(g, s, u, &p.rings)
            let leave = u < pour ? 1 - seg(u, tip, pour) * 0.6 : 0.4 * (1 - seg(u, pour, pour + 0.08))
            let arrive = u < tip ? seg(u, tip * 0.8, tip) * 0.5 : 0.5 + 0.5 * seg(u, tip, 1)
            p.shadows.append(Shadow(x: g.baseX[s], y: g.baseY[s], r: g.R, alpha: leave))
            p.shadows.append(Shadow(x: g.baseX[s + 1], y: g.baseY[s + 1], r: g.R, alpha: arrive))
        } else if t < liftFrom {
            stackRings(&p.rings, g.baseX[steps], g.baseY[steps], n, g.pitch * squash(t - 6.4))
            p.shadows.append(Shadow(x: g.baseX[steps], y: g.baseY[steps], r: g.R, alpha: 1))
        } else if t < liftFrom + liftFor {
            let v = (t - liftFrom) / liftFor
            liftRings(g, steps, v, &p.rings)
            let e = easeInOutCubic(v)
            p.shadows.append(Shadow(x: g.baseX[steps], y: g.baseY[steps], r: g.R, alpha: 1 - seg(v, 0, 0.25)))
            p.shadows.append(Shadow(x: g.baseX[0], y: g.baseY[0], r: g.R * (1.4 - 0.4 * e), alpha: seg(v, 0.7, 1)))
        } else {
            stackRings(&p.rings, g.baseX[0], g.baseY[0], n, g.pitch * squash(t - 8.4))
            p.shadows.append(Shadow(x: g.baseX[0], y: g.baseY[0], r: g.R, alpha: 1))
        }
        p.label = loopLabel(t)
        return p
    }
}
