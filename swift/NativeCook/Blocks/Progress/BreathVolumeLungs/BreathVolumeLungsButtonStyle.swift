import SwiftUI

struct BreathVolumeLungsButtonStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(reduceMotion ? nil : .interpolatingSpring(mass: 1, stiffness: 322.3, damping: 30.5), value: configuration.isPressed)
    }
}
