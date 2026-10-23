import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct BubbleTabBarDemo: View {
    var body: some View {
        BubbleTabBar(autoPlay: true)
    }
}

#Preview {
    BubbleTabBarDemo()
}
