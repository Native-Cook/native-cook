import SwiftUI

struct TouchGlowButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x03031a)) { TouchGlowButton(autoPlay: true) }
    }
}

#Preview {
    TouchGlowButtonDemo()
}
