import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct EmberGlowStoryDemo: View {
    var body: some View {
        EmberGlowStory(autoPlay: true)
    }
}

#Preview {
    EmberGlowStoryDemo()
}
