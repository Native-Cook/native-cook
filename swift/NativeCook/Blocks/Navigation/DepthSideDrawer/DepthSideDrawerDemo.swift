import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct DepthSideDrawerDemo: View {
    var body: some View {
        DepthSideDrawer(autoPlay: true)
    }
}

#Preview {
    DepthSideDrawerDemo()
}
