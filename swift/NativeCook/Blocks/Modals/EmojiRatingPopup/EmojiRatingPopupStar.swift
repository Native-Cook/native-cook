import SwiftUI

/// One outline star. It fills yellow with a pop and a few sparkles when the rating reaches it.
struct EmojiRatingPopupStar: View {
    static let size = 36.0
    static let gap = 14.0
    static func width(_ count: Int) -> Double { Double(count) * size + Double(count - 1) * gap }

    private static let outline = Color(hex: 0x8d939c)
    private static let filled = Color(hex: 0xffbe2e)
    private static let popScale = 1.3
    private static let popUp = 0.09
    private static let popSettle = Spring(duration: 0.26, bounce: 1 - 0.55)
    private static let sparkFor = 0.38
    private static let sparkReach = 22.0
    private static let sparkSize = 5.0
    // Four sparkles leave the star along these directions (unit vectors).
    private static let sparks = [(0.71, -0.71), (0.71, 0.71), (-0.71, 0.71), (-0.71, -0.71)]

    let date: Date
    /// Whether the rating has reached this star.
    let lit: Bool
    /// 0 until the star has appeared with the dialog, 1 when it is in place; may overshoot.
    let appear: Double
    let reduced: Bool

    @State private var pop = EmojiRatingPopupChannel(1)
    @State private var spark = EmojiRatingPopupChannel(0)

    var body: some View {
        let scaleBy = pop.value(at: date)
        let p = spark.value(at: date)
        ZStack {
            Image(systemName: "star")
                .font(.system(size: 31))
                .foregroundStyle(Self.outline)
            Image(systemName: "star.fill")
                .font(.system(size: 31))
                .foregroundStyle(Self.filled)
                .opacity(lit ? 1 : 0)
            ForEach(0..<Self.sparks.count, id: \.self) { i in
                RoundedRectangle(cornerRadius: 1)
                    .fill(Self.filled)
                    .frame(width: Self.sparkSize, height: Self.sparkSize)
                    .rotationEffect(.degrees(45))
                    .scaleEffect(1 - p * 0.4)
                    .offset(x: Self.sparks[i].0 * Self.sparkReach * p, y: Self.sparks[i].1 * Self.sparkReach * p)
                    .opacity(p <= 0 || p >= 1 ? 0 : 1 - p)
            }
        }
        .frame(width: Self.size, height: Self.size)
        .scaleEffect(max(0, appear) * scaleBy)
        .opacity(min(1, max(0, appear * 2)))
        .onChange(of: lit) { _, now in
            if now && !reduced {
                pop.go([
                    .init(to: Self.popScale, curve: .timing(Self.popUp, { EmojiRatingPopupMotion.inOutQuad($0) })),
                    .init(to: 1, curve: .spring(Self.popSettle)),
                ])
                spark.set(0)
                spark.go(to: 1, .timing(Self.sparkFor, { EmojiRatingPopupMotion.inOutQuad($0) }))
            } else {
                pop.set(1)
            }
        }
    }
}
