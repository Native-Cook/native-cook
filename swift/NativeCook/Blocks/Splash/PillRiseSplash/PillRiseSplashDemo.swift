import SwiftUI

/// The splash on the whole screen, rising, opening and coming back by itself.
struct PillRiseSplashDemo: View {
    var body: some View {
        PillRiseSplash(autoPlay: true)
            .background(Color(hex: 0xa3eb6e).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PillRiseSplashDemo()
}
