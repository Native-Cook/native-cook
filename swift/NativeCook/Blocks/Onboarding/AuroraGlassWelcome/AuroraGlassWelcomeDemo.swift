import SwiftUI

/// The welcome on the whole screen, its card tilting by itself, under a hidden status bar.
struct AuroraGlassWelcomeDemo: View {
    var body: some View {
        AuroraGlassWelcome(autoPlay: true)
            .background(Color(hex: 0x040815).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    AuroraGlassWelcomeDemo()
}
