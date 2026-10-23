import SwiftUI

/// The sheet of bubble wrap alone on its pastel backdrop, popping a smiley and sweeping by itself.
struct BubbleWrapPopDemo: View {
    var body: some View {
        BubbleWrapPop(autoPlay: true)
            .background(Color(hex: 0xFBF3EA).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    BubbleWrapPopDemo()
}
