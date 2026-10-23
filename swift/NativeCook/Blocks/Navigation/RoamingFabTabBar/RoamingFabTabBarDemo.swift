import SwiftUI

/// The block is a whole screen with its own background, so it is shown without a stage.
struct RoamingFabTabBarDemo: View {
    var body: some View {
        RoamingFabTabBar(autoPlay: true)
    }
}

#Preview {
    RoamingFabTabBarDemo()
}
