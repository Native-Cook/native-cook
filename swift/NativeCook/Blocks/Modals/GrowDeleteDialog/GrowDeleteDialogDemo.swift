import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct GrowDeleteDialogDemo: View {
    var body: some View {
        GrowDeleteDialog(autoPlay: true)
    }
}

#Preview {
    GrowDeleteDialogDemo()
}
