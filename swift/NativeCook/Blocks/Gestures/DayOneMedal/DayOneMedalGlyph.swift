import SwiftUI

struct DayOneMedalGlyph: View {
    let name: String
    let colour: Color
    private static let paths: [String: [Path]] = {
        guard let url = Bundle.main.url(forResource: "DayOneMedalIcons", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let icons = try? JSONDecoder().decode([String: [String]].self, from: data) else { return [:] }
        return icons.mapValues { $0.map(DayOneMedalSVG.path) }
    }()

    var body: some View {
        Canvas { context, size in
            var ink = context
            ink.scaleBy(x: size.width / 24, y: size.height / 24)
            for path in Self.paths[name] ?? [] { ink.fill(path, with: .color(colour), style: FillStyle(eoFill: true)) }
        }
        .accessibilityHidden(true)
    }
}

enum DayOneMedalSVG {
    static func path(_ source: String) -> Path {
        let expression = try! NSRegularExpression(pattern: "[A-Za-z]|[-+]?(?:[0-9]*\\.)?[0-9]+(?:[eE][-+]?[0-9]+)?")
        let string = source as NSString
        let tokens = expression.matches(in: source, range: NSRange(location: 0, length: string.length)).map { string.substring(with: $0.range) }
        var result = Path()
        var i = 0
        var current = CGPoint.zero
        var start = CGPoint.zero
        func number() -> Double {
            defer { i += 1 }
            return Double(tokens[i]) ?? 0
        }
        func point() -> CGPoint { CGPoint(x: number(), y: number()) }
        while i < tokens.count {
            let command = tokens[i]
            i += 1
            switch command {
            case "M":
                current = point()
                start = current
                result.move(to: current)
            case "L":
                current = point()
                result.addLine(to: current)
            case "H":
                current.x = number()
                result.addLine(to: current)
            case "V":
                current.y = number()
                result.addLine(to: current)
            case "C":
                let a = point()
                let b = point()
                current = point()
                result.addCurve(to: current, control1: a, control2: b)
            case "Q":
                let a = point()
                current = point()
                result.addQuadCurve(to: current, control: a)
            case "A":
                let rx = number()
                let ry = number()
                _ = number()
                let large = number() != 0
                let clockwise = number() != 0
                let end = point()
                // These supplied glyphs use circular arcs with an unrotated axis.
                let dx = (current.x - end.x) / 2
                let dy = (current.y - end.y) / 2
                let denominator = rx * rx * dy * dy + ry * ry * dx * dx
                let factor = (large == clockwise ? -1.0 : 1.0) * sqrt(max(0, (rx * rx * ry * ry - denominator) / max(denominator, 1e-12)))
                let cx = factor * rx * dy / ry + (current.x + end.x) / 2
                let cy = -factor * ry * dx / rx + (current.y + end.y) / 2
                let a = atan2((current.y - cy) / ry, (current.x - cx) / rx)
                var delta = atan2((end.y - cy) / ry, (end.x - cx) / rx) - a
                if clockwise && delta < 0 { delta += 2 * .pi }
                if !clockwise && delta > 0 { delta -= 2 * .pi }
                let steps = max(1, Int(ceil(abs(delta) / (.pi / 2))))
                for step in 0..<steps {
                    let begin = a + delta * Double(step) / Double(steps)
                    let finish = a + delta * Double(step + 1) / Double(steps)
                    let bend = 4 / 3 * tan((finish - begin) / 4)
                    let first = CGPoint(x: cx + rx * (cos(begin) - bend * sin(begin)),
                                        y: cy + ry * (sin(begin) + bend * cos(begin)))
                    let second = CGPoint(x: cx + rx * (cos(finish) + bend * sin(finish)),
                                         y: cy + ry * (sin(finish) - bend * cos(finish)))
                    result.addCurve(to: CGPoint(x: cx + rx * cos(finish), y: cy + ry * sin(finish)),
                                    control1: first, control2: second)
                }
                current = end
            case "Z", "z":
                result.closeSubpath()
                current = start
            default: break
            }
        }
        return result
    }
}
