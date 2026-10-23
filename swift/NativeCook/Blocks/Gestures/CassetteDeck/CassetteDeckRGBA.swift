import SwiftUI

/// A colour kept as 0...255 channels so it can be mixed the way Reanimated's `interpolateColor` does.
struct CassetteDeckRGBA: Equatable {
    var r: Double
    var g: Double
    var b: Double
    var a: Double

    init(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) {
        self.r = r
        self.g = g
        self.b = b
        self.a = a
    }

    init(hex: UInt32) {
        self.init(Double((hex >> 16) & 0xff), Double((hex >> 8) & 0xff), Double(hex & 0xff))
    }

    var color: Color {
        Color(.sRGB, red: r / 255, green: g / 255, blue: b / 255, opacity: a)
    }

    /// Matches Reanimated's default `interpolateColor`: clamp, interpolate in gamma 2.2 linear space,
    /// then round the RGB channels after converting them back to gamma space. Alpha stays linear and rounds to 3 decimals.
    static func mix(_ from: CassetteDeckRGBA, _ to: CassetteDeckRGBA, _ amount: Double) -> CassetteDeckRGBA {
        let t = min(max(amount, 0), 1)
        let gamma = 2.2
        func channel(_ start: Double, _ end: Double) -> Double {
            let linearStart = pow(start / 255, gamma)
            let linearEnd = pow(end / 255, gamma)
            let linear = linearStart + (linearEnd - linearStart) * t
            return (pow(max(0, linear), 1 / gamma) * 255).rounded()
        }
        let alpha = ((from.a + (to.a - from.a) * t) * 1000).rounded() / 1000
        return CassetteDeckRGBA(
            channel(from.r, to.r),
            channel(from.g, to.g),
            channel(from.b, to.b),
            alpha
        )
    }
}
