import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct SubitemTabBarDemo: View {
    var body: some View {
        SubitemTabBar(autoPlay: true)
    }
}

#Preview {
    SubitemTabBarDemo()
}
