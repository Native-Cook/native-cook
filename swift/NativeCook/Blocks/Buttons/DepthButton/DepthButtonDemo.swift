import SwiftUI

struct DepthButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf5e9d3)) { DepthButtonGroup(autoPlay: true) }
    }
}

#Preview {
    DepthButtonDemo()
}
