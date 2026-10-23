import SwiftUI

/// The meadow on the whole screen, its swarm gathering, scattering and syncing by itself, under a
/// hidden status bar.
struct FireflySwarmDemo: View {
    var body: some View {
        FireflySwarm(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    FireflySwarmDemo()
}
