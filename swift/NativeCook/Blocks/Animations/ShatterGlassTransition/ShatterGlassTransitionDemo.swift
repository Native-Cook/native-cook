import SwiftUI

/// The pane on the whole screen, breaking and mending by itself.
struct ShatterGlassTransitionDemo: View {
    var body: some View {
        ShatterGlassTransition(autoPlay: true)
            .background(Color(shatterHex: 0x05060a).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    ShatterGlassTransitionDemo()
}
