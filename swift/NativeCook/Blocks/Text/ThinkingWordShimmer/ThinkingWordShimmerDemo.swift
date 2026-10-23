import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct ThinkingWordShimmerDemo: View {
    var body: some View {
        ThinkingWordShimmer(autoPlay: true)
    }
}

#Preview {
    ThinkingWordShimmerDemo()
}
