import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct WaveScrubSliderDemo: View {
    var body: some View {
        WaveScrubSlider(autoPlay: true)
    }
}

#Preview {
    WaveScrubSliderDemo()
}
