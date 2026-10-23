import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct SplitFlapBoardDemo: View {
    var body: some View {
        SplitFlapBoard(autoPlay: true)
    }
}

#Preview {
    SplitFlapBoardDemo()
}
