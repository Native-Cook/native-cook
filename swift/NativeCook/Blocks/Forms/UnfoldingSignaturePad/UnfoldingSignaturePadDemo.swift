import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct UnfoldingSignaturePadDemo: View {
    var body: some View {
        UnfoldingSignaturePad(autoPlay: true)
    }
}

#Preview {
    UnfoldingSignaturePadDemo()
}
