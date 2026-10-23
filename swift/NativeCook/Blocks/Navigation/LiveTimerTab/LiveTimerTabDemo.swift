import SwiftUI

/// The block is a whole screen with its own background, so it is shown without a stage.
struct LiveTimerTabDemo: View {
    var body: some View {
        LiveTimerTab(autoPlay: true)
    }
}

#Preview {
    LiveTimerTabDemo()
}
