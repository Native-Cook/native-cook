import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ParticleCardDetailsDemo: View {
    var body: some View { ParticleCardDetails(autoPlay: true) }
}

#Preview { ParticleCardDetailsDemo() }
