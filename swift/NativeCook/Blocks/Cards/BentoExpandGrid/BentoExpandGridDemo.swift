import SwiftUI

/// The bento on the whole screen, opening and closing its notes by itself.
struct BentoExpandGridDemo: View {
    var body: some View {
        BentoExpandGrid(autoPlay: true)
            .background(Color(hex: 0xf1ebe0).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    BentoExpandGridDemo()
}
