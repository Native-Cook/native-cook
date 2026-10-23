import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct StackingToastFeedDemo: View {
    var body: some View {
        StackingToastFeed(autoPlay: true)
    }
}

#Preview {
    StackingToastFeedDemo()
}
