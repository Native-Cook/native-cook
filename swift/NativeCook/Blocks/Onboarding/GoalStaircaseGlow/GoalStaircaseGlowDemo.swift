import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct GoalStaircaseGlowDemo: View {
    var body: some View {
        GoalStaircaseGlow(autoPlay: true)
    }
}

#Preview {
    GoalStaircaseGlowDemo()
}
