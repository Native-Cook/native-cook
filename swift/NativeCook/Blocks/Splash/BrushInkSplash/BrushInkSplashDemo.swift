import SwiftUI

/// The launch screen on the whole screen, painting itself.
struct BrushInkSplashDemo: View {
    var body: some View {
        BrushInkSplash(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    BrushInkSplashDemo()
}
