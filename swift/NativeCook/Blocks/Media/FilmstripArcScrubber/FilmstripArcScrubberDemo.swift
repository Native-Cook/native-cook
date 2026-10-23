import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct FilmstripArcScrubberDemo: View {
    var body: some View {
        FilmstripArcScrubber(autoPlay: true)
    }
}

#Preview {
    FilmstripArcScrubberDemo()
}
