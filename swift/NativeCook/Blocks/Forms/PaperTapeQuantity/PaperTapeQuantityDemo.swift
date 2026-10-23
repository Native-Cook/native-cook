import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct PaperTapeQuantityDemo: View {
    var body: some View { PaperTapeQuantity(autoPlay: true) }
}

#Preview { PaperTapeQuantityDemo() }
