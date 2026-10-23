import SwiftUI

struct RainbowOutlineSearchDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xF3F3F3), height: 240) { RainbowOutlineSearch(autoPlay: true) }
            // The stage stays put when the keyboard comes up.
            .ignoresSafeArea(.keyboard)
    }
}

#Preview {
    RainbowOutlineSearchDemo()
}
