import SwiftUI

/// The proofing timer alone, rising, shaping and baking its loaf by itself.
struct BreadRisingProgressDemo: View {
    var body: some View {
        BreadRisingProgress(autoPlay: true)
            .background(BreadRisingProgressColors.root.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    BreadRisingProgressDemo()
}
