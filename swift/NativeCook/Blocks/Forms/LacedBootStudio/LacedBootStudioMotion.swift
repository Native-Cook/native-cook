import Foundation
import CoreGraphics

struct LacedBootStudioScene {
    var progress = 0.0
    var trims = Array(repeating: 0.0, count: 11)
    var spanAlpha = Array(repeating: 1.0, count: 11)
    var slacks = Array(repeating: 6.375, count: 11)
    var tensions = Array(repeating: 0.15, count: 6)
    var automaticSlack = Array(repeating: 0.0, count: 11)
    var tailMorph = 0.0
    var tip = CGPoint(x: 63, y: 181)
    var tangent = CGPoint(x: 1, y: 0)
    var tipBackoff = 0.0
    var automaticAgletY = 0.0
    var tipAlpha = 1.0
    var velocityLag = 0.0
    var overpull = CGPoint.zero
    var materials = [1.0, 0.0, 0.0]
    var soles = [1.0, 0.0]
    var savedMix = 0.0
    var rows: Int { min(6, max(0, Int(floor(progress + 1e-8)))) }
}

struct LacedBootStudioCurve {
    let a: CGPoint
    let c1: CGPoint
    let c2: CGPoint
    let b: CGPoint
    func point(_ t: Double) -> CGPoint {
        let u = 1 - t
        return CGPoint(x: u * u * u * a.x + 3 * u * u * t * c1.x + 3 * u * t * t * c2.x + t * t * t * b.x,
                       y: u * u * u * a.y + 3 * u * u * t * c1.y + 3 * u * t * t * c2.y + t * t * t * b.y)
    }
    func tangent(_ t: Double) -> CGPoint {
        let u = 1 - t
        let x = 3 * u * u * (c1.x - a.x) + 6 * u * t * (c2.x - c1.x) + 3 * t * t * (b.x - c2.x)
        let y = 3 * u * u * (c1.y - a.y) + 6 * u * t * (c2.y - c1.y) + 3 * t * t * (b.y - c2.y)
        let length = max(1e-9, hypot(x, y))
        return CGPoint(x: x / length, y: y / length)
    }
    func prefix(_ t: Double) -> Self {
        let p = LacedBootStudioMath.point
        let ab = p(a, c1, t), bc = p(c1, c2, t), cd = p(c2, b, t)
        let abc = p(ab, bc, t), bcd = p(bc, cd, t)
        return .init(a: a, c1: ab, c2: abc, b: p(abc, bcd, t))
    }
    func segment(_ low: Double, _ high: Double) -> Self {
        let upper = prefix(high)
        guard low > 0, high > 0 else { return upper }
        let reversed = Self(a: upper.b, c1: upper.c2, c2: upper.c1, b: upper.a)
        let part = reversed.prefix(1 - low / high)
        return Self(a: part.b, c1: part.c2, c2: part.c1, b: part.a)
    }
}

struct LacedBootStudioArc {
    let points: [CGPoint]
    let distances: [Double]
    var length: Double { distances.last ?? 0 }
    init(_ curve: LacedBootStudioCurve) {
        let values = (0..<32).map { curve.point(Double($0) / 31) }
        var lengths = [0.0]
        for i in 1..<32 { lengths.append(lengths[i - 1] + hypot(values[i].x - values[i - 1].x, values[i].y - values[i - 1].y)) }
        points = values; distances = lengths
    }
    func parameter(_ fraction: Double) -> Double {
        let distance = LacedBootStudioMath.clamp(fraction) * length
        guard distance > 0 else { return 0 }
        for i in 1..<32 where distances[i] >= distance {
            let u = (distance - distances[i - 1]) / max(1e-9, distances[i] - distances[i - 1])
            return (Double(i - 1) + u) / 31
        }
        return 1
    }
    func nearest(_ point: CGPoint) -> (fraction: Double, distance: Double) {
        var best = (fraction: 0.0, distance: Double.infinity)
        for i in 1..<32 {
            let a = points[i - 1], b = points[i]
            let dx = b.x - a.x, dy = b.y - a.y
            let u = LacedBootStudioMath.clamp(((point.x - a.x) * dx + (point.y - a.y) * dy) / max(1e-9, dx * dx + dy * dy))
            let q = CGPoint(x: a.x + dx * u, y: a.y + dy * u)
            let distance = hypot(point.x - q.x, point.y - q.y)
            if distance < best.distance { best = ((distances[i - 1] + (distances[i] - distances[i - 1]) * u) / max(1e-9, length), distance) }
        }
        return best
    }
}

enum LacedBootStudioGeometry {
    static let center = CGPoint(x: 272.5, y: 308)
    static let rotation = 4.0 * Double.pi / 180
    static func eyelet(row: Int, right: Bool) -> CGPoint {
        CGPoint(x: right ? 76 - 0.55 * Double(row) : 41 + 0.55 * Double(row), y: 166 - 22 * Double(row))
    }
    static func row(_ span: Int) -> Int { span == 0 ? 0 : (span + 1) / 2 }
    static func endpoints(_ span: Int) -> (CGPoint, CGPoint) {
        if span == 0 { return (eyelet(row: 0, right: false), eyelet(row: 0, right: true)) }
        let r = row(span)
        return span.isMultiple(of: 2) ? (eyelet(row: r, right: false), eyelet(row: r, right: true)) :
            (eyelet(row: r - 1, right: true), eyelet(row: r, right: false))
    }
    static func curve(_ span: Int, slack: Double) -> LacedBootStudioCurve {
        let (a, b) = endpoints(span)
        return .init(a: a, c1: CGPoint(x: a.x + (b.x - a.x) * 0.33, y: a.y + (b.y - a.y) * 0.33 + slack),
                     c2: CGPoint(x: a.x + (b.x - a.x) * 0.67, y: a.y + (b.y - a.y) * 0.67 + slack), b: b)
    }
    static let tipRoute = LacedBootStudioCurve(a: CGPoint(x: 63, y: 181), c1: CGPoint(x: 67, y: 174),
                                               c2: CGPoint(x: 76, y: 176), b: CGPoint(x: 76, y: 166))
    static let arcs: [LacedBootStudioArc] = [LacedBootStudioArc(tipRoute)] + (1..<11).map { LacedBootStudioArc(curve($0, slack: 6.375)) }
    static func tail(_ scene: LacedBootStudioScene) -> LacedBootStudioCurve {
        let completed = curve(0, slack: scene.slacks[0])
        let u = scene.tailMorph
        return .init(a: completed.a,
                     c1: LacedBootStudioMath.point(CGPoint(x: 43, y: 179), completed.c1, u),
                     c2: LacedBootStudioMath.point(CGPoint(x: 55, y: 185), completed.c2, u),
                     b: tipRoute.point(arcs[0].parameter(u)))
    }
    static func local(_ world: CGPoint) -> CGPoint {
        let x = world.x - center.x, y = world.y - center.y
        return CGPoint(x: cos(rotation) * x + sin(rotation) * y + 58.5,
                       y: -sin(rotation) * x + cos(rotation) * y + 160)
    }
    static func accepts(_ world: CGPoint) -> Bool { CGRect(x: -12, y: -12, width: 141, height: 344).contains(local(world)) }
    static func spanProgress(_ index: Int, _ fraction: Double) -> Double {
        if index == 0 { return fraction }
        return Double(row(index)) + (index.isMultiple(of: 2) ? 0.5 : 0) + fraction * 0.5
    }
    static func activeSpan(_ progress: Double) -> Int {
        if progress < 1 { return 0 }
        return min(10, max(1, Int(floor((progress - 1) * 2)) + 1))
    }
}

enum LacedBootStudioMath {
    static let loop = 8.0
    static func clamp(_ x: Double, _ low: Double = 0, _ high: Double = 1) -> Double { min(high, max(low, x)) }
    static func smooth(_ x: Double) -> Double { let u = clamp(x); return u * u * (3 - 2 * u) }
    static func point(_ a: CGPoint, _ b: CGPoint, _ u: Double) -> CGPoint { CGPoint(x: a.x + (b.x - a.x) * u, y: a.y + (b.y - a.y) * u) }
    static func residual(_ age: Double) -> Double { 1 - smooth((age - 0.12) / 0.31) }
    static func canonical(progress: Double, tensions: [Double]) -> LacedBootStudioScene {
        var scene = LacedBootStudioScene()
        scene.progress = clamp(progress, 0, 6); scene.tensions = tensions.map { clamp($0) }
        scene.tailMorph = clamp(progress)
        scene.trims[0] = clamp(progress)
        for i in 1..<11 {
            let r = Double(LacedBootStudioGeometry.row(i))
            scene.trims[i] = clamp((progress - r - (i.isMultiple(of: 2) ? 0.5 : 0)) * 2)
        }
        for i in 0..<11 {
            scene.slacks[i] = 7.5 * (1 - scene.tensions[LacedBootStudioGeometry.row(i)]) +
                (i == 0 ? 0 : 2 * sin(.pi * scene.trims[i]))
        }
        updateTip(&scene)
        return scene
    }
    static func rest() -> LacedBootStudioScene { canonical(progress: 0, tensions: Array(repeating: 0.15, count: 6)) }
    static func final() -> LacedBootStudioScene { canonical(progress: 6, tensions: Array(repeating: 1, count: 6)) }
    static func authored(_ time: Double) -> LacedBootStudioScene {
        let t = max(0, time).truncatingRemainder(dividingBy: loop)
        var progress = 0.0
        if t >= 1 && t < 5.3 {
            for r in 0..<6 {
                if t < 1 + 0.48 * Double(r) { break }
                progress = max(progress, Double(r) + smooth((t - 1 - 0.48 * Double(r)) / 0.40))
            }
            progress = min(6, progress)
        } else if t >= 5.3 && t < 7.55 { progress = 6 * (1 - smooth((t - 5.3) / 2.25)) }
        var tensions = Array(repeating: 0.15, count: 6)
        if t >= 4.1 && t < 5 {
            for r in 0..<6 {
                let delay = 0.035 * Double(5 - r)
                let u = clamp((t - 4.1 - delay) / (0.62 - delay))
                tensions[r] = 0.15 + 0.85 * (1 - pow(1 - u, 3))
            }
        } else if t >= 5 && t < 5.3 { tensions = Array(repeating: 1 - 0.85 * smooth((t - 5) / 0.3), count: 6) }
        var scene = canonical(progress: progress, tensions: tensions)
        if t >= 3.88 && t < 4.1 {
            let bump = pow(sin(.pi * (t - 3.88) / 0.22), 2)
            scene.tipBackoff = 2.5 * bump
            scene.automaticSlack = Array(repeating: 2.625 * bump, count: 11)
            for i in 0..<11 { scene.slacks[i] += scene.automaticSlack[i] }
        }
        if t >= 4.1 && t <= 4.72 { scene.automaticAgletY = -0.9 * pow(sin(.pi * (t - 4.1) / 0.62), 2) }
        for r in 0..<6 {
            let start = 1 + 0.48 * Double(r)
            let ends = r == 0 ? [start + 0.4] : [start + 0.2, start + 0.4]
            if ends.contains(where: { t >= $0 && t < $0 + 0.035 }) { scene.tipAlpha = 0 }
        }
        if t >= 5.3 && t < 7.55 {
            for halfRow in 2...11 {
                let boundary = Double(halfRow) * 0.5
                let value = 1 - boundary / 6
                let inverse = 0.5 - sin(asin(1 - 2 * value) / 3)
                let passage = 5.3 + 2.25 * inverse
                if abs(t - passage) < 0.0175 { scene.tipAlpha = 0 }
            }
        }
        updateTip(&scene)
        return scene
    }
    static func updateTip(_ scene: inout LacedBootStudioScene) {
        let index = LacedBootStudioGeometry.activeSpan(scene.progress)
        if index == 0 {
            let curve = LacedBootStudioGeometry.tail(scene)
            scene.tip = curve.b; scene.tangent = curve.tangent(1)
        } else {
            let curve = LacedBootStudioGeometry.curve(index, slack: scene.slacks[index])
            let t = LacedBootStudioGeometry.arcs[index].parameter(scene.trims[index])
            scene.tip = curve.point(t); scene.tangent = curve.tangent(t)
        }
        scene.tip.x += scene.overpull.x - scene.tangent.x * scene.tipBackoff
        scene.tip.y += scene.overpull.y + scene.automaticAgletY - scene.tangent.y * scene.tipBackoff
    }
    static func held(from captured: LacedBootStudioScene, progress: Double, tensions: [Double],
                     age: Double, lag: Double, overpull: CGPoint) -> LacedBootStudioScene {
        var result = canonical(progress: progress, tensions: tensions)
        let original = canonical(progress: captured.progress, tensions: captured.tensions)
        let f = residual(age)
        let travel = 1 - smooth(abs(progress - captured.progress) / max(0.001, min(1, progress >= captured.progress ? 6 - captured.progress : captured.progress)))
        result.materials = captured.materials; result.soles = captured.soles; result.savedMix = captured.savedMix
        result.spanAlpha = captured.spanAlpha.map { 1 + ($0 - 1) * f * travel }
        result.velocityLag = lag; result.overpull = overpull
        for i in 0..<11 {
            let r = LacedBootStudioGeometry.row(i), tc = captured.tensions[r], current = result.tensions[r]
            let edge = current >= tc ? 1 - tc : tc
            let wt = edge <= 0 ? 1 : 1 - smooth(abs(current - tc) / edge)
            let automatic = captured.slacks[i] - original.slacks[i] - captured.velocityLag * (1 - tc)
            result.automaticSlack[i] = automatic * wt * f * travel
            result.slacks[i] += result.automaticSlack[i] + lag * (1 - current)
            result.trims[i] += (captured.trims[i] - original.trims[i]) * f * travel
        }
        result.tailMorph += (captured.tailMorph - original.tailMorph) * f * travel
        let tc = captured.tensions[5], current = result.tensions[5]
        let edge = current >= tc ? 1 - tc : tc
        let wt = edge <= 0 ? 1 : 1 - smooth(abs(current - tc) / edge)
        result.tipBackoff = captured.tipBackoff * f * wt * travel
        result.automaticAgletY = captured.automaticAgletY * f * wt * travel
        result.tipAlpha = 1 + (captured.tipAlpha - 1) * f * travel
        updateTip(&result)
        var originalTip = captured; updateTip(&originalTip)
        result.tip.x += (captured.tip.x - originalTip.tip.x) * f * travel
        result.tip.y += (captured.tip.y - originalTip.tip.y) * f * travel
        if age == 0 && progress == captured.progress && tensions == captured.tensions && lag == captured.velocityLag && overpull == captured.overpull { return captured }
        return result
    }
    static func released(from captured: LacedBootStudioScene, age: Double, downAge: Double) -> LacedBootStudioScene {
        var result = captured
        let oldF = residual(downAge)
        let f = oldF > 1e-9 ? residual(downAge + age) / oldF : 0
        let lag = captured.velocityLag * (1 - smooth(age / 0.09))
        for i in 0..<11 {
            result.automaticSlack[i] = captured.automaticSlack[i] * f
            let weight = 1 - captured.tensions[LacedBootStudioGeometry.row(i)]
            result.slacks[i] = captured.slacks[i] - captured.automaticSlack[i] * (1 - f) - captured.velocityLag * weight + lag * weight
        }
        result.velocityLag = lag
        result.tipBackoff *= f; result.automaticAgletY *= f
        result.tipAlpha = 1 + (captured.tipAlpha - 1) * f
        result.overpull = point(captured.overpull, .zero, smooth(age / 0.43))
        updateTip(&result)
        if age == 0 { return captured }
        return result
    }
    static func recovering(from captured: LacedBootStudioScene, age: Double) -> LacedBootStudioScene {
        let u = smooth(age / 0.6)
        if u >= 1 { var result = rest(); result.materials = captured.materials; result.soles = captured.soles; return result }
        let tensions = captured.tensions.map { $0 + (0.15 - $0) * u }
        var result = held(from: captured, progress: captured.progress * (1 - u), tensions: tensions,
                          age: 0, lag: captured.velocityLag * (1 - u), overpull: point(captured.overpull, .zero, u))
        let original = canonical(progress: captured.progress, tensions: captured.tensions)
        for i in 0..<11 {
            let baseline = 7.5 * (1 - result.tensions[LacedBootStudioGeometry.row(i)]) + (i == 0 ? 0 : 2 * sin(.pi * result.trims[i]))
            result.slacks[i] = baseline + (captured.slacks[i] - original.slacks[i]) * (1 - u)
            result.automaticSlack[i] = result.slacks[i] - baseline -
                result.velocityLag * (1 - result.tensions[LacedBootStudioGeometry.row(i)])
            result.spanAlpha[i] = captured.spanAlpha[i] + (1 - captured.spanAlpha[i]) * u
        }
        result.tipBackoff = captured.tipBackoff * (1 - u); result.automaticAgletY = captured.automaticAgletY * (1 - u)
        result.tipAlpha = 1 + (captured.tipAlpha - 1) * (1 - u)
        result.savedMix = captured.savedMix * (1 - u); updateTip(&result)
        if age <= 0 { return captured }
        return result
    }
    static func resolving(from captured: LacedBootStudioScene, rows: Int, age: Double) -> LacedBootStudioScene {
        let u = smooth(age / 0.12)
        var result = canonical(progress: Double(rows), tensions: captured.tensions)
        result.materials = captured.materials; result.soles = captured.soles; result.savedMix = captured.savedMix
        for i in 0..<11 {
            if result.trims[i] > captured.trims[i] { result.spanAlpha[i] = u }
            else if result.trims[i] < captured.trims[i], u < 1 {
                result.trims[i] = captured.trims[i]; result.slacks[i] = captured.slacks[i]
                result.spanAlpha[i] = captured.spanAlpha[i] * (1 - u)
            }
        }
        result.tipAlpha = u >= 1 ? 1 : 0
        if age <= 0 { return captured }
        return result
    }
}
