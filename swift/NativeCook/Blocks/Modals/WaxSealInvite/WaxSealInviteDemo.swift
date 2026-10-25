import SwiftUI

/// The invitation modal on the whole screen, opening and closing by itself.
struct WaxSealInviteDemo: View {
    var body: some View {
        WaxSealInvite(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    WaxSealInviteDemo()
}
