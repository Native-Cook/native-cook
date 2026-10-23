import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ValueWordWheelDemo: View {
    var body: some View {
        ValueWordWheel(autoPlay: true)
    }
}

#Preview {
    ValueWordWheelDemo()
}
