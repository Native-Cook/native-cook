import SwiftUI

struct StayBookingCardDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xececee), height: 520) { StayBookingCard(autoPlay: true) }
    }
}

#Preview {
    StayBookingCardDemo()
}
