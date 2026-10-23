import SwiftUI

/// The block is a whole screen with its own background, so it is shown without a stage.
struct LiquidActionPillDemo: View {
    var body: some View {
        LiquidActionPill(autoPlay: true)
    }
}

#Preview {
    LiquidActionPillDemo()
}
