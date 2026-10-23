import SwiftUI

struct ThinkingButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x17191b)) { ThinkingButton(autoPlay: true) }
    }
}

#Preview {
    ThinkingButtonDemo()
}
