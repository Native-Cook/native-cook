import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct TapeReelPhysicsDemo: View {
    var body: some View {
        TapeReelPhysics(autoPlay: true)
    }
}

#Preview {
    TapeReelPhysicsDemo()
}
