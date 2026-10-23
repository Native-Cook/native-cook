import SwiftUI

struct TicketButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x190e1c)) { TicketButton(autoPlay: true) }
    }
}

#Preview {
    TicketButtonDemo()
}
