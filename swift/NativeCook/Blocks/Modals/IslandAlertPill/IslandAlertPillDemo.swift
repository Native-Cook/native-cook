import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct IslandAlertPillDemo: View {
    var body: some View {
        IslandAlertPill(autoPlay: true)
    }
}

#Preview {
    IslandAlertPillDemo()
}
