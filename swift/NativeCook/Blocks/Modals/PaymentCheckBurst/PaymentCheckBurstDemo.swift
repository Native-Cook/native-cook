import SwiftUI

/// The block fills the screen, so it is shown without a stage.
struct PaymentCheckBurstDemo: View {
    var body: some View {
        PaymentCheckBurst(autoPlay: true)
    }
}

#Preview {
    PaymentCheckBurstDemo()
}
