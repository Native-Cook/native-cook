import SwiftUI

// The ground the ball stands on, coloured by the ball. The colour has a position, not a state:
// it is read straight off the carousel's fractional offset, so half way between two balls the
// room is half way between their colours. Mirrors BallBackdrop.tsx.

struct ChooseYourBallBackdrop: View {
    let carousel: ChooseYourBallCarousel

    /// One ground per slot, with the first repeated at the end: the loop's last ball stands next
    /// to its first, and the blend between them must not run back through all the others.
    private static let grounds: [ChooseYourBallRGB] = {
        let grounds = ChooseYourBallCatalog.order.map { ChooseYourBallEditorial.ground(aura: $0.spec.fx.aura) }
        return grounds + [grounds[0]]
    }()

    var body: some View {
        ZStack {
            tint
            // A fixed ramp back down to stage ink, so the foot of the screen stays dark enough
            // to print the chips, the button and the strip on.
            LinearGradient(
                stops: [
                    .init(color: Color(chooseYourBallHex: ChooseYourBallPalette.stageInk, opacity: 0.55), location: 0),
                    .init(color: Color(chooseYourBallHex: ChooseYourBallPalette.stageInk, opacity: 0.05), location: 0.32),
                    .init(color: Color(chooseYourBallHex: ChooseYourBallPalette.stageInk, opacity: 0.42), location: 0.74),
                    .init(color: Color(chooseYourBallHex: ChooseYourBallPalette.stageInk), location: 0.92),
                    .init(color: Color(chooseYourBallHex: ChooseYourBallPalette.stageInk), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        .accessibilityHidden(true)
    }

    private var tint: Color {
        let total = Double(carousel.total)
        var position = carousel.offset.truncatingRemainder(dividingBy: total)
        if position < 0 { position += total }
        let index = min(Int(position), carousel.total - 1)
        return Self.blend(Self.grounds[index], Self.grounds[index + 1], position - Double(index)).color
    }

    /// Blends in gamma 2.2 light and lands on whole 8-bit channels, which is how the reference's
    /// colour interpolation (Reanimated's interpolateColor) works.
    private static func blend(_ from: ChooseYourBallRGB, _ to: ChooseYourBallRGB, _ amount: Double) -> ChooseYourBallRGB {
        func channel(_ a: Double, _ b: Double) -> Double {
            let low = pow(a, 2.2)
            return (pow(low + (pow(b, 2.2) - low) * amount, 1 / 2.2) * 255).rounded() / 255
        }
        return ChooseYourBallRGB(
            red: channel(from.red, to.red), green: channel(from.green, to.green), blue: channel(from.blue, to.blue)
        )
    }
}
