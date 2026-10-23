import SwiftUI

struct WalletFirstDepositDemo: View {
    var body: some View {
        WalletFirstDeposit(autoPlay: autoPlay)
            .background(Color.white.ignoresSafeArea())
    }

    private var autoPlay: Bool {
        #if DEBUG
        // Exercise the public manual mode without changing the component or its default demo.
        return !ProcessInfo.processInfo.arguments.contains("-wallet-manual")
        #else
        return true
        #endif
    }
}

#Preview {
    WalletFirstDepositDemo()
}
