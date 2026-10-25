import SwiftUI

/// The whole "Studio" screen, playing its 8 s loop, with the status bar hidden.
struct MagnetSnapDockDemo: View {
    var body: some View {
        MagnetSnapDock(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    MagnetSnapDockDemo()
}
