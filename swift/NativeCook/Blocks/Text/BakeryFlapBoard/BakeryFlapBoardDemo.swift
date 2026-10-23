import SwiftUI

/// The bakery's flap board on its walnut wall, changing lines and opening a card by itself.
struct BakeryFlapBoardDemo: View {
    var body: some View {
        BakeryFlapBoard(autoPlay: true)
            .background(Color(hex: 0x1a0f09).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    BakeryFlapBoardDemo()
}
