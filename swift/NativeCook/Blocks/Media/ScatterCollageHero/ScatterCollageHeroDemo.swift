import SwiftUI

/// The collage on the whole screen, drifting and opening its heroes by itself.
struct ScatterCollageHeroDemo: View {
    var body: some View {
        ScatterCollageHero(autoPlay: true)
            .background(Color(hex: 0xfafaf7).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    ScatterCollageHeroDemo()
}
