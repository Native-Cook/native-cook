import SwiftUI

/// The hotel page on the whole screen, its sheet dragged up and down by itself.
struct JellySheetDemo: View {
    var body: some View {
        JellySheet(autoPlay: true)
            .background(Color(hex: 0x000000).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    JellySheetDemo()
}
