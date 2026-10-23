import SwiftUI

/// The welcome screen on the whole screen, turning its scenes by itself.
struct CurtainSplitWelcomeDemo: View {
    var body: some View {
        CurtainSplitWelcome(autoPlay: true)
            .background(Color(hex: 0x1a1916).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    CurtainSplitWelcomeDemo()
}
