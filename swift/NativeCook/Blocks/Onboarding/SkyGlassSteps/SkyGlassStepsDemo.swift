import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct SkyGlassStepsDemo: View {
    var body: some View {
        SkyGlassSteps(autoPlay: true)
    }
}

#Preview {
    SkyGlassStepsDemo()
}
