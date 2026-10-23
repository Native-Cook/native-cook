import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct LiquidFillWordDemo: View {
    var body: some View {
        LiquidFillWord(autoPlay: true)
    }
}

#Preview {
    LiquidFillWordDemo()
}
