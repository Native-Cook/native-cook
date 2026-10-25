import SwiftUI

/// The viewer and its reel fill the screen, the status bar hidden like the React Native demo.
struct ViewmasterReelDemo: View {
    var body: some View {
        ViewmasterReel(autoPlay: true)
            .background(ViewmasterReelPalette.bg.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    ViewmasterReelDemo()
}
