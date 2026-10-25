import SwiftUI

/// The signature script and the loop's timeline. The stylus position, the knobs and the drawn line
/// are pure functions of the loop time t; the frame at t = loop is the frame at 0.
enum EtchSketchSignatureMotion {
    static let loop = 10.0

    // The poster is the finished signature: it rests, is shaken clear, and is drawn again.
    static let shakeStart = 1.2
    static let sweepStart = 1.4
    static let sweepEnd = 2.4
    static let shakeEnd = 2.6
    static let drawStart = 2.9
    static let drawEnd = 8.6
    /// Points of travel per full turn of a knob.
    static let ptPerTurn = 120.0

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

    /// The signature in screen shares: a stair-stepped "J" (the bar, the stem, the hook) and an
    /// underline flourish with a wave. stairs = true turns the move into the alternating little
    /// steps two knobs make when turned in turns.
    private static let waypoints: [(u: Double, v: Double, stairs: Bool)] = [
        (0.16, 0.24, false),
        (0.5, 0.24, false),
        (0.36, 0.24, false),
        (0.36, 0.6, false),
        (0.33, 0.68, true),
        (0.26, 0.72, true),
        (0.19, 0.69, true),
        (0.15, 0.62, true),
        (0.15, 0.8, false),
        (0.55, 0.8, false),
        (0.62, 0.67, true),
        (0.69, 0.8, true),
        (0.76, 0.67, true),
        (0.83, 0.8, true),
        (0.88, 0.8, false),
    ]

    struct Signature: Equatable {
        let pts: [CGPoint]
        let run: [Double]
        var total: Double { run[run.count - 1] }
    }

    /// The signature as points inside the screen rect, with their running length.
    static func signature(_ r: CGRect) -> Signature {
        var pts = [CGPoint(x: r.minX + waypoints[0].u * r.width, y: r.minY + waypoints[0].v * r.height)]
        let step = 5.0
        for w in waypoints.dropFirst() {
            let tx = r.minX + w.u * r.width
            let ty = r.minY + w.v * r.height
            if !w.stairs {
                pts.append(CGPoint(x: tx, y: ty))
                continue
            }
            var cx = pts[pts.count - 1].x
            var cy = pts[pts.count - 1].y
            let n = max(1, Int((max(abs(tx - cx), abs(ty - cy)) / step).rounded()))
            let dx = (tx - cx) / Double(n)
            let dy = (ty - cy) / Double(n)
            for _ in 0..<n {
                cx += dx
                pts.append(CGPoint(x: cx, y: cy))
                cy += dy
                pts.append(CGPoint(x: cx, y: cy))
            }
        }
        var run = [0.0]
        for i in 1..<pts.count {
            run.append(run[i - 1] + hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y))
        }
        return Signature(pts: pts, run: run)
    }

    struct Etch {
        /// Length of the line drawn so far (pt), the stylus position.
        var drawn = 0.0
        var sx = 0.0
        var sy = 0.0
        /// Front of the powder resettling (y, pt; very negative when none) and its glow 0…1.
        var sweep = -1e4
        var band = 0.0
        /// Shake of the toy (rad).
        var wobble = 0.0
        /// 0…1: the signature is there (enables the confirm button).
        var signed = 0.0
    }

    /// The point at running length d along the signature.
    static func pointAt(_ s: Signature, _ d: Double) -> CGPoint {
        let n = s.run.count
        if d <= 0 { return s.pts[0] }
        if d >= s.run[n - 1] { return s.pts[n - 1] }
        var k = 1
        while k < n - 1 && s.run[k] < d { k += 1 }
        let f = (d - s.run[k - 1]) / max(0.0001, s.run[k] - s.run[k - 1])
        let a = s.pts[k - 1]
        let b = s.pts[k]
        return CGPoint(x: a.x + (b.x - a.x) * f, y: a.y + (b.y - a.y) * f)
    }

    /// A shake: a quick decaying wobble, `tau` seconds in.
    static func shake(_ tau: Double, _ len: Double) -> Double {
        if tau <= 0 || tau >= len { return 0 }
        return 0.045 * exp(-3.2 * tau / len) * sin(2 * .pi * 5.5 * tau) * sin(.pi * tau / len)
    }

    /// The whole loop for a screen of top `top` and height `h`.
    static func etchAt(_ t: Double, _ s: Signature, top: Double, h: Double) -> Etch {
        // The knobs turn with a gentle start and stop; the pen keeps its pace in between.
        let u = seg(t, drawStart, drawEnd)
        let pace = u < 0.08 ? u * u / 0.16 : u > 0.92 ? 1 - (1 - u) * (1 - u) / 0.16 : u
        // Whole until the sweep has passed, then nothing until the pen sets off again.
        let drawn = t < sweepEnd ? s.total : s.total * clamp01(pace)
        let at = pointAt(s, drawn)
        // While the powder resettles, the stylus is wound back from the signature's end to its start.
        let home = easeInOut(seg(t, sweepStart, sweepEnd))
        let end = pointAt(s, s.total)
        let sweeping = t >= sweepStart && t < sweepEnd
        var e = Etch()
        e.drawn = t >= sweepEnd && t < drawStart ? 0 : drawn
        e.sx = t < sweepEnd ? end.x + (s.pts[0].x - end.x) * home : at.x
        e.sy = t < sweepEnd ? end.y + (s.pts[0].y - end.y) * home : at.y
        e.sweep = sweeping ? top - 30 + (h + 60) * home : -1e4
        e.band = sweeping ? sin(.pi * home) : 0
        e.wobble = shake(t - shakeStart, shakeEnd - shakeStart)
        e.signed = 1 - seg(t, sweepStart, sweepStart + 0.3) + seg(t, drawStart + 0.3, drawStart + 0.6)
        return e
    }

    /// The drawn line, from the signature's points up to running length `drawn`.
    static func linePath(_ s: Signature, _ drawn: Double) -> Path {
        var p = Path()
        if drawn <= 0.5 { return p }
        p.move(to: s.pts[0])
        for k in 1..<s.run.count {
            if s.run[k] <= drawn {
                p.addLine(to: s.pts[k])
                continue
            }
            let f = (drawn - s.run[k - 1]) / max(0.0001, s.run[k] - s.run[k - 1])
            let a = s.pts[k - 1]
            let b = s.pts[k]
            p.addLine(to: CGPoint(x: a.x + (b.x - a.x) * f, y: a.y + (b.y - a.y) * f))
            break
        }
        return p
    }

    /// The knob's grip: notches round the rim.
    static func knobRidges(_ r: Double) -> Path {
        var p = Path()
        let n = 28
        for i in 0..<n {
            let a = Double(i) / Double(n) * .pi * 2
            p.move(to: CGPoint(x: cos(a) * (r - 5), y: sin(a) * (r - 5)))
            p.addLine(to: CGPoint(x: cos(a) * (r - 0.8), y: sin(a) * (r - 0.8)))
        }
        return p
    }
}
