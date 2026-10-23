import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct GlowFileUploaderDemo: View {
    var body: some View {
        GlowFileUploader(autoPlay: true)
    }
}

#Preview {
    GlowFileUploaderDemo()
}
