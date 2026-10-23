import SwiftUI

/// A button that dims while pressed, and while it is disabled when `dimWhenDisabled` is set.
struct DragMultiSelectDimStyle: ButtonStyle {
    var pressedOpacity: Double
    var dimWhenDisabled = false
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed || (dimWhenDisabled && !isEnabled) ? pressedOpacity : 1)
    }
}

/// Reports the add button's press phase so its enclosing shell and shadow scale together.
struct DragMultiSelectPressScaleStyle: ButtonStyle {
    @Binding var isPressed: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onChange(of: configuration.isPressed) { _, pressed in
                isPressed = pressed
            }
    }
}
