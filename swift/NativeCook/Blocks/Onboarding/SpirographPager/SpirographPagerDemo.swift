import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct SpirographPagerDemo: View {
    var body: some View {
        SpirographPager(autoPlay: true)
    }
}

#Preview {
    SpirographPagerDemo()
}
