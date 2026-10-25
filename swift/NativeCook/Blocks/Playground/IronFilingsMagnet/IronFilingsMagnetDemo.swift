import SwiftUI

/// The sheet of filings on the whole screen, its magnets moving by themselves.
struct IronFilingsMagnetDemo: View {
    var body: some View {
        IronFilingsMagnet(autoPlay: true)
            .background(IronFilingsMagnetArt.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    IronFilingsMagnetDemo()
}
