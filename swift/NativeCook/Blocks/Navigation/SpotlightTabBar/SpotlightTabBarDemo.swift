import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct SpotlightTabBarDemo: View {
    var body: some View {
        SpotlightTabBar(autoPlay: true)
    }
}

#Preview {
    SpotlightTabBarDemo()
}
