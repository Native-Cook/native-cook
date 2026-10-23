import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct BubbleRingRecorderDemo: View {
    var body: some View {
        BubbleRingRecorder(autoPlay: true)
    }
}

#Preview {
    BubbleRingRecorderDemo()
}
