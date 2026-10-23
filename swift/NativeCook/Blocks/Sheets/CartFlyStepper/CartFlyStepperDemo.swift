import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct CartFlyStepperDemo: View {
    var body: some View {
        CartFlyStepper(autoPlay: true)
    }
}

#Preview {
    CartFlyStepperDemo()
}
