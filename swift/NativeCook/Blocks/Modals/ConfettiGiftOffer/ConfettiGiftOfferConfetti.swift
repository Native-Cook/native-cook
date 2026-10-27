import SwiftUI

/// Paper confetti (ConfettiGiftOffer.tsx:61-84): 56 capsule strips. Each falls toward a terminal speed, sways on
/// its own period and keeps turning over, so its width breathes as the face tips away from the light.
/// React Native animates 56 views; one Canvas draws the same transforms here.
struct ConfettiGiftOfferConfetti: View {
    /// `confettiAge`: seconds since the burst began (negative before it, and past 1.7 nothing shows).
    var age: Double
    var reduced: Bool

    private static let count = 56
    private static let colors: [Color] = [
        ConfettiGiftOfferPalette.hex(0x3E7BF5),
        ConfettiGiftOfferPalette.hex(0xEF5A4E),
        ConfettiGiftOfferPalette.hex(0x7FD8BC),
        ConfettiGiftOfferPalette.hex(0xF4C43E),
    ]

    var body: some View {
        Canvas { context, size in
            guard !reduced, age >= 0, age <= 1.7 else { return }
            let width = size.width
            let height = size.height
            // The strip's life, opacity and fall are shared maths of `age`.
            let life = max(0, age)
            let opacity = ConfettiGiftOfferMotion.enter(ConfettiGiftOfferMotion.fraction(age, 0, 0.08))
                * (1 - ConfettiGiftOfferMotion.phase(age, 1.2, 1.7))
            if opacity <= 0 { return }
            for index in 0..<Self.count {
                let seed = Double((index * 137 + 31) % 101) / 101
                let verticalSeed = Double((index * 47 + 17) % 103) / 103
                let depth: Double = index % 5 == 0 ? 0.62 : index % 3 == 0 ? 0.82 : 1
                let fall = height * (1.05 * life - 0.32 * (1 - exp(-life * 2.4))) * depth
                let spin = life * (5 + seed * 6) + Double(index)
                let sway = sin(life * (2.2 + seed * 1.6) + Double(index)) * (14 + seed * 22)
                let drop = height * (verticalSeed * 1.2 - 0.25) + fall
                let turn = Double(index * 31) + life * (seed > 0.5 ? 210 : -190)
                let flat = 0.3 + 0.7 * abs(cos(spin))
                let stripWidth = (index % 3 != 0 ? 11 : 14) * depth
                let stripHeight = (index % 3 != 0 ? 30 : 36) * depth
                let left = seed * width
                var strip = context
                // translateX, translateY, rotate, scaleX about the strip's centre, in that order.
                strip.translateBy(x: left + stripWidth / 2 + sway, y: stripHeight / 2 + drop)
                strip.rotate(by: .degrees(turn))
                strip.scaleBy(x: flat, y: 1)
                let rect = CGRect(x: -stripWidth / 2, y: -stripHeight / 2, width: stripWidth, height: stripHeight)
                strip.fill(
                    Path(roundedRect: rect, cornerRadius: stripWidth / 2),
                    with: .color(Self.colors[index % 4].opacity(opacity))
                )
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
