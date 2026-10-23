import SwiftUI

// The call to action: a light pill whose label names the ball. The label morphs when the ball
// changes, and the pill answers a press by sinking a little. Mirrors ActionButton.tsx and
// MorphingLabel.tsx. Touches are routed by the screen (see ChooseYourBallGestures.swift), so this
// view only draws the state it is given; VoiceOver activates it through its own action.

struct ChooseYourBallActionButton: View {
    let icon: String
    let label: String
    /// What changed, as a number: the label's transition runs when this does.
    let labelToken: Int
    let isPressed: Bool
    /// The capped Dynamic Type multiple; the icon follows it, the label keeps its size.
    let typeScale: CGFloat
    let action: () -> Void

    static let height: CGFloat = 56
    private static let cornerRadius: CGFloat = 17
    private static let ink = Color(chooseYourBallHex: ChooseYourBallPalette.buttonInk)

    @Environment(\.displayScale) private var displayScale
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: 10) {
            Text(icon)
                .font(.system(size: 16 * typeScale, weight: .black))
                .foregroundStyle(Self.ink)

            // The diffing transition holds "EQUIP" and "BALL" still and changes only the word
            // between them. The spring is inside the carousel's own snap, so the label is never
            // still settling after the ball it names has stopped.
            Text(label)
                .font(.system(size: 12, weight: .black))
                .kerning(0.55)
                .foregroundStyle(Self.ink)
                .lineLimit(1)
                .contentTransition(.numericText())
                .animation(.spring(response: 0.4, dampingFraction: 0.6), value: labelToken)
        }
        .padding(.horizontal, 18)
        .frame(maxWidth: .infinity)
        .frame(height: Self.height)
        .background(Color(chooseYourBallHex: ChooseYourBallPalette.buttonSurface))
        // The press darkens the surface at once, as the reference's underlay does.
        .overlay(Self.ink.opacity(isPressed ? 0.1 : 0))
        // Lit from above: one hairline of near-white, so the pill has a top edge on a dark set.
        .overlay(alignment: .top) {
            Color.white.opacity(0.95)
                .frame(height: 1 / displayScale)
                .padding(.horizontal, 14)
        }
        .clipShape(RoundedRectangle(cornerRadius: Self.cornerRadius))
        .scaleEffect(isPressed ? 0.97 : 1)
        // 100 ms in, 160 ms out, cubic out; not played at all with Reduce Motion on.
        .animation(
            reduceMotion ? nil : .chooseYourBallEased(.outCubic, duration: isPressed ? 0.1 : 0.16),
            value: isPressed
        )
        .accessibilityElement()
        .accessibilityLabel(label)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(.default, action)
    }
}
