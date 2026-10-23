import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct AccountScrubTabBarDemo: View {
    var body: some View {
        AccountScrubTabBar(autoPlay: true)
    }
}

#Preview {
    AccountScrubTabBarDemo()
}
