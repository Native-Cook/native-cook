import SwiftUI

struct ChargeWaveCircleDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x141a16), height: 470) { ChargeWaveCircle(autoPlay: true) }
    }
}

#Preview {
    ChargeWaveCircleDemo()
}
