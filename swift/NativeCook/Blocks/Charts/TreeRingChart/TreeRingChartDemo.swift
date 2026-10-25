import SwiftUI

/// The savings slice on the whole screen, its rings shrinking and regrowing by themselves.
struct TreeRingChartDemo: View {
    var body: some View {
        TreeRingChart(autoPlay: true)
            .background(TreeRingChart.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    TreeRingChartDemo()
}
