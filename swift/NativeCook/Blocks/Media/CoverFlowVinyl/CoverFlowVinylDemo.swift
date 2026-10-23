import SwiftUI

/// The crate on the whole screen, flipping and playing by itself under a hidden status bar.
struct CoverFlowVinylDemo: View {
    var body: some View {
        CoverFlowVinyl(autoPlay: true)
            .background(Color(hex: 0x0d0c0b).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    CoverFlowVinylDemo()
}
