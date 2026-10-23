import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ActionGridTabBarDemo: View {
    var body: some View {
        ActionGridTabBar(autoPlay: true)
    }
}

#Preview {
    ActionGridTabBarDemo()
}
