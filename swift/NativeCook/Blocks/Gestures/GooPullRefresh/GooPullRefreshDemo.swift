import SwiftUI

/// The feed on its own, pulling, refreshing and scrolling by itself.
struct GooPullRefreshDemo: View {
    var body: some View {
        GooPullRefresh(autoPlay: true)
            .background(Color(hex: GooPullRefreshMetrics.page).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    GooPullRefreshDemo()
}
