import SwiftUI

/// The sessions on the whole screen, flung, struck and scrolled back by themselves.
struct WindChimeListDemo: View {
    var body: some View {
        WindChimeList(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    WindChimeListDemo()
}
