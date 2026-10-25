import SwiftUI

/// The diorama fills the screen, the status bar hidden like the React Native demo.
struct PaperCutDioramaSplashDemo: View {
    var body: some View {
        PaperCutDioramaSplash(autoPlay: true)
            .background(PaperCutDioramaSplashPalette.bg.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PaperCutDioramaSplashDemo()
}
