import SwiftUI

struct PulseFabButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf4f9ff)) { PulseFabButton(autoPlay: true) }
    }
}

#Preview {
    PulseFabButtonDemo()
}
