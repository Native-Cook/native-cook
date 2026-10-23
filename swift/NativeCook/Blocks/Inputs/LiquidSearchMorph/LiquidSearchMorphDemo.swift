import SwiftUI

/// The shop's home page on the whole screen, opening its search by itself.
struct LiquidSearchMorphDemo: View {
    var body: some View {
        LiquidSearchMorph(autoPlay: true)
            .background(LiquidSearchMorphMetrics.paper.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    LiquidSearchMorphDemo()
}
