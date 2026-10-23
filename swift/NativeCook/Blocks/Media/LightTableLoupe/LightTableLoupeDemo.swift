import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct LightTableLoupeDemo: View {
    var body: some View {
        LightTableLoupe(autoPlay: true)
    }
}

#Preview {
    LightTableLoupeDemo()
}
