import SwiftUI

struct GradientSubscribeButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x000000)) { GradientSubscribeButton(autoPlay: true) }
    }
}

#Preview {
    GradientSubscribeButtonDemo()
}
