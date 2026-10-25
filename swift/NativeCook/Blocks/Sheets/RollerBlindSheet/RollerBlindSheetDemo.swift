import SwiftUI

/// The bedroom fills the whole screen; the blind is pulled down and rolled up by itself.
struct RollerBlindSheetDemo: View {
    var body: some View {
        RollerBlindSheet(autoPlay: true)
            .background(RollerBlindSheetStyle.background.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    RollerBlindSheetDemo()
}
