import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct SleepColumnScrubberDemo: View {
    var body: some View {
        SleepColumnScrubber(autoPlay: true)
    }
}

#Preview {
    SleepColumnScrubberDemo()
}
