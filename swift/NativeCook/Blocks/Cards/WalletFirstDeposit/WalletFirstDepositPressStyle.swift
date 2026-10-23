import SwiftUI

/// The visible material lives below these semantic hit targets.
struct WalletFirstDepositPressStyle: ButtonStyle {
    var onPressChanged: (Bool) -> Void

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Rectangle())
            .onChange(of: configuration.isPressed) { _, pressed in onPressChanged(pressed) }
    }
}
