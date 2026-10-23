import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct RollingCountdownDigitsDemo: View {
    var body: some View {
        RollingCountdownDigits(autoPlay: true)
    }
}

#Preview {
    RollingCountdownDigitsDemo()
}
