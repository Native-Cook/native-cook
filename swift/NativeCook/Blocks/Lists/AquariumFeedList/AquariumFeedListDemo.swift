import SwiftUI

/// The reef log fills the screen, scrolling and feeding by itself.
struct AquariumFeedListDemo: View {
    var body: some View {
        AquariumFeedList(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    AquariumFeedListDemo()
}
