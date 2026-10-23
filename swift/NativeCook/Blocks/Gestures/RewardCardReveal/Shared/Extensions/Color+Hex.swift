import SwiftUI

extension Color {
    /// Parses CSS-style colour literals so the React Native strings can be copied verbatim:
    /// `#rgb`, `#rrggbb`, `#rrggbbaa`, `rgb(r, g, b)` and `rgba(r, g, b, a)`.
    init(css: String) {
        let trimmed = css.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

        if trimmed.hasPrefix("#") {
            var hex = String(trimmed.dropFirst())
            if hex.count == 3 {
                hex = hex.map { "\($0)\($0)" }.joined()
            }
            var value: UInt64 = 0
            Scanner(string: hex).scanHexInt64(&value)
            let hasAlpha = hex.count == 8
            let r = Double((value >> (hasAlpha ? 24 : 16)) & 0xff) / 255
            let g = Double((value >> (hasAlpha ? 16 : 8)) & 0xff) / 255
            let b = Double((value >> (hasAlpha ? 8 : 0)) & 0xff) / 255
            let a = hasAlpha ? Double(value & 0xff) / 255 : 1
            self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
            return
        }

        if trimmed.hasPrefix("rgb") {
            let inner = trimmed
                .replacingOccurrences(of: "rgba", with: "")
                .replacingOccurrences(of: "rgb", with: "")
                .replacingOccurrences(of: "(", with: "")
                .replacingOccurrences(of: ")", with: "")
            let parts = inner.split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
            if parts.count >= 3 {
                let alpha = parts.count > 3 ? parts[3] : 1
                self.init(.sRGB, red: parts[0] / 255, green: parts[1] / 255, blue: parts[2] / 255, opacity: alpha)
                return
            }
        }

        self.init(.sRGB, red: 1, green: 0, blue: 1, opacity: 1)
    }

    init(hex: String) {
        self.init(css: hex)
    }
}

extension Gradient {
    /// Builds a gradient from CSS colour strings with explicit stop positions (Skia `positions`).
    init(css colors: [String], positions: [Double]? = nil) {
        if let positions, positions.count == colors.count {
            self.init(stops: zip(colors, positions).map { Gradient.Stop(color: Color(css: $0), location: $1) })
        } else {
            self.init(colors: colors.map { Color(css: $0) })
        }
    }
}
