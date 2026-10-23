import SwiftUI

/// The board on the whole screen, moving and peeling its stickers by itself.
struct StickerBoardDemo: View {
    var body: some View {
        StickerBoard(autoPlay: true)
            .background(Color(hex: 0x8a6440).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    StickerBoardDemo()
}
