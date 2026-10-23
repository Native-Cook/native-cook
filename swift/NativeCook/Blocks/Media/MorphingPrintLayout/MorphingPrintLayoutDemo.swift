import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct MorphingPrintLayoutDemo: View {
    var body: some View {
        MorphingPrintLayout(autoPlay: true)
    }
}

#Preview {
    MorphingPrintLayoutDemo()
}
