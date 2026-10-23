import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct WatchfulEyesPermissionDemo: View {
    var body: some View {
        WatchfulEyesPermission(autoPlay: true)
    }
}

#Preview {
    WatchfulEyesPermissionDemo()
}
