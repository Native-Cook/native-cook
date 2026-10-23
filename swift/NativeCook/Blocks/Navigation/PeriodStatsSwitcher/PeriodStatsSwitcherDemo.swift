import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct PeriodStatsSwitcherDemo: View {
    var body: some View {
        PeriodStatsSwitcher(autoPlay: true)
    }
}

#Preview {
    PeriodStatsSwitcherDemo()
}
