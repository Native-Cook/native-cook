import SwiftUI

struct SpendingOrbitDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x191919), height: 370) { SpendingOrbit(autoPlay: true) }
    }
}

#Preview {
    SpendingOrbitDemo()
}
