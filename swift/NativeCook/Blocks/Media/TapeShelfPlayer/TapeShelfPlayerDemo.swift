import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct TapeShelfPlayerDemo: View {
    var body: some View {
        TapeShelfPlayer(autoPlay: true)
    }
}

#Preview {
    TapeShelfPlayerDemo()
}
