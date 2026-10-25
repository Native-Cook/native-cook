import SwiftUI

/// The whole budget screen, playing its 8 s loop, with the status bar hidden.
struct AbacusExpenseCounterDemo: View {
    var body: some View {
        AbacusExpenseCounter(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    AbacusExpenseCounterDemo()
}
