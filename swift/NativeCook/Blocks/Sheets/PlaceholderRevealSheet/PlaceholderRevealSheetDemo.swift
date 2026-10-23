import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct PlaceholderRevealSheetDemo: View {
    var body: some View {
        PlaceholderRevealSheet(autoPlay: true)
    }
}

#Preview {
    PlaceholderRevealSheetDemo()
}
