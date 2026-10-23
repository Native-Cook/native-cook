import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct OrbitUploadDropzoneDemo: View {
    var body: some View {
        OrbitUploadDropzone(autoPlay: true)
    }
}

#Preview {
    OrbitUploadDropzoneDemo()
}
