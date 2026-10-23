import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct BreathingTideWelcomeDemo: View {
    var body: some View {
        BreathingTideWelcome(autoPlay: true)
    }
}

#Preview {
    BreathingTideWelcomeDemo()
}
