import SwiftUI

struct GalaxyButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x04000f), height: 250) { GalaxyButton(autoPlay: true) }
    }
}

#Preview {
    GalaxyButtonDemo()
}
