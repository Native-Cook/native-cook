import SwiftUI

/// The rain gauges on the whole screen, draining, refilling under the rain and lifting Thursday.
struct RainGaugeChartDemo: View {
    var body: some View {
        RainGaugeChart(autoPlay: true)
            .background(RainGaugeChart.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    RainGaugeChartDemo()
}
