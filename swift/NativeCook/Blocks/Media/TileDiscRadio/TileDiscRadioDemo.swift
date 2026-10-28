import SwiftUI

/// The now-playing screen on the whole screen, changing station by itself.
struct TileDiscRadioDemo: View {
    var body: some View {
        TileDiscRadio(autoPlay: true)
    }
}

#Preview {
    TileDiscRadioDemo()
}
