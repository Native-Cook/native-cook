import SwiftUI

/// The Loaven splash on the whole screen, playing by itself, under a hidden status bar.
struct FoilMonogramSplashDemo: View {
    var body: some View {
        FoilMonogramSplash(autoPlay: true)
            .background(Color(hex: 0x130d0a).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    FoilMonogramSplashDemo()
}
