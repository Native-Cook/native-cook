import SwiftUI

enum GlowGestureGuideKind {
    case tap, hold, edge, swipe
}

/// A gesture's hand icon, drawn in a 32 × 32 box and scaled to the canvas; `since` is the seconds
/// since its row was chosen, which plays its little performance.
struct GlowGestureGuideIcon {
    // A pointing hand, and what each gesture adds around it.
    private static let hand = path(
        "M11 18V10.5C11 9.4 11.9 8.5 13 8.5C14.1 8.5 15 9.4 15 10.5V16.5"
            + "M15 15.5C15 14.6 15.7 13.9 16.6 13.9C17.5 13.9 18.2 14.6 18.2 15.5V17"
            + "M18.2 16.3C18.2 15.4 18.9 14.7 19.8 14.7C20.7 14.7 21.4 15.4 21.4 16.3V17.5"
            + "M21.4 17.2C21.4 16.3 22.1 15.6 23 15.6C23.9 15.6 24.6 16.3 24.6 17.2V22C24.6 26.4 21.6 29 17.6 29H16.4"
            + "C14 29 12.4 28 11.1 26.3L7.6 21.7C6.9 20.8 7.1 19.6 7.9 19C8.7 18.4 9.8 18.5 10.4 19.3L11 20.1V18"
    )
    private static let left = path("M7.5 4L4.5 7L7.5 10")
    private static let right = path("M18.5 4L21.5 7L18.5 10")
    private static let box = 32.0
    private static let line = 1.5
    /// The fingertip, the centre of the dots, arcs and ripples.
    private static let tip = CGPoint(x: 13, y: 10.5)
    private static let dots: [Double] = [-160, -125, -90, -55, -20]
    private static let dotR = 5.5
    private static let arcs = [4.8, 7.6]
    private static let arcFrom = -160.0
    private static let arcTo = -20.0
    private static let edgeX = 3.5
    // Each gesture's little performance, in seconds after its row is chosen.
    static let playFor = 1.3
    private static let playAt = 0.2

    static func draw(_ kind: GlowGestureGuideKind, in context: GraphicsContext, size: Double, since: Double, color: Color) {
        var c = context
        c.scaleBy(x: size / box, y: size / box)
        let stroke = StrokeStyle(lineWidth: line, lineCap: .round, lineJoin: .round)
        let shading = GraphicsContext.Shading.color(color)

        // Edge: the bar flashes twice and the hand nudges towards it.
        let edgeWave = wave(since, playAt, 0.8)
        let flash = edgeWave < 0 ? 0 : 0.5 - 0.5 * cos(4 * .pi * edgeWave)

        var handLayer = c
        if kind == .edge { handLayer.translateBy(x: 3 - 2 * flash, y: 0) }
        handLayer.stroke(hand, with: shading, style: stroke)
        if kind == .tap || kind == .edge {
            for deg in kind == .tap ? dots : Array(dots.prefix(3)) {
                let a = deg * .pi / 180
                let p = CGPoint(x: tip.x + dotR * cos(a), y: tip.y + dotR * sin(a))
                handLayer.fill(Path(ellipseIn: CGRect(x: p.x - 0.8, y: p.y - 0.8, width: 1.6, height: 1.6)), with: shading)
            }
        }

        switch kind {
        case .tap:
            // Two ripples from the fingertip.
            for k in 0..<2 {
                let u = wave(since, playAt + 0.45 * Double(k), 0.6)
                guard u >= 0 else { continue }
                let r = 2 + 8 * u
                var ring = c
                ring.opacity = 0.9 * pow(1 - u, 2)
                ring.stroke(Path(ellipseIn: CGRect(x: tip.x - r, y: tip.y - 1 - r, width: r * 2, height: r * 2)), with: shading, lineWidth: 1.2)
            }
        case .hold:
            // Two arcs leave the outer one and fade.
            c.stroke(arc(arcs[0]), with: shading, style: stroke)
            c.stroke(arc(arcs[1]), with: shading, style: stroke)
            for k in 0..<2 {
                let u = wave(since, playAt + 0.5 * Double(k), 0.7)
                guard u >= 0 else { continue }
                var ring = c
                ring.opacity = 0.9 * (1 - u)
                ring.stroke(arc(arcs[1] + 5 * u), with: shading, style: stroke)
            }
        case .edge:
            var bar = Path()
            bar.move(to: CGPoint(x: edgeX, y: 5))
            bar.addLine(to: CGPoint(x: edgeX, y: 29))
            var layer = c
            layer.opacity = 1 - 0.7 * flash
            layer.stroke(bar, with: shading, style: stroke)
        case .swipe:
            // The left chevron nudges left, then the right one right.
            var l = c
            l.translateBy(x: -3 * bump(since - playAt, 0.45), y: 0)
            l.stroke(left, with: shading, style: stroke)
            var r = c
            r.translateBy(x: 3 * bump(since - playAt - 0.45, 0.45), y: 0)
            r.stroke(right, with: shading, style: stroke)
        }
    }

    /// An arc round the fingertip at radius `r`, the same 16 steps the other versions draw.
    private static func arc(_ r: Double) -> Path {
        var path = Path()
        for k in 0...16 {
            let a = (arcFrom + (arcTo - arcFrom) * Double(k) / 16) * .pi / 180
            let p = CGPoint(x: tip.x + r * cos(a), y: tip.y + r * sin(a))
            if k == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        return path
    }

    private static func bump(_ s: Double, _ length: Double) -> Double {
        s >= 0 && s <= length ? sin(.pi * s / length) : 0
    }

    /// A wave starting at `at`: 0 → 1 over `length`, or -1 outside it.
    private static func wave(_ t: Double, _ at: Double, _ length: Double) -> Double {
        let u = (t - at) / length
        return u >= 0 && u <= 1 ? u : -1
    }

    /// A path from SVG commands M, L, H, V, C and Z, all absolute.
    private static func path(_ d: String) -> Path {
        var tokens: [String] = []
        var token = ""
        for c in d {
            if c.isLetter || c == " " {
                if !token.isEmpty { tokens.append(token) }
                token = ""
                if c.isLetter { tokens.append(String(c)) }
            } else {
                token.append(c)
            }
        }
        if !token.isEmpty { tokens.append(token) }
        var path = Path()
        var point = CGPoint.zero
        var i = 0
        var command = "M"
        func next() -> Double {
            defer { i += 1 }
            return Double(tokens[i]) ?? 0
        }
        while i < tokens.count {
            // A command letter, or more numbers for the one before it.
            if Double(tokens[i]) == nil {
                command = tokens[i]
                i += 1
            }
            switch command {
            case "M": point = CGPoint(x: next(), y: next()); path.move(to: point)
            case "L": point = CGPoint(x: next(), y: next()); path.addLine(to: point)
            case "H": point.x = next(); path.addLine(to: point)
            case "V": point.y = next(); path.addLine(to: point)
            case "C":
                let c1 = CGPoint(x: next(), y: next())
                let c2 = CGPoint(x: next(), y: next())
                point = CGPoint(x: next(), y: next())
                path.addCurve(to: point, control1: c1, control2: c2)
            default: path.closeSubpath()
            }
        }
        return path
    }
}
