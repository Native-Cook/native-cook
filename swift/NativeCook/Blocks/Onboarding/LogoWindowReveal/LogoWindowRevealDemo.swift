import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct LogoWindowRevealDemo: View {
    var body: some View {
        LogoWindowReveal(autoPlay: true)
    }
}

#Preview {
    LogoWindowRevealDemo()
}
