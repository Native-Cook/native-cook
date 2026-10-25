import SwiftUI

/// The frosted advisory on the whole screen, wiped and refrozen by itself.
struct FrostWipeAlertDemo: View {
    var body: some View {
        FrostWipeAlert(autoPlay: true)
            .background(Color(hex: 0x0a1424).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    FrostWipeAlertDemo()
}
