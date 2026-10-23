import SwiftUI

/// The player alone on the screen, playing and scrubbing by itself.
struct WaveformScrubberDemo: View {
    var body: some View {
        WaveformScrubber(autoPlay: true)
            .background(Color(hex: 0x05070c).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    WaveformScrubberDemo()
}
