import SwiftUI

/// The movie-night screen on its own, popping a pan of popcorn by itself.
struct PopcornPopButtonDemo: View {
    var body: some View {
        PopcornPopButton(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    PopcornPopButtonDemo()
}
