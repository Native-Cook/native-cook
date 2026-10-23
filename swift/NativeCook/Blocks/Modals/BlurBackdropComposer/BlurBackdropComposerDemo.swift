import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct BlurBackdropComposerDemo: View {
    var body: some View {
        BlurBackdropComposer(autoPlay: true)
    }
}

#Preview {
    BlurBackdropComposerDemo()
}
