import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct BlurEdgeTypeInDemo: View {
    var body: some View {
        BlurEdgeTypeIn(autoPlay: true)
    }
}

#Preview {
    BlurEdgeTypeInDemo()
}
