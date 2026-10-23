import SwiftUI

struct GlassDiscOnboardingDemo: View {
    var body: some View {
        GlassDiscOnboarding(autoPlay: true)
            .ignoresSafeArea()
    }
}

#Preview {
    GlassDiscOnboardingDemo()
}
