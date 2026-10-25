import SwiftUI

/// The travel journal's postcard on the whole screen, the handstamp cancelling it by itself.
struct PostcardStampCancelDemo: View {
    var body: some View {
        PostcardStampCancel(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    PostcardStampCancelDemo()
}
