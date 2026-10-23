import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct RecordStackDeckDemo: View {
    var body: some View {
        RecordStackDeck(autoPlay: true)
    }
}

#Preview {
    RecordStackDeckDemo()
}
