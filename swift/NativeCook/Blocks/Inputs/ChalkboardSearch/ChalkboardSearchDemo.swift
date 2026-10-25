import SwiftUI

/// The café's menu search on the whole screen, chalking and wiping a query by itself.
struct ChalkboardSearchDemo: View {
    var body: some View {
        ChalkboardSearch(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    ChalkboardSearchDemo()
}
