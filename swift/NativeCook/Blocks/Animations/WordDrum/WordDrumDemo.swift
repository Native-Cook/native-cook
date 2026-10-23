import SwiftUI

/// The drum alone on a black screen, turning and choosing by itself.
struct WordDrumDemo: View {
    var body: some View {
        WordDrum(autoPlay: true)
            .background(Color(hex: 0x000000).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    WordDrumDemo()
}
