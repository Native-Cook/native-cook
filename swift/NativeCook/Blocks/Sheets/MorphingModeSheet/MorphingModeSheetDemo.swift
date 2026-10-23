import SwiftUI

/// The block fills the screen and draws its own status bar, so it is shown without a stage.
struct MorphingModeSheetDemo: View {
    var body: some View {
        MorphingModeSheet(autoPlay: true)
    }
}

#Preview {
    MorphingModeSheetDemo()
}
