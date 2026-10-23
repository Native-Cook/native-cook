import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ScrambleDecodeHeadlineDemo: View {
    var body: some View {
        ScrambleDecodeHeadline(autoPlay: true)
    }
}

#Preview {
    ScrambleDecodeHeadlineDemo()
}
