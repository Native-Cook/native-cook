import SwiftUI

/// The block fills the screen and draws its own status bar, so it is shown without a stage.
struct DockExpandSheetDemo: View {
    var body: some View {
        DockExpandSheet(autoPlay: true)
    }
}

#Preview {
    DockExpandSheetDemo()
}
