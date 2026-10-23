import SwiftUI

struct RulerDialDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf5f5f6), height: 230) { RulerDial(autoPlay: true) }
    }
}

#Preview {
    RulerDialDemo()
}
