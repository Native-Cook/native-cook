import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct StripeSwirlTypeDemo: View {
    var body: some View {
        StripeSwirlType(autoPlay: true)
    }
}

#Preview {
    StripeSwirlTypeDemo()
}
