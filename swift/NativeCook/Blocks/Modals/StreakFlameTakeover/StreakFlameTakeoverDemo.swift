import SwiftUI

/// The block fills the screen, so it is shown without a stage.
struct StreakFlameTakeoverDemo: View {
    var body: some View {
        StreakFlameTakeover(autoPlay: true)
    }
}

#Preview {
    StreakFlameTakeoverDemo()
}
