import SwiftUI

struct ApplyCheckButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf3f3f3)) { ApplyCheckButton(autoPlay: true) }
    }
}

#Preview {
    ApplyCheckButtonDemo()
}
