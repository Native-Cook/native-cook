import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct WalletUnpackWelcomeDemo: View {
    var body: some View {
        WalletUnpackWelcome(autoPlay: true)
    }
}

#Preview {
    WalletUnpackWelcomeDemo()
}
