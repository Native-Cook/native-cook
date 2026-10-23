import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct DotMatrixCameraPadDemo: View {
    var body: some View {
        DotMatrixCameraPad(autoPlay: true)
    }
}

#Preview {
    DotMatrixCameraPadDemo()
}
