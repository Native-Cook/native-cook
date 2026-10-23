import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct RevolvingTextRingDemo: View {
    var body: some View {
        RevolvingTextRing(autoPlay: true)
    }
}

#Preview {
    RevolvingTextRingDemo()
}
