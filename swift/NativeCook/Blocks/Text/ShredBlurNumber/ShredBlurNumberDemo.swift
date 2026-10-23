import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ShredBlurNumberDemo: View {
    var body: some View {
        ShredBlurNumber(autoPlay: true)
    }
}

#Preview {
    ShredBlurNumberDemo()
}
