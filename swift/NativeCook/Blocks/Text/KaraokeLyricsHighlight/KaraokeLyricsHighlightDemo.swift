import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct KaraokeLyricsHighlightDemo: View {
    var body: some View {
        KaraokeLyricsHighlight(autoPlay: true)
    }
}

#Preview {
    KaraokeLyricsHighlightDemo()
}
