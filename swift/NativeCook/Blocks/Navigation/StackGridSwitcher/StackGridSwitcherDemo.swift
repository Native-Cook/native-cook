import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct StackGridSwitcherDemo: View {
    var body: some View {
        StackGridSwitcher(autoPlay: true)
    }
}

#Preview {
    StackGridSwitcherDemo()
}
