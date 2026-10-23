import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct CircleRevealMenuDemo: View {
    var body: some View {
        CircleRevealMenu(autoPlay: true)
    }
}

#Preview {
    CircleRevealMenuDemo()
}
