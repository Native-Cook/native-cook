import SwiftUI

/// The lamp alone on the screen, switching itself on and off.
struct PullCordLightDemo: View {
    var body: some View {
        PullCordLight(autoPlay: true)
            .background(Color(hex: 0x05060a).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PullCordLightDemo()
}
