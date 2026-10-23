import SwiftUI

struct ArrowExpandButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xeff6f9)) { ArrowExpandButton(autoPlay: true) }
    }
}

#Preview {
    ArrowExpandButtonDemo()
}
