import SwiftUI

/// The Forge screen on the whole screen, its metal morphing by itself.
struct LiquidMetalMorphDemo: View {
    var body: some View {
        LiquidMetalMorph(autoPlay: true)
            .background(LiquidMetalMorphMetrics.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    LiquidMetalMorphDemo()
}
