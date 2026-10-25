import SwiftUI

/// The confirm sheet alone on the whole screen, lighting and sending its lantern by itself.
struct PaperLanternConfirmDemo: View {
    var body: some View {
        PaperLanternConfirm(autoPlay: true)
            .background(PaperLanternConfirmPalette.night.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    PaperLanternConfirmDemo()
}
