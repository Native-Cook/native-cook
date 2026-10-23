import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ParticleListeningRingDemo: View {
    var body: some View {
        ParticleListeningRing(autoPlay: true)
    }
}

#Preview {
    ParticleListeningRingDemo()
}
