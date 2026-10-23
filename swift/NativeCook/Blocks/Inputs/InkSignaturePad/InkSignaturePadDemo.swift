import SwiftUI

/// The last page of a lease on the whole screen, signing, replaying, sealing and wiping itself.
struct InkSignaturePadDemo: View {
    var body: some View {
        InkSignaturePad(autoPlay: true)
            .background(InkSignaturePadMetrics.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    InkSignaturePadDemo()
}
