import SwiftUI

/// Pure model-space poses, including the support bounds for the moving diagonal front.
struct IconMorphSheetIcon {
    typealias V = SIMD3<Double>
    typealias Q = SIMD4<Double>

    static let loops = [3.2, 3.4, 3.4, 2.8]
    static let paces = [1.6, 3.4 / 2.3, 3.4 / 2.4, 1.4]
    static let heroes = [1.3, 1.4, 1.5, 1.15]
    var scene = Q.zero
    var radius = 1.72
    var shape = Q.zero
    var turn = 0.0
    var scale = 1.0
    var press = 0.0
    var wet = 0.0
    var front = Q.zero
    var zero = Q.zero
    var coins = [Q](repeating: .zero, count: 3)
    var coinTurns = [Q](repeating: .zero, count: 3)
    var safe = Q.zero
    var envelope = Q.zero
    var note = Q.zero
    var book = Q.zero
    var bookTurn = Q.zero

    static func clamp(_ x: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double {
        max(lo, min(hi, x))
    }

    static func ease(_ a: Double, _ b: Double, _ t: Double) -> Double {
        let x = clamp((t - a) / (b - a))
        return x * x * (3 - 2 * x)
    }

    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    static func pop(_ a: Double, _ b: Double, _ t: Double, _ overshoot: Double = 1.5) -> Double {
        let tail = clamp((t - a) / (b - a)) - 1
        return 1 + (1 + overshoot) * tail * tail * tail + overshoot * tail * tail
    }

    static func coin(_ t: Double, _ release: Double, _ which: Int) -> (Q, Q) {
        let big = which == 0
        let phase = (t - 1.3) * (big ? 3 : 3.8) + Double(which) * 0.35
        let hx = big ? 0 : which == 1 ? -0.37 : 0.37
        let hy = big ? 0.61 : -0.08
        let x = big ? -0.08 + 0.46 * sin(phase) : (which == 1 ? -1.03 : 1.12) + 0.12 * sin(phase)
        let y = big ? 0.52 + 0.18 * sin(phase) : -0.60 + 0.26 * sin(phase + Double(which))
        let z = big ? 0.12 + 0.54 * cos(phase) : 0.20 + 0.43 * sin(phase)
        let size = (big ? 0.63 : 0.26) * mix(0.16, 1, ease(0, t > 2.6 ? 0.24 : 0.6, release))
        let yaw = phase * (big ? 1.65 : 2.2) - (big ? 0.38 : 0.12)
        let pitch = big ? -0.18 : 0.60 * cos(phase)
        return (Q(mix(hx, x, release), mix(hy, y, release), mix(0, z, release), size),
                Q(cos(yaw), sin(yaw), cos(pitch), sin(pitch)))
    }

    init(position: Double, times: [Double], bounce: Double, lean: Double, press: Double, reduced: Bool) {
        let pos = Self.clamp(position, 0, 3)
        let lower = Int(floor(pos))
        let upper = min(3, lower + 1)
        let reverse = !reduced && pos > Double(lower) && times[lower] == 0 && times[upper] > 0
        let a = reverse ? upper : lower
        let b = reverse ? lower : upper
        let s = reverse ? Double(upper) - pos : pos - Double(lower)
        let w = Self.clamp((s - 0.06) / 0.88)
        let activity = Self.ease(0.06, 0.15, s) * (1 - Self.ease(0.85, 0.94, s))
        let mid = sin(.pi * s) * activity
        let times = (0..<4).map { reduced ? Self.heroes[$0] : $0 == a ? times[$0].truncatingRemainder(dividingBy: Self.loops[$0]) : 0 }
        let zt = times[0]
        let st = times[1]
        let et = times[2]
        let bt = times[3]
        let zr = Self.ease(0.38, 0.82, zt) * (1 - Self.ease(2.68, 2.96, zt))
        for i in 0..<3 {
            (coins[i], coinTurns[i]) = Self.coin(zt, zr, i)
            let c = coins[i]
            radius = max(radius, sqrt(c.x * c.x + c.y * c.y + c.z * c.z) + c.w + 0.06)
        }
        let safePose = Self.ease(0.30, 0.70, st) * (1 - Self.ease(2.75, 3.10, st))
        let opened = Self.ease(0.25, 0.70, et) * (1 - Self.ease(2.40, 2.90, et))
        let heart = Self.pop(0.32, 0.75, et) * (1 - Self.ease(2.40, 2.80, et))
        let grin = Self.pop(0.40, 0.83, et) * (1 - Self.ease(2.48, 2.88, et))
        let flop = Self.ease(1.35, 1.90, bt) * (1 - Self.ease(2.20, 2.80, bt))
        let badge = Self.pop(0.50, 0.86, bt) * (1 - Self.ease(1.35, 1.62, bt))
        scene = Q(Double(a), Double(b), w, s)
        shape = Q(0.24, 1, reduced ? 0 : 0.16 * sin(2 * .pi * s) * activity, reverse ? -1 : 1)
        turn = reduced ? 0 : (reverse ? 0.7 : -0.7) * mid + 0.35 * bounce + 0.45 * Self.clamp(lean, -1, 1)
        scale = reduced ? 1 : (1 - 0.05 * mid) * (1 + 0.08 * Self.clamp(bounce, -1, 1))
        self.press = reduced ? 0 : Self.clamp(press)
        wet = mid * mid
        zero = Q(zr, 1 - 0.025 * sin(.pi * zr), zt > 2.6 ? 1 : 0, 0)
        safe = Q(safePose, sin((st - 0.7) * .pi * 2 / 1.025), Self.pop(0.50, 0.82, st, 1.9) * (1 - Self.ease(2.75, 2.93, st)), 1)
        envelope = Q(opened, heart, grin, reduced ? 0 : .pi * 2 * Self.ease(2.90, 3.40, et))
        note = Q(Self.pop(0.28, 0.78, et) * (1 - Self.ease(2.40, 2.88, et)), sin(et * 2.5) * 0.025 * opened, sin(et * 3.5) * 0.10, et)
        book = Q(flop, badge, Self.ease(0.80, 1, bt), sin(.pi * flop))
        bookTurn = Q(-0.83 + 0.35 * flop, -0.10 + 0.28 * flop, -0.15 + 0.12 * flop, 0)
        if a == 0 { radius = Self.mix(radius, 1.72, Self.ease(0.85, 0.94, s)) }
        let lo = min(a, b)
        let hi = max(a, b)
        let band = lo == 1 && hi == 2 ? 0.60 : hi == 3 ? 0.55 : 0.46
        let direction = reverse ? -1.0 : 1.0
        front = Q(-max(support(a, -direction), support(b, -direction)) - band,
                  max(support(a, direction), support(b, direction)) + band, band, activity)
    }

    static func rx(_ p: V, _ a: Double) -> V { V(p.x, cos(a) * p.y + sin(a) * p.z, -sin(a) * p.y + cos(a) * p.z) }
    static func ry(_ p: V, _ a: Double) -> V { V(cos(a) * p.x - sin(a) * p.z, p.y, sin(a) * p.x + cos(a) * p.z) }
    static func rz(_ p: V, _ a: Double) -> V { V(cos(a) * p.x + sin(a) * p.y, -sin(a) * p.x + cos(a) * p.y, p.z) }
    static func dot(_ a: V, _ b: V) -> Double { a.x * b.x + a.y * b.y + a.z * b.z }
    static func rounded(_ n: V, _ x: Double, _ y: Double, _ r: Double, _ depth: Double, _ bevel: Double) -> Double {
        (x - r) * abs(n.x) + (y - r) * abs(n.y) + (r - bevel) * hypot(n.x, n.y) + (depth - bevel) * abs(n.z) + bevel
    }

    func support(_ id: Int, _ sign: Double) -> Double {
        let n = V(sqrt(0.5) * sign, sqrt(0.5) * sign, 0)
        if id == 0 {
            var q = Self.ry(n, -0.18)
            q.y *= zero.y
            var bound = Self.rounded(q, 0.51, 0.85, 0.43, 0.205, 0.065)
            if zero.x > 0 {
                for i in 0..<3 {
                    let c = coins[i]
                    let t = coinTurns[i]
                    let v = V(n.x, n.y * t.z, -n.y * t.w)
                    let local = V(v.x * t.x - v.z * t.y, v.y, v.x * t.y + v.z * t.x)
                    let depth = c.w * Self.mix(0.80, 0.105, max(zero.z, Self.ease(0, 0.82, zero.x))) + 0.028
                    bound = max(bound, Self.dot(n, V(c.x, c.y, c.z)) + c.w * hypot(local.x, local.y) + depth * abs(local.z))
                }
            }
            return bound
        }
        if id == 1 {
            let k = safe.x
            let q = Self.ry(n, -.pi * k - 0.14 * k)
            var bound = Self.rounded(q, 0.88, 0.88, 0.88, 0.11 - 0.02 * k, 0.044)
            if k > 0 {
                let f = Self.rx(V(-q.x, q.y, -q.z), -0.16 * k)
                let bud = safe.z
                if bud > 0 {
                    let centre = V(Self.mix(0.42, 0.65, bud), Self.mix(0.44, 0.66, bud), Self.mix(-0.065, 0.20, bud))
                    let size = Self.mix(0.18, 1, Self.ease(0, 0.65, bud))
                    let rim = max(0.285 * f.y, 0.285 * abs(f.x) + 0.2394 * f.y, -0.325 * f.y)
                    bound = max(bound, Self.dot(f, centre) + size * (rim + 0.025 + 0.095 * abs(f.z)))
                }
                bound = max(bound, Self.dot(f, V(0, 0.015, 0.32 * k)) + (f.y > 0 ? 0.12 : 0.28) * k * abs(f.y) + Self.mix(0.065, 0.35, k))
            }
            return bound
        }
        if id == 2 {
            let q = Self.rz(Self.rx(Self.ry(n, envelope.w - 0.10), -0.16), -0.10)
            var bound = Self.rounded(q, 0.91, 0.57, 0.13, 0.28, 0.06) - 0.10 * q.y + 0.085 * q.z
            let flapN = Self.rx(q, -envelope.x * 2.82)
            let flap = max(0.80 * abs(flapN.x), -0.66 * flapN.y) + 0.06 * hypot(flapN.x, flapN.y) + 0.065 * abs(flapN.z)
            bound = max(bound, Self.dot(q, V(0, 0.42, 0.02)) + flap + 0.14 * flapN.z)
            let rise = note.x
            if rise > 0 {
                bound = max(bound, Self.rounded(q, 0.76, 0.44 + 0.12 * rise, 0.14, 0.045, 0.028) + Self.dot(q, V(0, -0.08 + 0.37 * rise, -0.03 + 0.10 * rise)))
            }
            for i in 0..<2 {
                let bud = envelope[i + 1]
                if bud <= 0 { continue }
                let h = i == 0
                let x = Self.mix(h ? 0.40 : -0.50, h ? 1.04 : -0.96, bud)
                let y = Self.mix(h ? 0.02 : -0.20, h ? 0.78 : -0.54, bud) + (h ? 0.025 : 0.023) * sin(note.w * 3 + (h ? 0 : 2)) * min(bud, 1)
                let z = Self.mix(h ? -0.06 : 0.02, h ? 0.20 : 0.36, bud)
                bound = max(bound, Self.dot(q, V(x, y, z)) + (h ? 0.265 : 0.34) * Self.mix(0.16, 1, Self.ease(0, 0.72, bud)))
            }
            return bound + q.y * note.y
        }
        let q = Self.rx(Self.ry(Self.rz(n, bookTurn.y), bookTurn.z), bookTurn.x)
        var bound = Self.rounded(q, 0.93, 0.75, 0.18, 0.22, 0.027)
        bound = max(bound, 0.75 * q.x - 0.57 * q.y + (0.17 + 0.24 * book.w) * q.z + 0.18)
        if book.y > 0 {
            let b = book.y
            let centre = V(Self.mix(0.63, 0.86, b), Self.mix(0.54, 0.68, b), Self.mix(0.06, 0.37, b))
            bound = max(bound, Self.dot(q, centre) + 0.22 * Self.mix(0.16, 1, Self.ease(0, 0.70, b)))
        }
        return bound
    }

    func shader(size: CGSize, unit: Double, pixelRatio: Double) -> Shader {
        func scalar(_ x: Double) -> Shader.Argument { .float(Float(x)) }
        func vector(_ v: Q) -> Shader.Argument { .float4(Float(v.x), Float(v.y), Float(v.z), Float(v.w)) }
        return Shader(function: ShaderFunction(library: .default, name: "iconMorphSheet"), arguments: [
            .float2(Float(size.width / 2), Float(size.height / 2)), scalar(unit), scalar(pixelRatio), scalar(9), scalar(radius),
            vector(scene), vector(shape), scalar(turn), scalar(scale), scalar(press), scalar(wet), vector(front), vector(zero),
            vector(coins[0]), vector(coins[1]), vector(coins[2]), vector(coinTurns[0]), vector(coinTurns[1]), vector(coinTurns[2]),
            vector(safe), vector(envelope), vector(note), vector(book), vector(bookTurn)
        ])
    }
}
