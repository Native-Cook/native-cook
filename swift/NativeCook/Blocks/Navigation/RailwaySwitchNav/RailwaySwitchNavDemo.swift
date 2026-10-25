import SwiftUI

/// The signal box on the whole screen, its levers pulled and its train run by themselves.
struct RailwaySwitchNavDemo: View {
    var body: some View {
        RailwaySwitchNav(autoPlay: true)
            .background(RailwaySwitchNav.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    RailwaySwitchNavDemo()
}
