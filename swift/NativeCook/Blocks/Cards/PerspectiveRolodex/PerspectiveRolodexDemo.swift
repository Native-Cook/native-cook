import SwiftUI

/// The rolodex alone on a black screen, turning by itself.
struct PerspectiveRolodexDemo: View {
    var body: some View {
        PerspectiveRolodex(autoPlay: true)
            .background(Color(hex: 0x000000).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PerspectiveRolodexDemo()
}
