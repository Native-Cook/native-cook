import SwiftUI

/// The balloon onboarding on the whole screen, burning, climbing and landing by itself.
struct HotAirBalloonWelcomeDemo: View {
    var body: some View {
        HotAirBalloonWelcome(autoPlay: true)
            .background(HotAirBalloonWelcome.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    HotAirBalloonWelcomeDemo()
}
