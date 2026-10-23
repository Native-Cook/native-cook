import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct LiquidSwipePagesDemo: View {
    var body: some View {
        LiquidSwipePages(autoPlay: true)
    }
}

#Preview {
    LiquidSwipePagesDemo()
}
