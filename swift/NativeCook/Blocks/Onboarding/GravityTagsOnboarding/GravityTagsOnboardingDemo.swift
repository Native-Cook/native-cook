import SwiftUI

/// Port of `src/app/_layout.tsx`.
/// Drives the onboarding flow: 0 = Welcome, 1 = Tags.
///
/// This is the original app's `RootView`, under the block's name. Its third step (the Expo
/// starter's Home / Explore tabs) was never reached from the UI and is not part of the block.
struct GravityTagsOnboardingDemo: View {
    @State private var onboardingStep = 0

    var body: some View {
        ZStack {
            switch onboardingStep {
            case 0:
                WelcomeScreen(onContinue: handleWelcomeContinue)
            default:
                // The original layout passes an empty handler here, so the
                // Tags screen's Continue button is intentionally a no-op.
                TagsScreen(onContinue: {})
            }

            AnimatedSplashOverlay()
        }
        // The gallery this block is shown in asks for the dark scheme at its root, and an outer
        // preference wins over this one, so the block also sets the light scheme on its own views.
        .environment(\.colorScheme, .light)
        .preferredColorScheme(.light)
    }

    private func handleWelcomeContinue() {
        onboardingStep = 1
    }
}

#Preview {
    GravityTagsOnboardingDemo()
}
