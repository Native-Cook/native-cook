import SwiftUI

/// The flipbook alone on its table, riffling by itself.
struct FlipbookScrubDemo: View {
    var body: some View {
        FlipbookScrub(autoPlay: true)
            .background(Color(hex: FlipbookScrubMetrics.table).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    FlipbookScrubDemo()
}
