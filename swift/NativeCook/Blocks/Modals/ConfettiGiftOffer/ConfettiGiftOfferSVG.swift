import SwiftUI

/// Turns the SVG path strings of art.tsx and ConfettiGiftOffer.tsx into SwiftUI paths, so the bow, the
/// sparkles, the logos and the close mark are the same vectors React Native draws with react-native-svg.
/// Handles M L H V C S Q A Z in absolute and relative form, with implicit repeats.
enum ConfettiGiftOfferSVG {
    static func path(_ data: String) -> Path {
        var parser = Parser(Array(data.unicodeScalars))
        return parser.parse()
    }

    private struct Parser {
        private let source: [Unicode.Scalar]
        private var index = 0
        private var path = Path()
        private var current = CGPoint.zero
        private var subpathStart = CGPoint.zero
        private var lastCubicControl: CGPoint?
        private var lastQuadControl: CGPoint?

        init(_ source: [Unicode.Scalar]) {
            self.source = source
        }

        mutating func parse() -> Path {
            var command: Character = " "
            while true {
                skipSeparators()
                guard index < source.count else { break }
                let scalar = source[index]
                if Self.isCommand(scalar) {
                    command = Character(scalar)
                    index += 1
                    if command == "Z" || command == "z" {
                        path.closeSubpath()
                        current = subpathStart
                        lastCubicControl = nil
                        lastQuadControl = nil
                        continue
                    }
                } else if command == " " || command == "Z" || command == "z" {
                    break
                }
                guard run(command) else { break }
                // After the first pair a moveto continues as a lineto.
                if command == "M" { command = "L" } else if command == "m" { command = "l" }
            }
            return path
        }

        private static func isCommand(_ scalar: Unicode.Scalar) -> Bool {
            "MmLlHhVvCcSsQqAaZz".unicodeScalars.contains(scalar)
        }

        private mutating func skipSeparators() {
            while index < source.count {
                let scalar = source[index]
                if scalar == " " || scalar == "," || scalar == "\n" || scalar == "\t" || scalar == "\r" {
                    index += 1
                } else {
                    break
                }
            }
        }

        private mutating func number() -> CGFloat? {
            skipSeparators()
            let begin = index
            var text = ""
            func peek() -> Unicode.Scalar? { index < source.count ? source[index] : nil }
            if let sign = peek(), sign == "-" || sign == "+" {
                text.unicodeScalars.append(sign)
                index += 1
            }
            var digits = 0
            while let scalar = peek(), scalar.value >= 48 && scalar.value <= 57 {
                text.unicodeScalars.append(scalar)
                index += 1
                digits += 1
            }
            if let dot = peek(), dot == "." {
                text.unicodeScalars.append(dot)
                index += 1
                while let scalar = peek(), scalar.value >= 48 && scalar.value <= 57 {
                    text.unicodeScalars.append(scalar)
                    index += 1
                    digits += 1
                }
            }
            guard digits > 0 else {
                index = begin
                return nil
            }
            if let marker = peek(), marker == "e" || marker == "E" {
                var exponent = "e"
                var probe = index + 1
                if probe < source.count, source[probe] == "-" || source[probe] == "+" {
                    exponent.unicodeScalars.append(source[probe])
                    probe += 1
                }
                var exponentDigits = 0
                while probe < source.count, source[probe].value >= 48 && source[probe].value <= 57 {
                    exponent.unicodeScalars.append(source[probe])
                    probe += 1
                    exponentDigits += 1
                }
                if exponentDigits > 0 {
                    text += exponent
                    index = probe
                }
            }
            guard let value = Double(text) else { return nil }
            return CGFloat(value)
        }

        /// Arc flags are a single 0 or 1 and may touch the next number.
        private mutating func flag() -> Bool? {
            skipSeparators()
            guard index < source.count else { return nil }
            let scalar = source[index]
            guard scalar == "0" || scalar == "1" else { return nil }
            index += 1
            return scalar == "1"
        }

        private mutating func numbers(_ count: Int) -> [CGFloat]? {
            var values: [CGFloat] = []
            for _ in 0..<count {
                guard let value = number() else { return nil }
                values.append(value)
            }
            return values
        }

        private mutating func run(_ command: Character) -> Bool {
            let relative = command.isLowercase
            let origin = relative ? current : .zero
            switch command {
            case "M", "m":
                guard let n = numbers(2) else { return false }
                let point = CGPoint(x: origin.x + n[0], y: origin.y + n[1])
                path.move(to: point)
                current = point
                subpathStart = point
                lastCubicControl = nil
                lastQuadControl = nil
            case "L", "l":
                guard let n = numbers(2) else { return false }
                line(to: CGPoint(x: origin.x + n[0], y: origin.y + n[1]))
            case "H", "h":
                guard let n = numbers(1) else { return false }
                line(to: CGPoint(x: origin.x + n[0], y: current.y))
            case "V", "v":
                guard let n = numbers(1) else { return false }
                line(to: CGPoint(x: current.x, y: origin.y + n[0]))
            case "C", "c":
                guard let n = numbers(6) else { return false }
                let c1 = CGPoint(x: origin.x + n[0], y: origin.y + n[1])
                let c2 = CGPoint(x: origin.x + n[2], y: origin.y + n[3])
                let end = CGPoint(x: origin.x + n[4], y: origin.y + n[5])
                path.addCurve(to: end, control1: c1, control2: c2)
                current = end
                lastCubicControl = c2
                lastQuadControl = nil
            case "S", "s":
                guard let n = numbers(4) else { return false }
                let c1 = lastCubicControl.map { CGPoint(x: 2 * current.x - $0.x, y: 2 * current.y - $0.y) } ?? current
                let c2 = CGPoint(x: origin.x + n[0], y: origin.y + n[1])
                let end = CGPoint(x: origin.x + n[2], y: origin.y + n[3])
                path.addCurve(to: end, control1: c1, control2: c2)
                current = end
                lastCubicControl = c2
                lastQuadControl = nil
            case "Q", "q":
                guard let n = numbers(4) else { return false }
                let control = CGPoint(x: origin.x + n[0], y: origin.y + n[1])
                let end = CGPoint(x: origin.x + n[2], y: origin.y + n[3])
                path.addQuadCurve(to: end, control: control)
                current = end
                lastQuadControl = control
                lastCubicControl = nil
            case "A", "a":
                guard let radii = numbers(3), let large = flag(), let sweep = flag(), let end = numbers(2) else { return false }
                arc(rx: radii[0], ry: radii[1], rotation: radii[2], large: large, sweep: sweep,
                    to: CGPoint(x: origin.x + end[0], y: origin.y + end[1]))
            default:
                return false
            }
            return true
        }

        private mutating func line(to point: CGPoint) {
            path.addLine(to: point)
            current = point
            lastCubicControl = nil
            lastQuadControl = nil
        }

        /// SVG's endpoint arc, converted to cubic segments of at most 90 degrees (SVG 1.1 appendix F.6).
        private mutating func arc(rx radiusX: CGFloat, ry radiusY: CGFloat, rotation: CGFloat, large: Bool, sweep: Bool, to end: CGPoint) {
            var rx = abs(radiusX)
            var ry = abs(radiusY)
            let start = current
            if rx == 0 || ry == 0 || start == end {
                line(to: end)
                return
            }
            let phi = rotation * .pi / 180
            let cosPhi = cos(phi)
            let sinPhi = sin(phi)
            let halfDX = (start.x - end.x) / 2
            let halfDY = (start.y - end.y) / 2
            let x1 = cosPhi * halfDX + sinPhi * halfDY
            let y1 = -sinPhi * halfDX + cosPhi * halfDY
            let lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
            if lambda > 1 {
                let scale = lambda.squareRoot()
                rx *= scale
                ry *= scale
            }
            let sign: CGFloat = large == sweep ? -1 : 1
            let numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
            let denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1
            let coefficient = sign * max(0, numerator / denominator).squareRoot()
            let cx1 = coefficient * (rx * y1 / ry)
            let cy1 = coefficient * -(ry * x1 / rx)
            let centerX = cosPhi * cx1 - sinPhi * cy1 + (start.x + end.x) / 2
            let centerY = sinPhi * cx1 + cosPhi * cy1 + (start.y + end.y) / 2
            func angle(_ ux: CGFloat, _ uy: CGFloat, _ vx: CGFloat, _ vy: CGFloat) -> CGFloat {
                atan2(ux * vy - uy * vx, ux * vx + uy * vy)
            }
            let theta = angle(1, 0, (x1 - cx1) / rx, (y1 - cy1) / ry)
            var delta = angle((x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry)
            if !sweep && delta > 0 { delta -= 2 * .pi }
            if sweep && delta < 0 { delta += 2 * .pi }
            let segments = max(1, Int((abs(delta) / (.pi / 2)).rounded(.up)))
            let step = delta / CGFloat(segments)
            let handle = 4 / 3 * tan(step / 4)
            func map(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
                CGPoint(x: centerX + rx * cosPhi * x - ry * sinPhi * y, y: centerY + rx * sinPhi * x + ry * cosPhi * y)
            }
            var from = theta
            for _ in 0..<segments {
                let to = from + step
                let c1 = map(cos(from) - handle * sin(from), sin(from) + handle * cos(from))
                let c2 = map(cos(to) + handle * sin(to), sin(to) - handle * cos(to))
                path.addCurve(to: map(cos(to), sin(to)), control1: c1, control2: c2)
                from = to
            }
            current = end
            lastCubicControl = nil
            lastQuadControl = nil
        }
    }
}
