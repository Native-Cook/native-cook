import SwiftUI

/// The camera on its table, taking photos by itself.
struct PolaroidDevelopWelcomeDemo: View {
    var body: some View {
        PolaroidDevelopWelcome(autoPlay: true)
            .background(Color(hex: 0x140e0a).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PolaroidDevelopWelcomeDemo()
}
