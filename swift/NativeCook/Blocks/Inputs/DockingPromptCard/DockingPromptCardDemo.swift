import SwiftUI

struct DockingPromptCardDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x171717), height: 480) { DockingPromptCard(autoPlay: true) }
            // The stage stays put when the keyboard comes up.
            .ignoresSafeArea(.keyboard)
    }
}

#Preview {
    DockingPromptCardDemo()
}
