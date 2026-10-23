import SwiftUI

/// A time whose characters sit in cells of fixed width; a character that changes rolls: the old one
/// falls and fades while the new one drops in.
struct LiveTimerTabRollingTime: View {
    /// The time as text, "14:59".
    let text: String
    let size: Double
    let color: Color
    let weight: Font.Weight
    /// No roll: a character is simply replaced.
    var still = false

    // A digit's cell and a colon's, as fractions of the font size: fixed, so the time never changes width.
    private static let digit = 0.62
    private static let colon = 0.3
    private static let line = 1.3

    /// How a time, or one character of it, arrives and leaves.
    static let rollIn = AnyTransition.offset(y: -LiveTimerTabMotion.rollShift).combined(with: .opacity)
    static let rollOut = AnyTransition.offset(y: LiveTimerTabMotion.rollShift).combined(with: .opacity)
    static let animation = Animation.timingCurve(0.22, 1, 0.36, 1, duration: LiveTimerTabMotion.roll)

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(text.enumerated()), id: \.offset) { _, character in
                ZStack {
                    Text(String(character))
                        .font(.system(size: size, weight: weight))
                        .monospacedDigit()
                        .foregroundStyle(color)
                        .fixedSize()
                        .id(character)
                        .transition(still ? .identity : .asymmetric(insertion: Self.rollIn, removal: Self.rollOut))
                }
                .frame(width: size * (character == ":" ? Self.colon : Self.digit), height: (size * Self.line).rounded())
                .animation(still ? nil : Self.animation, value: character)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
    }
}
