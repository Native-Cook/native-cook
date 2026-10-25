import SwiftUI

/// The music-sync screen alone on the whole screen, its wax rising and sinking by itself.
struct LavaLampLoaderDemo: View {
    var body: some View {
        LavaLampLoader(autoPlay: true)
            .background(LavaLampLoaderPalette.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    LavaLampLoaderDemo()
}
