import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct MergingStepDotsDemo: View {
    var body: some View {
        MergingStepDots(autoPlay: true)
    }
}

#Preview {
    MergingStepDotsDemo()
}
