import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct PolarRoseSweepDemo: View {
    var body: some View { PolarRoseSweep(autoPlay: true) }
}

#Preview { PolarRoseSweepDemo() }
