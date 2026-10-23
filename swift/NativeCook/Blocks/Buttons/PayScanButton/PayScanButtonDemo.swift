import SwiftUI

struct PayScanButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe7fdff)) { PayScanButton(autoPlay: true) }
    }
}

#Preview {
    PayScanButtonDemo()
}
