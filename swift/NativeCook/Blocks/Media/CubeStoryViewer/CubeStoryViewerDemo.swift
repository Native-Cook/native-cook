import SwiftUI

/// The stories on the whole screen, playing by themselves under a hidden status bar.
struct CubeStoryViewerDemo: View {
    var body: some View {
        CubeStoryViewer(autoPlay: true)
            .background(Color(hex: 0x000000).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    CubeStoryViewerDemo()
}
