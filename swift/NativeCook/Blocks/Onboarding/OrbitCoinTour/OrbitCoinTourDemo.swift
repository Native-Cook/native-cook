import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct OrbitCoinTourDemo: View {
    var body: some View {
        OrbitCoinTour(autoPlay: true)
    }
}

#Preview {
    OrbitCoinTourDemo()
}
