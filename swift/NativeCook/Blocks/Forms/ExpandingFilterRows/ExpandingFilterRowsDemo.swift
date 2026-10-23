import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ExpandingFilterRowsDemo: View {
    var body: some View { ExpandingFilterRows(autoPlay: true) }
}

#Preview { ExpandingFilterRowsDemo() }

