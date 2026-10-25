import SwiftUI

/// The roof-pitch step on the whole screen, its level tilting by itself.
struct SpiritLevelSliderDemo: View {
    var body: some View {
        SpiritLevelSlider(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    SpiritLevelSliderDemo()
}
