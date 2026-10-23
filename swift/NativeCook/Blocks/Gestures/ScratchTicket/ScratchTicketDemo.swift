import SwiftUI

/// The ticket alone on its green table, scratched, revealed and dealt again by itself.
struct ScratchTicketDemo: View {
    var body: some View {
        ScratchTicket(autoPlay: true)
            .background(Color(hex: 0x13251b).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    ScratchTicketDemo()
}
