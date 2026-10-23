import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ChipMorphMenuDemo: View {
    var body: some View {
        ChipMorphMenu(autoPlay: true)
    }
}

#Preview {
    ChipMorphMenuDemo()
}
