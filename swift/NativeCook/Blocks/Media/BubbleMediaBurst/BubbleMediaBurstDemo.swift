import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct BubbleMediaBurstDemo: View {
    var body: some View {
        BubbleMediaBurst(autoPlay: true)
    }
}

#Preview {
    BubbleMediaBurstDemo()
}
