import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct DotIconTabBarDemo: View {
    var body: some View {
        DotIconTabBar(autoPlay: true)
    }
}

#Preview {
    DotIconTabBarDemo()
}
