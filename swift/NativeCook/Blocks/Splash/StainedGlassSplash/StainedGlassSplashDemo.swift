import SwiftUI

/// The splash alone on the whole screen, lighting, flooding and opening onto home by itself.
struct StainedGlassSplashDemo: View {
    var body: some View {
        StainedGlassSplash(autoPlay: true)
            .background(StainedGlassSplashPalette.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    StainedGlassSplashDemo()
}
