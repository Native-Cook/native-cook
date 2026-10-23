import SwiftUI

/// The block fills the screen and draws its own status bar, so it is shown without a stage.
struct SheetCardHandoffDemo: View {
    var body: some View {
        SheetCardHandoff(autoPlay: true)
    }
}

#Preview {
    SheetCardHandoffDemo()
}
