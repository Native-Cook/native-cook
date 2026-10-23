import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct SwipeDownDeckDemo: View {
    var body: some View {
        SwipeDownDeck(autoPlay: true)
    }
}

#Preview {
    SwipeDownDeckDemo()
}
