import SwiftUI

extension Path {
    /// Minimal SVG path parser supporting absolute `M`, `L` and `Z` commands,
    /// which is all the traced collection-card silhouettes use.
    init(svgMLZ source: String) {
        var path = Path()
        var tokens: [String] = []
        var current = ""
        for ch in source {
            if ch == " " || ch == "," || ch == "\n" {
                if !current.isEmpty { tokens.append(current); current = "" }
            } else if ch.isLetter {
                if !current.isEmpty { tokens.append(current); current = "" }
                tokens.append(String(ch))
            } else {
                current.append(ch)
            }
        }
        if !current.isEmpty { tokens.append(current) }

        var index = 0
        var command = "M"
        func nextNumber() -> CGFloat? {
            guard index < tokens.count, let value = Double(tokens[index]) else { return nil }
            index += 1
            return CGFloat(value)
        }
        while index < tokens.count {
            let token = tokens[index]
            if token.count == 1, token.first!.isLetter {
                command = token.uppercased()
                index += 1
                if command == "Z" {
                    path.closeSubpath()
                    continue
                }
            }
            guard let x = nextNumber(), let y = nextNumber() else { break }
            switch command {
            case "M":
                path.move(to: CGPoint(x: x, y: y))
                command = "L"
            default:
                path.addLine(to: CGPoint(x: x, y: y))
            }
        }
        self = path
    }
}
