import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ColorShiftCardStackDemo: View {
    var body: some View {
        ColorShiftCardStack(autoPlay: true)
    }
}

#Preview {
    ColorShiftCardStackDemo()
}
