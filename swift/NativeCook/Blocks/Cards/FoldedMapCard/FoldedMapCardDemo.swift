import SwiftUI

/// The trail screen on the whole display, unfolding its map by itself.
struct FoldedMapCardDemo: View {
    var body: some View {
        FoldedMapCard(autoPlay: true)
            .background(FoldedMapCardStyle.screen.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    FoldedMapCardDemo()
}
