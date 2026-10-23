import SwiftUI

/// The sand garden on the whole screen, raking, ringing and smoothing itself, under a hidden status bar.
struct ZenSandGardenDemo: View {
    var body: some View {
        ZenSandGarden(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    ZenSandGardenDemo()
}
