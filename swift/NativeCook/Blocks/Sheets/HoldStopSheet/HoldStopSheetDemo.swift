import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct HoldStopSheetDemo: View {
    var body: some View {
        HoldStopSheet(autoPlay: true)
    }
}

#Preview {
    HoldStopSheetDemo()
}
