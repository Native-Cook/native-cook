import SwiftUI

/// Ink and water on the whole screen, dropping ink by itself, under a hidden status bar.
struct InkBleedRevealDemo: View {
    var body: some View {
        InkBleedReveal(autoPlay: true)
            .background(Color(hex: 0xf3efe7).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    InkBleedRevealDemo()
}
