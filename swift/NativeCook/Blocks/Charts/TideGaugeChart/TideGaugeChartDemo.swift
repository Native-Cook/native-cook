import SwiftUI

/// The tide screen on the whole display, scrubbing its chart by itself.
struct TideGaugeChartDemo: View {
    var body: some View {
        TideGaugeChart(autoPlay: true)
            .background(Color(hex: 0x081822).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    TideGaugeChartDemo()
}
