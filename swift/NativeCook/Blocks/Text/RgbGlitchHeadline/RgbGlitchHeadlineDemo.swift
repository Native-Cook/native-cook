import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct RgbGlitchHeadlineDemo: View {
    var body: some View {
        RgbGlitchHeadline(autoPlay: true)
    }
}

#Preview {
    RgbGlitchHeadlineDemo()
}
