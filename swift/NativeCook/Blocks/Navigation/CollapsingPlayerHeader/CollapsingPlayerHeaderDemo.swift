import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct CollapsingPlayerHeaderDemo: View {
    var body: some View {
        CollapsingPlayerHeader(autoPlay: true)
    }
}

#Preview {
    CollapsingPlayerHeaderDemo()
}
