import SwiftUI

struct CutoutWindowCardDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x0e1411), height: 600) { CutoutWindowCard(autoPlay: true) }
    }
}

#Preview {
    CutoutWindowCardDemo()
}
