import SwiftUI

// Every number the stack uses, ported from the React Native block's motion.ts.

enum NotificationStackExpandMotion {
    /// Seconds. One autoPlay loop; rest at both ends (0–1.0 and 7.85–9).
    static let loop: Double = 9
    /// autoPlay: 2 s after the last touch it eases to rest and restarts at t = 0.
    static let resumeAfter: Double = 2

    enum T {
        static let ghostIn = 0.55
        static let tapOpen = 1.0
        static let expand = 1.05
        static let swipeStart = 2.55
        static let swipeEnd = 3.15
        static let flingSpan = 0.24
        static let gap = 3.3
        static let tapLess = 4.55
        static let collapse = 4.6
        static let peekStart = 5.75
        static let peekRelease = 6.45
        static let arrive = 7.25
        static let arriveSpan = 0.5
        static let rest = 7.85
    }

    static let cardH: Double = 84
    static let gap: Double = 8
    static let peek: Double = 9
    static let depthScale: Double = 0.05
    static let maxDepth: Double = 2
    static let headerH: Double = 44
    static let radius: Double = 20
    static let maxCards = 4

    static let expandSpring = NotificationStackExpandSpring(response: 0.5, ratio: 0.74)
    static let collapseSpring = NotificationStackExpandSpring(response: 0.42, ratio: 0.86)
    static let stagger: Double = 0.05
    static let gapSpring = NotificationStackExpandSpring(response: 0.42, ratio: 0.8)
    static let pushSpring = NotificationStackExpandSpring(response: 0.45, ratio: 0.8)
    static let swipeSpring = NotificationStackExpandSpring(response: 0.38, ratio: 0.82)
    static let peekSpring = NotificationStackExpandSpring(response: 0.38, ratio: 0.62)

    static let swipeDrag: Double = 150
    static let clearW: Double = 84
    static let flingFraction: Double = 0.42
    static let flingVelocity: Double = 900
    static let openFraction: Double = 0.6
    static let rubberRightK: Double = 0.3

    static let peekMax: Double = 0.32
    static let peekRange: Double = 150
    static let peekOpenDistance: Double = 110
    static let peekOpenVelocity: Double = 700
    static let ghostPeek: Double = 64

    static let arriveDrop: Double = 22
    static let arriveScale: Double = 0.03

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func mix(_ u: Double, _ a: Double, _ b: Double) -> Double { a + (b - a) * u }
    static func seg(_ t: Double, _ start: Double, _ span: Double) -> Double { clamp01((t - start) / span) }
    static func easeOutCubic(_ x: Double) -> Double { let u = 1 - x; return 1 - u * u * u }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    /// Reanimated's default `withTiming` curve.
    static func easeInOutQuad(_ x: Double) -> Double {
        x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2
    }

    /// The closed-form step response of a spring, so autoPlay is a pure function of t.
    static func springAt(_ t: Double, _ s: NotificationStackExpandSpring) -> Double {
        if t <= 0 { return 0 }
        let w = 2 * Double.pi / s.response
        let wd = w * sqrt(1 - s.ratio * s.ratio)
        return 1 - exp(-s.ratio * w * t) * (cos(wd * t) + (s.ratio * w / wd) * sin(wd * t))
    }

    /// Collapsed: the top of a card `depth` places back (scaled about its centre so its bottom edge peeks).
    static func collapsedY(_ depth: Double) -> Double {
        let d = min(max(depth, 0), maxDepth)
        let k = 1 - depthScale * d
        return cardH + d * peek - cardH * (1 + k) / 2
    }

    /// Expanded: the top of the card in list slot `slot`, under the header.
    static func expandedY(_ slot: Double) -> Double { headerH + slot * (cardH + gap) }

    /// How tall the group stands for `count` cards (fractional while one leaves) at fan-out `e`.
    static func groupHeight(_ e: Double, _ count: Double) -> Double {
        if count <= 0 { return 0 }
        let n = count
        let closed = n < 1 ? n * cardH : cardH + min(n - 1, maxDepth) * peek
        let open = n < 1 ? n * (headerH + cardH) : headerH + n * cardH + (n - 1) * gap
        return mix(e, closed, open)
    }

    static func peekFromDrag(_ dy: Double) -> Double {
        if dy <= 0 { return 0 }
        return peekMax * (1 - exp(-dy / peekRange))
    }

    static func rubberRight(_ x: Double) -> Double {
        if x <= 0 { return x }
        return x * rubberRightK / (1 + x / 220)
    }
}

struct NotificationStackExpandSpring {
    let response: Double
    let ratio: Double
}

/// One animated number, sampled against the timeline clock: a spring (with the finger's velocity), a
/// timing on Reanimated's default curve, or a value set at once. Optional delay holds the value first.
struct NotificationStackExpandTrack {
    private enum Kind { case still, spring(NotificationStackExpandSpring), timing(Double) }
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
        case .timing(let duration):
            if duration <= 0 { return to }
            let u = min(tau / duration, 1)
            return from + (to - from) * NotificationStackExpandMotion.easeInOutQuad(u)
        case .spring(let s):
            let w = 2 * Double.pi / s.response
            let z = s.ratio
            let wd = w * sqrt(1 - z * z)
            let d = from - to
            let b = (v0 + z * w * d) / wd
            return to + exp(-z * w * tau) * (d * cos(wd * tau) + b * sin(wd * tau))
        }
    }

    func velocity(at date: Date) -> Double {
        (value(at: date.addingTimeInterval(0.004)) - value(at: date.addingTimeInterval(-0.004))) / 0.008
    }

    /// When the value stops moving (to within a hair).
    var settles: Date {
        switch kind {
        case .still:
            return start.addingTimeInterval(delay)
        case .timing(let duration):
            return start.addingTimeInterval(delay + duration)
        case .spring(let s):
            let w = 2 * Double.pi / s.response
            let amp = abs(from - to) + abs(v0) / w + 1e-9
            let tail = max(0, log(amp / 0.0005) / (s.ratio * w))
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

    mutating func spring(to target: Double, _ s: NotificationStackExpandSpring, velocity: Double = 0,
                         delay: Double = 0, at date: Date) {
        let current = value(at: date)
        let v = delay > 0 ? 0 : (velocity != 0 ? velocity : 0)
        from = current
        to = target
        v0 = v
        start = date
        self.delay = delay
        kind = .spring(s)
    }

    mutating func timing(to target: Double, duration: Double, delay: Double = 0, at date: Date) {
        from = value(at: date)
        to = target
        v0 = 0
        start = date
        self.delay = delay
        kind = .timing(duration)
    }
}

/// A tiny SVG path reader for the block's glyphs (absolute M L H V C Q A Z only).
enum NotificationStackExpandSVG {
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
            } else if ch == "-" && !current.isEmpty && current.last != "e" {
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

    /// A circular SVG arc, through its centre parameterization.
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
        // SVG's sweep = 1 runs clockwise on screen (y down), which is SwiftUI's clockwise: false.
        path.addArc(center: CGPoint(x: cx, y: cy), radius: r, startAngle: .radians(Double(a0)),
                    endAngle: .radians(Double(a1)), clockwise: !sweep)
    }
}
