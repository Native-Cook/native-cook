import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct TrafficJamShakeDemo: View {
    var body: some View {
        TrafficJamShake(autoPlay: true)
    }
}

#Preview {
    TrafficJamShakeDemo()
}
