import SwiftUI

/// The Explore screen on the whole screen, switching its ingots into coins and back by itself.
struct IngotCoinSwitchDemo: View {
    var body: some View {
        IngotCoinSwitch(autoPlay: true)
    }
}

#Preview {
    IngotCoinSwitchDemo()
}
