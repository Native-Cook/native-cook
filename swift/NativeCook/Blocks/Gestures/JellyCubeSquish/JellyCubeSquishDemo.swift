import SwiftUI

/// The dessert-bar screen on the whole screen, poking and stretching the jelly by itself.
struct JellyCubeSquishDemo: View {
    var body: some View {
        JellyCubeSquish(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    JellyCubeSquishDemo()
}
