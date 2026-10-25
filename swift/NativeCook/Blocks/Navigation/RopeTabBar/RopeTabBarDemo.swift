import SwiftUI

/// The whole "Basecamp" screen, playing its 8 s loop, with the status bar hidden.
struct RopeTabBarDemo: View {
    var body: some View {
        RopeTabBar(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    RopeTabBarDemo()
}
