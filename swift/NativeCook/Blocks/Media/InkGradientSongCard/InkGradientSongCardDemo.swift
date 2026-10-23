import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct InkGradientSongCardDemo: View {
    var body: some View {
        InkGradientSongCard(autoPlay: true)
    }
}

#Preview {
    InkGradientSongCardDemo()
}
