import SwiftUI

// The source SVG paths are kept verbatim. This small reader supports the commands used
// by Icons.tsx, including endpoint-form elliptical arcs and their sweep/large-arc flags.
enum GiftCardPickerSVG {
    static func path(_ source: String) -> Path {
        let expression = try! NSRegularExpression(pattern: "[a-zA-Z]|[-+]?(?:[0-9]*\\.)?[0-9]+(?:[eE][-+]?[0-9]+)?")
        let ns = source as NSString
        let tokens = expression.matches(in: source, range: NSRange(location: 0, length: ns.length)).map { ns.substring(with: $0.range) }
        var path = Path()
        var point = CGPoint.zero
        var start = CGPoint.zero
        var index = 0
        var command = "M"
        func number() -> CGFloat {
            defer { index += 1 }
            return CGFloat(Double(tokens[index])!)
        }
        func pair(relative: Bool) -> CGPoint {
            let x = number()
            let y = number()
            return CGPoint(x: x + (relative ? point.x : 0), y: y + (relative ? point.y : 0))
        }
        while index < tokens.count {
            if tokens[index].first?.isLetter == true {
                command = tokens[index]
                index += 1
                if command.uppercased() == "Z" { path.closeSubpath(); point = start; continue }
            }
            let relative = command == command.lowercased()
            switch command.uppercased() {
            case "M":
                point = pair(relative: relative)
                start = point
                path.move(to: point)
                command = relative ? "l" : "L"
            case "L": point = pair(relative: relative); path.addLine(to: point)
            case "H": point.x = number() + (relative ? point.x : 0); path.addLine(to: point)
            case "V": point.y = number() + (relative ? point.y : 0); path.addLine(to: point)
            case "C":
                let c1 = pair(relative: relative)
                let c2 = pair(relative: relative)
                let end = pair(relative: relative)
                path.addCurve(to: end, control1: c1, control2: c2)
                point = end
            case "A":
                let rx = number()
                let ry = number()
                let rotation = number()
                let large = number() != 0
                let sweep = number() != 0
                let end = pair(relative: relative)
                arc(&path, from: point, to: end, rx: rx, ry: ry, rotation: rotation, large: large, sweep: sweep)
                point = end
            default: preconditionFailure("Unsupported Gift Card Picker SVG command \(command)")
            }
        }
        return path
    }

    private static func arc(_ path: inout Path, from: CGPoint, to: CGPoint, rx inputRX: CGFloat, ry inputRY: CGFloat, rotation: CGFloat, large: Bool, sweep: Bool) {
        guard from != to else { return }
        var rx = abs(inputRX)
        var ry = abs(inputRY)
        guard rx > 0, ry > 0 else { path.addLine(to: to); return }
        let phi = rotation * .pi / 180
        let cosPhi = cos(phi)
        let sinPhi = sin(phi)
        let dx = (from.x - to.x) / 2
        let dy = (from.y - to.y) / 2
        let xp = cosPhi * dx + sinPhi * dy
        let yp = -sinPhi * dx + cosPhi * dy
        let radii = xp * xp / (rx * rx) + yp * yp / (ry * ry)
        if radii > 1 { let s = sqrt(radii); rx *= s; ry *= s }
        let denominator = rx * rx * yp * yp + ry * ry * xp * xp
        let numerator = max(0, rx * rx * ry * ry - denominator)
        let factor = (large == sweep ? -1.0 : 1.0) * sqrt(numerator / denominator)
        let cxp = factor * rx * yp / ry
        let cyp = -factor * ry * xp / rx
        let cx = cosPhi * cxp - sinPhi * cyp + (from.x + to.x) / 2
        let cy = sinPhi * cxp + cosPhi * cyp + (from.y + to.y) / 2
        let begin = atan2((yp - cyp) / ry, (xp - cxp) / rx)
        let finish = atan2((-yp - cyp) / ry, (-xp - cxp) / rx)
        var delta = finish - begin
        if !sweep && delta > 0 { delta -= 2 * .pi }
        if sweep && delta < 0 { delta += 2 * .pi }
        let count = max(1, Int(ceil(abs(delta) / (.pi / 2))))
        func point(_ angle: CGFloat) -> CGPoint {
            CGPoint(x: cx + rx * cosPhi * cos(angle) - ry * sinPhi * sin(angle), y: cy + rx * sinPhi * cos(angle) + ry * cosPhi * sin(angle))
        }
        func derivative(_ angle: CGFloat) -> CGPoint {
            CGPoint(x: -rx * cosPhi * sin(angle) - ry * sinPhi * cos(angle), y: -rx * sinPhi * sin(angle) + ry * cosPhi * cos(angle))
        }
        for i in 0..<count {
            let a = begin + delta * CGFloat(i) / CGFloat(count)
            let b = begin + delta * CGFloat(i + 1) / CGFloat(count)
            let alpha = 4 / 3 * tan((b - a) / 4)
            let p1 = point(a)
            let p2 = point(b)
            let d1 = derivative(a)
            let d2 = derivative(b)
            path.addCurve(to: p2, control1: CGPoint(x: p1.x + alpha * d1.x, y: p1.y + alpha * d1.y), control2: CGPoint(x: p2.x - alpha * d2.x, y: p2.y - alpha * d2.y))
        }
    }
}
