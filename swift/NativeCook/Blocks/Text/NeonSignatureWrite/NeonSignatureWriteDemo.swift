import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct NeonSignatureWriteDemo: View {
    var body: some View {
        NeonSignatureWrite(autoPlay: true)
    }
}

#Preview {
    NeonSignatureWriteDemo()
}
