import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct StressDotGridDonutDemo: View {
    var body: some View {
        StressDotGridDonut(autoPlay: true)
    }
}

#Preview {
    StressDotGridDonutDemo()
}
