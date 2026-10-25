import SwiftUI

/// The supper-club screen on the whole screen, pulling the cloth by itself.
struct TableclothPullTrickDemo: View {
    var body: some View {
        TableclothPullTrick(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    TableclothPullTrickDemo()
}
