import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct SwipeDeckToolbarDemo: View {
    var body: some View {
        SwipeDeckToolbar(autoPlay: true)
    }
}

#Preview {
    SwipeDeckToolbarDemo()
}
