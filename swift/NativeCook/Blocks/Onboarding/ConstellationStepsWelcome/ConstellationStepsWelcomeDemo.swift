import SwiftUI

/// The night-sky onboarding on the whole screen, turning and drawing its constellations by itself.
struct ConstellationStepsWelcomeDemo: View {
    var body: some View {
        ConstellationStepsWelcome(autoPlay: true)
            .background(ConstellationStepsWelcome.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    ConstellationStepsWelcomeDemo()
}
