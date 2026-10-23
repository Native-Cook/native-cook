import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct MoodStackColumnsDemo: View {
    var body: some View {
        MoodStackColumns(autoPlay: true)
    }
}

#Preview {
    MoodStackColumnsDemo()
}
