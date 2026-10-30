import SwiftUI

/// Full screen, with its own safe-area layout and the system status bar.
struct PlasmaSquircleChargeDemo: View {
    var body: some View {
        PlasmaSquircleCharge(autoPlay: true)
    }
}

#Preview {
    PlasmaSquircleChargeDemo()
}
