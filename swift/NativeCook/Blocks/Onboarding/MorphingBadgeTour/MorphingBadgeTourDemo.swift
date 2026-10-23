import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct MorphingBadgeTourDemo: View {
    var body: some View {
        MorphingBadgeTour(autoPlay: true)
    }
}

#Preview {
    MorphingBadgeTourDemo()
}
