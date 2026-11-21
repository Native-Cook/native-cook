import CoreGraphics
import Foundation

/// The bundled icon paths use SVG M/L/H/V/C/A/Z; elliptical arcs retain the original endpoints and radii.
enum HourScrubSkySVG {
    static func path(_ source: String) -> CGPath {
        let rx = try! NSRegularExpression(pattern: "[a-zA-Z]|[-+]?(?:[0-9]*\\.)?[0-9]+(?:[eE][-+]?[0-9]+)?")
        let ns = source as NSString
        let tokens = rx.matches(in: source, range: NSRange(location: 0, length: ns.length)).map { ns.substring(with: $0.range) }
        var i = 0, command = "M", p = CGPoint.zero, first = CGPoint.zero
        let path = CGMutablePath()
        func value() -> Double { defer { i += 1 }; return Double(tokens[i]) ?? 0 }
        while i < tokens.count {
            if tokens[i].first?.isLetter == true { command = tokens[i]; i += 1 }
            let relative = command == command.lowercased(), c = command.uppercased()
            if c == "Z" { path.closeSubpath(); p = first; command = "M"; continue }
            guard i < tokens.count else { break }
            let base = relative ? p : .zero
            if c == "M" || c == "L" {
                let q = CGPoint(x: value() + base.x, y: value() + base.y)
                if c == "M" { path.move(to: q); first = q; command = relative ? "l" : "L" } else { path.addLine(to: q) }; p = q
            } else if c == "H" { p.x = value() + base.x; path.addLine(to: p) }
            else if c == "V" { p.y = value() + base.y; path.addLine(to: p) }
            else if c == "C" {
                let q1 = CGPoint(x: value() + base.x, y: value() + base.y), q2 = CGPoint(x: value() + base.x, y: value() + base.y), q = CGPoint(x: value() + base.x, y: value() + base.y)
                path.addCurve(to: q, control1: q1, control2: q2); p = q
            } else if c == "Q" {
                let control = CGPoint(x: value() + base.x, y: value() + base.y)
                let q = CGPoint(x: value() + base.x, y: value() + base.y)
                path.addQuadCurve(to: q, control: control); p = q
            } else if c == "A" {
                let a = value(), b = value(), phi = value(), large = value() != 0, sweep = value() != 0
                let q = CGPoint(x: value() + base.x, y: value() + base.y)
                arc(path, from: p, to: q, rx: a, ry: b, phi: phi, large: large, sweep: sweep); p = q
            } else { break }
        }
        return path
    }
    private static func arc(_ path: CGMutablePath, from p: CGPoint, to q: CGPoint, rx: Double, ry: Double, phi: Double, large: Bool, sweep: Bool) {
        if rx == 0 || ry == 0 { path.addLine(to: q); return }
        if p == q { return }
        let angle = phi * .pi / 180, co = cos(angle), si = sin(angle)
        let dx = (p.x - q.x) / 2, dy = (p.y - q.y) / 2, xp = co * dx + si * dy, yp = -si * dx + co * dy
        var a = abs(rx), b = abs(ry)
        let lambda = xp * xp / (a * a) + yp * yp / (b * b)
        if lambda > 1 { a *= sqrt(lambda); b *= sqrt(lambda) }
        let factor = (large == sweep ? -1.0 : 1.0) * sqrt(max(0, (a * a * b * b - a * a * yp * yp - b * b * xp * xp) / (a * a * yp * yp + b * b * xp * xp)))
        let cxp = factor * a * yp / b, cyp = -factor * b * xp / a
        let cx = co * cxp - si * cyp + (p.x + q.x) / 2, cy = si * cxp + co * cyp + (p.y + q.y) / 2
        let start = atan2((yp - cyp) / b, (xp - cxp) / a)
        var delta = atan2((-yp - cyp) / b, (-xp - cxp) / a) - start
        if sweep && delta < 0 { delta += 2 * .pi }; if !sweep && delta > 0 { delta -= 2 * .pi }
        let count = max(1, Int(ceil(abs(delta) / (.pi / 2))))
        func point(_ t: Double) -> CGPoint { CGPoint(x: cx + a * co * cos(t) - b * si * sin(t), y: cy + a * si * cos(t) + b * co * sin(t)) }
        func tangent(_ t: Double) -> CGPoint { CGPoint(x: -a * co * sin(t) - b * si * cos(t), y: -a * si * sin(t) + b * co * cos(t)) }
        for j in 0..<count {
            let t0 = start + delta * Double(j) / Double(count), t1 = start + delta * Double(j + 1) / Double(count), k = 4 / 3.0 * tan((t1 - t0) / 4)
            let p0 = point(t0), p1 = point(t1), v0 = tangent(t0), v1 = tangent(t1)
            path.addCurve(to: p1, control1: CGPoint(x: p0.x + k * v0.x, y: p0.y + k * v0.y), control2: CGPoint(x: p1.x - k * v1.x, y: p1.y - k * v1.y))
        }
    }
}
