import SwiftUI

/// The onboarding on the whole screen, its lantern turning by itself.
struct LighthouseBeamWelcomeDemo: View {
    var body: some View {
        LighthouseBeamWelcome(autoPlay: true)
            .background(LighthouseBeamWelcomeArt.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    LighthouseBeamWelcomeDemo()
}
