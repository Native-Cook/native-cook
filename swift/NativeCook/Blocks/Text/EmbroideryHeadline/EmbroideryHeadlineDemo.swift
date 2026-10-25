import SwiftUI

/// The magazine cover alone, its headline unpicked and stitched again by itself.
struct EmbroideryHeadlineDemo: View {
    var body: some View {
        EmbroideryHeadline(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    EmbroideryHeadlineDemo()
}
