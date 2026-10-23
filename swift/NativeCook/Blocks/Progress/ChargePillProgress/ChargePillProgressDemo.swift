import SwiftUI

struct ChargePillProgressDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xebebeb), height: 250) { ChargePillProgress(autoPlay: true) }
    }
}

#Preview {
    ChargePillProgressDemo()
}
