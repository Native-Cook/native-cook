import SwiftUI

// Every number the tour uses, ported from the React Native block's motion.ts.

struct CoachMarkSpotlightBox {
    var x: Double
    var y: Double
    var w: Double
    var h: Double
    var r: Double

    var rect: CGRect { CGRect(x: x, y: y, width: w, height: h) }
}

struct CoachMarkSpotlightSpring {
    let response: Double
    let ratio: Double
}

enum CoachMarkSpotlightMotion {
    /// Seconds. One autoPlay loop; rest at both ends on step 1 (0–1.05 and 8.35–9).
    static let loop: Double = 9
    static let resumeAfter: Double = 2

    enum T {
        static let ghostIn = 0.5
        static let next1 = 1.05
        static let morph1 = 1.15
        static let tapCard = 3.25
        static let morph2 = 3.35
        static let dragStart = 5.0
        static let dragEnd = 5.45
        static let morph3 = 5.5
        static let skip = 6.8
        static let fadeOut = 6.85
        static let fadeOutSpan = 0.4
        static let reset = 7.3
        static let intro = 7.45
        static let introSpan = 0.85
        static let tipIn = 7.95
        static let tipInSpan = 0.4
        static let rest = 8.35
    }

    static let morph: Double = 0.75
    static let tipLag: Double = 0.07
    static let travelSqueeze: Double = 0.3
    static let morphSpring = CoachMarkSpotlightSpring(response: 0.55, ratio: 0.82)
    static let tipSpring = CoachMarkSpotlightSpring(response: 0.6, ratio: 0.78)
    static let dragSpring = CoachMarkSpotlightSpring(response: 0.4, ratio: 0.7)
    static let irisSpring = CoachMarkSpotlightSpring(response: 0.6, ratio: 0.8)
    static let irisScale: Double = 3.2

    static let pad: Double = 8
    static let softEdge: Double = 5
    static let ringInset: Double = 3
    static let pulsePeriod: Double = 1.4
    static let pulseGrow: Double = 14
    static let pulseAlpha: Double = 0.5
    static let holds: [(Double, Double)] = [(8.35, 10.0), (1.95, 3.2), (4.15, 4.95), (6.3, 6.78)]

    static let tipMaxW: Double = 340
    static let tipH: Double = 176
    static let tipGap: Double = 14
    static let arrowW: Double = 20
    static let arrowH: Double = 9
    static let tipFadeScale: Double = 0.88
    static let tipOpacityGain: Double = 3
    static let swipeStep: Double = 64
    static let swipeVelocity: Double = 500
    static let swipeRubber: Double = 0.45
    static let ghostDrag: Double = 78

    static let dim = Color.rgba(16, 21, 28, 0.66)

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func mix(_ u: Double, _ a: Double, _ b: Double) -> Double { a + (b - a) * u }
    static func seg(_ t: Double, _ start: Double, _ span: Double) -> Double { clamp01((t - start) / span) }
    static func easeOutCubic(_ x: Double) -> Double { let u = 1 - x; return 1 - u * u * u }
    static func easeInCubic(_ x: Double) -> Double { x * x * x }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func easeInOutQuad(_ x: Double) -> Double {
        x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2
    }

    static func springAt(_ t: Double, _ s: CoachMarkSpotlightSpring) -> Double {
        if t <= 0 { return 0 }
        let w = 2 * Double.pi / s.response
        let wd = w * sqrt(1 - s.ratio * s.ratio)
        return 1 - exp(-s.ratio * w * t) * (cos(wd * t) + (s.ratio * w / wd) * sin(wd * t))
    }

    /// Piecewise-linear value of a per-step list at a fractional step p (clamped to the ends).
    static func along(_ values: [Double], _ p: Double) -> Double {
        let last = values.count - 1
        if p <= 0 { return values[0] }
        if p >= Double(last) { return values[last] }
        let i = Int(floor(p))
        return mix(p - Double(i), values[i], values[i + 1])
    }

    /// The cutout at progress p: interpolated between neighbouring targets, narrowed mid-travel, then
    /// opened by `iris` (1 = a circle irisScale times the first target around it, 0 = onto it).
    static func cutoutAt(_ targets: [CoachMarkSpotlightBox], _ p: Double, _ iris: Double) -> CoachMarkSpotlightBox {
        let last = targets.count - 1
        let q = min(max(p, 0), Double(last))
        let i = last == 0 ? 0 : (q >= Double(last) ? last - 1 : Int(floor(q)))
        let u = last == 0 ? 0 : q - Double(i)
        let a = targets[i]
        let b = targets[min(i + 1, last)]
        let squeeze = 1 - travelSqueeze * sin(Double.pi * u)
        let cx = mix(u, a.x + a.w / 2, b.x + b.w / 2)
        let cy = mix(u, a.y + a.h / 2, b.y + b.h / 2)
        var w = mix(u, a.w, b.w) * squeeze
        var h = mix(u, a.h, b.h) * squeeze
        var r = min(mix(u, a.r, b.r) * squeeze, min(w, h) / 2)
        var x = cx - w / 2
        var y = cy - h / 2
        if iris > 0 {
            let f = targets[0]
            let fx = f.x + f.w / 2
            let fy = f.y + f.h / 2
            let big = max(f.w, f.h) * irisScale
            w = mix(iris, w, big)
            h = mix(iris, h, big)
            r = mix(iris, r, big / 2)
            x = mix(iris, x, fx - big / 2)
            y = mix(iris, y, fy - big / 2)
        }
        return CoachMarkSpotlightBox(x: x, y: y, w: w, h: h, r: r)
    }
}

/// One animated number sampled against the timeline clock: a spring, a timing on a curve, or a value.
struct CoachMarkSpotlightTrack {
    enum Curve { case quad, inOutCubic, inCubic, outCubic, linear }
    private enum Kind { case still, spring(CoachMarkSpotlightSpring), timing(Double, Curve) }
    private var kind: Kind = .still
    private var from: Double
    private var to: Double
    private var v0: Double = 0
    private var start: Date = .distantPast
    private var delay: Double = 0

    init(_ value: Double) {
        from = value
        to = value
    }

    var target: Double { to }

    func value(at date: Date) -> Double {
        let tau = date.timeIntervalSince(start) - delay
        if tau <= 0 { return from }
        switch kind {
        case .still:
            return to
        case .timing(let duration, let curve):
            if duration <= 0 { return to }
            let u = min(tau / duration, 1)
            let M = CoachMarkSpotlightMotion.self
            let k: Double
            switch curve {
            case .quad: k = M.easeInOutQuad(u)
            case .inOutCubic: k = M.easeInOutCubic(u)
            case .inCubic: k = M.easeInCubic(u)
            case .outCubic: k = M.easeOutCubic(u)
            case .linear: k = u
            }
            return from + (to - from) * k
        case .spring(let s):
            let w = 2 * Double.pi / s.response
            let z = s.ratio
            let wd = w * sqrt(1 - z * z)
            let d = from - to
            let b = (v0 + z * w * d) / wd
            return to + exp(-z * w * tau) * (d * cos(wd * tau) + b * sin(wd * tau))
        }
    }

    /// When the value is within `tolerance` of its target for good.
    func settles(within tolerance: Double = 0.0005) -> Date {
        switch kind {
        case .still:
            return start.addingTimeInterval(delay)
        case .timing(let duration, _):
            return start.addingTimeInterval(delay + duration)
        case .spring(let s):
            let w = 2 * Double.pi / s.response
            let amp = abs(from - to) + abs(v0) / w + 1e-9
            let tail = max(0, log(amp / tolerance) / (s.ratio * w))
            return start.addingTimeInterval(delay + tail)
        }
    }

    mutating func set(_ value: Double, at date: Date = .now) {
        kind = .still
        from = value
        to = value
        v0 = 0
        start = date
        delay = 0
    }

    mutating func spring(to target: Double, _ s: CoachMarkSpotlightSpring, velocity: Double = 0, delay: Double = 0, at date: Date) {
        from = value(at: date)
        to = target
        v0 = delay > 0 ? 0 : velocity
        start = date
        self.delay = delay
        kind = .spring(s)
    }

    mutating func timing(to target: Double, duration: Double, curve: Curve = .quad, delay: Double = 0, at date: Date) {
        from = value(at: date)
        to = target
        v0 = 0
        start = date
        self.delay = delay
        kind = .timing(duration, curve)
    }
}

/// A tiny SVG path reader for the block's glyphs (absolute M L H V C Q A Z only).
enum CoachMarkSpotlightSVG {
    static func path(_ d: String) -> Path {
        var path = Path()
        var tokens: [String] = []
        var current = ""
        func flush() { if !current.isEmpty { tokens.append(current); current = "" } }
        for ch in d {
            if ch.isLetter {
                flush()
                tokens.append(String(ch))
            } else if ch == " " || ch == "," {
                flush()
            } else if ch == "-" && !current.isEmpty {
                flush()
                current.append(ch)
            } else {
                current.append(ch)
            }
        }
        flush()
        var i = 0
        var cmd = "M"
        var p = CGPoint.zero
        var startPoint = CGPoint.zero
        func num() -> CGFloat {
            defer { i += 1 }
            return CGFloat(Double(tokens[i]) ?? 0)
        }
        while i < tokens.count {
            if let c = tokens[i].first, c.isLetter {
                cmd = tokens[i]
                i += 1
                if cmd == "Z" {
                    path.closeSubpath()
                    p = startPoint
                    continue
                }
            }
            switch cmd {
            case "M":
                p = CGPoint(x: num(), y: num())
                path.move(to: p)
                startPoint = p
                cmd = "L"
            case "L":
                p = CGPoint(x: num(), y: num())
                path.addLine(to: p)
            case "H":
                p = CGPoint(x: num(), y: p.y)
                path.addLine(to: p)
            case "V":
                p = CGPoint(x: p.x, y: num())
                path.addLine(to: p)
            case "C":
                let c1 = CGPoint(x: num(), y: num())
                let c2 = CGPoint(x: num(), y: num())
                p = CGPoint(x: num(), y: num())
                path.addCurve(to: p, control1: c1, control2: c2)
            case "Q":
                let c = CGPoint(x: num(), y: num())
                p = CGPoint(x: num(), y: num())
                path.addQuadCurve(to: p, control: c)
            case "A":
                let r = num()
                _ = num()
                _ = num()
                let large = num() != 0
                let sweep = num() != 0
                let end = CGPoint(x: num(), y: num())
                addArc(&path, from: p, to: end, radius: r, large: large, sweep: sweep)
                p = end
            default:
                i += 1
            }
        }
        return path
    }

    private static func addArc(_ path: inout Path, from a: CGPoint, to b: CGPoint, radius: CGFloat, large: Bool, sweep: Bool) {
        let dx = (a.x - b.x) / 2
        let dy = (a.y - b.y) / 2
        let d2 = dx * dx + dy * dy
        guard d2 > 0 else { return }
        var r = radius
        if d2 > r * r { r = sqrt(d2) }
        let k = sqrt(max(0, (r * r - d2) / d2)) * (large == sweep ? -1 : 1)
        let cx = k * dy + (a.x + b.x) / 2
        let cy = -k * dx + (a.y + b.y) / 2
        let a0 = atan2(a.y - cy, a.x - cx)
        var a1 = atan2(b.y - cy, b.x - cx)
        if sweep && a1 < a0 { a1 += 2 * .pi }
        if !sweep && a1 > a0 { a1 -= 2 * .pi }
        path.addArc(center: CGPoint(x: cx, y: cy), radius: r, startAngle: .radians(Double(a0)),
                    endAngle: .radians(Double(a1)), clockwise: !sweep)
    }
}

/// An SVG glyph stroked like react-native-svg (round caps and joins) in a box of `size`.
struct CoachMarkSpotlightGlyph: View {
    let d: String
    let size: CGSize
    var color: Color = .white
    var width: CGFloat = 1.7
    var fill = false

    var body: some View {
        let path = CoachMarkSpotlightSVG.path(d)
        Group {
            if fill {
                path.fill(color)
            } else {
                path.stroke(color, style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}
