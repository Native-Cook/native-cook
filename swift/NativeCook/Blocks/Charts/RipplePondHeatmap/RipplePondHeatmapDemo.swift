import SwiftUI

/// The month on the whole screen, its pond rippling under scripted taps and a wake.
struct RipplePondHeatmapDemo: View {
    var body: some View {
        RipplePondHeatmap(autoPlay: true)
            .background(RipplePondHeatmapPalette.base.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    RipplePondHeatmapDemo()
}
