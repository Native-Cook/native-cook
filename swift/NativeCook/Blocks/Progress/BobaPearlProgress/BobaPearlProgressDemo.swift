import SwiftUI

/// The order screen on the whole screen, brewing by itself.
struct BobaPearlProgressDemo: View {
    var body: some View {
        BobaPearlProgress(autoPlay: true)
            .background(BobaPearlProgressArt.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    BobaPearlProgressDemo()
}
