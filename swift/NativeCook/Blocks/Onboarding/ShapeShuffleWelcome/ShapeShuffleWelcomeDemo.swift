import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ShapeShuffleWelcomeDemo: View {
    var body: some View {
        ShapeShuffleWelcome(autoPlay: true)
    }
}

#Preview {
    ShapeShuffleWelcomeDemo()
}
