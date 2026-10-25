import SwiftUI

/// The onboarding on the whole display, folding and throwing its sheets by itself.
struct PaperPlaneStepsDemo: View {
    var body: some View {
        PaperPlaneSteps(autoPlay: true)
            .background(PaperPlaneStepsStyle.screen.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PaperPlaneStepsDemo()
}
