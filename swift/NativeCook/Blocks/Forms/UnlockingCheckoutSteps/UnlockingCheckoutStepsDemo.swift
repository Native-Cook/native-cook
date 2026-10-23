import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct UnlockingCheckoutStepsDemo: View {
    var body: some View {
        UnlockingCheckoutSteps(autoPlay: true)
    }
}

#Preview {
    UnlockingCheckoutStepsDemo()
}
