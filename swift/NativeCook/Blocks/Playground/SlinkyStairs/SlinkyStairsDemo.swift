import SwiftUI

/// The desk-toy playground on its own, the slinky walking down the stairs by itself.
struct SlinkyStairsDemo: View {
    var body: some View {
        SlinkyStairs(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    SlinkyStairsDemo()
}
