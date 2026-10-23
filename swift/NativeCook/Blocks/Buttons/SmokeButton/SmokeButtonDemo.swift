import SwiftUI

struct SmokeButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x050505), height: 260) { SmokeButton() }
    }
}

#Preview {
    SmokeButtonDemo()
}
