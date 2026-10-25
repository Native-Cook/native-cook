import SwiftUI

/// The reward screen on the whole screen, its bubbles popping and being blown again by themselves.
struct SoapBubblePopDemo: View {
    var body: some View {
        SoapBubblePop(autoPlay: true)
            .background(SoapBubblePopPalette.base.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    SoapBubblePopDemo()
}
