import SwiftUI

struct GlassLoadingButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x1a1a1a), height: 240) { GlassLoadingButton(autoPlay: true) }
    }
}

#Preview {
    GlassLoadingButtonDemo()
}
