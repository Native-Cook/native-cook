import SwiftUI

struct TallyCounterButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x2c2c2c), height: 240) { TallyCounterButton(autoPlay: true) }
    }
}

#Preview {
    TallyCounterButtonDemo()
}
