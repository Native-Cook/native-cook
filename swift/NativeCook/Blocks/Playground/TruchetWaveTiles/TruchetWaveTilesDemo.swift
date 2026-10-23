import SwiftUI

/// The tiles on the whole screen, sending waves, changing style and palette by themselves.
struct TruchetWaveTilesDemo: View {
    var body: some View {
        TruchetWaveTiles(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    TruchetWaveTilesDemo()
}
