import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct WeightWaveWallDemo: View {
    var body: some View {
        WeightWaveWall(autoPlay: true)
    }
}

#Preview {
    WeightWaveWallDemo()
}
