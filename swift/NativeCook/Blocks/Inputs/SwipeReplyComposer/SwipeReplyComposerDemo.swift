import SwiftUI

struct SwipeReplyComposerDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x111110), height: 500) { SwipeReplyComposer(autoPlay: true) }
            // The panel stays where it is when the keyboard comes up.
            .ignoresSafeArea(.keyboard)
    }
}

#Preview {
    SwipeReplyComposerDemo()
}
