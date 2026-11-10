import SwiftUI

struct PasskeyGlassSealPressStyle: ButtonStyle {
    var reduced: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduced ? 0.96 : 1)
            .opacity(configuration.isPressed && reduced ? 0.72 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.15), value: configuration.isPressed)
    }
}
