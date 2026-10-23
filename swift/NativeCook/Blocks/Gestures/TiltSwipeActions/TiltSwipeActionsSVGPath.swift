import SwiftUI

/// A small SVG path-data reader (M L H V C S A Z, absolute and relative) for the block's icon glyphs.
enum TiltSwipeActionsSVGPath {
    static func path(_ data: String) -> Path {
        var reader = Reader(Array(data))
        var path = Path()
        var command: Character = "M"
        var current = CGPoint.zero
        var subpathStart = CGPoint.zero
        var lastControl: CGPoint?

        while true {
            reader.skipSeparators()
            if reader.atEnd { break }
            if let next = reader.command() {
                command = next
            } else if !"MmLlHhVvCcSsAaZz".contains(command) {
                break
            }
            let relative = command.isLowercase
            let origin = relative ? current : .zero
            var control: CGPoint?
            switch command {
            case "M", "m":
                guard let x = reader.number(), let y = reader.number() else { return path }
                current = CGPoint(x: origin.x + x, y: origin.y + y)
                subpathStart = current
                path.move(to: current)
                command = relative ? "l" : "L"
            case "L", "l":
                guard let x = reader.number(), let y = reader.number() else { return path }
                current = CGPoint(x: origin.x + x, y: origin.y + y)
                path.addLine(to: current)
            case "H", "h":
                guard let x = reader.number() else { return path }
                current = CGPoint(x: origin.x + x, y: current.y)
                path.addLine(to: current)
            case "V", "v":
                guard let y = reader.number() else { return path }
                current = CGPoint(x: current.x, y: origin.y + y)
                path.addLine(to: current)
            case "C", "c":
                guard let x1 = reader.number(), let y1 = reader.number(),
                      let x2 = reader.number(), let y2 = reader.number(),
                      let x = reader.number(), let y = reader.number() else { return path }
                let c1 = CGPoint(x: origin.x + x1, y: origin.y + y1)
                let c2 = CGPoint(x: origin.x + x2, y: origin.y + y2)
                current = CGPoint(x: origin.x + x, y: origin.y + y)
                path.addCurve(to: current, control1: c1, control2: c2)
                control = c2
            case "S", "s":
                guard let x2 = reader.number(), let y2 = reader.number(),
                      let x = reader.number(), let y = reader.number() else { return path }
                let c1 = lastControl.map { CGPoint(x: 2 * current.x - $0.x, y: 2 * current.y - $0.y) } ?? current
                let c2 = CGPoint(x: origin.x + x2, y: origin.y + y2)
                current = CGPoint(x: origin.x + x, y: origin.y + y)
                path.addCurve(to: current, control1: c1, control2: c2)
                control = c2
            case "A", "a":
                guard let rx = reader.number(), let ry = reader.number(), let rotation = reader.number(),
                      let large = reader.flag(), let sweep = reader.flag(),
                      let x = reader.number(), let y = reader.number() else { return path }
                let end = CGPoint(x: origin.x + x, y: origin.y + y)
                addArc(&path, from: current, radii: CGSize(width: rx, height: ry), rotation: rotation, large: large, sweep: sweep, to: end)
                current = end
            default: // Z, z
                path.closeSubpath()
                current = subpathStart
            }
            lastControl = control
        }
        return path
    }

    /// SVG's endpoint arc, as cubic curves of at most a quarter turn each (SVG 1.1, F.6.5).
    private static func addArc(_ path: inout Path, from start: CGPoint, radii: CGSize, rotation: Double, large: Bool, sweep: Bool, to end: CGPoint) {
        var rx = abs(radii.width)
        var ry = abs(radii.height)
        guard rx > 0, ry > 0, start != end else {
            path.addLine(to: end)
            return
        }
        let phi = rotation * .pi / 180
        let cosPhi = cos(phi)
        let sinPhi = sin(phi)
        let dx = (start.x - end.x) / 2
        let dy = (start.y - end.y) / 2
        let x1 = cosPhi * dx + sinPhi * dy
        let y1 = -sinPhi * dx + cosPhi * dy
        let lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
        if lambda > 1 {
            let scale = lambda.squareRoot()
            rx *= scale
            ry *= scale
        }
        let numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
        let denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1
        let sign: Double = large == sweep ? -1 : 1
        let coefficient = sign * max(0, numerator / denominator).squareRoot()
        let cxp = coefficient * (rx * y1 / ry)
        let cyp = coefficient * (-ry * x1 / rx)
        let cx = cosPhi * cxp - sinPhi * cyp + (start.x + end.x) / 2
        let cy = sinPhi * cxp + cosPhi * cyp + (start.y + end.y) / 2
        let ux = (x1 - cxp) / rx
        let uy = (y1 - cyp) / ry
        let vx = (-x1 - cxp) / rx
        let vy = (-y1 - cyp) / ry
        let startAngle = atan2(uy, ux)
        var delta = atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        if !sweep && delta > 0 { delta -= 2 * .pi }
        if sweep && delta < 0 { delta += 2 * .pi }

        let segments = max(1, Int((abs(delta) / (.pi / 2)).rounded(.up)))
        let step = delta / Double(segments)
        let handle = 4.0 / 3.0 * tan(step / 4)
        func point(_ x: Double, _ y: Double) -> CGPoint {
            CGPoint(x: cx + cosPhi * rx * x - sinPhi * ry * y, y: cy + sinPhi * rx * x + cosPhi * ry * y)
        }
        for index in 0..<segments {
            let a = startAngle + step * Double(index)
            let b = a + step
            path.addCurve(
                to: point(cos(b), sin(b)),
                control1: point(cos(a) - handle * sin(a), sin(a) + handle * cos(a)),
                control2: point(cos(b) + handle * sin(b), sin(b) - handle * cos(b))
            )
        }
    }

    private struct Reader {
        let characters: [Character]
        var index = 0

        init(_ characters: [Character]) {
            self.characters = characters
        }

        var atEnd: Bool { index >= characters.count }

        mutating func skipSeparators() {
            while index < characters.count, characters[index] == " " || characters[index] == "," || characters[index] == "\n" {
                index += 1
            }
        }

        mutating func command() -> Character? {
            skipSeparators()
            guard index < characters.count, characters[index].isLetter else { return nil }
            defer { index += 1 }
            return characters[index]
        }

        mutating func flag() -> Bool? {
            skipSeparators()
            guard index < characters.count, characters[index] == "0" || characters[index] == "1" else { return nil }
            defer { index += 1 }
            return characters[index] == "1"
        }

        mutating func number() -> Double? {
            skipSeparators()
            var cursor = index
            var text = ""
            if cursor < characters.count, characters[cursor] == "-" || characters[cursor] == "+" {
                text.append(characters[cursor])
                cursor += 1
            }
            var digits = false
            var dot = false
            while cursor < characters.count {
                let character = characters[cursor]
                if character.isASCII, character.isNumber {
                    digits = true
                } else if character == ".", !dot {
                    dot = true
                } else {
                    break
                }
                text.append(character)
                cursor += 1
            }
            guard digits, let value = Double(text) else { return nil }
            index = cursor
            return value
        }
    }
}
