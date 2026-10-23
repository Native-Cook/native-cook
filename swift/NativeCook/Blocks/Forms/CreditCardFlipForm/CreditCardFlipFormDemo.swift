import SwiftUI

/// The payment form on the whole screen, filling in a card and paying by itself.
struct CreditCardFlipFormDemo: View {
    var body: some View {
        CreditCardFlipForm(autoPlay: true)
            .background(CreditCardFlipFormMetrics.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    CreditCardFlipFormDemo()
}
