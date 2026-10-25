import SwiftUI

/// The launch screen fills the screen, fuelling and launching by itself.
struct RocketLaunchProgressDemo: View {
    var body: some View {
        RocketLaunchProgress(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    RocketLaunchProgressDemo()
}
