import SwiftUI

/// The onboarding pages on the whole screen, pouring over each other by themselves, under a hidden
/// status bar.
struct LiquidSwipeOnboardingDemo: View {
    var body: some View {
        LiquidSwipeOnboarding(autoPlay: true)
            .background(Color(hex: 0xff5b1f).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    LiquidSwipeOnboardingDemo()
}
