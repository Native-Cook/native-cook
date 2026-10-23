import SwiftUI

struct StampedTicketPileDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf7f1f1), height: 560) { StampedTicketPile(autoPlay: true) }
    }
}

#Preview {
    StampedTicketPileDemo()
}
