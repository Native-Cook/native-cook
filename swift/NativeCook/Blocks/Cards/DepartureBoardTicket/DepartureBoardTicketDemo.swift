import SwiftUI

struct DepartureBoardTicketDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf3f3f5), height: 640) { DepartureBoardTicket(autoPlay: true) }
    }
}

#Preview {
    DepartureBoardTicketDemo()
}
