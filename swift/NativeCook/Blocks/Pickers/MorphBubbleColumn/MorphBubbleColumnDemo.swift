import SwiftUI

/// The survey column full screen, walking its selection, opening a site and closing it by itself.
struct MorphBubbleColumnDemo: View {
    var body: some View {
        MorphBubbleColumn(autoPlay: true)
            .background(Color(hex: 0xf4f4f0).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    MorphBubbleColumnDemo()
}
