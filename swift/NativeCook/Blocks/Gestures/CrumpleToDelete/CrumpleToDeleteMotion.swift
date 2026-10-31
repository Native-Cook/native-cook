/// Deterministic mesh geometry and the nine-second script ported from ctd-motion.ts.
import Foundation

// Direct translation of ctd-motion.ts. All geometry remains in full-screen points.
/// Shares geometry and motion functions between live interaction and pinned previews.
enum CrumpleToDeleteMotion {
    static let loop = 9.0
    static let gx = 13, gy = 16
    struct Geo {
        var width, height, top, bottom: Double
        var nw: Double { min(width - 56, 352) }
        var nh: Double { (nw * 1.2).rounded() }
        var hx: Double { width / 2 }
        var hy: Double { top + 128 + nh / 2 }
        var bw: Double { 156 }
        var bh: Double { 597 * 0.26 }
        var binX: Double { width / 2 - 78 }
        var binY: Double { height - bottom - 20 - bh }
        var bcx: Double { binX + 315.4 * 0.26 }
        var bcy: Double { binY + 157.7 * 0.26 }
        var bx: Double { bcx }
        var by: Double { bcy - 6 }
        var brx: Double { 231.6 * 0.26 * 0.94 }
        var bry: Double { 136 * 0.26 * 0.9 }
    }
    struct Pose: Equatable {
        var x = 0.0, y = 0.0, c = 0.0, spin = 0.0, sc = 1.0
        var sink = 0.0, wobble = 0.0, toast = 0.0, lift = 0.0
    }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = seg(x, a, b)
        return t * t * (3 - 2 * t)
    }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func springStep(_ t: Double, _ duration: Double, _ ratio: Double) -> Double {
        if t <= 0 { return 0 }
        let w = 2 * Double.pi / duration
        if ratio >= 1 { return 1 - exp(-w * t) * (1 + w * t) }
        let wd = w * sqrt(1 - ratio * ratio)
        return 1 - exp(-ratio * w * t) * (cos(wd * t) + (ratio * w / wd) * sin(wd * t))
    }
    static func springTo(
        _ x: Double, _ v: Double, _ target: Double, _ duration: Double, _ ratio: Double, _ h: Double
    ) -> (Double, Double) {
        let w = 2 * Double.pi / duration
        let nv = v + (w * w * (target - x) - 2 * ratio * w * v) * h
        return (x + nv * h, nv)
    }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }
    static func tossPos(_ q: Double, _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> (Double, Double) {
        let apex = min(y0, y1) - 120
        return (x0 + (x1 - x0) * q, (1 - q) * (1 - q) * y0 + 2 * (1 - q) * q * apex + q * q * y1)
    }
    static func released(_ tr: Double, _ x0: Double, _ y0: Double, _ c0: Double, _ spin0: Double, _ g: Geo) -> Pose
    {
        let c = c0 + (1 - c0) * springStep(tr, 0.42, 0.78)
        let q = easeInOut(seg(tr, 0.28, 0.9))
        let p = tossPos(q, x0, y0, g.bx, g.by)
        let land = tr - 0.9
        return Pose(
            x: p.0, y: p.1, c: c, spin: spin0 + 7 * q, sc: 1 - 0.18 * q,
            sink: easeInOut(seg(tr, 0.9, 1.25)), wobble: land > 0 ? exp(-land * 5) * sin(land * 26) : 0,
            toast: smooth(1, 1.3, tr), lift: 1 - smooth(0, 0.3, tr))
    }
    static func undone(_ tu: Double, _ spin0: Double, _ g: Geo) -> Pose {
        let q = easeInOut(seg(tu, 0.18, 0.72))
        let p = tossPos(q, g.bx, g.by, g.hx, g.hy)
        let open = springStep(tu - 0.62, 0.8, 0.62)
        return Pose(
            x: p.0, y: p.1, c: max(0, 1 - open), spin: (spin0 + 7) * (1 - q) * (1 - smooth(0, 1, open)),
            sc: 0.82 + 0.18 * q, sink: 1 - easeOut(seg(tu, 0, 0.22)), wobble: 0, toast: 1 - smooth(0, 0.25, tu),
            lift: 0)
    }
    /// Returns the complete autoplay pose at a loop time, without accumulating state.
    static func script(_ t: Double, _ g: Geo) -> Pose {
        if t < 1 || t >= 8 { return Pose(x: g.hx, y: g.hy) }
        if t < 2.55 {
            let u = easeInOut(seg(t, 1, 2.55))
            let dy = 210 * u
            return Pose(
                x: g.hx + 18 * u, y: g.hy + dy, c: min(1, dy / 300), spin: 0.12 * u, sc: 1, sink: 0, wobble: 0,
                toast: 0, lift: smooth(1, 1.2, t))
        }
        if t < 6 { return released(t - 2.55, g.hx + 18, g.hy + 210, 0.7, 0.12, g) }
        return undone(t - 6, 0.12, g)
    }
    // Hashes and topology are immutable, and computed only once.
    static let seeds: [(Double, Double, Double)] = (0..<(gx * gy)).map {
        let n = Double($0)
        return (hash(n) * 2 - 1, hash(n + 91.7) * 2 - 1, hash(n + 33.1))
    }
    static let indices: [UInt16] = {
        var out: [UInt16] = []
        for j in 0..<(gy - 1) {
            for i in 0..<(gx - 1) {
                let a = j * gx + i
                out +=
                    ((i + j) % 2 == 0
                    ? [a, a + 1, a + gx, a + 1, a + gx + 1, a + gx]
                    : [a, a + 1, a + gx + 1, a, a + gx + 1, a + gx]).map(UInt16.init)
            }
        }
        return out
    }()
    /// Rebuilds the seeded paper vertices for the current crumple, spin, and sink.
    static func crumple(_ p: Pose, _ g: Geo) -> [CrumpleToDeleteVertex] {
        let c = max(0, min(1, p.c))
        let e = c * c * (3 - 2 * c)
        let wr = sin(Double.pi * min(1, c * 1.25))
        let cs = cos(p.spin)
        let sn = sin(p.spin)
        let sc = p.sc * (1 - 0.25 * p.sink) * (1 + 0.025 * p.lift)
        var out: [CrumpleToDeleteVertex] = []
        out.reserveCapacity(gx * gy)
        for j in 0..<gy {
            for i in 0..<gx {
                let id = j * gx + i
                let u = Double(i) / Double(gx - 1) * 2 - 1
                let v = Double(j) / Double(gy - 1) * 2 - 1
                let (r1, r2, r3) = seeds[id]
                let x0 = u * g.nw / 2
                let y0 = v * g.nh / 2
                let du = u * sqrt(1 - v * v / 2)
                let dv = v * sqrt(1 - u * u / 2)
                let edge = max(abs(u), abs(v))
                let rb = 46 * (0.72 + 0.28 * r3) * (1 - 0.18 * edge * edge)
                let bx = du * rb + r1 * 5
                let by = dv * rb + r2 * 5
                let gather = 1 - 0.45 * e
                let x = (x0 * gather + (bx - x0 * gather) * pow(e, 1.4) + r1 * 13 * wr) * sc
                let y = (y0 * gather + (by - y0 * gather) * pow(e, 1.4) + r2 * 13 * wr) * sc
                let facing = 0.5 + 0.5 * (r1 * 0.7 - r2 * 0.7)
                let b = max(0, min(1, 1 - c * (0.62 - 0.6 * facing) - c * 0.16 * edge))
                let shade = SIMD4<Float>(
                    Float((255 * pow(b, 0.85)).rounded() / 255), Float((255 * b).rounded() / 255),
                    Float((255 * pow(b, 1.25)).rounded() / 255), 1)
                out.append(
                    CrumpleToDeleteVertex(
                        position: .init(Float(p.x + x * cs - y * sn), Float(p.y + 26 * p.sink + x * sn + y * cs)),
                        uv: .init(Float(i) / Float(gx - 1), Float(j) / Float(gy - 1)), color: shade))
            }
        }
        return out
    }
}
