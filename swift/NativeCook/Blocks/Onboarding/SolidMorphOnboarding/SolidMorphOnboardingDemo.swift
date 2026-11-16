import SwiftUI

struct SolidMorphOnboardingDemo: View {
    static let loop = SolidMorphOnboarding.loop
    var body: some View {
        SolidMorphOnboarding(autoPlay: true)
    }
}

#Preview {
    SolidMorphOnboardingDemo()
}
