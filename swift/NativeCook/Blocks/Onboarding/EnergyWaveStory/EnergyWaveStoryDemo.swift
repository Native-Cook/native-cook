import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct EnergyWaveStoryDemo: View {
    var body: some View {
        EnergyWaveStory(autoPlay: true)
    }
}

#Preview {
    EnergyWaveStoryDemo()
}
