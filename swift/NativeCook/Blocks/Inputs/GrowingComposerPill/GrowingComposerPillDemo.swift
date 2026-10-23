import SwiftUI

struct GrowingComposerPillDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf1eff8), height: 460) { GrowingComposerPill(autoPlay: true) }
            // The composer stays where it is when the keyboard comes up.
            .ignoresSafeArea(.keyboard)
    }
}

#Preview {
    GrowingComposerPillDemo()
}
