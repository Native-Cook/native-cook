import SwiftUI

// The ball's name, set as the largest thing on the screen and printed behind the ball. Two
// lines: the identity in full white, "BALL" as a ghost. Only the first line animates, letter by
// letter; the second is on every ball. Mirrors BallWordmark.tsx.

struct ChooseYourBallWordmark: View {
    /// Everything before the last word: the part that names the ball.
    let hero: String
    /// The last word, always "BALL".
    let tail: String
    /// Font size of the display line.
    let size: CGFloat
    let width: CGFloat
    /// The capped Dynamic Type multiple; only the tail follows it.
    let typeScale: CGFloat
    let reduceMotion: Bool

    /// Optical left margin, shared by both lines.
    private static let lineInset: CGFloat = 14

    var body: some View {
        // Room for the glyphs' ascent plus the rise they enter from.
        let heroHeight = size * 1.34
        let tailSize = size * typeScale
        let tailFont = UIFont.systemFont(ofSize: tailSize, weight: .black)
        // The tail's line is 1.02 of its size, pulled 0.44 of the size up under the hero. The
        // line is shorter than the type, and the glyphs keep their place at its foot.
        let tailTop = heroHeight - size * 0.44 + (tailSize * 1.02 - tailFont.lineHeight)

        ZStack(alignment: .topLeading) {
            ChooseYourBallMorphingText(
                text: hero,
                fitTexts: ChooseYourBallEditorial.heroes,
                fontSize: size,
                height: heroHeight,
                paddingLeft: Self.lineInset,
                tracking: -size * 0.037,
                width: width,
                reduceMotion: reduceMotion
            )

            Text(tail)
                .font(.system(size: tailSize, weight: .black))
                .kerning(-3)
                .foregroundStyle(.white.opacity(0.15))
                .lineLimit(1)
                .fixedSize()
                .offset(x: Self.lineInset, y: tailTop)
        }
        .frame(width: width, alignment: .topLeading)
        .accessibilityElement()
        .accessibilityLabel("\(hero) \(tail)")
        .accessibilityAddTraits(.isHeader)
    }
}
