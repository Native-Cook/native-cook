import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct StageCardSheetDemo: View {
    var body: some View {
        StageCardSheet(autoPlay: true)
    }
}

#Preview {
    StageCardSheetDemo()
}
