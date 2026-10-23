import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct DetentMethodSheetDemo: View {
    var body: some View {
        DetentMethodSheet(autoPlay: true)
    }
}

#Preview {
    DetentMethodSheetDemo()
}
