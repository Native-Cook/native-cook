import SwiftUI

struct TravelPassStackDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x1d1e30), height: 660) { TravelPassStack(autoPlay: true) }
    }
}

#Preview {
    TravelPassStackDemo()
}
