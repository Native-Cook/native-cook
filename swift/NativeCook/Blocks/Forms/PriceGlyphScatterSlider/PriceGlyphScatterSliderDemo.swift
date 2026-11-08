import SwiftUI

/// The budget card on the whole screen, dragging its own thumb up and down.
struct PriceGlyphScatterSliderDemo: View {
    var body: some View {
        PriceGlyphScatterSlider(autoPlay: true)
    }
}

#Preview {
    PriceGlyphScatterSliderDemo()
}
