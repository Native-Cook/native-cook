import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct MorphingTitleMenuDemo: View {
    var body: some View {
        MorphingTitleMenu(autoPlay: true)
    }
}

#Preview {
    MorphingTitleMenuDemo()
}
