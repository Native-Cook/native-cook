import SwiftUI

/// The block fills the screen with its own backdrop, so it is shown without a stage.
struct SemicircleSpendGaugeDemo: View {
    var body: some View {
        SemicircleSpendGauge(autoPlay: true)
    }
}

#Preview {
    SemicircleSpendGaugeDemo()
}
