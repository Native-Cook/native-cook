import SwiftUI

struct ReloadButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf7f7f7)) { ReloadButton(autoPlay: true) }
    }
}

#Preview {
    ReloadButtonDemo()
}
