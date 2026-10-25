import SwiftUI

/// The player on the whole screen, playing its loop by itself.
struct PrismSpectrumPlayerDemo: View {
    var body: some View {
        PrismSpectrumPlayer(autoPlay: true)
            .background(PrismSpectrumPlayerArt.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PrismSpectrumPlayerDemo()
}
