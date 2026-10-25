import SwiftUI

/// The member card form on the whole screen, typing and punching a card by itself.
struct PunchCardFormDemo: View {
    var body: some View {
        PunchCardForm(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    PunchCardFormDemo()
}
