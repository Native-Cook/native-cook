import SwiftUI

struct ImagePromptCardDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xF3F3F5), height: 460) { ImagePromptCard(autoPlay: true) }
            // The stage stays put when the keyboard comes up.
            .ignoresSafeArea(.keyboard)
    }
}

#Preview {
    ImagePromptCardDemo()
}
