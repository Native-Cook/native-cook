import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct RideHeightFilterDemo: View {
    var body: some View { RideHeightFilter(autoPlay: true) }
}

#Preview { RideHeightFilterDemo() }
