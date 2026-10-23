import SwiftUI

struct FlightStatusCardDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf1f1f1), height: 420) { FlightStatusCard(autoPlay: true) }
    }
}

#Preview {
    FlightStatusCardDemo()
}
