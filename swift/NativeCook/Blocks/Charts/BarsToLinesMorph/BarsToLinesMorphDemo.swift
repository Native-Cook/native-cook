import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct BarsToLinesMorphDemo: View {
    var body: some View {
        BarsToLinesMorph(autoPlay: true)
    }
}

#Preview {
    BarsToLinesMorphDemo()
}
