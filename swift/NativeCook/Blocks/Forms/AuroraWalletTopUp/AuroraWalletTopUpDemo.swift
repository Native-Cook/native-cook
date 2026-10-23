import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct AuroraWalletTopUpDemo: View {
    var body: some View {
        AuroraWalletTopUp(autoPlay: true)
    }
}

#Preview {
    AuroraWalletTopUpDemo()
}
