import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct GlowGestureGuideDemo: View {
    var body: some View {
        GlowGestureGuide(autoPlay: true)
    }
}

#Preview {
    GlowGestureGuideDemo()
}
