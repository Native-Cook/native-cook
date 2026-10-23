import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage. It starts
/// on the last tab, so the first hop is the long one.
struct HoppingDotTabBarDemo: View {
    var body: some View {
        HoppingDotTabBar(initialTab: 3, autoPlay: true)
    }
}

#Preview {
    HoppingDotTabBarDemo()
}
