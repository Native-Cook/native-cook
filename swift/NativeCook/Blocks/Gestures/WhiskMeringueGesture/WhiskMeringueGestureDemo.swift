import SwiftUI

/// The countertop fills the whole screen; the whites are whisked into stiff peaks by themselves.
struct WhiskMeringueGestureDemo: View {
    var body: some View {
        WhiskMeringueGesture(autoPlay: true)
            .background(WhiskMeringueGestureStyle.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    WhiskMeringueGestureDemo()
}
