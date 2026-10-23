import SwiftUI

/// The table on the whole screen, laying, toppling and standing up its run by itself.
struct DominoChainDemo: View {
    var body: some View {
        DominoChain(autoPlay: true)
            .background(Color(hex: 0x07090a).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    DominoChainDemo()
}
