import SwiftUI

struct FlightProgressDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x000000), height: 440) {
            VStack(spacing: 22) {
                FlightProgress(autoPlay: true)
                FlightProgress(autoPlay: true, tint: .orange)
            }
        }
    }
}

#Preview {
    FlightProgressDemo()
}
