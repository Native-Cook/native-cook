import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct RangeMorphPriceLineDemo: View {
    var body: some View {
        RangeMorphPriceLine(autoPlay: true)
    }
}

#Preview {
    RangeMorphPriceLineDemo()
}
