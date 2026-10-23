import SwiftUI

/// The onboarding full screen, paging through by itself.
struct GlassRodOnboardingDemo: View {
    var body: some View {
        GlassRodOnboarding(autoPlay: true)
            .background(Color(hex: 0x0d0907).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    GlassRodOnboardingDemo()
}
