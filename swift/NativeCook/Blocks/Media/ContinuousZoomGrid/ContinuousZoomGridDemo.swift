import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ContinuousZoomGridDemo: View {
    var body: some View {
        ContinuousZoomGrid(autoPlay: true)
    }
}

#Preview {
    ContinuousZoomGridDemo()
}
