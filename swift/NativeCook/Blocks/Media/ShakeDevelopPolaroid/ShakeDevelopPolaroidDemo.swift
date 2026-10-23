import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ShakeDevelopPolaroidDemo: View {
    var body: some View {
        ShakeDevelopPolaroid(autoPlay: true)
    }
}

#Preview {
    ShakeDevelopPolaroidDemo()
}
