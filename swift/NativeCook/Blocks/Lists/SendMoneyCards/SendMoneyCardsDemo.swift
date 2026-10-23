import SwiftUI

struct SendMoneyCardsDemo: View {
    var body: some View {
        SendMoneyCards(autoPlay: true)
            .ignoresSafeArea()
            .statusBarHidden()
    }
}

#Preview {
    SendMoneyCardsDemo()
}
