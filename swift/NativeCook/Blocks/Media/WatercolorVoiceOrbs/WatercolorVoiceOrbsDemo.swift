import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct WatercolorVoiceOrbsDemo: View {
    var body: some View {
        WatercolorVoiceOrbs(autoPlay: true)
    }
}

#Preview {
    WatercolorVoiceOrbsDemo()
}
