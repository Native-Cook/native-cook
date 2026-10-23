import SwiftUI

/// The herbarium on its paper, pinching itself in and out through the column counts.
struct PinchDensityGridDemo: View {
    var body: some View {
        PinchDensityGrid(autoPlay: true)
            .background(Color(hex: 0xeeece4).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PinchDensityGridDemo()
}
