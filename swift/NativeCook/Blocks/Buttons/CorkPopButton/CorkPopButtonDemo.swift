import SwiftUI

/// The order-placed screen on its own, popping its champagne cork by itself.
struct CorkPopButtonDemo: View {
    var body: some View {
        CorkPopButton(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    CorkPopButtonDemo()
}
