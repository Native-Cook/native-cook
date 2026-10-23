import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct SwipeFlipWordDemo: View {
    var body: some View {
        SwipeFlipWord(autoPlay: true)
    }
}

#Preview {
    SwipeFlipWordDemo()
}
