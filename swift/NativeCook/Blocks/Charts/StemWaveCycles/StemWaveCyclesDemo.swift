import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct StemWaveCyclesDemo: View {
    var body: some View { StemWaveCycles(autoPlay: true) }
}

#Preview { StemWaveCyclesDemo() }
