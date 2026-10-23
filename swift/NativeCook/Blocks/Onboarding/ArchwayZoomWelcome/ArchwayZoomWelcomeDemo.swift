import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ArchwayZoomWelcomeDemo: View {
    var body: some View {
        ArchwayZoomWelcome(autoPlay: true)
    }
}

#Preview {
    ArchwayZoomWelcomeDemo()
}
