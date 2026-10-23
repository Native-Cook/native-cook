import SwiftUI

/// The wavy header of the dialog; its colour follows the rating.
struct EmojiRatingPopupWave: View {
    static let height = 108.0

    // The header's waves, in a box 320 wide: the colour field, a lighter wave over it and a pale crest.
    private static let base = EmojiRatingPopupPath.svg("M0 0H320V80C284 104 248 72 204 86C160 100 124 72 84 86C50 98 22 92 0 82Z")
    private static let light = EmojiRatingPopupPath.svg("M0 0H320V60C288 50 256 88 212 76C170 64 140 94 96 78C60 66 28 72 0 62Z")
    private static let crest = EmojiRatingPopupPath.svg("M0 0H320V36C278 48 244 22 198 34C152 46 112 24 66 38C38 46 16 42 0 36Z")

    let width: Double
    /// Where the dialog's mood is: 0 for none yet, then 1 to 5 stars.
    let mood: Double
    /// The colour of the field at each mood, and of the wave over it.
    let field: [UInt32]
    let wave: [UInt32]

    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: width / 320, y: 1)
            context.fill(Self.base, with: .color(Self.mix(field, mood)))
            context.fill(Self.light, with: .color(Self.mix(wave, mood)))
            context.fill(
                Self.crest,
                with: .linearGradient(
                    Gradient(colors: [.white.opacity(0.42), .white.opacity(0.12)]),
                    startPoint: .zero, endPoint: CGPoint(x: 0, y: 48)
                )
            )
        }
        .frame(width: width, height: Self.height)
    }

    /// A straight mix in RGB between the two stops the mood sits between.
    private static func mix(_ stops: [UInt32], _ mood: Double) -> Color {
        let m = min(max(mood, 0), Double(stops.count - 1))
        let i = min(Int(m), stops.count - 2)
        let t = m - Double(i)
        func channel(_ shift: UInt32) -> Double {
            let a = Double((stops[i] >> shift) & 0xff)
            let b = Double((stops[i + 1] >> shift) & 0xff)
            return (a + (b - a) * t) / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0))
    }
}
