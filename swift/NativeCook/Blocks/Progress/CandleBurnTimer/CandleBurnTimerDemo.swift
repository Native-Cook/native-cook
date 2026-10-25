import SwiftUI

/// The Ember timer on the whole screen, its candle burning down and relit by itself.
struct CandleBurnTimerDemo: View {
    var body: some View {
        CandleBurnTimer(autoPlay: true)
            .background(CandleBurnTimerMetrics.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    CandleBurnTimerDemo()
}
