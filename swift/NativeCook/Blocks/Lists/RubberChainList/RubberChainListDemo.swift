import SwiftUI

/// The list of shows on its own, flinging and opening a row by itself.
struct RubberChainListDemo: View {
    var body: some View {
        RubberChainList(autoPlay: true)
            .background(Color(hex: RubberChainListMetrics.background).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    RubberChainListDemo()
}
