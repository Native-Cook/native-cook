import SwiftUI

/// The ticket screen on the whole screen, the fuse burning by itself.
struct FuseBurnButtonDemo: View {
    var body: some View {
        FuseBurnButton(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    FuseBurnButtonDemo()
}
