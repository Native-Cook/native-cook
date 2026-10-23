import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct RopeTextTrailDemo: View {
    var body: some View {
        RopeTextTrail(autoPlay: true)
    }
}

#Preview {
    RopeTextTrailDemo()
}
