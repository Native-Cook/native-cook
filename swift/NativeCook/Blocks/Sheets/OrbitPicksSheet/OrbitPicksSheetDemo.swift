import SwiftUI

/// The block fills the screen and draws its own status bar, so it is shown without a stage.
struct OrbitPicksSheetDemo: View {
    var body: some View {
        OrbitPicksSheet(autoPlay: true)
    }
}

#Preview {
    OrbitPicksSheetDemo()
}
