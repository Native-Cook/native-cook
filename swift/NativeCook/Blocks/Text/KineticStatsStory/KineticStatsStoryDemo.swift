import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct KineticStatsStoryDemo: View {
    var body: some View {
        KineticStatsStory(autoPlay: true)
    }
}

#Preview {
    KineticStatsStoryDemo()
}
