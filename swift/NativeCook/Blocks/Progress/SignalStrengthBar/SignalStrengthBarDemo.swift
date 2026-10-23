import SwiftUI

struct SignalStrengthBarDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x4f6b49), height: 310) { SignalStrengthBar(autoPlay: true) }
    }
}

#Preview {
    SignalStrengthBarDemo()
}
