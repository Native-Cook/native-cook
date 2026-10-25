import SwiftUI

/// The candle ritual screen alone, full screen, striking its match until touched.
struct MatchstickStrikeButtonDemo: View {
    var body: some View {
        MatchstickStrikeButton(autoPlay: true)
            .background(Color(hex: 0x150E0A).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    MatchstickStrikeButtonDemo()
}
