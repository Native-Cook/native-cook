import SwiftUI

struct HoldToSpeakInputDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x2b2b2b), height: 300) { HoldToSpeakInput(autoPlay: true) }
    }
}

#Preview {
    HoldToSpeakInputDemo()
}
