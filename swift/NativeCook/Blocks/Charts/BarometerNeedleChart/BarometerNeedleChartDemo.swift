import SwiftUI

/// The weather screen on the whole screen, scrubbing through the storm by itself.
struct BarometerNeedleChartDemo: View {
    var body: some View {
        BarometerNeedleChart(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    BarometerNeedleChartDemo()
}
